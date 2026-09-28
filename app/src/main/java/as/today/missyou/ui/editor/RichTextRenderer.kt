package `as`.today.missyou.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `as`.today.missyou.domain.model.CalloutBlock
import `as`.today.missyou.domain.model.CalloutTone
import `as`.today.missyou.domain.model.CodeBlock
import `as`.today.missyou.domain.model.DividerBlock
import `as`.today.missyou.domain.model.FileBlock
import `as`.today.missyou.domain.model.HeadingBlock

import `as`.today.missyou.domain.model.ImageBlock
import `as`.today.missyou.domain.model.ListStyle
import `as`.today.missyou.domain.model.ParagraphBlock
import `as`.today.missyou.domain.model.QuoteBlock
import `as`.today.missyou.domain.model.RichBlock
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.StyledText
import `as`.today.missyou.domain.model.TableBlock
import `as`.today.missyou.domain.model.TextAlign as BlockAlign
import `as`.today.missyou.domain.model.TextMark
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import `as`.today.missyou.ui.theme.highlightColor

/**
 * Converts a [StyledText] into an [AnnotatedString].
 *
 * Shared by the editor and the reader so what a user sees while writing is exactly
 * what they get after the entry locks. Marks are resolved to absolute colours here
 * rather than as span styles on theme attributes, which keeps the rendering stable
 * when the theme changes under a locked entry.
 */
@Composable
fun StyledText.toAnnotatedString(
    linkColor: Color = MaterialTheme.colorScheme.primary,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    codeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightForeground: Color = MaterialTheme.colorScheme.onSurface,
): AnnotatedString = buildAnnotatedString {
    append(text)
    marks.forEach { range ->
        if (range.isEmpty) return@forEach
        addStyle(
            SpanStyle(
                fontWeight = when (range.mark) {
                    TextMark.BOLD -> FontWeight.Bold
                    else -> null
                },
                fontStyle = if (range.mark == TextMark.ITALIC) FontStyle.Italic else null,
                textDecoration = when (range.mark) {
                    TextMark.UNDERLINE -> TextDecoration.Underline
                    TextMark.STRIKETHROUGH -> TextDecoration.LineThrough
                    else -> null
                },
                fontFamily = if (range.mark == TextMark.CODE) FontFamily.Monospace else null,
                background = if (range.mark == TextMark.CODE) codeBackground else Color.Unspecified,
                color = if (range.mark == TextMark.CODE) codeColor else Color.Unspecified,
            ),
            range.start,
            range.end,
        )
    }
    highlights.forEach { range ->
        if (range.isEmpty) return@forEach
        addStyle(
            SpanStyle(
                background = highlightColor(range.tone, isDarkTheme()),
                color = highlightForeground,
            ),
            range.start,
            range.end,
        )
    }
    links.forEach { range ->
        if (range.isEmpty) return@forEach
        addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            range.start,
            range.end,
        )
        // String annotations carry the URL and let ClickableText resolve a tap
        // offset back to the link without holding a parallel structure.
        addStringAnnotation(LINK_TAG, range.url, range.start, range.end)
    }
}

const val LINK_TAG = "today.link"

@Composable
private fun isDarkTheme(): Boolean = androidx.compose.foundation.isSystemInDarkTheme() ||
    `as`.today.missyou.ui.theme.TodayTheme.settings.themeMode ==
    `as`.today.missyou.domain.model.ThemeMode.DARK ||
    `as`.today.missyou.ui.theme.TodayTheme.settings.themeMode ==
    `as`.today.missyou.domain.model.ThemeMode.AMOLED

/**
 * Renders a document for reading.
 *
 * This is the only renderer: the locked-entry reader, list previews and the export
 * preview all go through it, so a sealed entry is displayed by exactly the same code
 * path that displayed it while it was editable.
 */
@Composable
fun RichDocumentView(
    document: RichDocument,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onAttachmentClick: (String) -> Unit = {},
    imageResolver: (String) -> (@Composable () -> Unit)? = { null },
) {
    Column(modifier = modifier) {
        var numberedRun = 0
        document.blocks.forEach { block ->
            when (block) {
                is ParagraphBlock -> {
                    if (block.listStyle == ListStyle.NUMBERED) numberedRun++ else numberedRun = 0
                    ParagraphView(
                        block = block,
                        number = numberedRun,
                        onLinkClick = onLinkClick,
                    )
                }
                else -> numberedRun = 0
            }
            BlockView(block, onLinkClick, onAttachmentClick, imageResolver)
        }
    }
}

