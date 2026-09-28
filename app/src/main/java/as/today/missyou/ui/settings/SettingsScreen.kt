package `as`.today.missyou.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.domain.model.NavigationAppearance
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.ChoiceRow
import `as`.today.missyou.ui.components.NoticeCard
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SecondaryButton
import `as`.today.missyou.ui.components.SettingsRow
import `as`.today.missyou.ui.components.SettingsSection
import `as`.today.missyou.ui.components.SettingsSwitchRow
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.navigation.Destination
import `as`.today.missyou.ui.navigation.FloatingPillNavigation
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Non-fatal messages the settings screen shows after an action. */
data class SettingsNotice(val message: String, val isError: Boolean = false)

class SettingsViewModel(container: AppContainer) : AppViewModel(container) {

    private val notices = MutableStateFlow<List<SettingsNotice>>(emptyList())
    val notice: StateFlow<List<SettingsNotice>> = notices.asStateFlow()

    /**
     * Every settings mutation funnels through here.
     *
     * Changing the lock mode has a side effect outside the settings store — the
     * Keystore-wrapped vault key is re-wrapped so it can only be unwrapped after
     * the new authentication. Doing that in one place means a new setting row can
     * never forget it.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = container.settings.settings.first()
            container.settings.update(transform)
            val after = container.settings.settings.first()
            if (before.appLockMode != after.appLockMode) {
                container.onLockModeChanged()
            }
        }
    }

    fun setThemeMode(mode: `as`.today.missyou.domain.model.ThemeMode) =
        update { it.copy(themeMode = mode) }

    fun setAccent(accent: `as`.today.missyou.domain.model.AccentColor) =
        update { it.copy(accentColor = accent) }

    fun setNavigationAppearance(appearance: NavigationAppearance) =
        update { it.copy(navigationAppearance = appearance) }

    fun setReduceMotion(enabled: Boolean) = update { it.copy(reduceMotion = enabled) }
    fun setCompactMode(enabled: Boolean) = update { it.copy(compactMode = enabled) }
    fun setHighContrast(enabled: Boolean) = update { it.copy(highContrast = enabled) }
    fun setTextScale(scale: Float) = update { it.copy(textScale = scale) }
    fun setLargerTouchTargets(enabled: Boolean) = update { it.copy(largerTouchTargets = enabled) }
    fun setHaptics(level: `as`.today.missyou.domain.model.HapticLevel) = update { it.copy(haptics = level) }
    fun setAutoSave(enabled: Boolean) = update { it.copy(autoSaveEnabled = enabled) }
    fun setAutoSaveSeconds(seconds: Int) = update { it.copy(autoSaveSeconds = seconds.coerceIn(1, 60)) }
    fun setShowWordCount(enabled: Boolean) = update { it.copy(showWordCount = enabled) }
    fun setShowReadingTime(enabled: Boolean) = update { it.copy(showReadingTime = enabled) }
    fun setShowWritingDuration(enabled: Boolean) = update { it.copy(showWritingDuration = enabled) }
    fun setHidePreviews(enabled: Boolean) = update { it.copy(hidePreviews = enabled) }
    fun setDisableScreenshots(enabled: Boolean) = update { it.copy(disableScreenshots = enabled) }
    fun setEncryptExports(enabled: Boolean) = update { it.copy(encryptExports = enabled) }
    fun setLockMode(mode: AppLockMode) = update { it.copy(appLockMode = mode) }
    fun setRolloverMinutes(minutes: Int) = update { it.copy(rolloverMinutes = minutes.coerceIn(0, 720)) }
    fun setSpellCheck(enabled: Boolean) = update { it.copy(spellCheck = enabled) }
    fun setMarkdownShortcuts(enabled: Boolean) = update { it.copy(markdownShortcuts = enabled) }
    fun setDailyPrompt(enabled: Boolean) = update { it.copy(dailyPromptEnabled = enabled) }
    fun setEditorFont(font: `as`.today.missyou.domain.model.EditorFont) = update { it.copy(editorFont = font) }
    fun setEditorTextSize(size: Int) = update { it.copy(editorTextSizeSp = size.coerceIn(12, 28)) }
    fun setLineSpacing(spacing: Float) = update { it.copy(lineSpacing = spacing.coerceIn(1.0f, 2.5f)) }
    fun setDefaultTemplate(template: String) = update { it.copy(defaultTemplate = template) }

    fun resetSettings() {
        viewModelScope.launch { container.settings.reset() }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.clearAllData()
            container.settings.reset()
            notices.value = listOf(
                SettingsNotice("All journal data has been erased from this device.", isError = true),
            )
        }
    }

    fun report(message: String, isError: Boolean = false) {
        notices.value = notices.value + SettingsNotice(message, isError)
    }
}

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigate: (Destination) -> Unit,
    onOpenBackup: () -> Unit,
) {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val notices by viewModel.notice.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = Spacing.lg,
                bottom = LayoutMetrics.contentBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "title") {
                Text(
                    text = "Settings",
                    modifier = Modifier
                        .padding(horizontal = LayoutMetrics.horizontalMargin)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            notices.forEachIndexed { index, notice ->
                item(key = "notice-$index") {
                    NoticeCard(
                        text = notice.message,
                        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                    )
                }
            }

            // ---------------------------------------------------------- appearance
            item(key = "appearance") {
                SettingsSection(
                    title = "Appearance",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "theme") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        ChoiceRow(
                            label = "Theme",
                            options = `as`.today.missyou.domain.model.ThemeMode.entries,
                            selected = settings.themeMode,
                            onSelect = viewModel::setThemeMode,
                            labelOf = { it.displayName() },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "Accent",
                            options = `as`.today.missyou.domain.model.AccentColor.entries,
                            selected = settings.accentColor,
                            onSelect = viewModel::setAccent,
                            labelOf = { it.displayName() },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "Navigation",
                            options = NavigationAppearance.entries,
                            selected = settings.navigationAppearance,
                            onSelect = viewModel::setNavigationAppearance,
                            labelOf = { it.displayName() },
                        )
                    }
                }
            }
            item(key = "display") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        SettingsSwitchRow(
                            title = "Reduce motion",
                            subtitle = "Removes animation and scaling across the app",
                            checked = settings.reduceMotion,
                            onCheckedChange = viewModel::setReduceMotion,
                        )
                        SettingsSwitchRow(
                            title = "Compact mode",
                            subtitle = "Tighter spacing to fit more on screen",
                            checked = settings.compactMode,
                            onCheckedChange = viewModel::setCompactMode,
                        )
                        SettingsSwitchRow(
                            title = "High contrast",
                            subtitle = "Stronger separation between text and background",
                            checked = settings.highContrast,
                            onCheckedChange = viewModel::setHighContrast,
                        )
                    }
                }
            }
            item(key = "text-scale") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Text size",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        ChoiceRow(
                            label = "",
                            options = TEXT_SCALES,
                            selected = settings.textScale,
                            onSelect = viewModel::setTextScale,
                            labelOf = { it.displayName() },
                        )
                    }
                }
            }

            // ------------------------------------------------------- accessibility
            item(key = "accessibility") {
                SettingsSection(
                    title = "Accessibility",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "accessibility-rows") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        SettingsSwitchRow(
                            title = "Larger touch targets",
                            subtitle = "Increases the size of every tap target",
                            checked = settings.largerTouchTargets,
                            onCheckedChange = viewModel::setLargerTouchTargets,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "Haptics",
                            options = `as`.today.missyou.domain.model.HapticLevel.entries,
                            selected = settings.haptics,
                            onSelect = viewModel::setHaptics,
                            labelOf = { it.displayName() },
                        )
                    }
                }
            }

            // ------------------------------------------------------------- journal
            item(key = "journal") {
                SettingsSection(
                    title = "Journal",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "journal-rows") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        ChoiceRow(
                            label = "Day rolls over at",
                            options = ROLLOVERS,
                            selected = settings.rolloverMinutes,
                            onSelect = viewModel::setRolloverMinutes,
                            labelOf = { it.displayName(settings.rolloverMinutes) },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SettingsSwitchRow(
                            title = "Daily prompt",
                            subtitle = "Shows a writing prompt for today",
                            checked = settings.dailyPromptEnabled,
                            onCheckedChange = viewModel::setDailyPrompt,
                        )
                        SettingsSwitchRow(
                            title = "Show word count",
                            checked = settings.showWordCount,
                            onCheckedChange = viewModel::setShowWordCount,
                        )
                        SettingsSwitchRow(
                            title = "Show reading time",
                            checked = settings.showReadingTime,
                            onCheckedChange = viewModel::setShowReadingTime,
                        )
                        SettingsSwitchRow(
                            title = "Show time spent writing",
                            checked = settings.showWritingDuration,
                            onCheckedChange = viewModel::setShowWritingDuration,
                        )
                    }
                }
            }

            // -------------------------------------------------------------- saving
            item(key = "saving") {
                SettingsSection(
                    title = "Saving",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "saving-rows") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        SettingsSwitchRow(
                            title = "Save automatically",
                            subtitle = "Writes your entry a few seconds after you stop typing",
                            checked = settings.autoSaveEnabled,
                            onCheckedChange = viewModel::setAutoSave,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "After",
                            options = AUTOSAVE_DELAYS,
                            selected = settings.autoSaveSeconds,
                            onSelect = viewModel::setAutoSaveSeconds,
                            labelOf = { "${it}s" },
                        )
                    }
                }
            }

            // -------------------------------------------------------------- editor
            item(key = "editor") {
                SettingsSection(
                    title = "Editor",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "editor-rows") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        ChoiceRow(
                            label = "Editor font",
                            options = `as`.today.missyou.domain.model.EditorFont.entries,
                            selected = settings.editorFont,
                            onSelect = viewModel::setEditorFont,
                            labelOf = { it.displayName() },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "Text size",
                            options = EDITOR_SIZES,
                            selected = settings.editorTextSizeSp,
                            onSelect = viewModel::setEditorTextSize,
                            labelOf = { "$it sp" },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SettingsSwitchRow(
                            title = "Spell check",
                            checked = settings.spellCheck,
                            onCheckedChange = viewModel::setSpellCheck,
                        )
                        SettingsSwitchRow(
                            title = "Markdown shortcuts",
                            subtitle = "Typing # or - applies a heading or list",
                            checked = settings.markdownShortcuts,
                            onCheckedChange = viewModel::setMarkdownShortcuts,
                        )
                    }
                }
            }

            // ------------------------------------------------------------- privacy
            item(key = "privacy") {
                SettingsSection(
                    title = "Privacy",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "privacy-rows") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        SettingsSwitchRow(
                            title = "Hide entry previews",
                            subtitle = "Archive shows word counts instead of text",
                            checked = settings.hidePreviews,
                            onCheckedChange = viewModel::setHidePreviews,
                        )
                        SettingsSwitchRow(
                            title = "Block screenshots",
                            subtitle = "Prevents the screen being captured or recorded",
                            checked = settings.disableScreenshots,
                            onCheckedChange = viewModel::setDisableScreenshots,
                        )
                        SettingsSwitchRow(
                            title = "Encrypt exports by default",
                            subtitle = "Backup files require a passphrase",
                            checked = settings.encryptExports,
                            onCheckedChange = viewModel::setEncryptExports,
                        )
                    }
                }
            }
            item(key = "lock") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            text = "App lock",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Requires a PIN or your fingerprint to open the journal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ChoiceRow(
                            label = "",
                            options = AppLockMode.entries,
                            selected = settings.appLockMode,
                            onSelect = viewModel::setLockMode,
                            labelOf = { it.displayName() },
                        )
                    }
                }
            }

            // -------------------------------------------------------------- danger
            item(key = "data") {
                SettingsSection(
                    title = "Your data",
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }
            item(key = "data-buttons") {
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(
                        Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        SecondaryButton(
                            text = "Import and export",
                            onClick = onOpenBackup,
                        )
                        SecondaryButton(
                            text = "Reset all settings",
                            onClick = viewModel::resetSettings,
                        )
                        PrimaryButton(
                            text = "Erase all journal data",
                            onClick = viewModel::clearAllData,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            }

            item(key = "version") {
                Text(
                    text = "Today ${container.appVersion} · everything stays on this device",
                    modifier = Modifier.padding(
                        horizontal = LayoutMetrics.horizontalMargin,
                        vertical = Spacing.md,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FloatingPillNavigation(
            current = Destination.SETTINGS,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private val TEXT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
private val ROLLOVERS = listOf(0, 60, 180, 300, 600, 720)
private val AUTOSAVE_DELAYS = listOf(2, 5, 10, 20, 30)
private val EDITOR_SIZES = listOf(14, 16, 17, 18, 20, 22)

private fun Float.displayName(): String =
    if (this == 1.0f) "Default" else "${(this * 100).toInt()}%"

private fun Int.displayName(currentRollover: Int): String = when (this) {
    0 -> "Midnight"
    60 -> "1:00 am"
    180 -> "3:00 am"
    300 -> "5:00 am"
    600 -> "10:00 am"
    720 -> "Noon"
    else -> "$this min"
}
