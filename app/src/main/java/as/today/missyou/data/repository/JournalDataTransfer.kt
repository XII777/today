package `as`.today.missyou.data.repository

import `as`.today.missyou.core.AppDispatchers
import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import `as`.today.missyou.core.time.ClockSnapshot
import `as`.today.missyou.data.RichDocumentCodec
import `as`.today.missyou.data.export.Exporters
import `as`.today.missyou.data.import.BackupReader
import `as`.today.missyou.data.import.JournalImporter
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ExportFormat
import `as`.today.missyou.domain.model.ExportScope
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.ImportResult
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.repository.AttachmentStore
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Import and export for the journal.
 *
 * ## Import safety
 *
 * The importer is insert-only. There is no upsert and no update path, so it is
 * structurally incapable of overwriting an entry, sealed or not. Conflicts are
 * detected up front by [plan] and resolved by the user.
 *
 * ## Export ownership
 *
 * Export never touches the network, never needs an account, and never consults a
 * subscription. It reads the journal through the same repository the UI uses and
 * writes a file the user chose with the system document picker.
 */
internal class JournalDataTransfer(
    private val importer: JournalImporter,
    private val attachmentStore: AttachmentStore?,
    private val dispatchers: AppDispatchers,
    private val appVersionProvider: () -> String,
    private val clockSnapshotProvider: () -> ClockSnapshot,
    private val allEntriesProvider: suspend () -> List<JournalEntry>,
) {

    suspend fun plan(bytes: ByteArray, passphrase: CharArray?, today: LocalDate): Result<ImportPlan> =
        withContext(dispatchers.io) {
            runCatching { importer.plan(BackupReader.read(bytes, passphrase), today) }
        }

    suspend fun execute(
        plan: ImportPlan,
        resolution: ConflictResolution,
        today: LocalDate,
    ): Result<ImportResult> = withContext(dispatchers.io) {
        runCatching { importer.execute(plan, resolution, today, clockSnapshotProvider()) }
    }

    suspend fun buildBackup(
        scope: ExportScope,
        dates: Set<LocalDate>,
        entryId: String?,
        includeAttachments: Boolean,
    ): Result<ByteArray> = withContext(dispatchers.io) {
        runCatching {
            val selected = selectEntries(scope, dates, entryId)
            if (selected.isEmpty()) throw noEntriesSelected()
            val entries = if (includeAttachments) selected else selected.map { it.copy(attachments = emptyList()) }
            Exporters.toJson(backupFor(entries))
        }
    }

    suspend fun render(
        format: ExportFormat,
        scope: ExportScope,
        dates: Set<LocalDate>,
        entryId: String?,
    ): Result<String> = withContext(dispatchers.io) {
        runCatching {
            val selected = selectEntries(scope, dates, entryId)
            if (selected.isEmpty()) throw noEntriesSelected()
            when (format) {
                ExportFormat.MARKDOWN -> Exporters.toMarkdown(selected)
                ExportFormat.PLAIN_TEXT -> Exporters.toPlainText(selected)
                ExportFormat.HTML -> Exporters.toHtml(selected)
                ExportFormat.JSON -> String(Exporters.toJson(backupFor(selected)), Charsets.UTF_8)
            }
        }
    }

    private suspend fun backupFor(entries: List<JournalEntry>) = Exporters.toBackup(
        entries = entries,
        appVersion = appVersionProvider(),
        contentEncoder = RichDocumentCodec::encode,
        attachmentBytes = { attachment -> attachmentStore?.read(attachment.storageName) },
    )

    private suspend fun selectEntries(
        scope: ExportScope,
        dates: Set<LocalDate>,
        entryId: String?,
    ): List<JournalEntry> {
        val all = allEntriesProvider()
        return when (scope) {
            ExportScope.ALL_ENTRIES -> all.sortedBy { it.journalDate }
            ExportScope.SELECTED_DATES -> all.filter { it.journalDate in dates }.sortedBy { it.journalDate }
            ExportScope.SINGLE_ENTRY -> all.filter { it.id == entryId }
        }
    }

    private fun noEntriesSelected() =
        JournalException(JournalErrorReason.ExportFailed, detail = "No entries matched the selection")
}
