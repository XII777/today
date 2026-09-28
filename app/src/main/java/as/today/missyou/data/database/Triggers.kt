package `as`.today.missyou.data.database

/**
 * The SQL that turns "an entry from yesterday is read-only" from a UI convention
 * into a database invariant.
 *
 * ## Why triggers
 *
 * The specification requires immutability to survive "UI bypass, repository
 * misuse, import overwrite, database update attempts, accidental duplicate
 * replacement, and date manipulation". Checks in Kotlin cannot cover all of those,
 * because anything holding the database file can issue SQL directly. Triggers can:
 * they run inside the same statement, they cannot be bypassed by a forgotten `if`,
 * and they still fire for statements issued by Room.
 *
 * ## The seal window
 *
 * Once the effective journal day passes an entry's `journal_date`, stamping
 * `locked_at` onto that entry is legitimate maintenance, not a violation. The
 * `seal_open` flag on `journal_state` opens that one operation, inside a single
 * transaction, and is cleared immediately afterwards.
 */
internal object Triggers {

    val STATEMENTS: List<String> = listOf(
        // The effective journal day may only ever move forward. This is the last
        // line of defence against a device clock that has been wound back.
        """
        CREATE TRIGGER IF NOT EXISTS journal_state_no_backwards
        BEFORE UPDATE OF current_date ON journal_state
        FOR EACH ROW
        WHEN NEW.current_date < OLD.current_date
        BEGIN
            SELECT RAISE(ABORT, 'JOURNAL_CLOCK_ROLLBACK');
        END
        """,

        // Refuse any modification to a row whose journal day has already rolled
        // over. This covers content, title, tags, dates, everything.
        """
        CREATE TRIGGER IF NOT EXISTS journal_entries_immutable_update
        BEFORE UPDATE ON journal_entries
        FOR EACH ROW
        WHEN OLD.journal_date < (SELECT current_date FROM journal_state WHERE id = 0)
             AND (SELECT seal_open FROM journal_state WHERE id = 0) = 0
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_IMMUTABLE');
        END
        """,

        // Deletion is refused for anything already sealed, and for anything the
        // effective journal day has moved past.
        """
        CREATE TRIGGER IF NOT EXISTS journal_entries_immutable_delete
        BEFORE DELETE ON journal_entries
        FOR EACH ROW
        WHEN OLD.locked_at IS NOT NULL
             OR OLD.journal_date < (SELECT current_date FROM journal_state WHERE id = 0)
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_IMMUTABLE');
        END
        """,

        // A lock is permanent. Even the app cannot unseal a row, and therefore a
        // clock change, a restore, or a bug cannot unseal a row either.
        """
        CREATE TRIGGER IF NOT EXISTS journal_entries_lock_is_permanent
        BEFORE UPDATE OF locked_at ON journal_entries
        FOR EACH ROW
        WHEN OLD.locked_at IS NOT NULL AND NEW.locked_at IS NULL
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_LOCK_IS_PERMANENT');
        END
        """,

        // An entry always belongs to the day it was written on. Back-dating an
        // existing row is refused even for a day that is still open.
        """
        CREATE TRIGGER IF NOT EXISTS journal_entries_date_is_permanent
        BEFORE UPDATE OF journal_date ON journal_entries
        FOR EACH ROW
        WHEN NEW.journal_date <> OLD.journal_date
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_DATE_IS_PERMANENT');
        END
        """,

        // An entry may only ever be created with a date that is not already past.
        // Writing to a day that has rolled over creates a row that is locked from
        // the very first instant, which is what makes backfill and import safe.
        """
        CREATE TRIGGER IF NOT EXISTS journal_entries_past_insert_must_be_locked
        BEFORE INSERT ON journal_entries
        FOR EACH ROW
        WHEN NEW.journal_date < (SELECT current_date FROM journal_state WHERE id = 0)
             AND NEW.locked_at IS NULL
        BEGIN
            SELECT RAISE(ABORT, 'PAST_ENTRY_MUST_BE_LOCKED');
        END
        """,

        // Attachments inherit the immutability of their entry: once the parent is
        // sealed, its file rows are frozen too.
        """
        CREATE TRIGGER IF NOT EXISTS attachments_immutable_update
        BEFORE UPDATE ON attachments
        FOR EACH ROW
        WHEN (SELECT locked_at FROM journal_entries WHERE id = OLD.entry_id) IS NOT NULL
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_IMMUTABLE');
        END
        """,

        """
        CREATE TRIGGER IF NOT EXISTS attachments_immutable_delete
        BEFORE DELETE ON attachments
        FOR EACH ROW
        WHEN (SELECT locked_at FROM journal_entries WHERE id = OLD.entry_id) IS NOT NULL
        BEGIN
            SELECT RAISE(ABORT, 'ENTRY_IMMUTABLE');
        END
        """,
    )
}
