package `as`.today.missyou.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `as`.today.missyou.domain.model.AccentColor
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.domain.model.EditorFont
import `as`.today.missyou.domain.model.ThemeMode

internal val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Shapes follow the radius scale from §48. */
private val TodayShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radii.small),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Radii.small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Radii.medium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Radii.large),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radii.xlarge),
)

/**
 * The application theme (§22).
 *
 * Themes are resolved in this order: an explicit light/dark choice, then AMOLED
 * (dark with true-black surfaces), then Custom (a user-picked accent), then the
 * system setting.
 */
@Composable
fun TodayTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.CUSTOM -> systemDark
    }
    val amoled = settings.themeMode == ThemeMode.AMOLED

    val accent = when {
        settings.themeMode == ThemeMode.CUSTOM && settings.customAccentArgb != null ->
            AccentScheme.custom(settings.customAccentArgb.toComposeColor(), dark)
        else -> AccentScheme.named(settings.accentColor, dark, settings.highContrast)
    }

    val colorScheme = if (dark) {
        darkScheme(accent, settings.highContrast, amoled)
    } else {
        lightScheme(accent, settings.highContrast, amoled)
    }

    val bodyFont = when (settings.editorFont) {
        EditorFont.SYSTEM -> FontFamily.SansSerif
        EditorFont.SERIF -> FontFamily.Serif
        EditorFont.MONOSPACE -> FontFamily.Monospace
    }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = todayTypography(settings.textScale, bodyFont, settings.compactMode),
            shapes = TodayShapes,
            content = content,
        )
    }
}

/** Convenience accessors so screens do not each re-read the composition local. */
object TodayTheme {
    val settings: AppSettings
        @Composable @ReadOnlyComposable get() = LocalAppSettings.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalAppSettings.current.let { _ ->
            androidx.compose.foundation.isSystemInDarkTheme() ||
                LocalAppSettings.current.themeMode == ThemeMode.DARK ||
                LocalAppSettings.current.themeMode == ThemeMode.AMOLED
        }

    val spacing: Dp get() = Spacing.md
}

/** Swatch for a highlight tone, honouring the active theme. */
@Composable
fun highlightColor(tone: `as`.today.missyou.domain.model.HighlightTone, dark: Boolean): Color =
    if (dark) Palette.Highlights.dark(tone) else Palette.Highlights.light(tone)

/** The swatch a user picks from in the editor's highlight sheet. */
@Composable
fun highlightSwatch(tone: `as`.today.missyou.domain.model.HighlightTone): Color =
    highlightColor(tone, TodayTheme.isDark)

/** Content padding that clears the floating pill navigation. */
object LayoutMetrics {
    val navigationBarHeight: Dp = 68.dp
    val floatingNavHeight: Dp = 62.dp
    val contentBottomInset: Dp = navigationBarHeight + floatingNavHeight + Spacing.md
    val horizontalMargin: Dp = Spacing.md
    val cardCorner: Dp = Radii.large
}
