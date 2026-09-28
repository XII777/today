package `as`.today.missyou.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Converts an instant into "the journal day it belongs to".
 *
 * The rollover time is the moment the current journal day is sealed. A rollover
 * of `00:00` seals a day at midnight; a rollover of `04:00` keeps the day open
 * until 4am, which suits people who write late at night.
 *
 * @param rolloverMinutes minutes after midnight at which the previous journal day
 *        becomes immutable. `0` means midnight.
 */
@JvmInline
value class DayRollover(val minutesAfterMidnight: Int) {

    init {
        require(minutesAfterMidnight in 0..MINUTES_IN_A_DAY - 1) {
            "Rollover must be within a single day, was $minutesAfterMidnight"
        }
    }

    operator fun plus(minutes: Int): DayRollover = DayRollover(minutesAfterMidnight + minutes)

    operator fun minus(minutes: Int): DayRollover = DayRollover(minutesAfterMidnight - minutes)

    val asDuration: java.time.Duration
        get() = java.time.Duration.ofMinutes(minutesAfterMidnight.toLong())

    fun formatLabel(): String {
        val value = minutesAfterMidnight
        val hours = value / 60
        val mins = value % 60
        val suffix = if (hours < 12) "AM" else "PM"
        val displayHour = when {
            hours % 12 == 0 -> 12
            else -> hours % 12
        }
        return if (mins == 0) {
            "$displayHour:00 $suffix"
        } else {
            "%d:%02d %s".format(displayHour, mins, suffix)
        }
    }

    companion object {
        const val MINUTES_IN_A_DAY = 24 * 60
        val Midnight = DayRollover(0)
    }
}

/**
 * Journal-day arithmetic.
 *
 * Everything here is pure so the immutability rules can be exhaustively tested.
 */
object JournalDates {

    /** The journal day that [wallClockMillis] falls into, honouring the rollover. */
    fun journalDateAt(
        wallClockMillis: Long,
        zone: ZoneId,
        rollover: DayRollover,
    ): LocalDate {
        val shifted = Instant.ofEpochMilli(wallClockMillis).minus(rollover.asDuration)
        return shifted.atZone(zone).toLocalDate()
    }

    /**
     * The instant at which [journalDate] stops being editable.
     *
     * Returned as wall-clock millis purely for display ("locks in 3h 12m"); it is
     * never used to decide whether an entry is locked. That decision always runs
     * through [journalDateAt] on the *effective* current date.
     */
    fun lockInstantMillis(
        journalDate: LocalDate,
        zone: ZoneId,
        rollover: DayRollover,
    ): Long = ZonedDateTime.of(journalDate.plusDays(1), LocalTime.MIDNIGHT, zone)
        .plus(rollover.asDuration)
        .toInstant()
        .toEpochMilli()

    /** True when [journalDate] is still open for writing. */
    fun isEditable(journalDate: LocalDate, currentJournalDate: LocalDate): Boolean =
        !journalDate.isBefore(currentJournalDate)

    /**
     * How much of [journalDate] has already elapsed, as a 0f..1f fraction.
     *
     * Only meaningful for the current journal day.
     */
    fun dayProgress(
        journalDate: LocalDate,
        wallClockMillis: Long,
        zone: ZoneId,
        rollover: DayRollover,
    ): Float {
        if (journalDate != journalDateAt(wallClockMillis, zone, rollover)) return 1f
        val dayStart = journalDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = lockInstantMillis(journalDate, zone, rollover)
        val span = (dayEnd - dayStart).toDouble()
        if (span <= 0.0) return 1f
        val done = (wallClockMillis - dayStart).toDouble()
        return (done / span).coerceIn(0.0, 1.0).toFloat()
    }
}
