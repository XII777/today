package `as`.today.missyou.ui.backup

import android.net.Uri
import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.time.JournalDates
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ExportFormat
import `as`.today.missyou.domain.model.ExportScope
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.ImportResult
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Which half of the backup screen is showing. */
enum class BackupTab { EXPORT, IMPORT }

/** The export options the user has chosen. */
data class ExportSelection(
    val format: ExportFormat = ExportFormat.MARKDOWN,
    val scope: ExportScope = ExportScope.ALL_ENTRIES,
    val dates: Set<LocalDate> = emptySet(),
    val entryId: String? = null,
    val includeAttachments: Boolean = true,
    val encrypt: Boolean = false,
) {
    val canExport: Boolean
        get() = when (scope) {
            ExportScope.ALL_ENTRIES -> true
            ExportScope.SELECTED_DATES -> dates.isNotEmpty()
            ExportScope.SINGLE_ENTRY -> entryId != null
        }
}

/** Everything the backup screen renders. */
data class BackupUiState(
    val tab: BackupTab = BackupTab.EXPORT,
    val busy: Boolean = false,
    val export: ExportSelection = ExportSelection(),
    val entriesByMonth: List<Pair<YearMonth, List<JournalEntrySummary>>> = emptyList(),
    val totalEntries: Int = 0,
    val importPlan: ImportPlan? = null,
    val importResult: ImportResult? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val isReviewingImport: Boolean get() = importPlan != null
    val passphraseRequired: Boolean get() = export.encrypt
}

/**
 * Import and export (§18).
 *
 * Import is deliberately two-phase. [planImport] reads and reports without writing
 * anything, so the user sees exactly which days would be added and which already
 * hold a sealed entry *before* anything is written. That matters because the app's
 * central promise is that a sealed entry is never replaced — a one-shot import
 * button would make that promise unverifiable from the user's side.
 */
class BackupViewModel(container: AppContainer) : AppViewModel(container) {

