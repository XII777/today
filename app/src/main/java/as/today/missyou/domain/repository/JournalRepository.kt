package `as`.today.missyou.domain.repository

import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.domain.model.JournalInsights
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ExportScope
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.ImportResult
import `as`.today.missyou.domain.model.SearchResult
import `as`.today.missyou.domain.model.TodayOverview
import `as`.today.missyou.core.time.ClockSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The journal's public contract.
 *
 * Every mutating operation returns a [Result]. A failure carries a
 * `JournalErrorReason`, which the UI turns into user-facing copy. Critically,
 * mutation methods are the *only* way to change an entry: there is no
 * `updateEntry(JournalEntry)` escape hatch, and no delete that ignores the lock
 * state.
 */
interface JournalRepository {

    // ------------------------------------------------------------------ clock

    /** The effective journal day. Monotonic: never moves backwards. */
    fun currentJournalDate(): LocalDate

    fun clockSnapshot(): ClockSnapshot

    /** True while the app should be warning the user that the clock moved. */
    fun clockWarning(): Boolean

    fun acknowledgeClockWarning()

    // ------------------------------------------------------------------ observe

    fun observeToday(): Flow<JournalEntry?>

    fun observeOverview(): Flow<TodayOverview>

    fun observeEntry(id: String): Flow<JournalEntry?>

    fun observeDay(date: LocalDate): Flow<JournalEntry?>

    fun observeArchive(limit: Int, offset: Int): Flow<List<JournalEntrySummary>>

    fun observeWindow(from: LocalDate, to: LocalDate): Flow<List<JournalEntrySummary>>

    fun observeEntryCount(): Flow<Int>

    // ------------------------------------------------------------------ read

    suspend fun entryForDate(date: LocalDate): JournalEntry?

    suspend fun entryById(id: String): JournalEntry?

    suspend fun allEntries(): List<JournalEntry>

    // ------------------------------------------------------------------ write

    /**
     * Creates (or returns) the entry for the current journal day.
     *
     * Safe to call repeatedly: an existing entry for today is returned untouched
     * rather than replaced.
     */
    suspend fun createTodaysEntry(title: String? = null, prompt: String? = null): Result<JournalEntry>

    /**
     * Creates a locked entry for a day that has already rolled over.
     *
     * The row is born sealed, which the database enforces independently, so this is
     * the supported way to backfill or restore a past day.
     */
    suspend fun backfillEntry(date: LocalDate, title: String? = null, prompt: String? = null): Result<JournalEntry>

    /**
     * Persists an edit.
     *
     * Rejected with `EntryLocked` once the day has rolled over. The check happens
     * here *and* in the database, on purpose.
     */
    suspend fun saveEntry(
        id: String,
        title: String,
        document: RichDocument,
        tags: List<String>,
        prompt: String?,
        writingDurationMillis: Long,
    ): Result<JournalEntry>

    /**
     * Deletes an entry that is still today's open day.
     *
     * Rejected with `EntryLocked` for anything already sealed.
     */
    suspend fun deleteOpenEntry(id: String): Result<Unit>

    // ------------------------------------------------------------------ derived

    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<SearchResult>

    suspend fun insights(): JournalInsights

    /**
     * Advances the stored effective date and seals every expired entry.
     *
     * Called on every app start and whenever the day changes while the app is open.
     */
    suspend fun synchroniseClock()

    // ------------------------------------------------------------------ data

    /** Reads a backup and reports what would happen, without writing anything. */
    suspend fun planImport(bytes: ByteArray, passphrase: CharArray?): Result<ImportPlan>

    /**
     * Applies a plan.
     *
     * Only ever inserts. A date that already holds a sealed entry is never
     * replaced; [ConflictResolution] decides whether to skip, archive or cancel.
     */
    suspend fun executeImport(
        plan: ImportPlan,
        resolution: ConflictResolution,
    ): Result<ImportResult>

    /** Serialises the requested entries into a backup payload. */
    suspend fun buildExport(
        scope: ExportScope,
        dates: Set<java.time.LocalDate>,
        entryId: String?,
        includeAttachments: Boolean,
    ): Result<ByteArray>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 100
    }
}
