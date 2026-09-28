package `as`.today.missyou.ui.home

import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.core.time.DayRollover
import `as`.today.missyou.core.time.JournalDates
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.domain.model.DateFormat
import `as`.today.missyou.domain.model.DailyPrompts
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.domain.model.JournalInsights
import `as`.today.missyou.domain.model.TodayOverview
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.readableMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the home screen renders. */
data class HomeUiState(
    val loading: Boolean = true,
    val journalDate: LocalDate = LocalDate.now(),
    val greeting: String = "",
    val weekday: String = "",
    val dateLabel: String = "",
    val prompt: String? = null,
    val entry: JournalEntry? = null,
    val isToday: Boolean = true,
    val preview: String = "",
    val wordCount: Int = 0,
    val writingStreak: Int = 0,
    val totalEntries: Int = 0,
    val recentEntries: List<JournalEntrySummary> = emptyList(),
    val lockCountdown: String? = null,
    val clockWarning: String? = null,
    val errorMessage: String? = null,
) {
    val primaryAction: PrimaryAction
        get() = when {
            entry != null && entry.isLocked -> PrimaryAction.READ_LOCKED
            entry != null -> PrimaryAction.CONTINUE
            else -> PrimaryAction.WRITE
        }
}

enum class PrimaryAction { WRITE, CONTINUE, READ_LOCKED }

/**
 * Home state.
 *
 * Every value is derived from repository flows; the view model keeps no copy of an
 * entry. Because `observeOverview` re-targets itself when the effective journal
 * day changes, the home screen rolls over on its own without any manual refresh.
 */
class HomeViewModel(container: AppContainer) : AppViewModel(container) {

    private val refreshTick = MutableStateFlow(0)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val insights = MutableStateFlow(JournalInsights.Empty)

    val uiState: StateFlow<HomeUiState> = combine(
        container.settings.settings,
        combine(
            container.journal.observeOverview(),
            container.journal.observeArchive(limit = RECENT_LIMIT, offset = 0),
            container.journal.observeEntryCount(),
        ) { overview, recent, total -> Triple(overview, recent, total) },
        combine(refreshTick, errorMessage.asStateFlow(), insights) { tick, error, stats ->
            Triple(tick, error, stats)
        },
    ) { settings, (overview, recent, total), (_, error, stats) ->
        buildState(settings, overview, recent, total, stats, error)
    }
        .catch { throwable -> emit(HomeUiState(loading = false, errorMessage = throwable.readableMessage())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    init {
        reloadInsights()
    }

    private fun buildState(
        settings: AppSettings,
        overview: TodayOverview,
        recent: List<JournalEntrySummary>,
        total: Int,
        stats: JournalInsights,
        error: String?,
    ): HomeUiState {
        val date = overview.journalDate
        val entry = overview.entry
        return HomeUiState(
            loading = false,
            journalDate = date,
            greeting = greetingFor(LocalTime.now()),
            weekday = date.format(DAY_FORMAT),
            dateLabel = date.format(labelFormatter(settings.dateFormat)),
            prompt = if (settings.dailyPromptEnabled) {
                entry?.prompt ?: DailyPrompts.forEpochDay(date.toEpochDay())
            } else {
                entry?.prompt
            },
            entry = entry,
            isToday = overview.isToday,
            preview = entry?.let { TextMetrics.preview(it.document.plainText) }.orEmpty(),
            wordCount = entry?.wordCount ?: 0,
            writingStreak = stats.currentStreakDays,
            totalEntries = total,
            recentEntries = recent.filter { it.id != entry?.id }.take(RECENT_LIMIT),
            lockCountdown = if (settings.lockCountdown && overview.isToday && entry != null) {
                lockCountdown(date, settings.rolloverMinutes, ZoneId.systemDefault())
            } else {
                null
            },
            clockWarning = if (container.journal.clockWarning()) CLOCK_WARNING else null,
            errorMessage = error,
        )
    }

    fun reloadInsights() {
        viewModelScope.launch {
            insights.value = container.journal.insights()
        }
    }

    fun refresh() {
        refreshTick.value++
        reloadInsights()
    }

    fun deleteToday() {
        val entry = uiState.value.entry ?: return
        viewModelScope.launch {
            container.journal.deleteOpenEntry(entry.id).onFailure { errorMessage.value = it.readableMessage() }
            refresh()
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun acknowledgeClockWarning() {
        container.journal.acknowledgeClockWarning()
        refresh()
    }

    companion object {
        const val RECENT_LIMIT = 12

        const val CLOCK_WARNING =
            "The device clock moved backwards. Locked entries stay locked."

        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")

        fun labelFormatter(format: DateFormat): DateTimeFormatter = when (format) {
            DateFormat.ISO -> DateTimeFormatter.ISO_LOCAL_DATE
            DateFormat.DAY_MONTH_YEAR -> DateTimeFormatter.ofPattern("d MMMM yyyy")
            DateFormat.MONTH_DAY -> DateTimeFormatter.ofPattern("MMMM d")
            DateFormat.MONTH_DAY_YEAR -> DateTimeFormatter.ofPattern("MMM d, yyyy")
            DateFormat.WEEKDAY_LONG -> DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
        }

        fun greetingFor(time: LocalTime): String = when (time.hour) {
            in 0..4 -> "Still awake"
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Winding down"
        }

        /** Human countdown until the current day seals, e.g. "Locks in 3h 12m". */
        fun lockCountdown(
            date: LocalDate,
            rolloverMinutes: Int,
            zone: ZoneId,
            now: Long = System.currentTimeMillis(),
        ): String {
            val lockAt = JournalDates.lockInstantMillis(date, zone, DayRollover(rolloverMinutes))
            val remaining = lockAt - now
            if (remaining <= 0) return "Locks at rollover"
            val hours = remaining / 3_600_000
            val minutes = (remaining % 3_600_000) / 60_000
            return when {
                hours > 0 -> "Locks in ${hours}h ${minutes}m"
                minutes > 0 -> "Locks in ${minutes}m"
                else -> "Locks in under a minute"
            }
        }
    }
}
