package `as`.today.missyou.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.Attachment
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.EmptyState
import `as`.today.missyou.ui.components.IconPillButton
import `as`.today.missyou.ui.components.LockBadge
import `as`.today.missyou.ui.components.TagPill
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.editor.RichDocumentView
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ReaderUiState(
    val loading: Boolean = true,
    val entry: JournalEntry? = null,
    val date: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    val readTime: String = "",
)

/**
 * The read-only view of an entry (§8).
 *
 * Locked entries are simply not editable, so this screen never has to defend
 * against an edit: the edit affordance only appears while the day is still open.
 */
class ReaderViewModel(
    container: AppContainer,
    private val date: LocalDate,
) : AppViewModel(container) {

    val uiState: StateFlow<ReaderUiState> = combine(
        container.journal.observeDay(date),
        container.settings.settings,
    ) { entry, settings ->
        val words = entry?.wordCount ?: 0
        ReaderUiState(
            loading = false,
            entry = entry,
            date = date,
            isToday = date == container.journal.currentJournalDate(),
            readTime = if (settings.showReadingTime && words > 0) {
                TextMetrics.readingTimeLabel(words)
            } else {
                ""
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState(date = date),
    )
}

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

@Composable
fun ReaderScreen(
    container: AppContainer,
    date: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val viewModel = containerViewModel { ReaderViewModel(it, date) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = `as`.today.missyou.domain.model.AppSettings(),
    )
    val entry = state.entry

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LayoutMetrics.horizontalMargin),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPillButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            if (entry != null && !entry.isLocked) {
                IconPillButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Edit entry",
                    onClick = onEdit,
                )
            } else if (entry != null) {
                LockBadge(locked = true, compact = true)
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            text = date.format(FULL_DATE),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )

        if (entry == null) {
            Spacer(Modifier.height(Spacing.xl))
            EmptyState(
                title = "Nothing written",
                body = if (state.isToday) {
                    "You have not written anything for today yet."
                } else {
                    "There is no entry for this day."
                },
            )
            return@Column
        }

        if (entry.title.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (entry.prompt != null && entry.prompt.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            TodayCard {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        text = "Daily prompt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = entry.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        RichDocumentView(
            document = entry.document,
            modifier = Modifier.fillMaxWidth(),
        )

        if (entry.tags.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                entry.tags.forEach { tag -> TagPill(tag) }
            }
        }

        if (entry.attachments.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Attachments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            entry.attachments.forEach { attachment ->
                AttachmentRow(attachment)
                Spacer(Modifier.height(Spacing.xs))
            }
        }

        Spacer(Modifier.height(Spacing.md))
        val stats = buildList {
            if (entry.wordCount > 0) add("${entry.wordCount} words")
            if (state.readTime.isNotEmpty()) add(state.readTime)
            if (settings.showWritingDuration && entry.writingDurationMillis > 0) {
                add(`as`.today.missyou.ui.insights.InsightsViewModel.formatWritingTime(entry.writingDurationMillis, true))
            }
        }
        if (stats.isNotEmpty()) {
            Text(
                text = stats.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.isLocked) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "This day is sealed. It can no longer be changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(LayoutMetrics.contentBottomInset))
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment) {
    TodayCard {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.small))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = attachment.mimeType.substringAfterLast('/').uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${attachment.byteSize / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
