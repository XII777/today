package `as`.today.missyou.ui.common

import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException

/**
 * Maps a failure onto copy a person can act on (§52).
 *
 * Error text is chosen here rather than surfaced from exception messages, so a
 * refactor can never accidentally show a stack-trace fragment to a user, and every
 * message is written in plain language.
 */
fun Throwable.readableMessage(): String = when (this) {
    is JournalException -> when (reason) {
        JournalErrorReason.EntryLocked ->
            "This journal entry is already locked and cannot be changed."

        JournalErrorReason.ImportConflict ->
            "This date already contains a locked journal entry. Your existing entry was kept unchanged."

        JournalErrorReason.ClockRollback ->
            "The device clock moved backwards, so this change was not saved."

        JournalErrorReason.FutureDate -> "That day has not happened yet."

        JournalErrorReason.ExportFailed -> "The export could not be created."

        JournalErrorReason.ImportUnreadable -> "That file is not a Today journal backup."

        JournalErrorReason.IntegrityFailure -> "This entry failed its integrity check."

        JournalErrorReason.CryptoFailure -> "The journal could not be unlocked on this device."

        JournalErrorReason.EntryNotFound -> "That entry no longer exists."

        JournalErrorReason.LockedOut -> "Unlock the app to continue."

        JournalErrorReason.Unknown -> "Something went wrong. Please try again."
    }

    else -> "Something went wrong. Please try again."
}
