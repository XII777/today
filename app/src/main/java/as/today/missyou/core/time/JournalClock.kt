package `as`.today.missyou.core.time

import `as`.today.missyou.core.time.JournalDates.journalDateAt
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * A single observation of the clock.
 *
 * @param journalDate the *effective* journal day. This is monotonic: it never moves
 *        backwards, no matter what the device clock does.
 * @param rollbackDetected true when the wall clock moved backwards relative to what
 *        we had already observed, or when it disagrees with the monotonic clock.
 */
data class ClockSnapshot(
    val journalDate: LocalDate,
    val wallClockMillis: Long,
    val elapsedRealtimeMillis: Long,
    val rollbackDetected: Boolean,
    val backwardsAmountMillis: Long,
) {
    val clockLooksTampered: Boolean get() = rollbackDetected
}

/**
 * The single source of truth for "what day is it, as far as the journal cares".
 *
 * ## Why this exists
 *
 * The defining promise of the app is that a locked entry stays locked. A naive
 * implementation compares the stored journal date against `LocalDate.now()`. That
 * promise is trivially broken by setting the device clock back, so this class
 * adds two protections:
 *
 * 1. **Monotonic effective date.** The effective journal day is
 *    `max(rawJournalDay, highestJournalDayEverObserved)`. Moving the clock back
 *    therefore cannot un-seal anything: the effective date simply does not move.
 * 2. **Detection.** The caller is told that a rollback happened so the UI can warn
 *    the user, satisfying "warn the user when appropriate".
 *
 * The highest observed day is additionally persisted by the repository, and the
 * database carries a trigger that refuses to let `journal_state.current_date`
 * decrease, so the guarantee survives process death and tampering.
 */
class JournalClock(
    private val timeProvider: TimeProvider,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {

    private data class State(
        val highestJournalDate: LocalDate?,
        val highestWallClockMillis: Long,
        val highestElapsedRealtimeMillis: Long,
        val rollbackDetected: Boolean,
        val backwardsAmountMillis: Long,
        val acknowledgedBackwardsMillis: Long,
    )

    private val state = AtomicReference(
        State(
            highestJournalDate = null,
            highestWallClockMillis = Long.MIN_VALUE,
            highestElapsedRealtimeMillis = Long.MIN_VALUE,
            rollbackDetected = false,
            backwardsAmountMillis = 0L,
            acknowledgedBackwardsMillis = 0L,
        ),
    )

    /**
     * Restores the highest previously observed journal day.
     *
     * Called at startup with the value recovered from the database so that the
     * monotonic guarantee spans process restarts.
     */
    fun restore(highestJournalDate: LocalDate?, highestWallClockMillis: Long) {
        state.updateAndGet { current ->
            val best = listOfNotNull(current.highestJournalDate, highestJournalDate).maxOrNull()
            current.copy(
                highestJournalDate = best,
                highestWallClockMillis = maxOf(current.highestWallClockMillis, highestWallClockMillis),
            )
        }
    }

    /** The highest journal day this process has observed, or `null` before the first tick. */
    fun highestObservedJournalDate(): LocalDate? = state.get().highestJournalDate

    /**
     * Reads the clock and returns the effective journal day.
     *
     * Safe to call as often as needed; it performs no I/O and no allocation beyond
     * the snapshot itself.
     */
    fun tick(rollover: DayRollover): ClockSnapshot {
        val wall = timeProvider.wallClockMillis()
        val elapsed = timeProvider.elapsedRealtimeMillis()
        val zone = zoneProvider()
        val rawDate = journalDateAt(wall, zone, rollover)

        while (true) {
            val current = state.get()
            val previousDate = current.highestJournalDate
            val effectiveDate = when {
                previousDate == null -> rawDate
                rawDate.isAfter(previousDate) -> rawDate
                else -> previousDate
            }

            // Only a journal day that moves backwards threatens immutability, so
            // that is the only thing that warrants a warning. The wall clock can
            // legitimately move backwards within a single day, and the user can
            // legitimately *raise* the rollover time, which shifts the journal day
            // earlier without anything being wrong. Warning on either would train the
            // user to dismiss a message that does not mean anything.
            val dateWentBack = previousDate != null && rawDate.isBefore(previousDate)
            val backwardsBy = if (dateWentBack) {
                (current.highestWallClockMillis - wall).coerceAtLeast(0L)
            } else {
                0L
            }

            // A rollback the user has already dismissed stays dismissed for that same
            // magnitude, so acknowledging is not a no-op: the condition is still
            // present, but it is not re-reported. Winding the clock back *further*
            // than was acknowledged is a new event and does warn again.
            val detected = dateWentBack && backwardsBy > current.acknowledgedBackwardsMillis

            val next = State(
                highestJournalDate = effectiveDate,
                highestWallClockMillis = maxOf(current.highestWallClockMillis, wall),
                highestElapsedRealtimeMillis =
                    maxOf(current.highestElapsedRealtimeMillis, elapsed),
                rollbackDetected = detected,
                backwardsAmountMillis = maxOf(current.backwardsAmountMillis, backwardsBy),
                acknowledgedBackwardsMillis = current.acknowledgedBackwardsMillis,
            )
            if (state.compareAndSet(current, next)) {
                return ClockSnapshot(
                    journalDate = effectiveDate,
                    wallClockMillis = wall,
                    elapsedRealtimeMillis = elapsed,
                    rollbackDetected = detected,
                    backwardsAmountMillis = backwardsBy,
                )
            }
        }
    }

    /**
     * Dismisses the rollback warning for a backwards move of the size seen so far.
     *
     * A later, larger rollback re-warns, so dismissing is never permanent.
     */
    fun acknowledgeClockWarning() {
        state.updateAndGet {
            it.copy(
                rollbackDetected = false,
                acknowledgedBackwardsMillis =
                    maxOf(it.acknowledgedBackwardsMillis, it.backwardsAmountMillis),
            )
        }
    }

    /**
     * True when the monotonic clock suggests the wall clock jumped forward a long
     * way in a very short time, which is the signature of manual clock editing
     * rather than real elapsed time.
     */
    fun looksLikeForwardJump(
        previous: ClockSnapshot,
        current: ClockSnapshot,
        toleranceMillis: Long = DEFAULT_FORWARD_JUMP_TOLERANCE_MILLIS,
    ): Boolean {
        val wallDelta = current.wallClockMillis - previous.wallClockMillis
        val elapsedDelta = current.elapsedRealtimeMillis - previous.elapsedRealtimeMillis
        if (elapsedDelta <= 0L) return false
        return wallDelta - elapsedDelta > toleranceMillis
    }

    private companion object {
        const val DEFAULT_FORWARD_JUMP_TOLERANCE_MILLIS = 6L * 60L * 60L * 1000L
    }
}
