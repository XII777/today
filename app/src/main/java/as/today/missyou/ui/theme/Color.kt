package `as`.today.missyou.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The application's own palette.
 *
 * These colours were chosen for this project. Nothing here is sampled from, derived
 * from, or matched to another product's brand palette, and the app ships no bitmap
 * assets at all beyond its own launcher vector (§35, §36, §37).
 *
 * The system is built around a single violet accent with a warm, slightly paper
 * toned neutral ramp, which keeps long-form writing comfortable while still feeling
 * friendly rather than clinical.
 */
object Palette {

    // ------------------------------------------------------------ accent
    val VioletLight = Color(0xFF6C4CE0)
    val VioletDark = Color(0xFFB6A5FF)
    val VioletContainerLight = Color(0xFFE7E0FF)
    val VioletContainerDark = Color(0xFF3A2C6B)

    val IndigoLight = Color(0xFF3F51B5)
    val IndigoDark = Color(0xFFB5C0FF)
    val IndigoContainerLight = Color(0xFFDEE0FF)
    val IndigoContainerDark = Color(0xFF2B3382)

    val TealLight = Color(0xFF00796B)
    val TealDark = Color(0xFF6FE0CE)
    val TealContainerLight = Color(0xFFCFF3EC)
    val TealContainerDark = Color(0xFF00504A)

    val ForestLight = Color(0xFF2E6B3E)
    val ForestDark = Color(0xFF8FD79C)
    val ForestContainerLight = Color(0xFFD3EED8)
    val ForestContainerDark = Color(0xFF1B4A29)

    val AmberLight = Color(0xFF9A6400)
    val AmberDark = Color(0xFFFFC65C)
    val AmberContainerLight = Color(0xFFFFE7BC)
    val AmberContainerDark = Color(0xFF6A4500)

    val CoralLight = Color(0xFFC0452F)
    val CoralDark = Color(0xFFFFB4A4)
    val CoralContainerLight = Color(0xFFFFDBD2)
    val CoralContainerDark = Color(0xFF7A2A1A)

    val RoseLight = Color(0xFFB02F5E)
    val RoseDark = Color(0xFFFFB0C8)
    val RoseContainerLight = Color(0xFFFFD9E3)
    val RoseContainerDark = Color(0xFF7D1F42)

    val SlateLight = Color(0xFF44546A)
    val SlateDark = Color(0xFFB7C6DC)
    val SlateContainerLight = Color(0xFFD9E2F0)
    val SlateContainerDark = Color(0xFF2C3A4D)

    // ------------------------------------------------------------ light neutrals
    val LightBackground = Color(0xFFFBF8FF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF1ECF8)
    val LightSurfaceContainer = Color(0xFFF6F2FB)
    val LightOnBackground = Color(0xFF1B1A21)
    val LightOnSurface = Color(0xFF1B1A21)
    val LightOnSurfaceVariant = Color(0xFF5A5866)
    val LightOutline = Color(0xFFC9C3D4)
    val LightOutlineVariant = Color(0xFFE3DEEC)
    val LightError = Color(0xFFB3261E)
    val LightOnError = Color(0xFFFFFFFF)

