package `as`.today.missyou.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import `as`.today.missyou.domain.model.AccentColor
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.domain.model.DateFormat
import `as`.today.missyou.domain.model.EditorFont
import `as`.today.missyou.domain.model.FirstDayOfWeek
import `as`.today.missyou.domain.model.HapticLevel
import `as`.today.missyou.domain.model.NavigationAppearance
import `as`.today.missyou.domain.model.ThemeMode
import `as`.today.missyou.domain.model.ToolbarItem
import `as`.today.missyou.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Preferences, stored in a DataStore file inside the app's private storage.
 *
 * Two properties are deliberate and load-bearing:
 *
 *  * `corruptionHandler` falls back to defaults instead of throwing, so a damaged
 *    preferences file never blocks app start. Losing a preference is recoverable;
 *    losing the journal is not, and they are not stored together.
 *  * Nothing here is needed to read the journal, so preferences can be reset by the
 *    user or by a system restore without affecting a single entry.
 */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override val dayRolloverMinutes: Flow<Int> = settings
        .map { it.rolloverMinutes }
        .distinctUntilChanged()

    override val appLockMode: Flow<AppLockMode> = settings
        .map { it.appLockMode }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences.writeAll(updated)
        }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = this[Keys.THEME_MODE].toEnum(defaults.themeMode),
            accentColor = this[Keys.ACCENT].toEnum(defaults.accentColor),
            customAccentArgb = this[Keys.ACCENT_CUSTOM],
            navigationAppearance = this[Keys.NAV_APPEARANCE]
                .toEnum(defaults.navigationAppearance),
            reduceMotion = this[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            compactMode = this[Keys.COMPACT] ?: defaults.compactMode,
            highContrast = this[Keys.HIGH_CONTRAST] ?: defaults.highContrast,

            textScale = this[Keys.TEXT_SCALE] ?: defaults.textScale,
            largerTouchTargets = this[Keys.LARGE_TOUCH] ?: defaults.largerTouchTargets,
            haptics = this[Keys.HAPTICS].toEnum(defaults.haptics),

            rolloverMinutes = (this[Keys.ROLLOVER] ?: defaults.rolloverMinutes).coerceIn(0, 1439),
            defaultTemplate = this[Keys.TEMPLATE] ?: defaults.defaultTemplate,
            autoSaveEnabled = this[Keys.AUTOSAVE_ENABLED] ?: defaults.autoSaveEnabled,
            autoSaveSeconds = (this[Keys.AUTOSAVE_SECONDS] ?: defaults.autoSaveSeconds).coerceIn(2, 60),
            showWordCount = this[Keys.SHOW_WORDS] ?: defaults.showWordCount,
            showReadingTime = this[Keys.SHOW_READING] ?: defaults.showReadingTime,
            showWritingDuration = this[Keys.SHOW_DURATION] ?: defaults.showWritingDuration,
            lockConfirmation = this[Keys.LOCK_CONFIRM] ?: defaults.lockConfirmation,
            lockCountdown = this[Keys.LOCK_COUNTDOWN] ?: defaults.lockCountdown,
            lockWarning = this[Keys.LOCK_WARNING] ?: defaults.lockWarning,
            dateFormat = this[Keys.DATE_FORMAT].toEnum(defaults.dateFormat),
            firstDayOfWeek = this[Keys.FIRST_DAY].toEnum(defaults.firstDayOfWeek),
            dailyPromptEnabled = this[Keys.DAILY_PROMPT] ?: defaults.dailyPromptEnabled,

            editorFont = this[Keys.EDITOR_FONT].toEnum(defaults.editorFont),
            editorTextSizeSp = (this[Keys.EDITOR_TEXT_SIZE] ?: defaults.editorTextSizeSp).coerceIn(12, 30),
            lineSpacing = (this[Keys.LINE_SPACING] ?: defaults.lineSpacing).coerceIn(1.0f, 2.5f),
            markdownShortcuts = this[Keys.MARKDOWN] ?: defaults.markdownShortcuts,
            spellCheck = this[Keys.SPELLCHECK] ?: defaults.spellCheck,
            autoCapitalize = this[Keys.AUTOCAP] ?: defaults.autoCapitalize,
            toolbarItems = decodeToolbar(this[Keys.TOOLBAR]) ?: defaults.toolbarItems,

            hidePreviews = this[Keys.HIDE_PREVIEWS] ?: defaults.hidePreviews,
            appLockMode = this[Keys.APP_LOCK].toEnum(defaults.appLockMode),
            disableScreenshots = this[Keys.BLOCK_SCREENSHOTS] ?: defaults.disableScreenshots,
            encryptExports = this[Keys.ENCRYPT_EXPORT] ?: defaults.encryptExports,

            onboardingComplete = this[Keys.ONBOARDED] ?: defaults.onboardingComplete,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeAll(value: AppSettings) {
        this[Keys.THEME_MODE] = value.themeMode.name
        this[Keys.ACCENT] = value.accentColor.name
        value.customAccentArgb?.let { this[Keys.ACCENT_CUSTOM] = it } ?: remove(Keys.ACCENT_CUSTOM)
        this[Keys.NAV_APPEARANCE] = value.navigationAppearance.name
        this[Keys.REDUCE_MOTION] = value.reduceMotion
        this[Keys.COMPACT] = value.compactMode
        this[Keys.HIGH_CONTRAST] = value.highContrast

        this[Keys.TEXT_SCALE] = value.textScale
        this[Keys.LARGE_TOUCH] = value.largerTouchTargets
        this[Keys.HAPTICS] = value.haptics.name

        this[Keys.ROLLOVER] = value.rolloverMinutes
        this[Keys.TEMPLATE] = value.defaultTemplate
        this[Keys.AUTOSAVE_ENABLED] = value.autoSaveEnabled
        this[Keys.AUTOSAVE_SECONDS] = value.autoSaveSeconds
        this[Keys.SHOW_WORDS] = value.showWordCount
        this[Keys.SHOW_READING] = value.showReadingTime
        this[Keys.SHOW_DURATION] = value.showWritingDuration
        this[Keys.LOCK_CONFIRM] = value.lockConfirmation
        this[Keys.LOCK_COUNTDOWN] = value.lockCountdown
        this[Keys.LOCK_WARNING] = value.lockWarning
        this[Keys.DATE_FORMAT] = value.dateFormat.name
        this[Keys.FIRST_DAY] = value.firstDayOfWeek.name
        this[Keys.DAILY_PROMPT] = value.dailyPromptEnabled

        this[Keys.EDITOR_FONT] = value.editorFont.name
        this[Keys.EDITOR_TEXT_SIZE] = value.editorTextSizeSp
        this[Keys.LINE_SPACING] = value.lineSpacing
        this[Keys.MARKDOWN] = value.markdownShortcuts
        this[Keys.SPELLCHECK] = value.spellCheck
        this[Keys.AUTOCAP] = value.autoCapitalize
        this[Keys.TOOLBAR] = encodeToolbar(value.toolbarItems)

        this[Keys.HIDE_PREVIEWS] = value.hidePreviews
        this[Keys.APP_LOCK] = value.appLockMode.name
        this[Keys.BLOCK_SCREENSHOTS] = value.disableScreenshots
        this[Keys.ENCRYPT_EXPORT] = value.encryptExports

        this[Keys.ONBOARDED] = value.onboardingComplete
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val ACCENT_CUSTOM = longPreferencesKey("accent_custom")
        val NAV_APPEARANCE = stringPreferencesKey("nav_appearance")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val COMPACT = booleanPreferencesKey("compact")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")

        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val LARGE_TOUCH = booleanPreferencesKey("large_touch")
        val HAPTICS = stringPreferencesKey("haptics")

        val ROLLOVER = intPreferencesKey("rollover_minutes")
        val TEMPLATE = stringPreferencesKey("template")
        val AUTOSAVE_ENABLED = booleanPreferencesKey("autosave_enabled")
        val AUTOSAVE_SECONDS = intPreferencesKey("autosave_seconds")
        val SHOW_WORDS = booleanPreferencesKey("show_words")
        val SHOW_READING = booleanPreferencesKey("show_reading")
        val SHOW_DURATION = booleanPreferencesKey("show_duration")
        val LOCK_CONFIRM = booleanPreferencesKey("lock_confirm")
        val LOCK_COUNTDOWN = booleanPreferencesKey("lock_countdown")
        val LOCK_WARNING = booleanPreferencesKey("lock_warning")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val FIRST_DAY = stringPreferencesKey("first_day")
        val DAILY_PROMPT = booleanPreferencesKey("daily_prompt")

        val EDITOR_FONT = stringPreferencesKey("editor_font")
        val EDITOR_TEXT_SIZE = intPreferencesKey("editor_text_size")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val MARKDOWN = booleanPreferencesKey("markdown")
        val SPELLCHECK = booleanPreferencesKey("spellcheck")
        val AUTOCAP = booleanPreferencesKey("autocap")
        val TOOLBAR = stringPreferencesKey("toolbar")

        val HIDE_PREVIEWS = booleanPreferencesKey("hide_previews")
        val APP_LOCK = stringPreferencesKey("app_lock")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val ENCRYPT_EXPORT = booleanPreferencesKey("encrypt_export")

        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
    this?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: fallback

private fun encodeToolbar(items: List<ToolbarItem>): String = items.joinToString(",") { it.name }

private fun decodeToolbar(raw: String?): List<ToolbarItem>? {
    if (raw.isNullOrBlank()) return null
    val decoded = raw.split(",").mapNotNull { name ->
        ToolbarItem.entries.firstOrNull { it.name == name }
    }
    return decoded.ifEmpty { null }
}
