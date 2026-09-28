package `as`.today.missyou.ui.settings

import `as`.today.missyou.domain.model.AccentColor
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.model.EditorFont
import `as`.today.missyou.domain.model.HapticLevel
import `as`.today.missyou.domain.model.NavigationAppearance
import `as`.today.missyou.domain.model.ThemeMode

/**
 * Human-readable names for the setting enums.
 *
 * These live here rather than in `strings.xml` because the settings screen is the
 * only consumer, and a plain Kotlin map keeps each enum's vocabulary next to the
 * row that presents it. Unknown values fall back to a tidied-up constant name so a
 * new enum member can never render as a blank chip.
 */
fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
    ThemeMode.CUSTOM -> "Custom"
}

fun NavigationAppearance.displayName(): String = when (this) {
    NavigationAppearance.FROSTED -> "Frosted"
    NavigationAppearance.SOLID -> "Solid"
    NavigationAppearance.TRANSPARENT -> "Transparent"
}

fun AccentColor.displayName(): String = name.lowercase()
    .replaceFirstChar { it.uppercase() }

fun HapticLevel.displayName(): String = when (this) {
    HapticLevel.OFF -> "Off"
    HapticLevel.SUBTLE -> "Subtle"
    HapticLevel.FULL -> "Strong"
}

fun AppLockMode.displayName(): String = when (this) {
    AppLockMode.OFF -> "Off"
    AppLockMode.PIN -> "PIN"
    AppLockMode.BIOMETRIC -> "Fingerprint"
    AppLockMode.PIN_OR_BIOMETRIC -> "PIN or fingerprint"
}

fun EditorFont.displayName(): String = when (this) {
    EditorFont.SYSTEM -> "System"
    EditorFont.SERIF -> "Serif"
    EditorFont.MONOSPACE -> "Monospace"
}
