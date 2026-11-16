package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.playback.PlaybackFeedbackGate
import org.junit.Assert.*
import org.junit.Test

class PlaybackFeedbackTests {
    private fun PlaybackFeedbackGate.intent(
        play: Boolean,
        user: Boolean = true,
        foreground: Boolean = true,
        matchingEpisode: Boolean = true,
        enabled: Boolean = true,
    ) = onPlayIntent(play, user, foreground, matchingEpisode, enabled)

    @Test fun `initial play is covered by entry and only explicit pause resume adds feedback`() {
        val gate = PlaybackFeedbackGate()
        assertFalse(gate.intent(true))
        assertFalse(gate.intent(false))
        assertTrue(gate.intent(true))
        assertFalse(gate.intent(true))
        assertFalse(gate.intent(false))
        assertTrue(gate.intent(true))
    }

    @Test fun `buffering and repeated play intent cannot replay a consumed resume`() {
        val gate = PlaybackFeedbackGate(initiallyPaused = true)
        assertTrue(gate.intent(true))
        // Buffering and seek recovery change isPlaying, but leave playWhenReady true.
        repeat(20) { assertFalse(gate.intent(true)) }
    }

    @Test fun `background pause and automatic foreground resume do not arm feedback`() {
        val gate = PlaybackFeedbackGate()
        assertFalse(gate.intent(true))
        assertFalse(gate.intent(false, foreground = false))
        assertFalse(gate.intent(true))
        // A genuine user pause survives a background round trip that remains paused.
        assertFalse(gate.intent(false))
        assertFalse(gate.intent(false, foreground = false))
        assertTrue(gate.intent(true))
    }

    @Test fun `automatic restore before resumed lifecycle clears an inferred pause`() {
        val gate = PlaybackFeedbackGate(initiallyPaused = true)
        assertFalse(gate.intent(true, foreground = false))
        assertFalse(gate.intent(true))
    }

    @Test fun `focus changes episode replacement and errors discard pending feedback`() {
        for (cancel in listOf<(PlaybackFeedbackGate) -> Unit>(
            { it.intent(false, user = false) },
            { it.intent(false, matchingEpisode = false) },
            { it.intent(false, enabled = false) },
            { it.reset() },
        )) {
            val gate = PlaybackFeedbackGate(initiallyPaused = true)
            cancel(gate)
            assertFalse(gate.intent(true))
        }
    }
}
