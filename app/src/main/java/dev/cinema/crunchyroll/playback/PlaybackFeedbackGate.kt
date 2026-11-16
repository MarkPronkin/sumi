package dev.cinema.crunchyroll.playback

/** Tracks play intent, rather than isPlaying, which also changes for buffering and seeking. */
internal class PlaybackFeedbackGate(initiallyPaused: Boolean = false) {
    private var pausedByUser = initiallyPaused

    fun onPlayIntent(
        playWhenReady: Boolean,
        userRequest: Boolean,
        foreground: Boolean,
        matchingEpisode: Boolean,
        enabled: Boolean,
    ): Boolean {
        if (!matchingEpisode || !enabled) {
            reset()
            return false
        }
        // Background pause is an engine operation. Keep an existing user pause, but
        // never arm feedback for that operation or its automatic foreground resume.
        if (!foreground) {
            if (playWhenReady) reset()
            return false
        }
        if (!userRequest) {
            reset()
            return false
        }
        if (!playWhenReady) {
            pausedByUser = true
            return false
        }
        val resume = pausedByUser
        reset()
        return resume
    }

    fun reset() { pausedByUser = false }
}
