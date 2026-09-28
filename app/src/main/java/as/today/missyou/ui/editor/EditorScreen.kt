package `as`.today.missyou.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange as ComposeTextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.DailyPrompts
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.ListStyle
import `as`.today.missyou.domain.model.ParagraphBlock
import `as`.today.missyou.domain.model.RichBlock
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.TextMark
import `as`.today.missyou.domain.model.ToolbarItem
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.IconPillButton
import `as`.today.missyou.ui.components.LockBadge
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SaveConfirmation
import `as`.today.missyou.ui.components.TagPill
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.components.rememberHaptics
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import `as`.today.missyou.ui.theme.TodayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * The writing surface (§9).
 *
 * The editor owns as little as it can: the ViewModel holds the document and is the
 * only thing that talks to the repository. Each block is a plain
 * [BasicTextField] that renders the styled text as an annotated string while
 * reporting raw text edits upwards, which keeps selection handling in one place
 * instead of scattered across composables.
 */
@Composable
fun EditorScreen(
    container: AppContainer,
    date: LocalDate,
    onBack: () -> Unit,
) {
    val viewModel = containerViewModel(key = "editor-$date") { EditorViewModel(it, date) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    // Leaving always settles the timer and flushes any pending autosave, so the
    // writing-time figure and the saved document agree.
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveEditor() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                date = date,
                isLocked = state.isLocked,
                onBack = {
                    viewModel.onLeaveEditor()
                    onBack()
                },
                onSave = {
                    haptics.medium()
                    viewModel.save()
                },
            )

            if (state.status is EditorStatus.Failed) {
                val message = (state.status as EditorStatus.Failed).message
                TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.isLocked) {
                LockedBanner()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = Spacing.sm,
                    bottom = Spacing.xl,
                ),
            ) {
                item(key = "title") {
                    TitleField(
                        value = state.title,
                        editable = state.canEdit,
                        onValueChange = viewModel::onTitleChanged,
                    )
                }

                state.prompt?.takeIf { it.isNotBlank() && state.isToday }?.let { promptText ->
                    item(key = "prompt") {
                        PromptCard(
                            text = promptText,
                            onDismiss = { viewModel.onPromptChanged("") },
                        )
                    }
                }

                items(state.document.blocks, key = { it.id }) { block ->
                    BlockEditor(
                        block = block,
                        state = state,
                        viewModel = viewModel,
                        isFocused = state.focusedBlockId == block.id,
                    )
                }
            }

            if (state.canEdit) {
                BlockInsertBar(
                    toolbar = state.toolbar,
                    onHeading = viewModel::cycleHeading,
                    onQuote = viewModel::toggleQuote,
                    onBullets = { viewModel.toggleListStyle(ListStyle.BULLET) },
                    onNumbered = { viewModel.toggleListStyle(ListStyle.NUMBERED) },
                    onChecklist = { viewModel.toggleListStyle(ListStyle.CHECK) },
                    onDivider = viewModel::insertDivider,
                    onCode = viewModel::insertCodeBlock,
                )
            }
        }

        // Floats over the whole editor so a save is confirmed even when the
        // keyboard is up, and disappears on its own.
        SaveConfirmation(
            visible = state.status is EditorStatus.Saved,
            message = "Saved locally",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.canEdit) 64.dp else Spacing.md),
        )
    }
}

@Composable
private fun EditorTopBar(
    date: LocalDate,
    isLocked: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconPillButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm),
        ) {
            Text(
                text = date.format(FULL_DATE),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isLocked) {
                LockBadge(locked = true, compact = true)
            }
        }
        if (!isLocked) {
            IconPillButton(
                icon = Icons.Filled.Check,
                contentDescription = "Save entry",
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun LockedBanner() {
    TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                text = "This day is sealed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = "The day has rolled over, so this entry can no longer be edited.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TitleField(
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LayoutMetrics.horizontalMargin),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Title",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = editable,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
    }
}

@Composable
private fun PromptCard(text: String, onDismiss: () -> Unit) {
    TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.xs)) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                text = "Today's prompt",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Renders one block.
 *
 * Text blocks share a single implementation; the type only changes the decoration
 * and the surrounding affordances, which keeps selection and editing behaviour
 * identical everywhere.
 */