@Composable
private fun BlockView(
    block: RichBlock,
    onLinkClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    imageResolver: (String) -> (@Composable () -> Unit)?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is ParagraphBlock -> Unit // already rendered by ParagraphView

        is HeadingBlock -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            Text(
                text = block.text.toAnnotatedString(),
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                style = style,
                color = onSurface,
            )
        }

        is QuoteBlock -> Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = Spacing.xs),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = block.text.toAnnotatedString(),
                style = MaterialTheme.typography.bodyLarge,
                color = onSurface,
                fontStyle = FontStyle.Italic,
            )
        }

        is CalloutBlock -> {
            val accent = calloutAccent(block.tone)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs)
                    .clip(RoundedCornerShape(Radii.medium))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(Spacing.sm),
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(accent),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = block.tone.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    Text(
                        text = block.text.toAnnotatedString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                    )
                }
            }
        }

        is CodeBlock -> Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .clip(RoundedCornerShape(Radii.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.sm),
        ) {
            if (!block.language.isNullOrBlank()) {
                Text(
                    text = block.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is DividerBlock -> HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        is TableBlock -> TableView(block)

        is ImageBlock -> {
            val resolver = imageResolver(block.attachmentId)
            if (resolver != null) {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                    resolver()
                }
            }
            if (block.caption.text.isNotBlank()) {
                Text(
                    text = block.caption.toAnnotatedString(),
                    modifier = Modifier.padding(bottom = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is FileBlock -> Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs)
                .clip(RoundedCornerShape(Radii.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickableRow { onAttachmentClick(block.attachmentId) }
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Attachment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "Attachment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ParagraphView(
    block: ParagraphBlock,
    number: Int,
    onLinkClick: (String) -> Unit,
) {
    val style = MaterialTheme.typography.bodyLarge
    val alignment = when (block.alignment) {
        BlockAlign.START -> TextAlign.Start
        BlockAlign.CENTER -> TextAlign.Center
        BlockAlign.END -> TextAlign.End
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.indentLevel * INDENT_DP).dp, top = Spacing.xxs),
        verticalAlignment = Alignment.Top,
    ) {
        when (block.listStyle) {
            ListStyle.BULLET -> BulletMark(block.indentLevel)
            ListStyle.NUMBERED -> Text(
                text = "$number.",
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(BulletWidth),
            )
            ListStyle.CHECK -> Icon(
                imageVector = if (block.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (block.checked) "Checked" else "Unchecked",
                tint = if (block.checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp),
            )
            ListStyle.NONE -> Spacer(Modifier.width(BulletWidth))
        }
        if (block.listStyle != ListStyle.NONE) Spacer(Modifier.width(Spacing.xs))
        ClickableText(
            text = block.text.toAnnotatedString(),
            style = style,
            textAlign = alignment,
            modifier = Modifier.weight(1f),
            onLinkClick = onLinkClick,
        )
    }
}

@Composable
private fun BulletMark(indent: Int) {
    Text(
        text = "•",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(BulletWidth),
    )
}

@Composable
private fun TableView(block: TableBlock) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .horizontalScroll(rememberScrollState()),
    ) {
        block.rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEach { cell ->
                    val isHeader = rowIndex == 0 && block.hasHeaderRow
                    Box(
                        Modifier
                            .widthMin(96.dp)
                            .clip(RoundedCornerShape(Radii.small))
                            .background(
                                if (isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            )
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    ) {
                        Text(
                            text = cell.toAnnotatedString(),
                            style = if (isHeader) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            HorizontalDivider(color = border)
        }
    }
}

@Composable
private fun CalloutTone.displayName(): String = when (this) {
    CalloutTone.INFO -> "Note"
    CalloutTone.SUCCESS -> "Good"
    CalloutTone.WARNING -> "Careful"
    CalloutTone.DANGER -> "Important"
    CalloutTone.TIP -> "Tip"
}

@Composable
private fun calloutAccent(tone: CalloutTone): Color = when (tone) {
    CalloutTone.INFO -> MaterialTheme.colorScheme.primary
    CalloutTone.SUCCESS -> MaterialTheme.colorScheme.secondary
    CalloutTone.WARNING -> MaterialTheme.colorScheme.tertiary
    CalloutTone.DANGER -> MaterialTheme.colorScheme.error
    CalloutTone.TIP -> MaterialTheme.colorScheme.primary
}

/**
 * Clickable rich text that reports which link was activated.
 *
 * Compose's `ClickableText` reports a character offset rather than a URL, so the
 * offset is resolved back through the string annotations added by
 * [toAnnotatedString]. Returning `null` from the callback lets Compose fall back to
 * its own handling, which is what happens for taps that are not on a link.
 */
@Suppress("DEPRECATION")
@Composable
fun ClickableText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    onLinkClick: (String) -> Unit = {},
) {
    // `ClickableText` is deprecated in favour of `LinkAnnotation`-carrying text, but
    // the migration would mean rewriting `toAnnotatedString` and every caller of it.
    // The deprecation is behavioural, not a bug, so the existing implementation is
    // kept until the annotation pipeline is reworked as a single change.
    @Suppress("DEPRECATION")
    androidx.compose.foundation.text.ClickableText(
        text = text,
        modifier = modifier,
        style = style.copy(textAlign = textAlign ?: TextAlign.Unspecified),
        onClick = { offset ->
            text.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.let(onLinkClick)
        },
    )
}


private val BulletWidth = 22.dp
private const val INDENT_DP = 18

@Composable
private fun Modifier.widthMin(min: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.defaultMinSize(minWidth = min))

@Composable
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
