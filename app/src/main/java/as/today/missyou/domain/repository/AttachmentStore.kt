package `as`.today.missyou.domain.repository

/** Result of persisting an attachment's bytes. */
data class StoredAttachment(
    val storageName: String,
    val byteSize: Long,
    val contentHash: String,
    val width: Int,
    val height: Int,
)

/**
 * Local-only storage for images and files attached to entries.
 *
 * There is no remote counterpart by design: §7 requires that everything the user
 * inserts stays local, and §17 requires the Storage Access Framework rather than
 * broad storage permissions, so the bytes are copied into the app's private
 * directory and the original URI is never retained.
 */
interface AttachmentStore {

    /** Encrypts and writes [bytes] under [attachmentId]; returns the stored descriptor. */
    suspend fun store(
        attachmentId: String,
        bytes: ByteArray,
        mimeType: String,
    ): StoredAttachment

    suspend fun read(storageName: String): ByteArray?

    suspend fun delete(storageName: String)

    /** Total bytes currently used by attachments, shown in Settings → Storage. */
    suspend fun usedBytes(): Long

    suspend fun attachmentCount(): Int
}
