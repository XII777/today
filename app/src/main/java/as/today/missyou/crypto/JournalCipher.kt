package `as`.today.missyou.crypto

import javax.crypto.SecretKey

/**
 * The single capability the data layer needs in order to read or write a column.
 *
 * Keeping this an interface is what allows the immutability tests to run on a plain
 * JVM: the production implementation is backed by the Android Keystore, the test
 * implementation is backed by a fixed in-memory key. The repository logic under
 * test is byte-for-byte the same in both cases.
 */
interface JournalCipher {
    fun seal(plaintext: String, aad: String): ByteArray
    fun open(sealed: ByteArray, aad: String): String
    fun openOrNull(sealed: ByteArray?, aad: String): String? = if (sealed == null) null else open(sealed, aad)
    fun sealNullable(plaintext: String?, aad: String): ByteArray? = if (plaintext == null) null else seal(plaintext, aad)
}

/** Production implementation: AES-GCM under the Keystore-wrapped vault key. */
class KeystoreJournalCipher(private val vault: VaultKeyStore) : JournalCipher {
    override fun seal(plaintext: String, aad: String): ByteArray =
        SealCodec.seal(vault.obtainOrCreate(), plaintext, aad)

    override fun open(sealed: ByteArray, aad: String): String =
        SealCodec.open(vault.obtainOrCreate(), sealed, aad)
}

/** JVM-test implementation using a caller supplied key. */
class DirectKeyJournalCipher(private val key: SecretKey) : JournalCipher {
    override fun seal(plaintext: String, aad: String): ByteArray = SealCodec.seal(key, plaintext, aad)
    override fun open(sealed: ByteArray, aad: String): String = SealCodec.open(key, sealed, aad)
}