@Composable
private fun BlockEditor(
    block: RichBlock,
    state: EditorUiState,
    viewModel: EditorViewModel,
    isFocused: Boolean,
) {
    val settings = TodayTheme.settings
    when (block) {
        is `as`.today.missyou.domain.model.CodeBlock -> CodeBlockEditor(
            block = block,
            editable = state.canEdit,
            onValueChange = { viewModel.onCodeChanged(block.id, it) },
        )

        is `as`.today.missyou.domain.model.DividerBlock -> HorizontalDividerBlock()

        is ParagraphBlock -> {
            val paragraph = block
            // Indent is clamped in whole density-independent pixels, so a deeply
            // nested list never pushes the text off the screen.
            val indent = (paragraph.indentLevel * INDENT_STEP_DP)
                .coerceAtMost(INDENT_CEILING_DP)
                .dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = LayoutMetrics.horizontalMargin + indent),
            ) {
                if (paragraph.isChecklistItem) {
                    CheckboxRow(
                        checked = paragraph.checked,
                        enabled = state.canEdit,
                        onToggle = { viewModel.toggleChecked(paragraph.id) },
                    )
                }
                TextFieldFor(
                    blockId = paragraph.id,
                    value = paragraph.text.text,
                    decoration = when {
                        paragraph.listStyle == ListStyle.BULLET -> "•  "
                        paragraph.listStyle == ListStyle.NUMBERED -> "1.  "
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    editable = state.canEdit,
                    spellCheck = settings.spellCheck,
                    autoCapitalize = settings.autoCapitalize,
                    imeAction = ImeAction.Default,
                    onFocus = { viewModel.onBlockFocused(paragraph.id) },
                    onTextChange = { viewModel.onTextChanged(paragraph.id, it) },
                    onSelectionChange = viewModel::onSelectionChanged,
                )
            }
        }

        is `as`.today.missyou.domain.model.HeadingBlock -> {
            val heading = block
            val style = when (heading.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            TextFieldFor(
                blockId = heading.id,
                value = heading.text.text,
                decoration = "",
                style = style,
                color = MaterialTheme.colorScheme.onBackground,
                editable = state.canEdit,
                spellCheck = settings.spellCheck,
                autoCapitalize = true,
                imeAction = ImeAction.Default,
                onFocus = { viewModel.onBlockFocused(heading.id) },
                onTextChange = { viewModel.onTextChanged(heading.id, it) },
                onSelectionChange = viewModel::onSelectionChanged,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        is `as`.today.missyou.domain.model.QuoteBlock -> {
            val quote = block
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = LayoutMetrics.horizontalMargin),
            ) {
                // The bar spans the actual text height rather than a fixed guess, so
                // a one-line quote and a ten-line quote both look right.
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
                TextFieldFor(
                    blockId = quote.id,
                    value = quote.text.text,
                    decoration = "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    editable = state.canEdit,
                    spellCheck = settings.spellCheck,
                    autoCapitalize = false,
                    imeAction = ImeAction.Default,
                    modifier = Modifier.padding(start = Spacing.sm),
                    onFocus = { viewModel.onBlockFocused(quote.id) },
                    onTextChange = { viewModel.onTextChanged(quote.id, it) },
                    onSelectionChange = viewModel::onSelectionChanged,
                )
            }
        }

        else -> Unit
    }
}
private val INDENT_STEP_DP = 16
private const val INDENT_CEILING_DP = 72

