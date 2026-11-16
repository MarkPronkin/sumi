package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.playback.relativeSeekPosition
import org.junit.Assert.*
import org.junit.Test

class SkipIntervalTests {
    @Test fun `old profiles and invalid preferences use ten seconds`() {
        assertEquals(10, ProfileData().skipIntervalSeconds)
        listOf(null, Int.MIN_VALUE, -10, 0, 1, 9, 11, 31, Int.MAX_VALUE).forEach {
            assertEquals(10, SkipInterval.seconds(it))
            assertEquals(10_000L, SkipInterval.millis(it))
        }
    }

    @Test fun `each supported interval keeps its value and converts to milliseconds`() {
        assertEquals(listOf(5, 10, 15, 30), SkipInterval.choices)
        SkipInterval.choices.forEach {
            assertEquals(it, SkipInterval.seconds(it))
            assertEquals(it * 1_000L, SkipInterval.millis(it))
        }
    }

    @Test fun `skip preferences remain isolated between demo and connected profiles`() {
        val connected = "account:fixture:primary"
        val otherConnected = "account:fixture:guest"
        val state = LocalState().updateProfile("main") {it.copy(skipIntervalSeconds=5)}
            .updateProfile("guest") {it.copy(skipIntervalSeconds=15)}
            .updateProfile(connected) {it.copy(skipIntervalSeconds=30)}
            .updateProfile(otherConnected) {it.copy(skipIntervalSeconds=10)}
        assertEquals(5, state.profiles.getValue("main").skipIntervalSeconds)
        assertEquals(15, state.profiles.getValue("guest").skipIntervalSeconds)
        assertEquals(30, state.profiles.getValue(connected).skipIntervalSeconds)
        assertEquals(10, state.profiles.getValue(otherConnected).skipIntervalSeconds)
        assertEquals(5, state.copy(activeProfile="main").current.skipIntervalSeconds)
    }

    @Test fun `repeated skips accumulate from the latest position and clamp at both ends`() {
        var position = 25_000L
        repeat(3) {position = relativeSeekPosition(position, 10_000, 60_000)}
        assertEquals(55_000L, position)
        repeat(3) {position = relativeSeekPosition(position, 10_000, 60_000)}
        assertEquals(60_000L, position)
        repeat(10) {position = relativeSeekPosition(position, -10_000, 60_000)}
        assertEquals(0L, position)
    }

    @Test fun `unknown duration and out of range positions never produce invalid seek targets`() {
        assertEquals(0L, relativeSeekPosition(30_000, 10_000, 0))
        assertEquals(0L, relativeSeekPosition(30_000, -10_000, Long.MIN_VALUE))
        assertEquals(10_000L, relativeSeekPosition(-1, 10_000, 60_000))
        assertEquals(50_000L, relativeSeekPosition(90_000, -10_000, 60_000))
        assertEquals(60_000L, relativeSeekPosition(90_000, 10_000, 60_000))
    }

    @Test fun `extreme positive and negative deltas saturate without integer overflow`() {
        assertEquals(Long.MAX_VALUE, relativeSeekPosition(Long.MAX_VALUE - 5, 10_000, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, relativeSeekPosition(25, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, relativeSeekPosition(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(0L, relativeSeekPosition(25, Long.MIN_VALUE, 60_000))
        assertEquals(25L, relativeSeekPosition(25, 0, 60_000))
    }
}
