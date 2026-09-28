package `as`.today.missyou.core.time

import android.os.SystemClock

/** The real system clock. */
class SystemTimeProvider : TimeProvider {
    override fun wallClockMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

/**
 * A clock under test control.
 *
 * Both clocks can be moved independently, which is what makes it possible to test
 * the two distinct failure modes: a wall clock wound backwards, and a reboot.
 */
class FakeTimeProvider(
    private var wall: Long = DEFAULT_WALL,
    private var elapsed: Long = DEFAULT_ELAPSED,
) : TimeProvider {

    override fun wallClockMillis(): Long = wall

    override fun elapsedRealtimeMillis(): Long = elapsed

    fun advanceWall(millis: Long) { wall += millis }

    fun rewindWall(millis: Long) { wall -= millis }

    fun advanceElapsed(millis: Long) { elapsed += millis }

    /** Simulates a reboot: uptime resets while the wall clock keeps its value. */
    fun reboot() { elapsed = 0 }

    fun setWall(millis: Long) { wall = millis }

    companion object {
        /** 2026-09-28T10:00:00Z, the date the specification uses as its example. */
        const val DEFAULT_WALL: Long = 1_790_718_000_000L
        const val DEFAULT_ELAPSED: Long = 3_600_000L
    }
}