@Composable
private fun TextFieldFor(
    value: String,
    blockId: String,
    decoration: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    editable: Boolean,
    spellCheck: Boolean,
    autoCapitalize: Boolean,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fontSizeSp = TodayTheme.settings.editorTextSizeSp

    // The field holds a TextFieldValue so selection survives recomposition; the
    // document is the source of truth for the text itself.
    val field = remember(blockId, value) {
        TextFieldValue(
            text = if (decoration.isEmpty()) value else decoration + value,
            selection = ComposeTextRange(
                if (decoration.isEmpty()) value.length else decoration.length + value.length,
            ),
        )
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = LayoutMetrics.horizontalMargin),
    ) {
        if (value.isEmpty() && decoration.isEmpty()) {
            Text(
                text = "Write something…",
                style = style,
                color = colors.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        BasicTextField(
            value = field,
            onValueChange = { updated ->
                val raw = if (decoration.isEmpty()) {
                    updated.text
                } else {
                    updated.text.removePrefix(decoration)
                }
                onTextChange(raw)
                onSelectionChange(
                    TextRange(
                        updated.selection.start - decoration.length.coerceAtMost(updated.selection.start),
                        updated.selection.end - decoration.length.coerceAtMost(updated.selection.end),
                    ).coerceToLength(raw.length),
                )
            },
            enabled = editable,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() }
                .testTag("block-$blockId"),
            textStyle = style.copy(
                color = color,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * TodayTheme.settings.lineSpacing).sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = if (autoCapitalize) {
                    KeyboardCapitalization.Sentences
                } else {
                    KeyboardCapitalization.None
                },
                autoCorrectEnabled = spellCheck,
                imeAction = imeAction,
            ),
        )
    }
}

private fun TextRange.coerceToLength(length: Int): TextRange =
    TextRange(start.coerceIn(0, length), end.coerceIn(0, length))

@Composable
private fun CodeBlockEditor(
    block: `as`.today.missyou.domain.model.CodeBlock,
    editable: Boolean,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val field = remember(block.id, block.code) { TextFieldValue(block.code) }
    TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.xxs)) {
        Box(Modifier.padding(Spacing.sm)) {
            BasicTextField(
                value = field,
                onValueChange = { onValueChange(it.text) },
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
        }
    }
}

@Composable
private fun HorizontalDividerBlock() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm, horizontal = LayoutMetrics.horizontalMargin)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun CheckboxRow(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radii.small))
                .background(if (checked) colors.primary else Color0)
                .clickable(enabled = enabled, onClick = onToggle)
                .semantics {
                    contentDescription = if (checked) "Checked" else "Unchecked"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0x00000000)

/**
 * The block-type bar that sits above the keyboard.
 *
 * It offers the structural actions only; character-level marks are applied through
 * a selection toolbar in the text field itself, which is where the user's attention
 * already is.
 */
@Composable
private fun BlockInsertBar(
    toolbar: List<ToolbarItem>,
    onHeading: () -> Unit,
    onQuote: () -> Unit,
    onBullets: () -> Unit,
    onNumbered: () -> Unit,
    onChecklist: () -> Unit,
    onDivider: () -> Unit,
    onCode: () -> Unit,
) {
    val actions = remember(toolbar) {
        buildList {
            if (ToolbarItem.HEADING in toolbar) {
                add(ToolAction(Icons.Filled.Title, "Heading", onHeading))
            }
            if (ToolbarItem.QUOTE in toolbar) {
                add(ToolAction(Icons.Filled.FormatQuote, "Quote", onQuote))
            }
            if (ToolbarItem.BULLET_LIST in toolbar) {
                add(ToolAction(Icons.AutoMirrored.Filled.FormatListBulleted, "Bulleted list", onBullets))
            }
            if (ToolbarItem.NUMBERED_LIST in toolbar) {
                add(ToolAction(Icons.Filled.FormatListNumbered, "Numbered list", onNumbered))
            }
            if (ToolbarItem.CHECKLIST in toolbar) {
                add(ToolAction(Icons.Filled.Check, "Checklist", onChecklist))
            }
            if (ToolbarItem.CODE in toolbar) {
                add(ToolAction(Icons.Filled.ArrowDropDown, "Code block", onCode))
            }
            add(ToolAction(Icons.Filled.ArrowDropDown, "Divider", onDivider))
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        actions.forEach { action ->
            IconPillButton(
                icon = action.icon,
                contentDescription = action.label,
                onClick = action.onClick,
            )
        }
    }
}

private data class ToolAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)
