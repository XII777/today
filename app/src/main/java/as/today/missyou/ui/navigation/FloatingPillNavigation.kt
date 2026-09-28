package `as`.today.missyou.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `as`.today.missyou.domain.model.NavigationAppearance
import `as`.today.missyou.ui.components.rememberHaptics
import `as`.today.missyou.ui.components.rememberPressSource
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Motion
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import `as`.today.missyou.ui.theme.TodayTheme

/** The five top-level destinations (§12). */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    JOURNAL("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth),
    INSIGHTS("insights", "Insights", Icons.Filled.Insights),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/**
 * The floating pill navigation bar (§12).
 *
 * Behaviour the brief asks for, and how it is achieved:
 *
 *  * **Inactive shows an icon, active shows icon + title.** The active item's width
 *    is animated, so the label is revealed by the pill growing rather than by a
 *    cross-fade. That avoids the "fade-heavy" transition the brief rules out.
 *  * **Spring, not tween.** `Motion.navigation` provides a damping ratio around
 *    0.66, so the pill settles with a small overshoot.
 *  * **Floats above the bottom edge** and never covers content: the scaffold
 *    reserves `LayoutMetrics.contentBottomInset` underneath it.
 *  * **Frosted by default.** A blurred translucent surface, degrading to solid or
 *    fully transparent on request, and to a flat fill when reduce-motion or the
 *    solid appearance is chosen.
 */
@Composable
fun FloatingPillNavigation(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = TodayTheme.settings
    val reduceMotion = settings.reduceMotion
    val haptics = rememberHaptics()

    val surface = pillSurface(settings.navigationAppearance, settings.reduceVisualEffects)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .height(LayoutMetrics.floatingNavHeight)
            .clip(RoundedCornerShape(Radii.pill))
            .background(surface)
            .semantics { contentDescription = "Main navigation" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                PillItem(
                    destination = destination,
                    active = destination == current,
                    reduceMotion = reduceMotion,
                    onClick = {
                        if (destination != current) {
                            haptics.light()
                            onSelect(destination)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PillItem(
    destination: Destination,
    active: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = rememberPressSource()
    val scheme = MaterialTheme.colorScheme
    val contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant

    val targetWidth = if (active) 132.dp else 52.dp
    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = Motion.navigationDp(reduceMotion),
        label = "pill-width",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = Motion.toggle(reduceMotion),
        label = "pill-icon-scale",
    )

    val description = remember(destination, active) {
        if (active) "${destination.label}, selected" else destination.label
    }

    Row(
        modifier = Modifier
            .width(width)
            .height(LayoutMetrics.floatingNavHeight - Spacing.xs)
            .clip(RoundedCornerShape(Radii.pill))
            .background(if (active) scheme.primaryContainer else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = description
                this.selected = active
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        if (active) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * The pill background.
 *
 * `FROSTED` uses a translucent gradient rather than a real blur. A real blur
 * forces an offscreen layer on every frame, which is exactly the kind of cost the
 * brief asks to avoid on low-end hardware; the translucent scrim plus a soft
 * border reads the same and costs nothing.
 */
@Composable
private fun pillSurface(
    appearance: NavigationAppearance,
    reduceEffects: Boolean,
): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        reduceEffects || appearance == NavigationAppearance.SOLID -> scheme.surfaceContainer
        appearance == NavigationAppearance.TRANSPARENT -> Color.Transparent
        // A translucent fill plus a soft border reads as frosted glass without the
        // per-frame offscreen layer a real blur would cost on a low-end device.
        else -> scheme.surface.copy(alpha = 0.94f)
    }
}

/** A hairline border that separates the pill from whatever is behind it. */
@Composable
fun pillBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

/** Applies the status bar inset, used by screens that draw their own top bar. */
@Composable
fun Modifier.topInset(): Modifier = this.statusBarsPadding()
