package `as`.today.missyou.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import `as`.today.missyou.domain.model.HapticLevel
import `as`.today.missyou.ui.theme.Motion
import `as`.today.missyou.ui.theme.TodayTheme

/**
 * Haptics, honouring the user's setting and the system toggle (§33).
 *
 * The three levels map onto the platform's own vocabulary: an off switch that
 * respects the user's ON/OFF choice, a light tick for ordinary interaction, and a
 * firmer confirmation reserved for things that actually matter such as a day
 * sealing or a checklist item completing.
 */
@Composable
fun rememberHaptics(): Haptics {
    val level = TodayTheme.settings.haptics
    val feedback = LocalHapticFeedback.current
    return remember(level, feedback) { Haptics(level, feedback) }
}

class Haptics(
    private val level: HapticLevel,
    private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val enabled: Boolean get() = level != HapticLevel.OFF

    /** Ordinary press, checkbox, selection change. */
    fun light() {
        if (level == HapticLevel.OFF) return
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Saving, navigation changes, theme changes. */
    fun medium() {
        if (level == HapticLevel.OFF) return
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** A journal entry sealing permanently. Deliberately the strongest. */
    fun strong() {
        if (level == HapticLevel.OFF) return
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * The press response used by every card and button in the app (§13: 1.0 → 0.96 → 1.0).
 *
 * It is a spring rather than a tween so releasing mid-animation carries momentum,
 * which is what makes the interaction feel physical instead of mechanical. When
 * reduce-motion is on the scale is skipped entirely rather than merely shortened,
 * because scaling is movement.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = PRESSED_SCALE,
    onPressFinished: () -> Unit = {},
): Modifier {
    val reduceMotion = TodayTheme.settings.reduceMotion
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = Motion.press(reduceMotion),
        label = "press-scale",
    )
    return this.graphicsLayer {
        scaleX = if (reduceMotion) 1f else scale
        scaleY = if (reduceMotion) 1f else scale
    }
}

const val PRESSED_SCALE = 0.96f

/** Interaction source that still reports presses for a modifier that also clicks. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }
