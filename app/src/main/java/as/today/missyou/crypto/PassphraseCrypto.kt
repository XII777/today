package `as`.today.missyou.crypto

import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-derived encryption for the optional encrypted-backup feature.
 *
 * The device Keystore key cannot leave the device, so an export that must be
 * portable has to be protected by something the user actually knows. This is
 * PBKDF2-HMAC-SHA256 with a high iteration count, stretched into an AES-256-GCM
 * key. The parameters are stored alongside the payload so the format stays
 * upgradeable.
 */
object PassphraseCrypto {

    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 210_000
    const val SALT_SIZE_BYTES = 16
    const val KEY_SIZE_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAGIC = "TQBK1"

    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations)
        val cipherText = AesGcm.runOrThrow { AesGcm.encrypt(key, plaintext, MAGIC.toByteArray(Charsets.UTF_8)) }
        return MAGIC.toByteArray(Charsets.US_ASCII) + iterations.toBigEndianBytes() + salt + cipherText
    }

    private fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte(),
    )

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        val magic = String(payload, 0, MAGIC.length, Charsets.US_ASCII)
        if (magic != MAGIC) {
            throw JournalException(JournalErrorReason.CryptoFailure, detail = "Not an encrypted backup")
        }
        var offset = MAGIC.length
        val iterations = payload.copyOfRange(offset, offset + 4).toBigEndianInt()
        offset += 4
        if (iterations < 10_000 || iterations > 5_000_000) {
            throw JournalException(JournalErrorReason.CryptoFailure, detail = "Bad iteration count")
        }
        val salt = payload.copyOfRange(offset, offset + SALT_SIZE_BYTES)
        offset += SALT_SIZE_BYTES
        val cipherText = payload.copyOfRange(offset, payload.size)
        val key = deriveKey(passphrase, salt, iterations)
        return AesGcm.runOrThrow {
            AesGcm.decrypt(key, cipherText, MAGIC.toByteArray(Charsets.UTF_8))
        }
    }

    fun isEncryptedBackup(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.length && String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    private fun ByteArray.toBigEndianInt(): Int =
        ((this[0].toInt() and 0xFF) shl 24) or
            ((this[1].toInt() and 0xFF) shl 16) or
            ((this[2].toInt() and 0xFF) shl 8) or
            (this[3].toInt() and 0xFF)

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val key = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(key, "AES")
    }
}

/** SHA-256 helpers used for content addressing and integrity hashes. */
object Digests {

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    /**
     * A short, stable fingerprint of a search token.
     *
     * Used for the on-disk token index: it lets the searcher match a query against
     * stored data without ever storing the token itself, and without decrypting the
     * whole journal.
     */
    fun tokenFingerprint(token: String): String = sha256Hex(token).take(TOKEN_FINGERPRINT_LENGTH)

    const val TOKEN_FINGERPRINT_LENGTH = 16

    private val HEX = "0123456789abcdef".toCharArray()
}
