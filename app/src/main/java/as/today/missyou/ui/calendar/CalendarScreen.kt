/**
 * `flatMapLatest` is still marked experimental in coroutines 1.9. Only the two view
 * models that switch an observed window need it, so they opt in at file scope
 * rather than the whole module.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package `as`.today.missyou.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.FirstDayOfWeek
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.IconPillButton
import `as`.today.missyou.ui.components.rememberHaptics
import `as`.today.missyou.ui.components.LockBadge
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.navigation.Destination
import `as`.today.missyou.ui.navigation.FloatingPillNavigation
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.TodayTheme
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** What a single day cell shows (§6). */
enum class DayState { EMPTY, WRITTEN, TODAY_WRITABLE, TODAY_EMPTY, LOCKED, FUTURE }

data class CalendarDay(
    val date: LocalDate,
    val state: DayState,
    val wordCount: Int,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDay?> = emptyList(),
    val weekDayLabels: List<String> = emptyList(),
    val selected: LocalDate? = null,
    val selectedEntry: JournalEntrySummary? = null,
    val today: LocalDate = LocalDate.now(),
)

class CalendarViewModel(container: AppContainer) : AppViewModel(container) {

    private val month = MutableStateFlow(YearMonth.from(container.journal.currentJournalDate()))
    private val selected = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<CalendarUiState> = combine(
        month.flatMapLatest { target ->
            val first = target.atDay(1)
            val last = target.atEndOfMonth()
            container.journal.observeWindow(first, last)
        },
        month,
        selected,
        container.settings.settings,
    ) { entries, target, chosen, settings ->
        val today = container.journal.currentJournalDate()
        buildState(target, entries, chosen, today, settings.firstDayOfWeek)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    private fun buildState(
        target: YearMonth,
        entries: List<JournalEntrySummary>,
        chosen: LocalDate?,
        today: LocalDate,
        firstDay: FirstDayOfWeek,
    ): CalendarUiState {
        val byDate = entries.associateBy { it.journalDate }
        val firstColumn = if (firstDay == FirstDayOfWeek.MONDAY) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        val leadingBlanks = Math.floorMod(
            target.atDay(1).dayOfWeek.value - firstColumn.value,
            7,
        )
        val cells = buildList {
            repeat(leadingBlanks) { add(null) }
            for (day in 1..target.lengthOfMonth()) {
                val date = target.atDay(day)
                val entry = byDate[date]
                add(
                    CalendarDay(
                        date = date,
                        state = when {
                            date == today && entry == null -> DayState.TODAY_EMPTY
                            date == today -> DayState.TODAY_WRITABLE
                            entry?.isLocked == true -> DayState.LOCKED
                            entry != null -> DayState.WRITTEN
                            date.isAfter(today) -> DayState.FUTURE
                            else -> DayState.EMPTY
                        },
                        wordCount = entry?.wordCount ?: 0,
                    ),
                )
            }
        }
        return CalendarUiState(
            month = target,
            days = cells,
            weekDayLabels = weekLabels(firstDay),
            selected = chosen,
            selectedEntry = chosen?.let(byDate::get),
            today = today,
        )
    }

    fun select(date: LocalDate) {
        selected.value = if (selected.value == date) null else date
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
        selected.value = null
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        selected.value = null
    }

    fun goToToday() {
        month.value = YearMonth.from(container.journal.currentJournalDate())
        selected.value = container.journal.currentJournalDate()
    }

    companion object {
        val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val DAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

        fun weekLabels(firstDay: FirstDayOfWeek): List<String> {
            val first = if (firstDay == FirstDayOfWeek.MONDAY) 1 else 0
            return (0 until 7).map { DAY_INITIALS[(first + it) % 7] }
        }
    }
}

/**
 * Calendar view (§6).
 *
 * Deliberately restrained: one accent colour, weight and a small dot carry all the
 * state, and every day also states its meaning in words for screen readers, so
 * nothing depends on colour alone.
 */
@Composable
fun CalendarScreen(
    container: AppContainer,
    onOpenEntry: (LocalDate) -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val viewModel = containerViewModel { CalendarViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = LayoutMetrics.horizontalMargin),
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.md))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPillButton(
                    icon = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous month",
                    onClick = viewModel::previousMonth,
                )
                Text(
                    text = state.month.format(CalendarViewModel.MONTH_LABEL),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconPillButton(
                    icon = Icons.Filled.ChevronRight,
                    contentDescription = "Next month",
                    onClick = viewModel::nextMonth,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            WeekHeader(state.weekDayLabels)

            Spacer(Modifier.height(Spacing.xs))
            MonthGrid(
                days = state.days,
                today = state.today,
                selected = state.selected,
                onSelect = { date ->
                    haptics.light()
                    viewModel.select(date)
                },
            )

            Spacer(Modifier.height(Spacing.md))
            SelectedDaySummary(
                state = state,
                onOpen = { date -> onOpenEntry(date) },
            )
        }

        FloatingPillNavigation(
            current = Destination.CALENDAR,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun WeekHeader(labels: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<CalendarDay?>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val reduceMotion = TodayTheme.settings.reduceMotion
    Column(Modifier.fillMaxWidth()) {
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f)) {
                        if (day == null) {
                            Spacer(Modifier.aspectRatio(1f))
                        } else {
                            DayCell(
                                day = day,
                                isToday = day.date == today,
                                isSelected = day.date == selected,
                                onClick = { onSelect(day.date) },
                                reduceMotion = reduceMotion,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    reduceMotion: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        isSelected -> scheme.primary
        isToday -> scheme.primaryContainer
        day.state == DayState.LOCKED -> scheme.tertiary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.onPrimaryContainer
        day.date.isAfter(day.date) -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    val dotColor = when {
        isSelected -> scheme.onPrimary
        day.state == DayState.LOCKED -> scheme.tertiary
        day.wordCount > 0 -> scheme.primary
        else -> Color.Transparent
    }

    val description = buildString {
        append(day.date.toString())
        append(
            when (day.state) {
                DayState.LOCKED -> ", locked entry"
                DayState.WRITTEN -> ", written, ${day.wordCount} words"
                DayState.TODAY_WRITABLE -> ", today, still editable"
                DayState.TODAY_EMPTY -> ", today, not written yet"
                DayState.EMPTY -> ", nothing written"
                DayState.FUTURE -> ", upcoming"
            },
        )
        if (isSelected) append(", selected")
    }

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radii.small))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

@Composable
private fun SelectedDaySummary(state: CalendarUiState, onOpen: (LocalDate) -> Unit) {
    val date = state.selected
    if (date == null) {
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Tap a day to see what you wrote.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val entry = state.selectedEntry
    TodayCard(onClick = { if (entry != null) onOpen(date) }) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry != null) LockBadge(locked = entry.isLocked, compact = true)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = when {
                    entry == null && date.isAfter(state.today) -> "This day has not happened yet."
                    entry == null -> "Nothing written on this day."
                    else -> "${entry.wordCount} words" + (if (entry.title.isNotBlank()) " · ${entry.title}" else "")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
