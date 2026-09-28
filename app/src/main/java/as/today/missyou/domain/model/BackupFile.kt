package `as`.today.missyou.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `.qjournal` backup format.
 *
 * ## Design rules
 *
 *  * **Open and documented.** Plain JSON with a published schema version, readable
 *    and writable with any text editor. The user owns the file and nothing in it is
 *    proprietary or locked-in.
 *  * **Self-describing.** Every entry carries the data needed to restore it exactly,
 *    including its `lockedAt`, so a restore reproduces history rather than
 *    reinterpreting it.
 *  * **Attachments inline.** Images and files are base64 embedded, so a backup is a
 *    single portable file with no external references to break.
 *  * **Integrity carrying.** Each entry stores the same `integrityHash` the app
 *    computes, so a tampered backup is detectable before it is imported.
 */
@Serializable
data class BackupFile(
    @SerialName("format") val format: String = FORMAT,
    @SerialName("schemaVersion") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("appVersion") val appVersion: String = "",
    @SerialName("exportedAt") val exportedAt: Long = 0L,
    @SerialName("entries") val entries: List<BackupEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "today-journal-backup"
        const val CURRENT_SCHEMA_VERSION = 1
        const val FILE_EXTENSION = "qjournal"
    }
}

@Serializable
data class BackupEntry(
    val id: String,
    @SerialName("journalDate") val journalDate: String,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long,
    /** `null` means the entry was still open when it was exported. */
    @SerialName("lockedAt") val lockedAt: Long? = null,
    val title: String = "",
    @SerialName("contentFormat") val contentFormat: String = "application/vnd.today.rich+json",
    /** The rich document, stored verbatim so formatting and attachments survive. */
    val document: String,
    val tags: List<String> = emptyList(),
    val prompt: String? = null,
    @SerialName("writingDurationMillis") val writingDurationMillis: Long = 0,
    @SerialName("wordCount") val wordCount: Int = 0,
    @SerialName("integrityHash") val integrityHash: String = "",
    val attachments: List<BackupAttachment> = emptyList(),
)

@Serializable
data class BackupAttachment(
    val id: String,
    @SerialName("displayName") val displayName: String,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("byteSize") val byteSize: Long,
    /** Base64 of the file bytes. */
    val data: String,
    @SerialName("contentHash") val contentHash: String,
    @SerialName("createdAt") val createdAt: Long,
    val width: Int = 0,
    val height: Int = 0,
)
