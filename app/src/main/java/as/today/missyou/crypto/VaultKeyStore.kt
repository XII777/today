package `as`.today.missyou.crypto

import `as`.today.missyou.core.JournalException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Creates and retrieves the non-exportable wrapping keys that live in the
 * platform Keystore.
 *
 * ## Envelope encryption
 *
 * Journal content is encrypted with a random data key `K`. `K` never touches disk in
 * the clear: it is wrapped by a Keystore key and the wrapped blob is stored in
 * preferences. This indirection matters for two reasons:
 *
 *  * A Keystore key is non-exportable, so a rooted attacker with a copy of the app
 *    sandbox still cannot read the journal.
 *  * Keeping `K` separate from the Keystore key means the Keystore key can be
 *    re-wrapped (for example after enabling biometric unlock) without re-encrypting
 *    the whole journal.
 *
 * Two wrap keys are maintained:
 *
 *  * [WRAP_ALIAS_PLAIN] – always available, used when the app is not locked.
 *  * [WRAP_ALIAS_AUTH] – requires a recent successful device authentication, used
 *    when the user turns on app lock. If this key is invalidated (the usual cause is
 *    enrolling a new fingerprint) the app fails closed rather than silently falling
 *    back to an unauthenticated key.
 */
class KeystoreWrapKeyProvider(
    private val authRequired: () -> Boolean,
) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun vaultWrapKey(): SecretKey = keyStore.getOrCreate(WRAP_ALIAS_PLAIN, requireUserAuth = false)

    fun authWrapKey(): SecretKey = keyStore.getOrCreate(WRAP_ALIAS_AUTH, requireUserAuth = true)

    fun activeWrapKey(): SecretKey = if (authRequired()) authWrapKey() else vaultWrapKey()

    /** True when the vault key is currently protected by a device-authentication wrap. */
    fun isAuthRequired(): Boolean = authRequired()

    fun hasAuthWrapKey(): Boolean = keyStore.containsAlias(WRAP_ALIAS_AUTH)

    fun deleteAllWrapKeys() {
        listOf(WRAP_ALIAS_PLAIN, WRAP_ALIAS_AUTH).forEach { alias ->
            runCatching { keyStore.deleteEntry(alias) }
        }
    }

    private fun KeyStore.getOrCreate(alias: String, requireUserAuth: Boolean): SecretKey {
        (getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Never require a device unlock for the plain wrap key: losing the
            // journal because a screen lock changed would be worse than the threat
            // this would mitigate, and the sandbox plus Keystore already protect it.
            .setUserAuthenticationRequired(requireUserAuth)
            .setRandomizedEncryptionRequired(true)

        if (requireUserAuth) {
            // `setUserAuthenticationParameters` is the API 30 spelling and allows
            // device-credential auth. On API 26-29 the older pair is the only
            // option, and it cannot combine the two authenticator types, so on those
            // devices the key is released for any biometric or for the device
            // credential separately depending on what the user enrolled.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
            }
            builder.setInvalidatedByBiometricEnrollment(true)
        } else {
            builder.setInvalidatedByBiometricEnrollment(false)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val WRAP_ALIAS_PLAIN = "today_vault_wrap_v1"
        const val WRAP_ALIAS_AUTH = "today_vault_wrap_auth_v1"
        const val AUTH_WINDOW_SECONDS = 30
    }
}

/**
 * Owns the vault data key `K` and its wrapped representation.
 *
 * [unwrap] is the only way to obtain plaintext encryption capability, which keeps
 * the "is the app unlocked?" decision in exactly one place.
 */
class VaultKeyStore(
    private val wrapKeyProvider: KeystoreWrapKeyProvider,
    private val storage: WrappedKeyStorage,
) {

    @Volatile
    private var cached: SecretKey? = null

    /**
     * Returns the vault key, generating and persisting a new one on first use.
     *
     * A previously stored wrapped key that can no longer be unwrapped (because the
     * Keystore entry was invalidated) is *not* silently replaced: doing so would
     * orphan every existing encrypted row. The failure is reported instead.
     */
    @Synchronized
    fun obtainOrCreate(): SecretKey {
        cached?.let { return it }
        val existing = storage.load(authRequired = wrapKeyProvider.isAuthRequired())
        val key = if (existing != null) {
            AesGcm.runOrThrow {
                val wrapKey = wrapKeyProvider.activeWrapKey()
                val plain = AesGcm.decrypt(wrapKey, existing, AAD)
                SecretKeySpecCompat.from(plain)
            }
        } else {
            val fresh = AesGcm.generateKey()
            val wrapKey = wrapKeyProvider.activeWrapKey()
            storage.save(AesGcm.encrypt(wrapKey, fresh.encoded, AAD), wrapKeyProvider.isAuthRequired())
            fresh
        }
        cached = key
        return key
    }

    /**
     * Re-wraps the vault key for the current authentication mode.
     *
     * Called when the user turns app lock on or off. The data key itself does not
     * change, so no journal content has to be re-encrypted.
     */
    @Synchronized
    fun rewrapForCurrentAuthMode() {
        val key = obtainOrCreate()
        val wrapKey = wrapKeyProvider.activeWrapKey()
        storage.save(AesGcm.encrypt(wrapKey, key.encoded, AAD), wrapKeyProvider.isAuthRequired())
    }

    /** True when the stored wrapped key can be opened with the currently active policy. */
    fun isAccessible(): Boolean = runCatching { obtainOrCreate() }.isSuccess

    /** Drops the in-memory key. The next [obtainOrCreate] re-opens the Keystore. */
    fun clearCache() {
        cached = null
    }

    /**
     * Destroys the wrapped key. Every encrypted row becomes permanently unreadable,
     * so callers must only reach this from an explicit, confirmed user action.
     */
    @Synchronized
    fun destroy() {
        cached = null
        storage.clear()
        wrapKeyProvider.deleteAllWrapKeys()
    }

    private companion object {
        /** Binds a wrapped key to this exact format so it cannot be replayed elsewhere. */
        val AAD = "today:vault:v1".toByteArray(Charsets.UTF_8)
    }
}

/** Persists the wrapped vault key bytes. */
interface WrappedKeyStorage {
    fun load(authRequired: Boolean): ByteArray?
    fun save(wrapped: ByteArray, authRequired: Boolean)
    fun clear()
}

private object SecretKeySpecCompat {
    fun from(bytes: ByteArray) = javax.crypto.spec.SecretKeySpec(bytes, "AES")
}
