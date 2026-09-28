package `as`.today.missyou.core.time

/**
 * Indirection over the system clock so that every date-dependent rule in the app
 * is testable without touching the device.
 */
interface TimeProvider {
    /** Wall-clock time, which the user (or the network) can move. */
    fun wallClockMillis(): Long

    /**
     * Monotonic time since boot. This cannot be moved backwards by the user, which
     * is what lets us tell "the clock was corrected" from "time really passed".
     */
    fun elapsedRealtimeMillis(): Long
}
