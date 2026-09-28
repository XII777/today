package `as`.today.missyou.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock

import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `as`.today.missyou.AppContainer

import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.ui.components.LockBadge
import `as`.today.missyou.ui.components.MemoryCard
import `as`.today.missyou.ui.components.NoticeCard
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SectionHeader
import `as`.today.missyou.ui.components.SecondaryButton
import `as`.today.missyou.ui.components.StatChip
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.navigation.Destination
import `as`.today.missyou.ui.navigation.FloatingPillNavigation
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The home screen (§4, §30).
 *
 * The hierarchy is the one the brief sketches: a greeting, the date, today's
 * journal as the single large card, recent memories as a browsable strip, and
 * writing activity as light statistics. It stays spacious rather than dense.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenEntry: (LocalDate) -> Unit,
    onWrite: (LocalDate) -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val viewModel = containerViewModel { HomeViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = `as`.today.missyou.domain.model.AppSettings(),
    )
    val listState = rememberLazyListState()

    // The countdown changes over time, so nudge the view model once a minute.
    LaunchedEffect(state.journalDate) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            viewModel.refresh()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = Spacing.xl,
                bottom = LayoutMetrics.contentBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "header") {
                HomeHeader(state)
            }

            val clockWarning = state.clockWarning
            if (clockWarning != null) {
                item(key = "clock-warning") {
                    NoticeCard(
                        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                        text = clockWarning,
                        icon = Icons.Filled.Lock,
                        action = {
                            SecondaryButton(text = "OK", onClick = viewModel::acknowledgeClockWarning)
                        },
                    )
                }
            }

            item(key = "today") {
                TodayCardSection(
                    state = state,
                    hidePreviews = settings.hidePreviews,
                    onPrimary = {
                        when (state.primaryAction) {
                            PrimaryAction.WRITE, PrimaryAction.CONTINUE -> onWrite(state.journalDate)
                            PrimaryAction.READ_LOCKED -> onOpenEntry(state.journalDate)
                        }
                    },
                    onOpenEntry = { onOpenEntry(state.journalDate) },
                )
            }

            if (state.recentEntries.isNotEmpty()) {
                item(key = "recent-header") { SectionHeader("Recent memories") }
                item(key = "recent-strip") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = LayoutMetrics.horizontalMargin),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.recentEntries, key = { it.id }) { summary ->
                            RecentMemory(
                                summary = summary,
                                hidePreviews = settings.hidePreviews,
                                onClick = { onOpenEntry(summary.journalDate) },
                            )
                        }
                    }
                }
            }

            if (state.totalEntries > 0) {
                item(key = "activity-header") { SectionHeader("Writing activity") }
                item(key = "activity") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LayoutMetrics.horizontalMargin),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        StatChip(
                            label = "entries",
                            value = state.totalEntries.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(
                            label = "day streak",
                            value = state.writingStreak.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(
                            label = "words today",
                            value = state.wordCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.recentEntries.isEmpty() && state.totalEntries == 0) {
                item(key = "empty") {
                    `as`.today.missyou.ui.components.EmptyState(
                        title = "Nothing written yet",
                        body = "Today is still waiting for your story.",
                        icon = Icons.Filled.AutoAwesome,
                    )
                }
            }
        }

        FloatingPillNavigation(
            current = Destination.HOME,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHeader(state: HomeUiState) {
    Column(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
        Text(
            text = "${state.greeting} \uD83D\uDC4B",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = state.weekday,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayCardSection(
    state: HomeUiState,
    hidePreviews: Boolean,
    onPrimary: () -> Unit,
    onOpenEntry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary

    `as`.today.missyou.ui.components.TodayCard(
        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        containerColor = scheme.primaryContainer,
        onClick = if (state.entry != null) onOpenEntry else null,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's journal",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
                if (state.entry != null) {
                    LockBadge(locked = state.entry?.isLocked == true)
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            if (state.entry == null) {
                Text(
                    text = state.prompt ?: "Write about your day",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimaryContainer,
                )
            } else {
                if (hidePreviews) {
                    Text(
                        text = "${state.wordCount} words written",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        text = state.preview.ifBlank { "Written and waiting." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onPrimaryContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.lockCountdown?.let { countdown ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = when (state.primaryAction) {
                        PrimaryAction.WRITE -> "Write today's entry"
                        PrimaryAction.CONTINUE -> "Continue today's entry"
                        PrimaryAction.READ_LOCKED -> "Yesterday's entry locked"
                    },
                    onClick = onPrimary,
                    icon = if (state.primaryAction == PrimaryAction.READ_LOCKED) {
                        Icons.Filled.Lock
                    } else {
                        Icons.Filled.Today
                    },
                    containerColor = if (state.primaryAction == PrimaryAction.READ_LOCKED) {
                        scheme.tertiary
                    } else {
                        accent
                    },
                    contentColor = if (state.primaryAction == PrimaryAction.READ_LOCKED) {
                        scheme.onTertiary
                    } else {
                        scheme.onPrimary
                    },
                )
            }
        }
    }
}

@Composable
private fun RecentMemory(
    summary: JournalEntrySummary,
    hidePreviews: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (summary.isLocked) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    MemoryCard(
        modifier = Modifier.width(190.dp),
        accent = accent,
        title = summary.journalDate.format(SHORT_DATE),
        preview = if (hidePreviews) {
            if (summary.isLocked) "Locked" else "Unread preview"
        } else {
            summary.title.ifBlank { summary.preview.ifBlank { "Written" } }
        },
        locked = summary.isLocked,
        onClick = onClick,
    )
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
