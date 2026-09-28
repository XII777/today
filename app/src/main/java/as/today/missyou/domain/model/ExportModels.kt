package `as`.today.missyou.domain.model

import java.time.LocalDate

/** Formats the user can export to (§18). */
enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain"),
    HTML("html", "text/html"),
}

/** What to include in an export. */
enum class ExportScope { ALL_ENTRIES, SELECTED_DATES, SINGLE_ENTRY }

/** Base64 / text export options (§18 "optionally local HTML/PDF", §20 encrypted backup). */
data class ExportRequest(
    val scope: ExportScope = ExportScope.ALL_ENTRIES,
    val dates: Set<LocalDate> = emptySet(),
    val entryId: String? = null,
    val includeAttachments: Boolean = true,
    val encrypt: Boolean = false,
    val passphrase: CharArray? = null,
)

/** The outcome of an import, split by what happened to each entry (§18). */
data class ImportPlan(
    val entries: List<BackupEntry>,
    val toInsert: List<ImportDecision>,
    val conflicts: List<ImportDecision>,
) {
    val insertCount: Int get() = toInsert.size
    val conflictCount: Int get() = conflicts.size
    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        val Empty = ImportPlan(emptyList(), emptyList(), emptyList())
    }
}

/**
 * One entry from a backup, plus what will happen to it.
 *
 * @param existingLocked true when a sealed entry already exists for that date, which
 *        is the case the specification calls out explicitly: the existing entry is
 *        kept and the incoming one is never written.
 */
data class ImportDecision(
    val entry: BackupEntry,
    val journalDate: LocalDate,
    val existingLocked: Boolean,
) {
    val willBeImported: Boolean get() = !existingLocked
}

/** What the user chose to do about conflicting entries. */
enum class ConflictResolution { SKIP, IMPORT_AS_ARCHIVE, CANCEL }

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val archived: Int = 0,
    val errors: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = errors.isEmpty()
}

/** A single selection in the export UI. */
data class ExportTarget(
    val label: String,
    val detail: String,
    val dates: Set<LocalDate>,
)
