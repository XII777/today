package `as`.today.missyou.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A [TimeProvider] the test drives by hand.
 *
 * The whole point of [TimeProvider] is that the immutability rules can be exercised
 * against a clock that moves when the test says so, including backwards.
 */
class FakeTimeProvider(
    var wall: Long = 0L,
    var elapsed: Long = 0L,
) : TimeProvider {
    override fun wallClockMillis(): Long = wall
    override fun elapsedRealtimeMillis(): Long = elapsed

    /** Moves both clocks forward, as real time passing would. */
    fun advance(millis: Long) {
        wall += millis
        elapsed += millis
    }

    /**
     * Moves only the wall clock.
     *
     * This is the shape of a user changing the device time, and it is the case the
     * journal has to survive.
     */
    fun moveWallClockOnly(to: Long) {
        wall = to
    }
}

class JournalClockTest {

    private val london = ZoneId.of("Europe/London")

    private fun millisFor(date: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), london).toInstant().toEpochMilli()

    @Test
    fun `effective day follows the wall clock when time only moves forward`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 3, 1), 9))
        val clock = JournalClock(time) { london }

        val first = clock.tick(DayRollover.Midnight)
        assertEquals(LocalDate.of(2026, 3, 1), first.journalDate)
        assertFalse(first.rollbackDetected)

        time.advance(24L * 60L * 60L * 1000L)
        val second = clock.tick(DayRollover.Midnight)
        assertEquals(LocalDate.of(2026, 3, 2), second.journalDate)
        assertFalse(second.rollbackDetected)
    }

    @Test
    fun `moving the clock back cannot unseal a day`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 3, 10), 12))
        val clock = JournalClock(time) { london }

        assertEquals(LocalDate.of(2026, 3, 10), clock.tick(DayRollover.Midnight).journalDate)

        // The user sets the device clock back three days. The effective day must not
        // follow it: this single property is what stops a rolled-over entry from
        // becoming editable again.
        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 3, 7), 12))
        val afterTamper = clock.tick(DayRollover.Midnight)

        assertEquals(LocalDate.of(2026, 3, 10), afterTamper.journalDate)
        assertTrue(afterTamper.rollbackDetected)
        assertTrue(afterTamper.clockLooksTampered)
        assertEquals(3L * 24L * 60L * 60L * 1000L, afterTamper.backwardsAmountMillis)
    }

    @Test
    fun `acknowledging a rollback stops it re-warning for the same size move`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 5, 5), 10))
        val clock = JournalClock(time) { london }
        clock.tick(DayRollover.Midnight)

        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 5, 1), 10))
        val warned = clock.tick(DayRollover.Midnight)
        assertTrue(warned.rollbackDetected)
        assertEquals(4L * 24L * 60L * 60L * 1000L, warned.backwardsAmountMillis)

        clock.acknowledgeClockWarning()

        // The clock is still behind, so the condition still holds. Acknowledging has
        // to suppress the report, otherwise the user dismisses a warning that
        // reappears on the very next frame.
        assertFalse(clock.tick(DayRollover.Midnight).rollbackDetected)
        assertFalse(clock.tick(DayRollover.Midnight).rollbackDetected)
    }

    @Test
    fun `a worse rollback than the one acknowledged warns again`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 5, 5), 10))
        val clock = JournalClock(time) { london }
        clock.tick(DayRollover.Midnight)

        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 5, 2), 10))
        assertTrue(clock.tick(DayRollover.Midnight).rollbackDetected)
        clock.acknowledgeClockWarning()
        assertFalse(clock.tick(DayRollover.Midnight).rollbackDetected)

        // Winding it back further is a new event, and must not be swallowed by the
        // acknowledgement of the previous one.
        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 4, 20), 10))
        assertTrue(clock.tick(DayRollover.Midnight).rollbackDetected)
    }

    @Test
    fun `moving the wall clock within a single day is not treated as tampering`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 5, 5), 17))
        val clock = JournalClock(time) { london }
        clock.tick(DayRollover.Midnight)

        // Same journal day, earlier wall time. Nothing is at risk, so the user should
        // not be nagged about a clock that is merely being adjusted.
        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 5, 5), 9))
        val snapshot = clock.tick(DayRollover.Midnight)

        assertFalse(snapshot.rollbackDetected)
        assertEquals(LocalDate.of(2026, 5, 5), snapshot.journalDate)
    }

    @Test
    fun `raising the rollover time does not look like tampering`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 5, 6), 2))
        val clock = JournalClock(time) { london }

        // Midnight rollover puts the user on the 6th.
        assertEquals(LocalDate.of(2026, 5, 6), clock.tick(DayRollover.Midnight).journalDate)

        // Switching to a 4am rollover at 2am legitimately moves the journal day back
        // to the 5th. The effective day stays put, and this is not tampering.
        val afterChange = clock.tick(DayRollover(4 * 60))
        assertEquals(LocalDate.of(2026, 5, 6), afterChange.journalDate)
        assertFalse(afterChange.rollbackDetected)
    }

    @Test
    fun `restoring a persisted day keeps the guarantee across a process restart`() {
        // First process: observe today, then "die".
        val first = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 6, 20), 20))
        JournalClock(first) { london }.tick(DayRollover.Midnight)

        // Second process: the user has wound the clock back before relaunching.
        val second = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 6, 1), 20))
        val restarted = JournalClock(second) { london }
        restarted.restore(highestJournalDate = LocalDate.of(2026, 6, 20), highestWallClockMillis = 0L)

        assertEquals(
            LocalDate.of(2026, 6, 20),
            restarted.tick(DayRollover.Midnight).journalDate,
        )
    }

    @Test
    fun `restore never lowers an already observed day`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 6, 20), 20))
        val clock = JournalClock(time) { london }
        clock.tick(DayRollover.Midnight)

        clock.restore(highestJournalDate = LocalDate.of(2020, 1, 1), highestWallClockMillis = 0L)

        assertEquals(
            LocalDate.of(2026, 6, 20),
            clock.tick(DayRollover.Midnight).journalDate,
        )
    }

    @Test
    fun `a large forward wall clock jump is distinguishable from real time`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 7, 1), 8))
        val clock = JournalClock(time) { london }
        val before = clock.tick(DayRollover.Midnight)

        // Only a minute of real time passes, but the wall clock leaps a week.
        time.advance(60_000L)
        time.moveWallClockOnly(millisFor(LocalDate.of(2026, 7, 8), 8))
        val after = clock.tick(DayRollover.Midnight)

        assertTrue(clock.looksLikeForwardJump(before, after))
    }

    @Test
    fun `genuine elapsed time is not mistaken for clock tampering`() {
        val time = FakeTimeProvider(wall = millisFor(LocalDate.of(2026, 7, 1), 8))
        val clock = JournalClock(time) { london }
        val before = clock.tick(DayRollover.Midnight)

        time.advance(6L * 60L * 60L * 1000L)
        val after = clock.tick(DayRollover.Midnight)

        assertFalse(clock.looksLikeForwardJump(before, after))
    }
}

