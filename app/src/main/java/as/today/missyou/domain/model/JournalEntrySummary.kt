package `as`.today.missyou.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/**
 * A lightweight row used by lists, the calendar and insights.
 *
 * The archive screen can show thousands of these, so it deliberately carries a
 * preview rather than the decrypted document.
 */
data class JournalEntrySummary(
    val id: String,
    val journalDate: LocalDate,
    val title: String,
    val preview: String,
    val tags: List<String>,
    val wordCount: Int,
    val lockedAt: Long?,
    val updatedAt: Long,
    val attachmentCount: Int,
) {
    val isLocked: Boolean get() = lockedAt != null
    val hasContent: Boolean get() = title.isNotBlank() || preview.isNotBlank()
}

/** An entry that has not been written yet. */
data class DayStatus(
    val date: LocalDate,
    val entry: JournalEntrySummary?,
) {
    val isWritten: Boolean get() = entry != null
    val isLocked: Boolean get() = entry?.isLocked ?: false
}

/** The editable state of "today" as presented on the home screen. */
data class TodayOverview(
    val journalDate: LocalDate,
    val entry: JournalEntry?,
    val isToday: Boolean,
) {
    val isWritten: Boolean get() = entry != null
    val primaryActionLabel: String
        get() = when {
            entry != null && entry.isLocked -> "Yesterday's entry locked"
            entry != null -> "Continue today's entry"
            else -> "Write today's entry"
        }
}

/** Aggregate statistics shown on the Insights screen. Non-gamified by design (§26). */
data class JournalInsights(
    val totalEntries: Int,
    val totalWords: Int,
    val writingDays: Int,
    val longestEntryWords: Int,
    val longestEntryDate: LocalDate?,
    val averageWordsPerEntry: Int,
    val totalWritingMillis: Long,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val monthlyActivity: List<MonthActivity>,
    val yearlyActivity: List<YearActivity>,
    val mostUsedTags: List<TagCount>,
    val dailyPromptCount: Int,
) {
    companion object {
        val Empty = JournalInsights(
            totalEntries = 0,
            totalWords = 0,
            writingDays = 0,
            longestEntryWords = 0,
            longestEntryDate = null,
            averageWordsPerEntry = 0,
            totalWritingMillis = 0,
            currentStreakDays = 0,
            longestStreakDays = 0,
            monthlyActivity = emptyList(),
            yearlyActivity = emptyList(),
            mostUsedTags = emptyList(),
            dailyPromptCount = 0,
        )
    }
}

data class MonthActivity(val month: YearMonth, val entries: Int, val words: Int)

data class YearActivity(val year: Int, val entries: Int, val words: Int)

data class TagCount(val tag: String, val count: Int)

/** A single search hit with a highlighted snippet. */
data class SearchResult(
    val entryId: String,
    val journalDate: LocalDate,
    val title: String,
    val snippet: Snippet,
    val matchedTokenCount: Int,
)

/**
 * A snippet of matched text.
 *
 * [matchRanges] are half-open offsets *into [plainText]*, which lets the UI render
 * highlights without re-running a regex and keeps the matching logic testable.
 */
data class Snippet(
    val plainText: String,
    val matchRanges: List<IntRange>,
) {
    val hasMatches: Boolean get() = matchRanges.isNotEmpty()
}