    // ------------------------------------------------------------ dark neutrals
    val DarkBackground = Color(0xFF121016)
    val DarkSurface = Color(0xFF1A1820)
    val DarkSurfaceVariant = Color(0xFF262330)
    val DarkSurfaceContainer = Color(0xFF211E29)
    val DarkOnBackground = Color(0xFFE7E1EE)
    val DarkOnSurface = Color(0xFFE7E1EE)
    val DarkOnSurfaceVariant = Color(0xFFB4AEBF)
    val DarkOutline = Color(0xFF3D3947)
    val DarkOutlineVariant = Color(0xFF2E2B37)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)

    // ------------------------------------------------------------ AMOLED
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledSurfaceContainer = Color(0xFF0B0B0D)
    val AmoledSurfaceVariant = Color(0xFF14141A)
    val AmoledOutline = Color(0xFF2C2C34)

    /**
     * Highlight tones for the editor (§7).
     *
     * Light and dark variants are both defined so a highlighted run keeps a
     * contrast ratio that passes WCAG AA against the text colour placed on it.
     */
    object Highlights {
        val YellowLight = Color(0xFFFFF59D)
        val GreenLight = Color(0xFFA5D6A7)
        val BlueLight = Color(0xFF90CAF9)
        val PinkLight = Color(0xFFF8BBD0)
        val OrangeLight = Color(0xFFFFCC80)

        val YellowDark = Color(0xFF6B5A12)
        val GreenDark = Color(0xFF2F5A38)
        val BlueDark = Color(0xFF1F4266)
        val PinkDark = Color(0xFF6B2A45)
        val OrangeDark = Color(0xFF6B4415)

        fun light(tone: `as`.today.missyou.domain.model.HighlightTone): Color = when (tone) {
            `as`.today.missyou.domain.model.HighlightTone.YELLOW -> YellowLight
            `as`.today.missyou.domain.model.HighlightTone.GREEN -> GreenLight
            `as`.today.missyou.domain.model.HighlightTone.BLUE -> BlueLight
            `as`.today.missyou.domain.model.HighlightTone.PINK -> PinkLight
            `as`.today.missyou.domain.model.HighlightTone.ORANGE -> OrangeLight
        }

        fun dark(tone: `as`.today.missyou.domain.model.HighlightTone): Color = when (tone) {
            `as`.today.missyou.domain.model.HighlightTone.YELLOW -> YellowDark
            `as`.today.missyou.domain.model.HighlightTone.GREEN -> GreenDark
            `as`.today.missyou.domain.model.HighlightTone.BLUE -> BlueDark
            `as`.today.missyou.domain.model.HighlightTone.PINK -> PinkDark
            `as`.today.missyou.domain.model.HighlightTone.ORANGE -> OrangeDark
        }

        /** Text colour that meets AA contrast on top of the highlight swatch. */
        fun onHighlight(tone: `as`.today.missyou.domain.model.HighlightTone, dark: Boolean): Color =
            if (dark) Color(0xFF14121A) else Color(0xFF1B1A21)
    }
}

