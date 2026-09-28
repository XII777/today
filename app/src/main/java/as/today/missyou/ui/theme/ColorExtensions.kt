package `as`.today.missyou.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Small colour helpers used to derive a usable custom-accent scheme.
 *
 * These are deliberately simple sRGB transforms rather than a full colour-science
 * pipeline: the input is a single user-picked swatch, and the output only has to be
 * "obviously good enough and readable", which these achieve in a few lines and at
 * zero runtime cost.
 */
internal fun Color.lighten(amount: Float): Color =
    Color(
        red = (red + (1f - red) * amount).coerceIn(0f, 1f),
        green = (green + (1f - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha,
    )

internal fun Color.darken(amount: Float): Color =
    Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha,
    )

internal fun Color.desaturate(amount: Float): Color {
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(
        red = red + (grey - red) * amount,
        green = green + (grey - green) * amount,
        blue = blue + (grey - blue) * amount,
        alpha = alpha,
    )

    }

/** Rotates hue by [fraction] of a full turn, keeping saturation and value. */
internal fun Color.rotate(fraction: Float): Color {
    val hsv = FloatArray(3)
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
    )
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[0] = (hsv[0] + fraction * 360f) % 360f
    val out = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(out) / 255f,
        green = android.graphics.Color.green(out) / 255f,
        blue = android.graphics.Color.blue(out) / 255f,
        alpha = alpha,
    )
}

/** Relative luminance, re-exported so callers need only one import. */
internal fun Color.relativeLuminance(): Float = luminance()

/**
 * WCAG contrast ratio between two opaque colours, 1.0 to 21.0.
 *
 * Used by the high-contrast setting to verify a derived pairing rather than
 * assuming it is readable.
 */
internal fun contrastRatio(a: Color, b: Color): Float {
    val lighter = max(a.relativeLuminance(), b.relativeLuminance())
    val darker = min(a.relativeLuminance(), b.relativeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Picks whichever of [darkCandidate] or [lightCandidate] contrasts better with
 * [background], so generated pairs never fail WCAG AA.
 */
internal fun readableOn(background: Color, darkCandidate: Color, lightCandidate: Color): Color =
    if (contrastRatio(background, darkCandidate) >= contrastRatio(background, lightCandidate)) {
        darkCandidate
    } else {
        lightCandidate
    }

/** Converts a Compose colour to a packed ARGB long, the form stored in settings. */
fun Color.toArgbLong(): Long =
    (alpha.toLong() shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()

/** Inverse of [toArgbLong]. */
fun Long.toComposeColor(): Color = Color(this.toULong() and 0xFFFFFFFFUL)

/** True when the two colours are perceptibly different. */
internal fun Color.isCloseTo(other: Color): Boolean =
    abs(red - other.red) < 0.01f && abs(green - other.green) < 0.01f && abs(blue - other.blue) < 0.01f
