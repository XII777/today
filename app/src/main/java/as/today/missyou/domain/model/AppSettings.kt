package `as`.today.missyou.domain.model

/** Theme options from §22. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED, CUSTOM }

/** Navigation bar treatments from §23. */
enum class NavigationAppearance { FROSTED, SOLID, TRANSPARENT }

/** Editor typefaces. All are platform fonts, so no font licence applies (§37). */
enum class EditorFont { SYSTEM, SERIF, MONOSPACE }

/** Date display style. */
enum class DateFormat { ISO, DAY_MONTH_YEAR, MONTH_DAY, MONTH_DAY_YEAR, WEEKDAY_LONG }

/** How the day changes over (§21 "Day rollover time"). */
enum class FirstDayOfWeek { MONDAY, SUNDAY }

enum class AppLockMode { OFF, PIN, BIOMETRIC, PIN_OR_BIOMETRIC }

/** How much haptic feedback to emit (§33). */
enum class HapticLevel { OFF, SUBTLE, FULL }

/** Which block types sit in the primary formatting bar. */
enum class ToolbarItem {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, HEADING, QUOTE,
    BULLET_LIST, NUMBERED_LIST, CHECKLIST, CODE, HIGHLIGHT, LINK,
}

data class AppSettings(
    // ------------------------------------------------------------- appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.VIOLET,
    val customAccentArgb: Long? = null,
    val navigationAppearance: NavigationAppearance = NavigationAppearance.FROSTED,
    val reduceMotion: Boolean = false,
    val compactMode: Boolean = false,
    val highContrast: Boolean = false,

    // ------------------------------------------------------------- accessibility
    val textScale: Float = 1.0f,
    val largerTouchTargets: Boolean = false,
    val haptics: HapticLevel = HapticLevel.SUBTLE,

    // ------------------------------------------------------------- journal
    val rolloverMinutes: Int = 0,
    val defaultTemplate: String = "",
    val autoSaveEnabled: Boolean = true,
    val autoSaveSeconds: Int = 5,
    val showWordCount: Boolean = true,
    val showReadingTime: Boolean = true,
    val showWritingDuration: Boolean = false,
    val lockConfirmation: Boolean = true,
    val lockCountdown: Boolean = true,
    val lockWarning: Boolean = true,
    val dateFormat: DateFormat = DateFormat.DAY_MONTH_YEAR,
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val dailyPromptEnabled: Boolean = true,

    // ------------------------------------------------------------- editor
    val editorFont: EditorFont = EditorFont.SYSTEM,
    val editorTextSizeSp: Int = 17,
    val lineSpacing: Float = 1.5f,
    val markdownShortcuts: Boolean = true,
    val spellCheck: Boolean = true,
    val autoCapitalize: Boolean = true,
    val toolbarItems: List<ToolbarItem> = DEFAULT_TOOLBAR,

    // ------------------------------------------------------------- privacy
    val hidePreviews: Boolean = false,
    val appLockMode: AppLockMode = AppLockMode.OFF,
    val disableScreenshots: Boolean = false,
    val encryptExports: Boolean = false,

    // ------------------------------------------------------------- state
    val onboardingComplete: Boolean = false,
) {
    val reduceVisualEffects: Boolean
        get() = reduceMotion || navigationAppearance == NavigationAppearance.SOLID

    val isDarkForced: Boolean?
        get() = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
            else -> null
        }

    companion object {
        val DEFAULT_TOOLBAR = listOf(
            ToolbarItem.BOLD,
            ToolbarItem.ITALIC,
            ToolbarItem.UNDERLINE,
            ToolbarItem.HEADING,
            ToolbarItem.BULLET_LIST,
            ToolbarItem.CHECKLIST,
            ToolbarItem.HIGHLIGHT,
            ToolbarItem.LINK,
        )
    }
}

/**
 * The accent palette.
 *
 * These are the app's own colours: an original set chosen to sit comfortably next
 * to the design system rather than borrowed from any existing product.
 */
enum class AccentColor(val labelResName: String) {
    VIOLET("Violet"),
    INDIGO("Indigo"),
    TEAL("Teal"),
    FOREST("Forest"),
    AMBER("Amber"),
    CORAL("Coral"),
    ROSE("Rose"),
    SLATE("Slate"),
    CUSTOM("Custom"),
}
