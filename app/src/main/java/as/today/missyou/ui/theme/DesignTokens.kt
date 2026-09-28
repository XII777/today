package `as`.today.missyou.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale (§48).
 *
 * Every gap in the app is one of these eight values. There is no ad-hoc padding,
 * which is what keeps a screen built by six different people looking like one app.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 44.dp
}

/** Corner radii (§48). */
object Radii {
    val small: Dp = 12.dp
    val medium: Dp = 16.dp
    val large: Dp = 20.dp
    val xlarge: Dp = 24.dp
    val pill: Dp = 999.dp
}

/** Minimum interactive sizes, respecting the larger-touch-target preference (§34). */
object TouchTargets {
    val standard: Dp = 48.dp
    val comfortable: Dp = 56.dp
}

/**
 * Spring animations.
 *
 * The brief asks for iOS-inspired spring interaction throughout, and the key to
 * that feel is the damping ratio: somewhere around 0.6–0.8 with medium stiffness.
 * Below roughly 0.5 the motion becomes cartoonish, and above 0.9 it stops reading
 * as a spring at all.
 *
 * Every value here is also available in a reduced-motion form via [Motion], which
 * swaps springs for short tweens without changing any call site.
 */
object Springs {

    /** Cards, list rows, anything the user presses. */
    val Press = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)

    /** The floating pill navigation expanding and collapsing. */
    val Navigation = spring<Float>(dampingRatio = 0.66f, stiffness = 460f)

    /** The same curve in Dp, for the pill's animated width. */
    val NavigationDp = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.66f, stiffness = 460f)

    /** Bottom sheets entering and leaving. */
    val Sheet = spring<Float>(dampingRatio = 0.86f, stiffness = 400f)

    /** Checkboxes, toggles, selection indicators. */
    val Toggle = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)

    /** The editor toolbar expanding. */
    val Toolbar = spring<Float>(dampingRatio = 0.7f, stiffness = 520f)

    /** Calendar month transitions. */
    val Calendar = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

    /** Generic value animation. */
    val Gentle = spring<Float>(dampingRatio = 0.8f, stiffness = 400f)

    /** Non-spring specs for offset/size animations where a spring would overshoot visibly. */
    val Offset: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 500f)
}

/**
 * Respects the "reduce motion" accessibility setting (§34).
 *
 * The rule is to keep *feedback* while removing *movement*: state changes still
 * animate, but over a very short distance and time, so nothing slides, bounces or
 * overshoots.
 */
object Motion {

    private val instant = tween<Float>(durationMillis = 90)
    private val instantDp = tween<androidx.compose.ui.unit.Dp>(durationMillis = 90)

    @Suppress("UNCHECKED_CAST")
    fun <T> spec(spring: AnimationSpec<T>, reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) instant as AnimationSpec<T> else spring

    /** Width/offset animations need a Dp-typed spec. */
    fun dp(spring: AnimationSpec<androidx.compose.ui.unit.Dp>, reduceMotion: Boolean): AnimationSpec<androidx.compose.ui.unit.Dp> =
        if (reduceMotion) instantDp else spring

    fun press(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Press, reduceMotion)

    fun navigation(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Navigation, reduceMotion)

    fun navigationDp(reduceMotion: Boolean): AnimationSpec<androidx.compose.ui.unit.Dp> =
        dp(Springs.NavigationDp, reduceMotion)

    fun sheet(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Sheet, reduceMotion)

    fun toggle(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Toggle, reduceMotion)

    fun toolbar(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Toolbar, reduceMotion)

    fun calendar(reduceMotion: Boolean): AnimationSpec<Float> = spec(Springs.Calendar, reduceMotion)
}
