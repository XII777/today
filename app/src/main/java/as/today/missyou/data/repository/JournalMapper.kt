package `as`.today.missyou.data.repository

import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.crypto.JournalCipher
import `as`.today.missyou.data.RichDocumentCodec
import `as`.today.missyou.data.database.AttachmentEntity
import `as`.today.missyou.data.database.DayProjection
import `as`.today.missyou.data.database.JournalEntryEntity
import `as`.today.missyou.data.search.Tokens
import `as`.today.missyou.domain.model.Attachment
import `as`.today.missyou.domain.model.ContentFormats
import `as`.today.missyou.domain.model.EntryMetadata
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.StyledText
import java.time.LocalDate

/**
 * Translates between the sealed database representation and the domain model.
 *
 * The AAD strings bind every ciphertext to `table|column|rowId`, so a sealed blob
 * copied from one column to another fails authentication instead of silently
 * producing a corrupt journal.
 */
internal class JournalMapper(private val cipher: JournalCipher) {

    fun toDomain(entity: JournalEntryEntity, attachments: List<Attachment> = emptyList()): JournalEntry {
        val contentJson = cipher.open(entity.contentSealed, aad(TABLE, COLUMN_CONTENT, entity.id))
        val tags = decodeTags(entity.tagsSealed, entity.id)
        return JournalEntry(
            id = entity.id,
            journalDate = LocalDate.parse(entity.journalDate),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lockedAt = entity.lockedAt,
            title = decodeTitle(entity.titleSealed, entity.id),
            document = RichDocumentCodec.decode(contentJson),
            contentFormat = entity.contentFormat,
            tags = tags,
            prompt = cipher.openOrNull(entity.promptSealed, aad(TABLE, COLUMN_PROMPT, entity.id)),
            writingDurationMillis = entity.writingDurationMillis,
            wordCount = entity.wordCount,
            integrityHash = entity.integrityHash,
            attachments = attachments,
        )
    }

    fun toSummary(projection: DayProjection): JournalEntrySummary {
        val title = decodeTitle(projection.titleSealed, projection.id)
        val tags = decodeTags(projection.tagsSealed, projection.id)
        return JournalEntrySummary(
            id = projection.id,
            journalDate = LocalDate.parse(projection.journalDate),
            title = title,
            preview = "",
            tags = tags,
            wordCount = projection.wordCount,
            lockedAt = projection.lockedAt,
            updatedAt = projection.updatedAt,
            attachmentCount = projection.attachmentCount,
        )
    }

    fun metadataFor(entry: JournalEntry, contentJson: String): EntryMetadata {
        val plain = entry.document.plainText
        return EntryMetadata(
            prompt = entry.prompt,
            tags = entry.tags,
            entryId = entry.id,
            journalDate = entry.journalDate.toString(),
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
            lockedAt = entry.lockedAt,
            wordCount = entry.wordCount,
            characterCount = plain.length,
            characterCountNoSpaces = TextMetrics.characterCountNoSpaces(plain),
            readingTimeMinutes = TextMetrics.readingTimeMinutes(entry.wordCount),
            writingDurationMillis = entry.writingDurationMillis,
            contentFormat = entry.contentFormat,
            integrityHash = entry.integrityHash,
            blockCount = entry.document.blocks.size,
            attachmentCount = entry.attachments.size,
        )
    }

    // ------------------------------------------------------------------ sealing

    fun sealTitle(value: String, id: String): ByteArray? =
        cipher.sealNullable(value.takeIf { it.isNotEmpty() }, aad(TABLE, COLUMN_TITLE, id))

    fun sealContent(document: RichDocument, id: String): Pair<String, ByteArray> {
        val json = RichDocumentCodec.encode(document)
        return json to cipher.seal(json, aad(TABLE, COLUMN_CONTENT, id))
    }

    fun sealTags(tags: List<String>, id: String): ByteArray? =
        cipher.sealNullable(encodeTags(tags).takeIf { it.isNotEmpty() }, aad(TABLE, COLUMN_TAGS, id))

    fun sealPrompt(prompt: String?, id: String): ByteArray? =
        cipher.sealNullable(prompt?.takeIf { it.isNotBlank() }, aad(TABLE, COLUMN_PROMPT, id))

    fun sealTokenIndex(tokens: List<String>, id: String): ByteArray =
        cipher.seal(Tokens.serialize(tokens), aad(TABLE, COLUMN_TOKEN_INDEX, id))

    fun openTokenIndex(sealed: ByteArray, id: String): List<String> =
        Tokens.deserialize(cipher.open(sealed, aad(TABLE, COLUMN_TOKEN_INDEX, id)))

    // ------------------------------------------------------------------ tags

    fun encodeTags(tags: List<String>): String =
        tags.map { normaliseTag(it) }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    fun decodeTags(sealed: ByteArray?, id: String): List<String> {
        val raw = cipher.openOrNull(sealed, aad(TABLE, COLUMN_TAGS, id)) ?: return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun decodeTitle(sealed: ByteArray?, id: String): String =
        cipher.openOrNull(sealed, aad(TABLE, COLUMN_TITLE, id)).orEmpty()

    fun attachmentOf(entity: AttachmentEntity): Attachment = Attachment(
        id = entity.id,
        entryId = entity.entryId,
        displayName = cipher.open(entity.displayNameSealed, aad(ATTACHMENT_TABLE, ATTACHMENT_NAME, entity.id)),
        mimeType = entity.mimeType,
        byteSize = entity.byteSize,
        storageName = entity.storageName,
        contentHash = entity.contentHash,
        createdAt = entity.createdAt,
        width = entity.width,
        height = entity.height,
    )

    fun sealDisplayName(name: String, attachmentId: String): ByteArray =
        cipher.seal(name, aad(ATTACHMENT_TABLE, ATTACHMENT_NAME, attachmentId))

    /** Builds the search-token set for an entry from title, body, tags and prompt. */
    fun tokensFor(title: String, document: RichDocument, tags: List<String>, prompt: String?): List<String> {
        val body = document.plainText
        val combined = buildString {
            append(title).append('\n')
            append(body)
            tags.forEach { append('\n').append(it.removePrefix("#")) }
            prompt?.let { append('\n').append(it) }
        }
        return Tokens.fromText(combined)
    }

    /** Preview text for list rows, derived from the document. */
    fun previewOf(document: RichDocument, maxChars: Int = 180): String =
        TextMetrics.preview(document.plainText, maxChars)

    /** Builds the starting document for a new entry from the user's template. */
    fun starterDocument(firstBlockId: String, template: String?): RichDocument =
        if (template.isNullOrBlank()) {
            RichDocument.empty(firstBlockId)
        } else {
            RichDocument.ofPlainText(template.trim()) { firstBlockId }
        }

    companion object {
        const val TABLE = "journal_entries"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_TAGS = "tags"
        const val COLUMN_PROMPT = "prompt"
        const val COLUMN_TOKEN_INDEX = "token_index"
        const val ATTACHMENT_TABLE = "attachments"
        const val ATTACHMENT_NAME = "display_name"

        const val DEFAULT_CONTENT_FORMAT = ContentFormats.RICH_JSON

        fun aad(table: String, column: String, rowId: String): String = "$table|$column|$rowId"

        /** `#work` and `work` are the same tag; stored lower-case with a leading `#`. */
        fun normaliseTag(raw: String): String {
            val trimmed = raw.trim().removePrefix("#").lowercase()
            return if (trimmed.isEmpty()) "" else "#$trimmed"
        }
    }
}
