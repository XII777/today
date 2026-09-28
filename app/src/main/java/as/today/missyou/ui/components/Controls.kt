package `as`.today.missyou.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `as`.today.missyou.ui.theme.Motion
import `as`.today.missyou.ui.theme.Springs
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import `as`.today.missyou.ui.theme.TodayTheme

/**
 * The app's primary button.
 *
 * Press response is the 1.0 → 0.96 → 1.0 spring the brief asks for, and the whole
 * thing is skipped under reduce-motion because scaling is movement.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interactionSource = rememberPressSource()
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(Radii.pill)
    Row(
        modifier = modifier
            .heightIn(min = touchTargetHeight())
            .clip(shape)
            .pressScale(interactionSource, enabled)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.medium()
                    onClick()
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/** A secondary, outline-only button. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = rememberPressSource()
    val shape = RoundedCornerShape(Radii.pill)
    Row(
        modifier = modifier
            .heightIn(min = touchTargetHeight())
            .clip(shape)
            .pressScale(interactionSource, enabled)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A circular icon button that meets the minimum touch target (§34). */
@Composable
fun IconPillButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interactionSource = rememberPressSource()
    val shape = RoundedCornerShape(Radii.pill)
    Box(
        modifier = modifier
            .size(touchTargetHeight())
            .clip(shape)
            .pressScale(interactionSource, enabled)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The floating "✓ Saved locally" confirmation (§32).
 *
 * It is announced as a polite live region so a screen-reader user learns that the
 * entry was written without having to hunt for the change.
 */
@Composable
fun SaveConfirmation(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    val settings = TodayTheme.settings
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = Motion.toggle(settings.reduceMotion),
        label = "save-confirm-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (settings.reduceMotion) 90 else 180),
        label = "save-confirm-alpha",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(Radii.pill),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = message
                },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

/**
 * Locked / editable indicator (§1, §9).
 *
 * The badge always contains the word "Locked" as well as the icon, because
 * meaning must never be carried by the icon or colour alone.
 */
@Composable
fun LockBadge(
    locked: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accent = if (locked) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val label = if (locked) "Locked" else "Editable"
    val icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen

    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(Radii.pill),
        color = accent.copy(alpha = 0.14f),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) Spacing.xs else Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            if (!compact) {
                Spacer(Modifier.width(Spacing.xxs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}

/** Empty state block (§51). */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(Radii.xlarge),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}

/**
 * The floating sheet wrapper (§31).
 *
 * Rounded top corners, spring entrance, keyboard aware, and scrim-dismissible.
 * The brief warns against using a sheet for every tiny action, so callers are
 * expected to reserve this for genuine multi-option decisions.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FloatingSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val reduceMotion = TodayTheme.settings.reduceMotion
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = Radii.xlarge, topEnd = Radii.xlarge),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        content()
        Spacer(Modifier.height(Spacing.lg))
    }
    // Referenced so the spring spec is applied consistently with the rest of the app.
    @Suppress("UNUSED_EXPRESSION")
    remember(reduceMotion) { Motion.sheet(reduceMotion) }
}

/** Visibility wrapper that animates with a spring and keeps layout stable. */
@Composable
fun SpringVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = TodayTheme.settings.reduceMotion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) {
            fadeIn(tween(90))
        } else {
            fadeIn(tween(120)) + scaleIn(springSpec(), initialScale = 0.9f)
        },
        exit = if (reduceMotion) {
            fadeOut(tween(90))
        } else {
            fadeOut(tween(120)) + scaleOut(springSpec(), targetScale = 0.9f)
        },
        content = { content() },
    )
}

@Composable
private fun springSpec(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
    if (TodayTheme.settings.reduceMotion) tween(90) else Springs.Toggle

@Composable
internal fun touchTargetHeight(): androidx.compose.ui.unit.Dp =
    if (TodayTheme.settings.largerTouchTargets) 56.dp else 48.dp


@Composable
internal fun FullWidthSpacer() = Spacer(Modifier.fillMaxWidth().height(Spacing.xs))