/** A resolved accent, either from a named palette or fully custom. */
data class AccentScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
) {
    companion object {
        fun named(name: `as`.today.missyou.domain.model.AccentColor, dark: Boolean, highContrast: Boolean): AccentScheme =
            when (name) {
                `as`.today.missyou.domain.model.AccentColor.VIOLET -> violet(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.INDIGO -> indigo(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.TEAL -> teal(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.FOREST -> forest(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.AMBER -> amber(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.CORAL -> coral(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.ROSE -> rose(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.SLATE -> slate(dark, highContrast)
                `as`.today.missyou.domain.model.AccentColor.CUSTOM -> violet(dark, highContrast)
            }

        /**
         * Builds a scheme from an arbitrary user colour.
         *
         * Container and "on" colours are derived rather than left to the user: a
         * contrast ratio that fails AA is a real accessibility defect, so the app
         * computes a tint and picks the readable foreground automatically.
         */
        fun custom(seed: Color, dark: Boolean): AccentScheme {
            val luminance = seed.luminance()
            val onSeed = if (luminance > 0.55f) Palette.LightOnSurface else Palette.DarkOnSurface
            val container = if (dark) seed.darken(0.55f) else seed.lighten(0.82f)
            val onContainer = if (container.luminance() > 0.55f) {
                Palette.LightOnSurface
            } else {
                Palette.DarkOnSurface
            }
            val secondary = if (dark) seed.desaturate(0.25f).lighten(0.25f) else seed.desaturate(0.3f).darken(0.1f)
            return AccentScheme(
                primary = seed,
                onPrimary = onSeed,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                secondary = secondary,
                onSecondary = onSeed,
                secondaryContainer = if (dark) secondary.darken(0.6f) else secondary.lighten(0.82f),
                onSecondaryContainer = onContainer,
                tertiary = seed.rotate(0.12f),
                onTertiary = onSeed,
                error = if (dark) Palette.DarkError else Palette.LightError,
                onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
            )
        }

        private fun violet(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.VioletDark else Palette.VioletLight,
            onPrimary = if (dark) Color(0xFF241A4D) else Color.White,
            primaryContainer = if (dark) Palette.VioletContainerDark else Palette.VioletContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFE7E0FF) else Color(0xFF2A1D63),
            secondary = if (dark) Color(0xFF9ED6B8) else Color(0xFF3F6B52),
            onSecondary = if (dark) Color(0xFF0C2C1B) else Color.White,
            secondaryContainer = if (dark) Color(0xFF2A5040) else Color(0xFFCFEBD8),
            onSecondaryContainer = if (dark) Color(0xFFB7EFCB) else Color(0xFF16351F),
            tertiary = if (dark) Color(0xFFFFC46B) else Color(0xFF8A5000),
            onTertiary = if (dark) Color(0xFF3A2400) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun indigo(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.IndigoDark else Palette.IndigoLight,
            onPrimary = if (dark) Color(0xFF1B2260) else Color.White,
            primaryContainer = if (dark) Palette.IndigoContainerDark else Palette.IndigoContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFDEE0FF) else Color(0xFF1A2270),
            secondary = if (dark) Color(0xFF9FD6C4) else Color(0xFF2F6355),
            onSecondary = if (dark) Color(0xFF0A2A22) else Color.White,
            secondaryContainer = if (dark) Color(0xFF254B40) else Color(0xFFCBEADC),
            onSecondaryContainer = if (dark) Color(0xFFB9EEDB) else Color(0xFF113028),
            tertiary = if (dark) Color(0xFFFFB877) else Color(0xFF8A4C00),
            onTertiary = if (dark) Color(0xFF3A2000) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun teal(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.TealDark else Palette.TealLight,
            onPrimary = if (dark) Color(0xFF00382F) else Color.White,
            primaryContainer = if (dark) Palette.TealContainerDark else Palette.TealContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFCFF3EC) else Color(0xFF00382F),
            secondary = if (dark) Color(0xFFB9C6FF) else Color(0xFF45589B),
            onSecondary = if (dark) Color(0xFF1A2A66) else Color.White,
            secondaryContainer = if (dark) Color(0xFF334480) else Color(0xFFDDE1FF),
            onSecondaryContainer = if (dark) Color(0xFFDDE1FF) else Color(0xFF152252),
            tertiary = if (dark) Color(0xFFFFCF9A) else Color(0xFF8A5300),
            onTertiary = if (dark) Color(0xFF3B2800) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun forest(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.ForestDark else Palette.ForestLight,
            onPrimary = if (dark) Color(0xFF0B3316) else Color.White,
            primaryContainer = if (dark) Palette.ForestContainerDark else Palette.ForestContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFD3EED8) else Color(0xFF123D1D),
            secondary = if (dark) Color(0xFFFFCF87) else Color(0xFF7A5A18),
            onSecondary = if (dark) Color(0xFF3A2A00) else Color.White,
            secondaryContainer = if (dark) Color(0xFF5A4211) else Color(0xFFFFE7BC),
            onSecondaryContainer = if (dark) Color(0xFFFFE7BC) else Color(0xFF3A2A00),
            tertiary = if (dark) Color(0xFFB7C6FF) else Color(0xFF45589B),
            onTertiary = if (dark) Color(0xFF1A2A66) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun amber(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.AmberDark else Palette.AmberLight,
            onPrimary = if (dark) Color(0xFF3A2400) else Color.White,
            primaryContainer = if (dark) Palette.AmberContainerDark else Palette.AmberContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFFFE7BC) else Color(0xFF3A2400),
            secondary = if (dark) Color(0xFFB7C6FF) else Color(0xFF45589B),
            onSecondary = if (dark) Color(0xFF1A2A66) else Color.White,
            secondaryContainer = if (dark) Color(0xFF334480) else Color(0xFFDDE1FF),
            onSecondaryContainer = if (dark) Color(0xFFDDE1FF) else Color(0xFF152252),
            tertiary = if (dark) Color(0xFF9ED6B8) else Color(0xFF3F6B52),
            onTertiary = if (dark) Color(0xFF0C2C1B) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun coral(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.CoralDark else Palette.CoralLight,
            onPrimary = if (dark) Color(0xFF4A1509) else Color.White,
            primaryContainer = if (dark) Palette.CoralContainerDark else Palette.CoralContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFFFDBD2) else Color(0xFF4A1509),
            secondary = if (dark) Color(0xFFB7C6FF) else Color(0xFF45589B),
            onSecondary = if (dark) Color(0xFF1A2A66) else Color.White,
            secondaryContainer = if (dark) Color(0xFF334480) else Color(0xFFDDE1FF),
            onSecondaryContainer = if (dark) Color(0xFFDDE1FF) else Color(0xFF152252),
            tertiary = if (dark) Color(0xFFFFCF87) else Color(0xFF7A5A18),
            onTertiary = if (dark) Color(0xFF3A2A00) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun rose(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.RoseDark else Palette.RoseLight,
            onPrimary = if (dark) Color(0xFF4A0B26) else Color.White,
            primaryContainer = if (dark) Palette.RoseContainerDark else Palette.RoseContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFFFD9E3) else Color(0xFF4A0B26),
            secondary = if (dark) Color(0xFF9ED6B8) else Color(0xFF3F6B52),
            onSecondary = if (dark) Color(0xFF0C2C1B) else Color.White,
            secondaryContainer = if (dark) Color(0xFF2A5040) else Color(0xFFCFEBD8),
            onSecondaryContainer = if (dark) Color(0xFFB7EFCB) else Color(0xFF16351F),
            tertiary = if (dark) Color(0xFFFFCF87) else Color(0xFF7A5A18),
            onTertiary = if (dark) Color(0xFF3A2A00) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )

        private fun slate(dark: Boolean, highContrast: Boolean) = AccentScheme(
            primary = if (dark) Palette.SlateDark else Palette.SlateLight,
            onPrimary = if (dark) Color(0xFF16202F) else Color.White,
            primaryContainer = if (dark) Palette.SlateContainerDark else Palette.SlateContainerLight,
            onPrimaryContainer = if (dark) Color(0xFFD9E2F0) else Color(0xFF1E2C3D),
            secondary = if (dark) Color(0xFFB7C6DC) else Color(0xFF56606F),
            onSecondary = if (dark) Color(0xFF1E2632) else Color.White,
            secondaryContainer = if (dark) Color(0xFF2C3A4D) else Color(0xFFE1E6ED),
            onSecondaryContainer = if (dark) Color(0xFFD9E2F0) else Color(0xFF2A3440),
            tertiary = if (dark) Color(0xFFB7C6DC) else Color(0xFF56606F),
            onTertiary = if (dark) Color(0xFF1E2632) else Color.White,
            error = if (dark) Palette.DarkError else Palette.LightError,
            onError = if (dark) Palette.LightOnError else Palette.DarkOnError,
        )
    }
}

/** Light scheme, optionally with a stronger outline for the high-contrast setting. */
fun lightScheme(accent: AccentScheme, highContrast: Boolean, amoled: Boolean) = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    error = accent.error,
    onError = accent.onError,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnBackground,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnSurface,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
    surfaceContainer = Palette.LightSurfaceContainer,
    surfaceContainerHigh = Palette.LightSurfaceVariant,
    surfaceContainerHighest = Palette.LightSurfaceVariant,
    surfaceContainerLow = Palette.LightSurface,
    surfaceContainerLowest = Color.White,
    outline = if (highContrast) Palette.LightOnSurfaceVariant else Palette.LightOutline,
    outlineVariant = if (highContrast) Palette.LightOutline else Palette.LightOutlineVariant,
    inverseSurface = Palette.LightOnSurface,
    inverseOnSurface = Palette.LightSurface,
    inversePrimary = accent.primary,
    scrim = Color.Black,
    surfaceTint = accent.primary,
)

/** Dark scheme, with a true-black variant for the AMOLED theme. */
fun darkScheme(accent: AccentScheme, highContrast: Boolean, amoled: Boolean) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    error = accent.error,
    onError = accent.onError,
    background = if (amoled) Palette.AmoledBackground else Palette.DarkBackground,
    onBackground = if (amoled) Color(0xFFF0EBF5) else Palette.DarkOnBackground,
    surface = if (amoled) Palette.AmoledSurface else Palette.DarkSurface,
    onSurface = if (amoled) Color(0xFFF0EBF5) else Palette.DarkOnSurface,
    surfaceVariant = if (amoled) Palette.AmoledSurfaceVariant else Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,
    surfaceContainer = if (amoled) Palette.AmoledSurfaceContainer else Palette.DarkSurfaceContainer,
    surfaceContainerHigh = if (amoled) Palette.AmoledSurfaceVariant else Palette.DarkSurfaceVariant,
    surfaceContainerHighest = if (amoled) Palette.AmoledSurfaceVariant else Palette.DarkSurfaceVariant,
    surfaceContainerLow = if (amoled) Palette.AmoledSurface else Palette.DarkSurface,
    surfaceContainerLowest = if (amoled) Palette.AmoledBackground else Palette.DarkBackground,
    outline = if (highContrast) Palette.DarkOnBackground else Palette.DarkOutline,
    outlineVariant = if (highContrast) Palette.DarkOnSurfaceVariant else Palette.DarkOutlineVariant,
    inverseSurface = if (amoled) Color(0xFFF0EBF5) else Palette.DarkOnSurface,
    inverseOnSurface = if (amoled) Palette.AmoledBackground else Palette.DarkSurface,
    inversePrimary = accent.primary,
    scrim = Color.Black,
    surfaceTint = accent.primary,
)
