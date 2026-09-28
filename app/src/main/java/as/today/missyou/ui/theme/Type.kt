package `as`.today.missyou.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography (§48 "clear hierarchy").
 *
 * Only platform font families are used, so no font files ship with the app and no
 * font licence applies (§37). Serif and monospace variants come from the system and
 * are resolved by the platform, not bundled.
 */
internal fun todayTypography(
    textScale: Float,
    bodyFont: FontFamily,
    compact: Boolean,
): Typography {
    val scale = textScale.coerceIn(0.85f, 2.0f)

    fun sp(value: Float) = (value * scale).sp
    fun ls(multiplier: Float, base: Float) = (base * multiplier * (if (compact) 0.88f else 1f)).sp

    val default = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun style(
        size: Float,
        lineHeight: Float,
        weight: FontWeight,
        letterSpacing: Float = 0f,
    ) = TextStyle(
        fontFamily = bodyFont,
        fontSize = sp(size),
        lineHeight = ls(1f, lineHeight),
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = default,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    return Typography(
        displaySmall = style(34f, 42f, FontWeight.Bold, (-0.4f)),
        headlineLarge = style(30f, 38f, FontWeight.Bold, (-0.3f)),
        headlineMedium = style(25f, 33f, FontWeight.Bold, (-0.2f)),
        headlineSmall = style(21f, 29f, FontWeight.SemiBold, (-0.1f)),
        titleLarge = style(19f, 27f, FontWeight.SemiBold),
        titleMedium = style(17f, 25f, FontWeight.SemiBold),
        titleSmall = style(15f, 22f, FontWeight.SemiBold),
        bodyLarge = style(17f, 27f, FontWeight.Normal),
        bodyMedium = style(15f, 23f, FontWeight.Normal),
        bodySmall = style(13.5f, 20f, FontWeight.Normal),
        labelLarge = style(15f, 21f, FontWeight.SemiBold, 0.1f),
        labelMedium = style(13f, 18f, FontWeight.Medium, 0.2f),
        labelSmall = style(11.5f, 16f, FontWeight.Medium, 0.3f),
    )
}
