package `as`.today.missyou.security

import `as`.today.missyou.crypto.Digests
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashing for the app-lock PIN.
 *
 * A PIN has very little entropy, so it is stretched with PBKDF2-HMAC-SHA256 and a
 * per-install random salt, and compared in constant time. The PIN itself is never
 * stored, and never recoverable: forgetting it means the encrypted vault has to be
 * re-created, which the app states plainly rather than offering a back door.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val SALT_SIZE_BYTES = 16
    const val ITERATIONS = 180_000
    private const val KEY_LENGTH_BITS = 256

    private val random = SecureRandom()

    data class HashedPin(val salt: ByteArray, val hash: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    fun hash(pin: CharArray): HashedPin {
        val salt = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)
        return HashedPin(salt, derive(pin, salt, ITERATIONS))
    }

    fun verify(pin: CharArray, stored: HashedPin): Boolean {
        val candidate = derive(pin, stored.salt, ITERATIONS)
        return constantTimeEquals(candidate, stored.hash)
    }

    fun encode(hashed: HashedPin): String =
        "${Digests.sha256Hex(hashed.salt)}:${Digests.sha256Hex(hashed.hash)}"

    fun decode(encoded: String): HashedPin? {
        val parts = encoded.split(':')
        if (parts.size != 2) return null
        return runCatching {
            HashedPin(parts[0].hexToBytes(), parts[1].hexToBytes())
        }.getOrNull()
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparison whose duration does not depend on where the first difference is. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return ByteArray(length / 2) { index ->
        val high = Character.digit(this[index * 2], 16)
        val low = Character.digit(this[index * 2 + 1], 16)
        require(high >= 0 && low >= 0) { "Invalid hex string" }
        ((high shl 4) or low).toByte()
    }
}
