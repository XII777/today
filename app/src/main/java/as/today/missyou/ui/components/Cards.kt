package `as`.today.missyou.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing

/**
 * The app's card.
 *
 * A soft, generously rounded surface with no default elevation: depth comes from
 * the surface tint of the theme rather than from shadows, which reads cleaner on an
 * OLED panel and costs nothing to draw (§48 "soft rounded surfaces", "unnecessary
 * shadows").
 */
@Composable
fun TodayCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = rememberPressSource()
    val shape = RoundedCornerShape(LayoutMetrics.cardCorner)

    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .pressScale(interactionSource, enabled = enabled && onClick != null)
        .background(containerColor, shape)
        .then(
            if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier,
        )

    Column(
        modifier = base.then(
            if (onClick != null) {
                Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
            } else {
                Modifier
            },
        ),
        content = content,
    )
}

/** A compact card used in horizontal scrollers and the recent-memories row. */
@Composable
fun MemoryCard(
    modifier: Modifier = Modifier,
    accent: Color,
    title: String,
    preview: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = rememberPressSource()
    val shape = RoundedCornerShape(Radii.large)
    TodayCard(
        modifier = modifier
            .widthMemory()
            .semantics {
                contentDescription = buildString {
                    append(title)
                    if (preview.isNotBlank()) append(", ").append(preview)
                    append(if (locked) ", locked entry" else "")
                }
            },
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = accent.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(accent),
                )
                Gap()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Gap()
            Text(
                text = preview.ifBlank { "No preview" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Modifier.widthMemory(): Modifier = this.then(Modifier.fillMaxWidth(0.62f))

@Composable
fun Gap() {
    Box(Modifier.size(Spacing.xs))
}

/**
 * Section heading used across Settings, Insights and the archive.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = LayoutMetrics.horizontalMargin,
                end = LayoutMetrics.horizontalMargin,
                top = Spacing.lg,
                bottom = Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
        trailing?.invoke()
    }
}

/**
 * A labelled stat used on the home and insights screens.
 */
@Composable
fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(Radii.medium),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A pill-shaped tag.
 *
 * Tags carry text, not just colour, which satisfies the requirement that meaning is
 * never encoded through colour alone (§34).
 */
@Composable
fun TagPill(
    tag: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radii.pill)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Simple bordered surface used for empty states and inline notices. */
@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    action: @Composable (() -> Unit)? = null,
) {
    TodayCard(
        modifier = modifier,
        containerColor = accent.copy(alpha = 0.10f),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Gap()
                Box(Modifier.size(Spacing.xs))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            action?.invoke()
        }
    }
}
