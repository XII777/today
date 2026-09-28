package `as`.today.missyou.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** What a journal entry is doing right now (§2). */
enum class EntryState {
    /** The current journal day. Writable until the rollover. */
    EDITABLE,

    /** The day has rolled over. Permanently read-only. */
    LOCKED,
}

/** How the stored body should be interpreted. */
object ContentFormats {
    const val RICH_JSON = "application/vnd.today.rich+json"
    const val PLAIN_TEXT = "text/plain"
}

/**
 * A journal entry: exactly one per local calendar day.
 *
 * [journalDate] is immutable from the moment the row exists. It is the join key for
 * every immutability decision in the app, so it is stored as an ISO-8601 date in a
 * unique-indexed column, which makes the string comparison used by the database
 * triggers agree with `LocalDate` comparison.
 */
data class JournalEntry(
    val id: String,
    val journalDate: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val lockedAt: Long?,
    val title: String,
    val document: RichDocument,
    val contentFormat: String,
    val tags: List<String>,
    val prompt: String?,
    val writingDurationMillis: Long,
    val wordCount: Int,
    val integrityHash: String,
    val attachments: List<Attachment> = emptyList(),
) {
    val state: EntryState get() = if (lockedAt == null) EntryState.EDITABLE else EntryState.LOCKED

    val isLocked: Boolean get() = lockedAt != null

    val isEmptyDocument: Boolean get() = document.isEmpty

    val hasContent: Boolean get() = title.isNotBlank() || !document.isEmpty || tags.isNotEmpty()

    /** Stable string used for previews, search indexing and exports. */
    val searchableText: String
        get() = buildString {
            append(title)
            append('\n')
            append(document.plainText)
            if (tags.isNotEmpty()) {
                append('\n')
                append(tags.joinToString(" "))
            }
            prompt?.let {
                append('\n')
                append(it)
            }
        }

    fun copyForRead(): JournalEntry = copy(
        document = document,
        tags = tags,
    )
}

/**
 * A locally stored file belonging to an entry.
 *
 * The bytes never leave the device. [storageName] is a file name inside the app's
 * private, encrypted attachment directory.
 */
@Serializable
data class Attachment(
    val id: String,
    val entryId: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val storageName: String,
    val contentHash: String,
    val createdAt: Long,
    val width: Int = 0,
    val height: Int = 0,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

    val readableSize: String
        get() = when {
            byteSize < 1024 -> "$byteSize B"
            byteSize < 1024 * 1024 -> "%.0f KB".format(byteSize / 1024.0)
            else -> "%.1f MB".format(byteSize / (1024.0 * 1024.0))
        }
}

/** Everything shown in the "View metadata" sheet of a locked entry. */
@Serializable
data class EntryMetadata(
    val prompt: String? = null,
    val tags: List<String> = emptyList(),
    val entryId: String,
    val journalDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lockedAt: Long? = null,
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val characterCountNoSpaces: Int = 0,
    val readingTimeMinutes: Int = 0,
    val writingDurationMillis: Long = 0,
    val contentFormat: String = ContentFormats.RICH_JSON,
    val integrityHash: String = "",
    val blockCount: Int = 0,
    val attachmentCount: Int = 0,
)
