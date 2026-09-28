package `as`.today.missyou.crypto

import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/**
 * Seals individual database columns.
 *
 * The wire format is deliberately self-describing so the database can be migrated
 * to a new scheme later without guessing:
 *
 * ```
 * byte 0      format version (currently 1)
 * bytes 1..   iv (12) || ciphertext || gcm tag (16)
 * ```
 *
 * [aad] binds a ciphertext to the column it belongs to. Swapping the sealed title
 * of one row with the sealed content of another therefore fails authentication
 * instead of silently corrupting the journal.
 */
object SealCodec {

    const val FORMAT_VERSION = 1
    private const val HEADER_SIZE = 1

    fun seal(key: SecretKey, plaintext: String, aad: String): ByteArray {
        val bytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val cipherText = AesGcm.runOrThrow { AesGcm.encrypt(key, bytes, aad.toByteArray(Charsets.UTF_8)) }
        val out = ByteArray(HEADER_SIZE + cipherText.size)
        out[0] = FORMAT_VERSION.toByte()
        cipherText.copyInto(out, HEADER_SIZE)
        return out
    }

    fun open(key: SecretKey, sealed: ByteArray, aad: String): String {
        if (sealed.isEmpty()) return ""
        val version = sealed[0].toInt()
        if (version != FORMAT_VERSION) {
            throw JournalException(
                JournalErrorReason.CryptoFailure,
                detail = "Unsupported sealed field format: $version",
            )
        }
        val body = sealed.copyOfRange(HEADER_SIZE, sealed.size)
        val plain = AesGcm.runOrThrow { AesGcm.decrypt(key, body, aad.toByteArray(Charsets.UTF_8)) }
        return String(plain, StandardCharsets.UTF_8)
    }

    fun openOrNull(key: SecretKey, sealed: ByteArray?, aad: String): String? {
        if (sealed == null) return null
        return runCatching { open(key, sealed, aad) }.getOrNull()
    }

    fun openOrDefault(key: SecretKey, sealed: ByteArray?, aad: String, fallback: String): String =
        openOrNull(key, sealed, aad) ?: fallback

    /** Convenience for the many nullable text columns in the schema. */
    fun sealNullable(key: SecretKey, plaintext: String?, aad: String): ByteArray? =
        if (plaintext == null) null else seal(key, plaintext, aad)

    fun aadFor(table: String, column: String, rowId: String): String = "$table|$column|$rowId"
}