    private val tab = MutableStateFlow(BackupTab.EXPORT)
    private val selection = MutableStateFlow(ExportSelection())
    private val plan = MutableStateFlow<ImportPlan?>(null)
    private val result = MutableStateFlow<ImportResult?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val isError = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)

    /** Held between the plan and the confirm step; nothing is written until it is set. */
    private var pendingPayload: ByteArray? = null

    /**
     * Typing state, grouped so the whole thing can be assembled with typed
     * `combine` overloads instead of by unboxing `Array`s.
     */
    private data class BackupControls(
        val tab: BackupTab,
        val selection: ExportSelection,
        val busy: Boolean,
    )

    private data class BackupOutcome(
        val plan: ImportPlan?,
        val result: ImportResult?,
        val message: String?,
        val isError: Boolean,
    )

    /** Months present in the archive, newest first, for the date picker. */
    private data class Archive(
        val months: List<Pair<YearMonth, List<JournalEntrySummary>>>,
        val total: Int,
    )

    /**
     * The archive the date picker offers.
     *
     * Grouped here rather than in the composable so month boundaries and ordering
     * are decided in one place, and the screen only renders what it is given.
     */
    private val archiveFlow = container.journal.observeArchive(limit = MAX_ARCHIVE, offset = 0)
        .map { summaries ->
            Archive(
                months = summaries
                    .groupBy { YearMonth.from(it.journalDate) }
                    .toSortedMap(compareByDescending { it })
                    .map { (month, entries) -> month to entries.sortedByDescending { it.journalDate } },
                total = summaries.size,
            )
        }

    val uiState: StateFlow<BackupUiState> = combine(
        combine(tab, selection, busy) { t, s, b -> BackupControls(t, s, b) },
        combine(plan, result, message, isError) { p, r, m, e -> BackupOutcome(p, r, m, e) },
        archiveFlow,
    ) { controls, outcome, archiveView ->
        BackupUiState(
            tab = controls.tab,
            busy = controls.busy,
            export = controls.selection,
            entriesByMonth = archiveView.months,
            totalEntries = archiveView.total,
            importPlan = outcome.plan,
            importResult = outcome.result,
            message = outcome.message,
            isError = outcome.isError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState(),
    )

    fun setTab(value: BackupTab) {
        tab.value = value
        // Switching to import abandons any half-reviewed export state, and vice
        // versa, so a stale plan can never be confirmed from the wrong tab.
        if (value == BackupTab.EXPORT) {
            plan.value = null
            pendingPayload = null
        }
    }

    fun setFormat(format: ExportFormat) = updateSelection { it.copy(format = format) }

    fun setScope(scope: ExportScope) = updateSelection { it.copy(scope = scope) }

    fun setIncludeAttachments(include: Boolean) =
        updateSelection { it.copy(includeAttachments = include) }

    fun setEncrypt(encrypt: Boolean) = updateSelection { it.copy(encrypt = encrypt) }

    fun toggleDate(date: LocalDate) = updateSelection { current ->
        val dates = current.dates.toMutableSet()
        if (!dates.remove(date)) dates.add(date)
        current.copy(scope = ExportScope.SELECTED_DATES, dates = dates)
    }

    fun selectEntry(entryId: String, date: LocalDate) = updateSelection {
        it.copy(scope = ExportScope.SINGLE_ENTRY, entryId = entryId, dates = setOf(date))
    }

    private fun updateSelection(transform: (ExportSelection) -> ExportSelection) {
        selection.value = transform(selection.value)
    }

    /**
     * Builds the export payload.
     *
     * The bytes are produced but *not* written anywhere: handing them back to the
     * screen lets it ask for a save location through the system picker, which is
     * the only way to write to shared storage without asking for a broad permission.
     *
     * Encryption is applied by the screen before the payload is handed to the
     * picker, because the passphrase must not live in this class any longer than
     * the moment it takes to use it.
     */
    suspend fun buildExportPayload(): Result<ByteArray> {
        val current = selection.value
        if (!current.canExport) {
            return Result.failure(IllegalStateException("Choose what to export first."))
        }
        busy.value = true
        return try {
            val outcome = withContext(Dispatchers.IO) {
                container.journal.buildExport(
                    scope = current.scope,
                    dates = current.dates,
                    entryId = current.entryId,
                    includeAttachments = current.includeAttachments,
                )
            }
            busy.value = false
            outcome
        } catch (error: Throwable) {
            busy.value = false
            Result.failure(error)
        }
    }

    fun suggestedFileName(date: LocalDate = LocalDate.now()): String {
        val stamp = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val scopePart = when (selection.value.scope) {
            ExportScope.ALL_ENTRIES -> "journal"
            ExportScope.SELECTED_DATES -> "journal-selected"
            ExportScope.SINGLE_ENTRY -> "journal-entry"
        }
        return "$scopePart-$stamp.${selection.value.format.extension}"
    }

    /**
     * Writes the export to a location the user picked.
     *
     * The document is created in memory first so a failure part-way through cannot
     * leave a half-written file at a path the user was told contains their journal.
     */
    fun writeExportTo(uri: Uri) {
        viewModelScope.launch {
            val outcome = buildExportPayload()
            outcome
                .onSuccess { bytes ->
                    val written = withContext(Dispatchers.IO) {
                        runCatching {
                            container.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                                stream.write(bytes)
                            } ?: error("The chosen location could not be opened for writing.")
                        }
                    }
                    written
                        .onSuccess {
                            message.value = "Exported ${bytes.size / 1024} KB to ${uri.lastPathSegment ?: "your chosen location"}."
                            isError.value = false
                        }
                        .onFailure { error ->
                            message.value = error.readableMessage()
                            isError.value = true
                        }
                }
                .onFailure { error ->
                    message.value = error.readableMessage()
                    isError.value = true
                }
        }
    }

    /** Reads a picked backup file and plans the import. Writes nothing yet. */
    fun readImportFrom(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    container.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    } ?: error("The chosen file could not be opened.")
                }
            }
            bytes
                .onSuccess { planImport(it, null) }
                .onFailure { error ->
                    message.value = error.readableMessage()
                    isError.value = true
                }
        }
    }

    // ------------------------------------------------------------------ import

    /**
     * Reads a backup and reports what it would do. Writes nothing.
     */
    fun planImport(bytes: ByteArray, passphrase: CharArray?) {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            result.value = null
            val outcome = withContext(Dispatchers.IO) {
                container.journal.planImport(bytes, passphrase)
            }
            busy.value = false
            outcome
                .onSuccess { proposed ->
                    pendingPayload = bytes
                    plan.value = proposed
                    message.value = if (proposed.isEmpty) {
                        "That backup contains no entries."
                    } else {
                        null
                    }
                    isError.value = proposed.isEmpty
                }
                .onFailure { error ->
                    pendingPayload = null
                    plan.value = null
                    message.value = error.readableMessage()
                    isError.value = true
                }
        }
    }

    /** Applies a plan the user has reviewed. */
    fun confirmImport(resolution: ConflictResolution) {
        val payload = pendingPayload
        val proposed = plan.value
        if (payload == null || proposed == null) return

        viewModelScope.launch {
            busy.value = true
            val outcome = withContext(Dispatchers.IO) {
                container.journal.executeImport(proposed, resolution)
            }
            busy.value = false
            outcome
                .onSuccess { imported ->
                    result.value = imported
                    plan.value = null
                    pendingPayload = null
                    message.value = summarise(imported)
                    isError.value = !imported.isSuccess
                }
                .onFailure { error ->
                    message.value = error.readableMessage()
                    isError.value = true
                }
        }
    }

    /** Abandons a plan without writing anything. */
    fun cancelImport() {
        plan.value = null
        pendingPayload = null
        message.value = null
        isError.value = false
    }

    fun dismissMessage() {
        message.value = null
        isError.value = false
    }

    private fun summarise(outcome: ImportResult): String = buildList {
        if (outcome.imported > 0) add("${outcome.imported} imported")
        if (outcome.skipped > 0) add("${outcome.skipped} skipped")
        if (outcome.archived > 0) add("${outcome.archived} archived")
        add(if (outcome.isSuccess) "Done." else "Finished with problems.")
    }.joinToString(" · ")

    fun isDateSelected(date: LocalDate): Boolean = date in selection.value.dates

    companion object {
        /** The picker shows a bounded window so the list cannot grow without limit. */
        const val MAX_ARCHIVE = 400

        /** True when [date] is still writable, used to grey out impossible imports. */
        fun isWritable(date: LocalDate, today: LocalDate): Boolean =
            JournalDates.isEditable(date, today)
    }
}
