package `as`.today.missyou.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface JournalDao {

    // ---------------------------------------------------------------- state

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: JournalStateEntity)

    @Query("SELECT * FROM journal_state WHERE id = 0")
    suspend fun loadState(): JournalStateEntity?

    @Query("SELECT current_date FROM journal_state WHERE id = 0")
    suspend fun loadCurrentDate(): String?

    @Query("SELECT * FROM journal_state WHERE id = 0")
    fun observeState(): Flow<JournalStateEntity?>

    /**
     * Advances the effective journal day.
     *
     * The `journal_state_no_backwards` trigger refuses a smaller value, so this
     * statement doubles as the assertion that the clock guard is working.
     */
    @Query(
        """
        UPDATE journal_state
        SET current_date = :currentDate,
            highest_seen_wall_millis = MAX(highest_seen_wall_millis, :wallMillis),
            updated_at = :updatedAt
        WHERE id = 0
        """,
    )
    suspend fun advanceCurrentDate(currentDate: String, wallMillis: Long, updatedAt: Long)

    @Query("UPDATE journal_state SET seal_open = 1 WHERE id = 0")
    suspend fun openSealWindow()

    @Query("UPDATE journal_state SET seal_open = 0 WHERE id = 0")
    suspend fun closeSealWindow()

    // ---------------------------------------------------------------- sealing

    @Query("SELECT id, journal_date FROM journal_entries WHERE journal_date < :currentDate AND locked_at IS NULL")
    suspend fun findUnsealedDays(currentDate: String): List<UnsealedDay>

    @Query("UPDATE journal_entries SET locked_at = :lockedAt WHERE id = :id")
    suspend fun stampLockedAt(id: String, lockedAt: Long)

    // ---------------------------------------------------------------- entries

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE journal_date = :journalDate LIMIT 1")
    suspend fun findByDate(journalDate: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE journal_date = :journalDate LIMIT 1")
    fun observeByDate(journalDate: String): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun requireById(id: String): JournalEntryEntity

    /**
     * Inserts an entry.
     *
     * `OnConflictStrategy.ABORT` is deliberate: the unique index on `journal_date`
     * turns a duplicate day into a constraint violation rather than a silent
     * replacement of the existing entry. This is the last line of defence against
     * "accidental duplicate replacement" from the specification.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Update
    suspend fun updateEntry(entry: JournalEntryEntity)

    @Query(
        """
        UPDATE journal_entries
        SET title_sealed = :titleSealed,
            content_sealed = :contentSealed,
            content_format = :contentFormat,
            tags_sealed = :tagsSealed,
            prompt_sealed = :promptSealed,
            writing_duration_millis = :writingDurationMillis,
            word_count = :wordCount,
            attachment_count = :attachmentCount,
            token_index_sealed = :tokenIndexSealed,
            integrity_hash = :integrityHash,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateContent(
        id: String,
        titleSealed: ByteArray?,
        contentSealed: ByteArray,
        contentFormat: String,
        tagsSealed: ByteArray?,
        promptSealed: ByteArray?,
        writingDurationMillis: Long,
        wordCount: Int,
        attachmentCount: Int,
        tokenIndexSealed: ByteArray,
        integrityHash: String,
        updatedAt: Long,
    ): Int

    /**
     * Deletes an entry, but only while it is still today's open day.
     *
     * The redundant predicate mirrors the `journal_entries_immutable_delete`
     * trigger. The trigger is what actually enforces the rule; this clause means
     * the common path never even issues a statement that would be refused.
     */
    @Query(
        """
        DELETE FROM journal_entries
        WHERE id = :id
          AND locked_at IS NULL
          AND journal_date >= :currentDate
        """,
    )
    suspend fun deleteOpenEntry(id: String, currentDate: String): Int

    // ---------------------------------------------------------------- listings

    @Query(
        """
        SELECT id, journal_date, locked_at, title_sealed, word_count, updated_at, attachment_count, tags_sealed
        FROM journal_entries
        WHERE journal_date >= :from AND journal_date <= :to
        ORDER BY journal_date ASC
        """,
    )
    fun observeDayWindow(from: String, to: String): Flow<List<DayProjection>>

    @Query(
        """
        SELECT id, journal_date, locked_at, title_sealed, word_count, updated_at, attachment_count, tags_sealed
        FROM journal_entries
        ORDER BY journal_date DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observePage(limit: Int, offset: Int): Flow<List<DayProjection>>

    @Query("SELECT COUNT(*) FROM journal_entries")
    fun observeCount(): Flow<Int>

    @Query("SELECT journal_date FROM journal_entries ORDER BY journal_date DESC")
    fun observeAllDates(): Flow<List<String>>

    @Query("SELECT journal_date FROM journal_entries ORDER BY journal_date DESC")
    suspend fun allDates(): List<String>

    // ---------------------------------------------------------------- insights

    @Query(
        """
        SELECT
            COUNT(*) AS entry_count,
            COALESCE(SUM(word_count), 0) AS total_words,
            COALESCE(SUM(writing_duration_millis), 0) AS total_writing_millis,
            COALESCE(MAX(word_count), 0) AS max_words,
            (SELECT journal_date FROM journal_entries ORDER BY word_count DESC, journal_date ASC LIMIT 1) AS longest_date
        FROM journal_entries
        """,
    )
    suspend fun insightsAggregate(): InsightsAggregate

    @Query("SELECT id, tags_sealed FROM journal_entries")
    suspend fun allTagProjections(): List<TagProjection>

    @Query("SELECT id, token_index_sealed FROM journal_entries")
    suspend fun allTokenIndexProjections(): List<TokenIndexProjection>

    @Query("SELECT journal_date, word_count FROM journal_entries ORDER BY journal_date ASC")
    suspend fun allWordCounts(): List<DayWordCount>

    // ---------------------------------------------------------------- search

    @Query("SELECT * FROM journal_entries WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<JournalEntryEntity>

    // ---------------------------------------------------------------- attachments

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE entry_id = :entryId ORDER BY created_at ASC")
    suspend fun attachmentsFor(entryId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE entry_id IN (:entryIds) ORDER BY created_at ASC")
    fun observeAttachmentsFor(entryIds: List<String>): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun findAttachment(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE entry_id = :entryId AND id = :attachmentId LIMIT 1")
    suspend fun findEntryAttachment(entryId: String, attachmentId: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id AND entry_id = :entryId")
    suspend fun deleteAttachment(id: String, entryId: String): Int

    /** Removes every attachment row of an entry. Refused for sealed entries by trigger. */
    @Query("DELETE FROM attachments WHERE entry_id = :entryId")
    suspend fun deleteAttachmentRows(entryId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE entry_id = :entryId")
    suspend fun countAttachments(entryId: String): Int
}

internal data class DayWordCount(
    @androidx.room.ColumnInfo(name = "journal_date") val journalDate: String,
    @androidx.room.ColumnInfo(name = "word_count") val wordCount: Int,
)
