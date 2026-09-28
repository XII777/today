package `as`.today.missyou.core

import java.time.LocalDate

/**
 * Machine readable failure reasons.
 *
 * The UI maps these onto localised strings so that user-facing error copy never
 * depends on exception messages surviving refactors.
 */
enum class JournalErrorReason {
    /** The entry belongs to a day that has already rolled over. */
    EntryLocked,

    /** The entry does not exist (or was never created). */
    EntryNotFound,

    /** The device clock moved backwards and the write was refused. */
    ClockRollback,

    /** An integrity hash did not match the stored content. */
    IntegrityFailure,

    /** Import hit a date that already holds a locked entry. */
    ImportConflict,

    /** The import file could not be read or is not a recognised backup. */
    ImportUnreadable,

    /** Export target could not be written. */
    ExportFailed,

    /** The requested day is in the future and cannot be written yet. */
    FutureDate,

    /** App lock is active and the user has not authenticated. */
    LockedOut,

    /** A cryptographic operation failed. */
    CryptoFailure,

    /** Anything genuinely unexpected. */
    Unknown,
}

/**
 * The single exception type used across the data layer.
 *
 * [reason] is the stable, switchable part of the failure; [detail] is developer
 * oriented context and is never shown to the user.
 */
class JournalException(
    val reason: JournalErrorReason,
    val journalDate: LocalDate? = null,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: reason.name, cause)

/** `true` when the failure is specifically "this day is already sealed". */
fun Throwable.isLockedFailure(): Boolean =
    this is JournalException && reason == JournalErrorReason.EntryLocked
