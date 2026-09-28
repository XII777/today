package `as`.today.missyou.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per journal day.
 *
 * Every user-visible string is stored sealed (§20). The database therefore never
 * contains readable journal text, which is what lets the search feature work
 * without creating a plaintext index.
 *
 * `journal_date` is stored as an ISO-8601 `yyyy-MM-dd` string rather than an epoch
 * value for two reasons: it is exactly what the immutability triggers need to
 * compare, and a journal day is a calendar concept, not an instant.
 */
@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["journal_date"], unique = true),
        Index(value = ["locked_at"]),
        Index(value = ["created_at"]),
    ],
)
internal data class JournalEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "journal_date")
    val journalDate: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /** `null` while the day is still open. Once set it can never be cleared. */
    @ColumnInfo(name = "locked_at")
    val lockedAt: Long?,

    @ColumnInfo(name = "title_sealed")
    val titleSealed: ByteArray?,

    @ColumnInfo(name = "content_sealed")
    val contentSealed: ByteArray,

    @ColumnInfo(name = "content_format")
    val contentFormat: String,

    @ColumnInfo(name = "tags_sealed")
    val tagsSealed: ByteArray?,

    @ColumnInfo(name = "prompt_sealed")
    val promptSealed: ByteArray?,

    @ColumnInfo(name = "writing_duration_millis")
    val writingDurationMillis: Long,

    @ColumnInfo(name = "word_count")
    val wordCount: Int,

    @ColumnInfo(name = "attachment_count")
    val attachmentCount: Int,

    /**
     * Sealed newline-joined SHA-256 fingerprints of the entry's search tokens.
     *
     * Storing fingerprints rather than words means the database cannot be mined for
     * vocabulary, while search can still run without decrypting every document.
     */
    @ColumnInfo(name = "token_index_sealed")
    val tokenIndexSealed: ByteArray,

    @ColumnInfo(name = "integrity_hash")
    val integrityHash: String,
)

/** Metadata for one locally stored attachment. The bytes live outside the database. */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["entry_id"])],
)
internal data class AttachmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "entry_id")
    val entryId: String,

    @ColumnInfo(name = "display_name_sealed")
    val displayNameSealed: ByteArray,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "byte_size")
    val byteSize: Long,

    @ColumnInfo(name = "storage_name")
    val storageName: String,

    @ColumnInfo(name = "content_hash")
    val contentHash: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,
)

/**
 * Singleton row holding the effective journal day.
 *
 * The immutability triggers read [currentDate] on every write, which makes this
 * table part of the security model rather than a cache.
 */
@Entity(tableName = "journal_state")
internal data class JournalStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,

    /** Highest journal day ever observed. May only ever increase. */
    @ColumnInfo(name = "current_date")
    val currentDate: String,

    @ColumnInfo(name = "highest_seen_wall_millis")
    val highestSeenWallMillis: Long,

    /**
     * Transient flag that opens the seal window. It is set and cleared inside a
     * single transaction, so `locked_at` can be stamped onto expired rows even
     * though the table's other updates are refused at that point.
     */
    @ColumnInfo(name = "seal_open")
    val sealOpen: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** Aggregate row used by the Insights screen. */
internal data class InsightsAggregate(
    @ColumnInfo(name = "entry_count") val entryCount: Int,
    @ColumnInfo(name = "total_words") val totalWords: Long,
    @ColumnInfo(name = "total_writing_millis") val totalWritingMillis: Long,
    @ColumnInfo(name = "max_words") val maxWords: Int,
    @ColumnInfo(name = "longest_date") val longestDate: String?,
)

/** Per-day projection for the calendar and archive list. */
internal data class DayProjection(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "journal_date") val journalDate: String,
    @ColumnInfo(name = "locked_at") val lockedAt: Long?,
    @ColumnInfo(name = "title_sealed") val titleSealed: ByteArray?,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "attachment_count") val attachmentCount: Int,
    @ColumnInfo(name = "tags_sealed") val tagsSealed: ByteArray?,
)

/** Entry id plus its sealed token fingerprints, used to build the search index. */
internal data class TokenIndexProjection(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "token_index_sealed") val tokenIndexSealed: ByteArray,
)

/** Sealed tags for every entry, used to compute the "most used tags" insight. */
internal data class TagProjection(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tags_sealed") val tagsSealed: ByteArray?,
)

/** A journal day that is past its rollover and still needs a `locked_at` stamp. */
internal data class UnsealedDay(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "journal_date") val journalDate: String,
)
