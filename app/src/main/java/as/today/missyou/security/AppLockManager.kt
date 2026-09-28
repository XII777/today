package `as`.today.missyou.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.edit
import `as`.today.missyou.domain.model.AppLockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock state.
 *
 * The PIN hash lives in private preferences; the *mode* lives in settings, and the
 * vault key is re-wrapped when the mode changes. Nothing here ever sends data
 * anywhere, and no account exists to log in to.
 */
class AppLockManager(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    private val unlocked = MutableStateFlow(false)

    val isUnlocked: StateFlow<Boolean> = unlocked.asStateFlow()

    /** True when the device can satisfy a biometric or device-credential prompt. */
    fun isDeviceAuthAvailable(): Boolean {
        val result = BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun biometricStrength(): BiometricStrength = when (
        BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_STRONG)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStrength.STRONG
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStrength.NOT_ENROLLED
        else -> BiometricStrength.UNAVAILABLE
    }

    fun hasPin(): Boolean = preferences.contains(KEY_PIN_HASH)

    fun setPin(pin: CharArray) {
        val hashed = PinHasher.hash(pin)
        preferences.edit {
            putString(KEY_PIN_HASH, PinHasher.encode(hashed))
            putLong(KEY_PIN_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun verifyPin(pin: CharArray): Boolean {
        val stored = preferences.getString(KEY_PIN_HASH, null) ?: return false
        val hashed = PinHasher.decode(stored) ?: return false
        val matches = PinHasher.verify(pin, hashed)
        if (matches) markUnlocked()
        return matches
    }

    /** Clears the PIN. Callers must re-wrap the vault key for the new mode first. */
    fun clearPin() {
        preferences.edit { remove(KEY_PIN_HASH); remove(KEY_PIN_UPDATED_AT) }
    }

    fun markUnlocked() {
        unlocked.value = true
    }

    fun lock() {
        unlocked.value = false
    }

    /** Restores the last session's unlock when the process returns from the background. */
    fun onAppBackgrounded() {
        unlocked.value = false
    }

    fun supports(mode: AppLockMode): Boolean = when (mode) {
        AppLockMode.OFF -> true
        AppLockMode.PIN -> hasPin()
        AppLockMode.BIOMETRIC, AppLockMode.PIN_OR_BIOMETRIC -> isDeviceAuthAvailable()
    }

    private companion object {
        const val PREFS = "today_app_lock"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_UPDATED_AT = "pin_updated_at"
    }
}

enum class BiometricStrength { STRONG, NOT_ENROLLED, UNAVAILABLE }
