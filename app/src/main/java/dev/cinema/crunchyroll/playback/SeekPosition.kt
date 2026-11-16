package dev.cinema.crunchyroll.playback

/** Clamp before adding, so even repeated seeks and extreme deltas cannot overflow. */
internal fun relativeSeekPosition(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val duration = durationMs.coerceAtLeast(0)
    val position = positionMs.coerceIn(0, duration)
    val boundedDelta = deltaMs.coerceIn(-position, duration - position)
    return position + boundedDelta
}
