package `as`.today.missyou.data.import

import `as`.today.missyou.core.AppDispatchers
import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import `as`.today.missyou.core.time.ClockSnapshot
import `as`.today.missyou.data.IntegrityHasher
import `as`.today.missyou.data.database.AttachmentEntity
import `as`.today.missyou.data.database.JournalDao
import `as`.today.missyou.data.database.JournalEntryEntity
import `as`.today.missyou.data.repository.JournalMapper
import `as`.today.missyou.domain.model.BackupEntry
import `as`.today.missyou.domain.model.BackupFile
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ImportDecision
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.ImportResult
import `as`.today.missyou.domain.repository.AttachmentStore
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Base64

/**
 * Restores a `.qjournal` backup.
 *
 * ## The rule that matters
 *
 * **An import never overwrites.** The importer only ever issues `INSERT`. There is
 * no upsert path, no "replace" flag, and no code that builds an `UPDATE` for an
 * imported entry. When a date already has a sealed entry, the incoming entry is
 * either skipped, archived beside the journal, or the whole import is cancelled —
 * and the existing entry is left exactly as it was.
 *
 * The database enforces this too: `journal_date` is uniquely indexed, inserts
 * abort on conflict, and the immutability triggers would reject any `UPDATE`
 * against a row whose day has rolled over even if one were attempted.
 */
