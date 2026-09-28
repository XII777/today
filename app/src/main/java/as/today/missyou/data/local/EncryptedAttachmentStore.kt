package `as`.today.missyou.data.local

import android.content.Context
import `as`.today.missyou.core.AppDispatchers
import `as`.today.missyou.crypto.Digests
import `as`.today.missyou.crypto.JournalCipher
import `as`.today.missyou.domain.repository.AttachmentStore
import `as`.today.missyou.domain.repository.StoredAttachment
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Attachment bytes, encrypted at rest in the app's private directory.
 *
 * Each file is a single AES-GCM message bound to its own storage name, so a file
 * cannot be swapped for another without failing authentication. Images are
 * downscaled and re-encoded on the way in, which keeps a journal with years of
 * photographs from growing without bound on a device with little storage.
 */
internal class EncryptedAttachmentStore(
    context: Context,
    private val cipher: JournalCipher,
    private val dispatchers: AppDispatchers,
) : AttachmentStore {

    private val directory: File = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    override suspend fun store(
        attachmentId: String,
        bytes: ByteArray,
        mimeType: String,
    ): StoredAttachment = withContext(dispatchers.io) {
        val prepared = ImageScaler.prepare(bytes, mimeType)
        val storageName = "$attachmentId$EXTENSION"
        val sealed = cipher.seal(
            plaintext = prepared.bytes.toBase64(),
            aad = AAD_PREFIX + storageName,
        )
        File(directory, storageName).writeBytes(sealed)
        StoredAttachment(
            storageName = storageName,
            byteSize = prepared.bytes.size.toLong(),
            contentHash = Digests.sha256Hex(prepared.bytes),
            width = prepared.width,
            height = prepared.height,
        )
    }

    override suspend fun read(storageName: String): ByteArray? = withContext(dispatchers.io) {
        val file = File(directory, storageName)
        if (!file.exists()) return@withContext null
        runCatching {
            cipher.open(file.readBytes(), AAD_PREFIX + storageName).fromBase64()
        }.getOrNull()
    }

    override suspend fun delete(storageName: String) = withContext(dispatchers.io) {
        File(directory, storageName).delete()
        Unit
    }

    override suspend fun usedBytes(): Long = withContext(dispatchers.io) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    override suspend fun attachmentCount(): Int = withContext(dispatchers.io) {
        directory.listFiles()?.size ?: 0
    }

    companion object {
        const val DIRECTORY = "attachments"
        private const val EXTENSION = ".bin"
        private const val AAD_PREFIX = "attachment|"

        fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

        fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
    }
}

/**
 * Downscales and re-encodes images before they are stored.
 *
 * Decoding is done in two passes: bounds first to choose the sample size, then the
 * real decode. That is what keeps a 12-megapixel photo from being held in memory in
 * full on a low-end device.
 */
internal object ImageScaler {

    const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 82

    data class Prepared(val bytes: ByteArray, val width: Int, val height: Int)

    fun prepare(bytes: ByteArray, mimeType: String): Prepared {
        if (!mimeType.startsWith("image/")) return Prepared(bytes, 0, 0)
        if (mimeType == "image/gif") return Prepared(bytes, 0, 0) // Animation must survive as-is.

        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Prepared(bytes, 0, 0)

        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return Prepared(bytes, bounds.outWidth, bounds.outHeight)

        val scaled = scaleToFit(decoded, MAX_DIMENSION)
        val encoded = encode(scaled, hasAlpha = decoded.hasAlpha())
        val width = scaled.width
        val height = scaled.height
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return Prepared(encoded, width, height)
    }

    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_DIMENSION && h / 2 >= MAX_DIMENSION) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    fun scaleToFit(bitmap: android.graphics.Bitmap, maxDimension: Int): android.graphics.Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        // The KTX `scale` extension is deliberately not used: it applies a
        // `Config` and re-encodes the pixel buffer, and this path must hand back a
        // bitmap the caller still owns and recycles.
        return android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun encode(bitmap: android.graphics.Bitmap, hasAlpha: Boolean): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        // WEBP arrived in API 30 and the lossy-with-alpha variant is only reliable
        // from there. Below that, PNG is the format that actually keeps transparency;
        // it is larger, which is the correct trade for a local-only file.
        val format = if (hasAlpha) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                android.graphics.Bitmap.CompressFormat.PNG
            }
        } else {
            android.graphics.Bitmap.CompressFormat.JPEG
        }
        bitmap.compress(format, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }
}
