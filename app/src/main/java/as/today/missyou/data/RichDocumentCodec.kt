package `as`.today.missyou.data

import `as`.today.missyou.crypto.Digests
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.RichDocument
import kotlinx.serialization.json.Json

/**
 * Single source of truth for how rich documents are serialised.
 *
 * `ignoreUnknownKeys` is what makes a journal survive an app update that adds new
 * block types: an older install reading a newer record degrades gracefully instead
 * of throwing, and combined with the append-only migration policy it guarantees an
 * entry is never lost to a format change.
 */
internal object RichDocumentCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        prettyPrint = false
        classDiscriminator = "type"
    }

    fun encode(document: RichDocument): String = json.encodeToString(RichDocument.serializer(), document)

    fun decode(raw: String): RichDocument = runCatching {
        json.decodeFromString(RichDocument.serializer(), raw)
    }.getOrElse {
        // A document that cannot be parsed is still worth showing as text rather
        // than crashing the reader.
        RichDocument.ofPlainText(raw) { "recovered-${Digests.sha256Hex(raw).take(12)}" }
    }
}

/**
 * Content-addressed integrity hash.
 *
 * Covers every field that must never change after the day rolls over. It is stored
 * in the clear because it is a hash of sealed values: revealing it discloses
 * nothing, while a mismatch tells us the row has been replaced or tampered with
 * behind the app's back.
 */
internal object IntegrityHasher {

    private const val VERSION = "v1"

    /** ASCII unit separator: cannot occur in user text, so fields cannot be confused. */
    private val FIELD_SEPARATOR: String = Character.toString(0x1F)

    fun compute(
        id: String,
        journalDate: String,
        createdAt: Long,
        lockedAt: Long?,
        title: String,
        contentJson: String,
        tags: List<String>,
        prompt: String?,
    ): String {
        val canonical = buildString {
            append(VERSION).append(FIELD_SEPARATOR)
            append(id).append(FIELD_SEPARATOR)
            append(journalDate).append(FIELD_SEPARATOR)
            append(createdAt).append(FIELD_SEPARATOR)
            append(lockedAt ?: -1L).append(FIELD_SEPARATOR)
            append(title).append(FIELD_SEPARATOR)
            append(Digests.sha256Hex(contentJson)).append(FIELD_SEPARATOR)
            append(tags.joinToString(",")).append(FIELD_SEPARATOR)
            append(prompt.orEmpty())
        }
        return Digests.sha256Hex(canonical)
    }

    fun compute(entry: JournalEntry, contentJson: String): String = compute(
        id = entry.id,
        journalDate = entry.journalDate.toString(),
        createdAt = entry.createdAt,
        lockedAt = entry.lockedAt,
        title = entry.title,
        contentJson = contentJson,
        tags = entry.tags,
        prompt = entry.prompt,
    )

    fun verify(entry: JournalEntry, contentJson: String): Boolean =
        entry.integrityHash == compute(entry, contentJson)
}
