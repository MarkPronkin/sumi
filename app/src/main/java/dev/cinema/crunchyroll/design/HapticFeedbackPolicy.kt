package dev.cinema.crunchyroll.design

enum class CinemaHaptic { Click, SeriesOpen, TimelineTick }

/** Reads the current profile/device policy at the moment of input, including during a drag. */
class CinemaHaptics internal constructor(
    private val profileEnabled: () -> Boolean,
    private val television: () -> Boolean,
    private val feedback: (CinemaHaptic) -> Unit,
) {
    val enabled: Boolean get() = profileEnabled() && !television()

    fun perform(effect: CinemaHaptic) {
        if (enabled) feedback(effect)
    }
}

/** One tick per crossed second, coalescing fast jumps without scheduling catch-up feedback. */
class TimelineHapticGate(private val minimumIntervalMs: Long = 50L) {
    private var second: Long? = null
    private var lastPulseAtMs: Long? = null

    init { require(minimumIntervalMs >= 0) }

    fun begin(positionMs: Long) {
        second = positionMs.coerceAtLeast(0) / 1000
        lastPulseAtMs = null
    }

    fun update(positionMs: Long, nowMs: Long, enabled: Boolean): Boolean {
        val previous = second ?: return false
        second = positionMs.coerceAtLeast(0) / 1000
        if (!enabled) {
            lastPulseAtMs = null
            return false
        }
        if (second == previous) return false
        val lastPulse = lastPulseAtMs
        if (lastPulse != null && nowMs - lastPulse < minimumIntervalMs) return false
        lastPulseAtMs = nowMs
        return true
    }

    fun reset() {
        second = null
        lastPulseAtMs = null
    }
}
