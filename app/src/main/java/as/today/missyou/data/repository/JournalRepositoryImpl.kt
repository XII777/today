package `as`.today.missyou.data.repository

import `as`.today.missyou.core.AppDispatchers
import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.core.time.ClockSnapshot
import `as`.today.missyou.core.time.DayRollover
import `as`.today.missyou.core.time.JournalClock
import `as`.today.missyou.core.time.JournalDates
import `as`.today.missyou.data.IntegrityHasher
import `as`.today.missyou.data.RichDocumentCodec
import `as`.today.missyou.data.database.JournalDao
import `as`.today.missyou.data.database.JournalEntryEntity
import `as`.today.missyou.data.database.JournalStateEntity
import `as`.today.missyou.data.import.JournalImporter
import `as`.today.missyou.data.search.SearchIndex
import `as`.today.missyou.data.search.SnippetBuilder
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ExportFormat
import `as`.today.missyou.domain.model.ExportScope
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.ImportResult
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.domain.model.JournalInsights
import `as`.today.missyou.domain.model.MonthActivity
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.SearchResult
import `as`.today.missyou.domain.model.TagCount
import `as`.today.missyou.domain.model.TodayOverview
import `as`.today.missyou.domain.model.YearActivity
import `as`.today.missyou.domain.repository.AttachmentStore
import `as`.today.missyou.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * The journal's data layer.
 *
 * ## How immutability is enforced here
 *
 * Four independent layers, deliberately redundant:
 *
 * 1. **The repository refuses.** Every mutating path re-checks the effective journal
 *    day against the entry's `journalDate` and returns `EntryLocked`.
 * 2. **The database refuses.** `Triggers.STATEMENTS` abort any `UPDATE`/`DELETE`
 *    on an expired row, so a missed check in Kotlin cannot corrupt the guarantee.
 * 3. **The lock is permanent.** A `locked_at` value can never be cleared, and
 *    `journal_date` can never be changed, by any writer.
 * 4. **The clock cannot rewind.** The effective date is monotonic, and a trigger
 *    rejects any attempt to move `journal_state.current_date` backwards, so the
 *    attacker cannot unlock anything by setting the device clock back.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class JournalRepositoryImpl(
    private val dao: JournalDao,
    private val clock: JournalClock,
    private val dispatchers: AppDispatchers,
    private val mapper: JournalMapper,
    private val importer: JournalImporter,
    private val attachmentStore: AttachmentStore? = null,
    private val appVersion: String = "dev",
    private val idGenerator: IdGenerator = IdGenerator.Random,
    private val rolloverProvider: () -> DayRollover = { DayRollover.Midnight },
) : JournalRepository {

    private val writeLock = Mutex()
    private val cachedSnapshot = MutableStateFlow(clock.tick(rolloverProvider()))
    private val clockWarning = MutableStateFlow(false)
    private val searchIndex = MutableStateFlow(SearchIndex.Empty)

    // ------------------------------------------------------------------ clock

    override fun currentJournalDate(): LocalDate = snapshot().journalDate

    override fun clockSnapshot(): ClockSnapshot = snapshot()

    override fun clockWarning(): Boolean = clockWarning.value

    override fun acknowledgeClockWarning() {
        clock.acknowledgeClockWarning()
        clockWarning.value = false
    }

    private fun snapshot(): ClockSnapshot {
        val rolled = clock.tick(rolloverProvider())
        cachedSnapshot.value = rolled
        if (rolled.rollbackDetected) clockWarning.value = true
        return rolled
    }

    /**
     * Advances the stored effective date, then seals everything that has expired.
     *
     * Order matters: the date is written first so that the triggers start refusing
     * writes, and sealing happens second inside the same suspension.
     */
    override suspend fun synchroniseClock() = withContext(dispatchers.io) {
        val rolled = clock.tick(rolloverProvider())
        cachedSnapshot.value = rolled
        if (rolled.rollbackDetected) clockWarning.value = true

        writeLock.withLock {
            val stored = dao.loadState()
            if (stored == null) {
                dao.insertState(
                    JournalStateEntity(
                        currentDate = rolled.journalDate.toString(),
                        highestSeenWallMillis = rolled.wallClockMillis,
                        updatedAt = rolled.wallClockMillis,
                    ),
                )
            } else {
                clock.restore(LocalDate.parse(stored.currentDate), stored.highestSeenWallMillis)
                val afterRestore = clock.tick(rolloverProvider())
                dao.advanceCurrentDate(
                    currentDate = afterRestore.journalDate.toString(),
                    wallMillis = afterRestore.wallClockMillis,
                    updatedAt = afterRestore.wallClockMillis,
                )
                sealExpiredDays(afterRestore.journalDate)
            }
        }
    }

    /**
     * Stamps `locked_at` onto every entry whose day has rolled over.
     *
     * The seal window is opened and closed inside this function, so the only
     * statements that can run while past-dated rows are still mutable are the ones
     * issued here.
     */
    private suspend fun sealExpiredDays(current: LocalDate) {
        val expired = dao.findUnsealedDays(current.toString())
        if (expired.isEmpty()) return
        val rollover = rolloverProvider()
        val zone = java.time.ZoneId.systemDefault()
        dao.openSealWindow()
        try {
            for (day in expired) {
                val journalDate = LocalDate.parse(day.journalDate)
                val lockInstant = JournalDates.lockInstantMillis(journalDate, zone, rollover)
                dao.stampLockedAt(day.id, lockInstant)
            }
        } finally {
            dao.closeSealWindow()
        }
    }

    // ------------------------------------------------------------------ observe

    override fun observeToday(): Flow<JournalEntry?> =
        cachedSnapshot
            .map { it.journalDate.toString() }
            .distinctUntilChanged()
            .flatMapLatest { date -> dao.observeByDate(date).map { entity -> entity?.let(mapper::toDomain) } }

    override fun observeOverview(): Flow<TodayOverview> =
        combine(
            cachedSnapshot.map { it.journalDate }.distinctUntilChanged(),
            observeToday(),
        ) { date, entry ->
            TodayOverview(
                journalDate = date,
                entry = entry,
                isToday = date == clock.highestObservedJournalDate(),
            )
        }

    override fun observeEntry(id: String): Flow<JournalEntry?> =
        dao.observeById(id).map { entity -> entity?.let { mapper.toDomain(it) } }

    override fun observeDay(date: LocalDate): Flow<JournalEntry?> =
        dao.observeByDate(date.toString()).map { entity -> entity?.let { mapper.toDomain(it) } }

    override fun observeArchive(limit: Int, offset: Int): Flow<List<JournalEntrySummary>> =
        dao.observePage(limit, offset).map { rows -> rows.map(mapper::toSummary) }

    override fun observeWindow(from: LocalDate, to: LocalDate): Flow<List<JournalEntrySummary>> =
        dao.observeDayWindow(from.toString(), to.toString()).map { rows -> rows.map(mapper::toSummary) }

    override fun observeEntryCount(): Flow<Int> = dao.observeCount()

    // ------------------------------------------------------------------ read

    override suspend fun entryForDate(date: LocalDate): JournalEntry? = withContext(dispatchers.io) {
        dao.findByDate(date.toString())?.let { entity ->
            mapper.toDomain(entity, mapperAttachments(entity.id))
        }
    }

    override suspend fun entryById(id: String): JournalEntry? = withContext(dispatchers.io) {
        dao.findById(id)?.let { entity -> mapper.toDomain(entity, mapperAttachments(entity.id)) }
    }

    private suspend fun mapperAttachments(entryId: String) =
        dao.attachmentsFor(entryId).map(mapper::attachmentOf)

    override suspend fun allEntries(): List<JournalEntry> = withContext(dispatchers.io) {
        val entities = dao.allDates().mapNotNull { dao.findByDate(it) }
        val attachmentsByEntry = entities
            .flatMap { dao.attachmentsFor(it.id) }
            .groupBy { it.entryId }
        entities.map { entity ->
            mapper.toDomain(entity, attachmentsByEntry[entity.id].orEmpty().map(mapper::attachmentOf))
        }
    }

    // ------------------------------------------------------------------ write

    override suspend fun createTodaysEntry(title: String?, prompt: String?): Result<JournalEntry> =
        createEntry(currentJournalDate(), title, prompt)

    override suspend fun backfillEntry(date: LocalDate, title: String?, prompt: String?): Result<JournalEntry> =
        createEntry(date, title, prompt)

    private suspend fun createEntry(
        date: LocalDate,
        title: String?,
        prompt: String?,
    ): Result<JournalEntry> = withContext(dispatchers.io) {
        runCatching {
            val now = clock.tick(rolloverProvider())
            cachedSnapshot.value = now
            val today = now.journalDate
            if (date.isAfter(today)) {
                throw JournalException(JournalErrorReason.FutureDate, journalDate = date)
            }
            // A day that has already rolled over is created already sealed, which is
            // also what the `past_insert_must_be_locked` trigger requires.
            val lockedAt = if (date.isBefore(today)) {
                JournalDates.lockInstantMillis(date, java.time.ZoneId.systemDefault(), rolloverProvider())
            } else {
                null
            }

            val id = idGenerator.newId()
            val document = RichDocument.empty(idGenerator.newId())
            val tokens = mapper.tokensFor(title.orEmpty(), document, emptyList(), prompt)
            val contentJson = RichDocumentCodec.encode(document)
            val sealed = JournalEntryEntity(
                id = id,
                journalDate = date.toString(),
                createdAt = now.wallClockMillis,
                updatedAt = now.wallClockMillis,
                lockedAt = lockedAt,
                titleSealed = mapper.sealTitle(title.orEmpty(), id),
                contentSealed = mapper.sealContent(document, id).second,
                contentFormat = JournalMapper.DEFAULT_CONTENT_FORMAT,
                tagsSealed = null,
                promptSealed = mapper.sealPrompt(prompt, id),
                writingDurationMillis = 0,
                wordCount = 0,
                attachmentCount = 0,
                tokenIndexSealed = mapper.sealTokenIndex(tokens, id),
                integrityHash = IntegrityHasher.compute(
                    id = id,
                    journalDate = date.toString(),
                    createdAt = now.wallClockMillis,
                    lockedAt = lockedAt,
                    title = title.orEmpty(),
                    contentJson = contentJson,
                    tags = emptyList(),
                    prompt = prompt,
                ),
            )
            try {
                dao.insertEntry(sealed)
                mapper.toDomain(sealed)
            } catch (conflict: Exception) {
                // The unique index on journal_date fired. Return the existing entry
                // rather than replacing it: one day can only ever have one entry.
                dao.findByDate(date.toString())?.let { existing -> return@runCatching mapper.toDomain(existing) }
                throw JournalException(
                    JournalErrorReason.Unknown,
                    journalDate = date,
                    detail = "Could not create entry: ${conflict.message}",
                    cause = conflict,
                )
            }
        }
    }

    override suspend fun saveEntry(
        id: String,
        title: String,
        document: RichDocument,
        tags: List<String>,
        prompt: String?,
        writingDurationMillis: Long,
    ): Result<JournalEntry> = withContext(dispatchers.io) {
        runCatching {
            val entity = dao.findById(id) ?: throw JournalException(JournalErrorReason.EntryNotFound)
            val journalDate = LocalDate.parse(entity.journalDate)
            val today = currentJournalDate()

            // Layer 1: the repository refuses before touching the database.
            if (entity.lockedAt != null || journalDate.isBefore(today)) {
                throw JournalException(JournalErrorReason.EntryLocked, journalDate = journalDate)
            }

            val normalisedTags = tags.map { JournalMapper.normaliseTag(it) }.filter { it.isNotEmpty() }.distinct()
            val tokens = mapper.tokensFor(title, document, normalisedTags, prompt)
            val contentJson = RichDocumentCodec.encode(document)
            val wordCount = document.wordCount
            val now = clock.tick(rolloverProvider()).wallClockMillis

            val hash = IntegrityHasher.compute(
                id = id,
                journalDate = journalDate.toString(),
                createdAt = entity.createdAt,
                lockedAt = entity.lockedAt,
                title = title,
                contentJson = contentJson,
                tags = normalisedTags,
                prompt = prompt,
            )

            val updated = dao.updateContent(
                id = id,
                titleSealed = mapper.sealTitle(title, id),
                contentSealed = mapper.sealContent(document, id).second,
                contentFormat = JournalMapper.DEFAULT_CONTENT_FORMAT,
                tagsSealed = mapper.sealTags(normalisedTags, id),
                promptSealed = mapper.sealPrompt(prompt, id),
                writingDurationMillis = writingDurationMillis,
                wordCount = wordCount,
                attachmentCount = entity.attachmentCount,
                tokenIndexSealed = mapper.sealTokenIndex(tokens, id),
                integrityHash = hash,
                updatedAt = now,
            )
            if (updated == 0) {
                // The row was sealed between our check and this write.
                throw JournalException(JournalErrorReason.EntryLocked, journalDate = journalDate)
            }
            mapper.toDomain(dao.requireById(id), mapperAttachments(id))
        }
    }

    override suspend fun deleteOpenEntry(id: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val entity = dao.findById(id) ?: throw JournalException(JournalErrorReason.EntryNotFound)
            val journalDate = LocalDate.parse(entity.journalDate)
            if (entity.lockedAt != null || journalDate.isBefore(currentJournalDate())) {
                throw JournalException(JournalErrorReason.EntryLocked, journalDate = journalDate)
            }
            dao.deleteAttachmentRows(id)
            val removed = dao.deleteOpenEntry(id, currentJournalDate().toString())
            if (removed == 0) {
                throw JournalException(JournalErrorReason.EntryLocked, journalDate = journalDate)
            }
            Unit
        }
    }

    // ------------------------------------------------------------------ search

    override suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(dispatchers.default) {
        val tokens = TextMetrics.searchTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()

        val index = ensureSearchIndex()
        val candidateOrdinals = index.candidates(tokens)
        if (candidateOrdinals.isEmpty()) return@withContext emptyList()

        val ids = candidateOrdinals.mapNotNull(index::entryIdAt)
        val entities = dao.findByIds(ids)
        val byId = entities.associateBy { it.id }

        val results = mutableListOf<SearchResult>()
        for (ordinal in candidateOrdinals) {
            if (results.size >= limit) break
            val entryId = index.entryIdAt(ordinal) ?: continue
            val entity = byId[entryId] ?: continue
            val entry = mapper.toDomain(entity)
            val haystack = entry.searchableText
            val lowerHaystack = haystack.lowercase()
            // Verify against the real text: the index is fingerprinted, so a
            // 32-bit collision can produce a false candidate.
            if (tokens.none { lowerHaystack.contains(it) }) continue
            results += SearchResult(
                entryId = entry.id,
                journalDate = entry.journalDate,
                title = entry.title,
                snippet = SnippetBuilder.build(haystack, tokens),
                matchedTokenCount = tokens.count { lowerHaystack.contains(it) },
            )
        }
        results.sortedWith(compareByDescending<SearchResult> { it.matchedTokenCount }.thenByDescending { it.journalDate })
    }

    private suspend fun ensureSearchIndex(): SearchIndex {
        searchIndex.value.takeIf { !it.isEmpty }?.let { return it }
        val projections = dao.allTokenIndexProjections()
        val entryIds = projections.map { it.id }
        val tokenLists = projections.map { mapper.openTokenIndex(it.tokenIndexSealed, it.id) }
        val built = SearchIndex.build(entryIds, tokenLists)
        searchIndex.value = built
        return built
    }

    /** Drops the cached search index; called after any content change. */
    suspend fun invalidateSearchIndex() {
        searchIndex.value = SearchIndex.Empty
    }

    // ------------------------------------------------------------------ insights

    override suspend fun insights(): JournalInsights = withContext(dispatchers.default) {
        val aggregate = dao.insightsAggregate()
        val wordCounts = dao.allWordCounts()
        val tagCounts = buildTagCounts()

        val monthly = wordCounts
            .groupBy { YearMonth.from(LocalDate.parse(it.journalDate)) }
            .toSortedMap()
            .map { (month, rows) ->
                MonthActivity(month, rows.size, rows.sumOf { it.wordCount })
            }

        val yearly = wordCounts
            .groupBy { LocalDate.parse(it.journalDate).year }
            .toSortedMap()
            .map { (year, rows) -> YearActivity(year, rows.size, rows.sumOf { it.wordCount }) }

        val dates = wordCounts.map { LocalDate.parse(it.journalDate) }
        val streaks = computeStreaks(dates, currentJournalDate())
        val longestDate = aggregate.longestDate?.let(LocalDate::parse)

        JournalInsights(
            totalEntries = aggregate.entryCount,
            totalWords = aggregate.totalWords.toInt(),
            writingDays = dates.size,
            longestEntryWords = aggregate.maxWords,
            longestEntryDate = longestDate,
            averageWordsPerEntry = if (aggregate.entryCount == 0) 0 else
                (aggregate.totalWords / aggregate.entryCount).toInt(),
            totalWritingMillis = aggregate.totalWritingMillis,
            currentStreakDays = streaks.first,
            longestStreakDays = streaks.second,
            monthlyActivity = monthly,
            yearlyActivity = yearly,
            mostUsedTags = tagCounts,
            dailyPromptCount = 0,
        )
    }

    private suspend fun buildTagCounts(): List<TagCount> =
        dao.allTagProjections()
            .flatMap { mapper.decodeTags(it.tagsSealed, it.id) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_INSIGHT_TAGS)
            .map { TagCount(it.key, it.value) }

    /** Returns current streak and longest streak in days. */
    private fun computeStreaks(dates: List<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (dates.isEmpty()) return 0 to 0
        val unique = dates.toSortedSet()
        var longest = 1
        var run = 1
        var previous: LocalDate? = null
        for (date in unique) {
            val last = previous
            run = if (last != null && date == last.plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
            previous = date
        }
        val todayEntry = unique.contains(today)
        val yesterdayEntry = unique.contains(today.minusDays(1))
        val current = when {
            todayEntry -> countBackwards(unique, today)
            yesterdayEntry -> countBackwards(unique, today.minusDays(1))
            else -> 0
        }
        return current to longest
    }

    private fun countBackwards(dates: Set<LocalDate>, from: LocalDate): Int {
        var count = 0
        var cursor = from
        while (dates.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    // ------------------------------------------------------------------ data

    override suspend fun planImport(bytes: ByteArray, passphrase: CharArray?): Result<ImportPlan> =
        transfer.plan(bytes, passphrase, currentJournalDate())

    override suspend fun executeImport(
        plan: ImportPlan,
        resolution: ConflictResolution,
    ): Result<ImportResult> = transfer.execute(plan, resolution, currentJournalDate())

    override suspend fun buildExport(
        scope: ExportScope,
        dates: Set<LocalDate>,
        entryId: String?,
        includeAttachments: Boolean,
    ): Result<ByteArray> = transfer.buildBackup(scope, dates, entryId, includeAttachments)

    /** Renders a human-facing document (Markdown, plain text, HTML) for the share sheet. */
    suspend fun renderExport(
        format: ExportFormat,
        scope: ExportScope,
        dates: Set<LocalDate>,
        entryId: String?,
    ): Result<String> = transfer.render(format, scope, dates, entryId)

    private val transfer: JournalDataTransfer by lazy {
        JournalDataTransfer(
            importer = importer,
            attachmentStore = attachmentStore,
            dispatchers = dispatchers,
            appVersionProvider = { appVersion },
            clockSnapshotProvider = { clockSnapshot() },
            allEntriesProvider = { allEntries() },
        )
    }

    companion object {
        const val MAX_INSIGHT_TAGS = 12
    }
}