internal class JournalImporter(
    private val dao: JournalDao,
    private val mapper: JournalMapper,
    private val attachmentStore: AttachmentStore?,
    private val archiveStore: ImportArchiveStore,
    private val dispatchers: AppDispatchers,
) {

    /** Decides, per entry, whether it can be inserted or is a conflict. */
    suspend fun plan(file: BackupFile, today: LocalDate): ImportPlan = withContext(dispatchers.io) {
        val decisions = file.entries.map { entry ->
            val date = BackupReader.dateOf(entry)
            val existing = dao.findByDate(date.toString())
            val conflict = existing != null && (existing.lockedAt != null || date.isBefore(today))
            ImportDecision(entry = entry, journalDate = date, existingLocked = conflict)
        }
        ImportPlan(
            entries = file.entries,
            toInsert = decisions.filter { !it.existingLocked },
            conflicts = decisions.filter { it.existingLocked },
        )
    }

    suspend fun execute(
        plan: ImportPlan,
        resolution: ConflictResolution,
        today: LocalDate,
        now: ClockSnapshot,
    ): ImportResult = withContext(dispatchers.io) {
        if (resolution == ConflictResolution.CANCEL) {
            return@withContext ImportResult(errors = emptyList())
        }

        var imported = 0
        var skipped = 0
        var archived = 0
        val errors = mutableListOf<String>()

        for (decision in plan.toInsert) {
            val outcome = insert(decision, today, now)
            when (outcome) {
                ImportOutcome.INSERTED -> imported++
                ImportOutcome.SKIPPED -> skipped++
                is ImportOutcome.Failed -> errors += outcome.reason
            }
        }

        if (plan.conflicts.isNotEmpty()) {
            when (resolution) {
                ConflictResolution.SKIP -> skipped += plan.conflicts.size
                ConflictResolution.IMPORT_AS_ARCHIVE -> archived += plan.conflicts.count { decision ->
                    archiveStore.archive(decision) { fileBytes(decision) }
                }
                ConflictResolution.CANCEL -> Unit
            }
        }

        ImportResult(imported = imported, skipped = skipped, archived = archived, errors = errors)
    }

    private fun fileBytes(decision: ImportDecision): ByteArray =
        `as`.today.missyou.data.export.Exporters.toJsonBackup(listOf(decision.entry))

    private sealed interface ImportOutcome {
        data object INSERTED : ImportOutcome
        data object SKIPPED : ImportOutcome
        data class Failed(val reason: String) : ImportOutcome
    }

    private suspend fun insert(
        decision: ImportDecision,
        today: LocalDate,
        now: ClockSnapshot,
    ): ImportOutcome {
        val entry = decision.entry
        val date = decision.journalDate

        // A restored entry is a faithful copy of what was exported. A day that has
        // already rolled over is born sealed, which the database also demands.
        val lockedAt = when {
            date.isBefore(today) -> entry.lockedAt ?: now.wallClockMillis
            else -> entry.lockedAt
        }
        if (date.isBefore(today) && lockedAt == null) {
            return ImportOutcome.Failed("Refused to restore an unlocked entry for a past day: $date")
        }

        val document = BackupReader.documentOf(entry)
        val tags = entry.tags.map { JournalMapper.normaliseTag(it) }.filter { it.isNotEmpty() }.distinct()
        val id = if (entry.id.isNotBlank()) entry.id else "imported-$date"
        val tokens = mapper.tokensFor(entry.title, document, tags, entry.prompt)
        val contentJson = `as`.today.missyou.data.RichDocumentCodec.encode(document)

        val entity = JournalEntryEntity(
            id = id,
            journalDate = date.toString(),
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
            lockedAt = lockedAt,
            titleSealed = mapper.sealTitle(entry.title, id),
            contentSealed = mapper.sealContent(document, id).second,
            contentFormat = entry.contentFormat,
            tagsSealed = mapper.sealTags(tags, id),
            promptSealed = mapper.sealPrompt(entry.prompt, id),
            writingDurationMillis = entry.writingDurationMillis,
            wordCount = document.wordCount,
            attachmentCount = entry.attachments.size,
            tokenIndexSealed = mapper.sealTokenIndex(tokens, id),
            integrityHash = IntegrityHasher.compute(
                id = id,
                journalDate = date.toString(),
                createdAt = entry.createdAt,
                lockedAt = lockedAt,
                title = entry.title,
                contentJson = contentJson,
                tags = tags,
                prompt = entry.prompt,
            ),
        )

        return try {
            dao.insertEntry(entity)
            restoreAttachments(entry, id)
            ImportOutcome.INSERTED
        } catch (failure: Exception) {
            // A unique-index violation means something already occupies that day.
            // That is a skip, never a replacement.
            if (dao.findByDate(date.toString()) != null) {
                ImportOutcome.SKIPPED
            } else {
                ImportOutcome.Failed("Could not restore $date: ${failure.message}")
            }
        }
    }

    private suspend fun restoreAttachments(entry: BackupEntry, entryId: String) {
        val store = attachmentStore ?: return
        for (attachment in entry.attachments) {
            if (attachment.data.isEmpty()) continue
            val bytes = runCatching { Base64.getDecoder().decode(attachment.data) }.getOrNull() ?: continue
            val stored = store.store(attachment.id, bytes, attachment.mimeType)
            dao.insertAttachment(
                AttachmentEntity(
                    id = attachment.id,
                    entryId = entryId,
                    displayNameSealed = mapper.sealDisplayName(attachment.displayName, attachment.id),
                    mimeType = attachment.mimeType,
                    byteSize = stored.byteSize,
                    storageName = stored.storageName,
                    contentHash = stored.contentHash,
                    createdAt = attachment.createdAt,
                    width = stored.width,
                    height = stored.height,
                ),
            )
        }
    }
}

/** Where "import as separate archive" puts entries that must not touch the journal. */
internal class ImportArchiveStore(
    private val directory: java.io.File?,
    private val dispatchers: AppDispatchers,
) {

    suspend fun archive(decision: ImportDecision, write: (ImportDecision) -> ByteArray): Boolean =
        withContext(dispatchers.io) {
            val target = directory ?: return@withContext false
            runCatching {
                if (!target.exists()) target.mkdirs()
                val name = "imported_${decision.journalDate}_${System.currentTimeMillis()}.qjson"
                target.resolve(name).writeBytes(write(decision))
            }.isSuccess
        }

    suspend fun count(): Int = withContext(dispatchers.io) {
        directory?.listFiles()?.size ?: 0
    }

    suspend fun files(): List<java.io.File> = withContext(dispatchers.io) {
        directory?.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
    }
}
