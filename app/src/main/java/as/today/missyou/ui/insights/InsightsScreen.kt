package `as`.today.missyou.ui.insights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.JournalInsights
import `as`.today.missyou.domain.model.MonthActivity
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.EmptyState
import `as`.today.missyou.ui.components.SectionHeader
import `as`.today.missyou.ui.components.StatChip
import `as`.today.missyou.ui.components.TagPill
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.navigation.Destination
import `as`.today.missyou.ui.navigation.FloatingPillNavigation
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.format.DateTimeFormatter

data class InsightsUiState(
    val loading: Boolean = true,
    val insights: JournalInsights = JournalInsights.Empty,
    val writingTime: String = "",
)

/**
 * Insights (§7).
 *
 * Recomputed from the decrypted entries on the calling coroutine rather than held in
 * a cache, so the numbers can never drift from what the user actually wrote. It is a
 * one-shot computation per visit rather than a continuous observation, because
 * nothing here needs to update while the screen is merely being looked at.
 */
class InsightsViewModel(container: AppContainer) : AppViewModel(container) {

    private val insights = MutableStateFlow(JournalInsights.Empty)
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<InsightsUiState> = combine(
        insights,
        loading,
        container.settings.settings,
    ) { data, isLoading, settings ->
        InsightsUiState(
            loading = isLoading,
            insights = data,
            writingTime = formatWritingTime(data.totalWritingMillis, settings.showWritingDuration),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InsightsUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            insights.value = container.journal.insights()
            loading.value = false
        }
    }

    companion object {
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

        fun formatWritingTime(millis: Long, show: Boolean): String {
            if (!show || millis <= 0) return ""
            val duration = Duration.ofMillis(millis)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "under a minute"
            }
        }
    }
}

@Composable
fun InsightsScreen(
    container: AppContainer,
    onNavigate: (Destination) -> Unit,
) {
    val viewModel = containerViewModel { InsightsViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.insights

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = Spacing.lg,
                bottom = LayoutMetrics.contentBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "title") {
                Text(
                    text = "Insights",
                    modifier = Modifier
                        .padding(horizontal = LayoutMetrics.horizontalMargin)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (data.totalEntries == 0 && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing to reflect on yet",
                        body = "Statistics appear once you have written a few days.",
                        icon = Icons.Filled.Insights,
                    )
                }
            } else {
                item(key = "totals") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        StatChip(
                            value = data.totalEntries.toString(),
                            label = if (data.totalEntries == 1) "entry" else "entries",
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(
                            value = data.totalWords.toString(),
                            label = "words",
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(
                            value = data.writingDays.toString(),
                            label = "writing days",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (data.currentStreakDays > 0 || data.longestStreakDays > 0) {
                    item(key = "streaks") {
                        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text(
                                    text = "Streaks",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = streakSentence(data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (data.longestEntryDate != null && data.longestEntryWords > 0) {
                    item(key = "longest") {
                        TodayCard(
                            Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                        ) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text(
                                    text = "Longest entry",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = "${data.longestEntryWords} words on " +
                                        data.longestEntryDate.format(InsightsViewModel.LONG_DATE),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(
                                    text = "About ${TextMetrics.readingTimeLabel(data.longestEntryWords)} to read.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (state.writingTime.isNotEmpty()) {
                    item(key = "writing-time") {
                        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text(
                                    text = "Time spent writing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = state.writingTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (data.monthlyActivity.isNotEmpty()) {
                    item(key = "activity-header") {
                        SectionHeader(
                            title = "Activity",
                            modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                        )
                    }
                    item(key = "activity") {
                        ActivityBars(
                            entries = data.monthlyActivity,
                            modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                        )
                    }
                }

                if (data.mostUsedTags.isNotEmpty()) {
                    item(key = "tags-header") {
                        SectionHeader(
                            title = "Most used tags",
                            modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                        )
                    }
                    item(key = "tags") {
                        Column(
                            Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            data.mostUsedTags.take(12).chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                    row.forEach { tag ->
                                        TagPill("${tag.tag} · ${tag.count}")
                                    }
                                }
                            }
                        }
                    }
                }

                if (data.dailyPromptCount > 0) {
                    item(key = "prompts") {
                        Text(
                            text = "You wrote with a daily prompt on ${data.dailyPromptCount} " +
                                (if (data.dailyPromptCount == 1) "day." else "days."),
                            modifier = Modifier.padding(
                                horizontal = LayoutMetrics.horizontalMargin,
                                vertical = Spacing.sm,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        FloatingPillNavigation(
            current = Destination.INSIGHTS,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun streakSentence(data: JournalInsights): String = buildString {
    if (data.currentStreakDays > 0) {
        append("You are on a ${data.currentStreakDays}-day streak")
        if (data.longestStreakDays > data.currentStreakDays) {
            append(", short of your record of ${data.longestStreakDays}")
        }
    } else {
        append("No streak running. Your longest was ${data.longestStreakDays} ")
        append(if (data.longestStreakDays == 1) "day." else "days.")
    }
}

/**
 * Monthly activity as a simple bar row.
 *
 * Bars are scaled to the busiest month and each carries a textual value, so the
 * chart is decoration on top of information rather than the only way to read it.
 */
/** Fixed bar-area geometry, so the tallest month always exactly fills the row. */
private const val BAR_HEIGHT_PX = 96f
private const val MIN_BAR_PX = 4f

@Composable
private fun ActivityBars(entries: List<MonthActivity>, modifier: Modifier = Modifier) {
    val peak = entries.maxOfOrNull { it.words }?.coerceAtLeast(1) ?: 1
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    TodayCard(modifier) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT_PX.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                entries.takeLast(12).forEach { month ->
                    val fraction = month.words.toFloat() / peak.toFloat()
                    Box(
                        Modifier
                            .weight(1f)
                            .height(BAR_HEIGHT_PX.dp)
                            .clip(RoundedCornerShape(Radii.small))
                            .background(trackColor)
                            .semantics {
                                contentDescription =
                                    "${month.month}: ${month.words} words across ${month.entries} entries"
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((BAR_HEIGHT_PX * fraction).coerceAtLeast(MIN_BAR_PX).dp)
                                .clip(RoundedCornerShape(Radii.small))
                                .background(barColor),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entries.firstOrNull()?.month?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entries.lastOrNull()?.month?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