class JournalDatesTest {

    private val london = ZoneId.of("Europe/London")

    @Test
    fun `a late night entry still belongs to the day it was written on`() {
        val instant = ZonedDateTime.of(
            LocalDate.of(2026, 11, 3),
            java.time.LocalTime.of(23, 30),
            london,
        ).toInstant().toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 11, 3),
            JournalDates.journalDateAt(instant, london, DayRollover.Midnight),
        )
    }

    @Test
    fun `a rollover after midnight keeps yesterday open past midnight`() {
        val instant = ZonedDateTime.of(
            LocalDate.of(2026, 11, 4),
            java.time.LocalTime.of(2, 0),
            london,
        ).toInstant().toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 11, 4),
            JournalDates.journalDateAt(instant, london, DayRollover.Midnight),
        )
        assertEquals(
            LocalDate.of(2026, 11, 3),
            JournalDates.journalDateAt(instant, london, DayRollover(4 * 60)),
        )
    }

    @Test
    fun `a past day is never editable against the current day`() {
        val today = LocalDate.of(2026, 4, 9)
        assertFalse(JournalDates.isEditable(today.minusDays(1), today))
        assertTrue(JournalDates.isEditable(today, today))
        assertTrue(
            "A future day passes this check, but the repository rejects it separately " +
                "with FutureDate; this function only guards against the past.",
            JournalDates.isEditable(today.plusDays(3), today),
        )
    }

    @Test
    fun `day progress is clamped to a sane fraction`() {
        val zone = london
        val start = ZonedDateTime.of(LocalDate.of(2026, 4, 9), java.time.LocalTime.MIN, zone)
            .toInstant().toEpochMilli()
        val rollover = DayRollover.Midnight

        assertEquals(
            0f,
            JournalDates.dayProgress(LocalDate.of(2026, 4, 9), start, zone, rollover),
            0.001f,
        )
        assertEquals(
            "A fully elapsed day must report 1, not overflow past it.",
            1f,
            JournalDates.dayProgress(
                LocalDate.of(2026, 4, 9),
                start + 48L * 60L * 60L * 1000L,
                zone,
                rollover,
            ),
            0.001f,
        )
    }

    @Test
    fun `rollover labels read as a human would write them`() {
        assertEquals("12:00 AM", DayRollover.Midnight.formatLabel())
        assertEquals("4:00 AM", DayRollover(4 * 60).formatLabel())
        assertEquals("12:00 PM", DayRollover(12 * 60).formatLabel())
        assertEquals("4:30 PM", DayRollover(16 * 60 + 30).formatLabel())
    }
}
