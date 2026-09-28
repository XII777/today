package `as`.today.missyou.crypto

import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/**
 * AES-256-GCM with a random 96-bit IV per message.
 *
 * The stored layout is `iv (12 bytes) || ciphertext || tag (16 bytes)`, which is
 * the layout Android's [javax.crypto.spec.GCMParameterSpec] expects when the buffer
 * is passed straight to `doFinal`.
 */
object AesGcm {

    const val IV_SIZE_BYTES = 12
    const val TAG_SIZE_BITS = 128
    private const val KEY_SIZE_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun generateKey(): SecretKey = javax.crypto.KeyGenerator.getInstance("AES").apply {
        init(KEY_SIZE_BITS)
    }.generateKey()

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad ?: EMPTY)
        val cipherText = cipher.doFinal(plaintext)
        return cipher.iv + cipherText
    }

    fun decrypt(key: SecretKey, payload: ByteArray, aad: ByteArray? = null): ByteArray {
        if (payload.size <= IV_SIZE_BYTES) {
            throw JournalException(JournalErrorReason.CryptoFailure, detail = "Ciphertext too short")
        }
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val body = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            key,
            javax.crypto.spec.GCMParameterSpec(TAG_SIZE_BITS, iv),
        )
        cipher.updateAAD(aad ?: EMPTY)
        return cipher.doFinal(body)
    }

    /**
     * `CryptoException` from the platform is rethrown as a domain failure so callers
     * never have to reason about `KeyPermanentlyInvalidatedException` and friends.
     */
    inline fun <T> runOrThrow(
        reason: JournalErrorReason = JournalErrorReason.CryptoFailure,
        block: () -> T,
    ): T = try {
        block()
    } catch (e: GeneralSecurityException) {
        throw JournalException(reason, detail = e.message, cause = e)
    }

    private val EMPTY = ByteArray(0)
}
