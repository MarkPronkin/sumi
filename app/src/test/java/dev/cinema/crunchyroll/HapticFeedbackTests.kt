package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.design.CinemaHaptic
import dev.cinema.crunchyroll.design.CinemaHaptics
import dev.cinema.crunchyroll.design.TimelineHapticGate
import org.junit.Assert.*
import org.junit.Test

class HapticFeedbackTests {
    @Test fun `a drag ticks at whole seconds in either direction but not within a second`() {
        val gate = TimelineHapticGate()
        gate.begin(900)
        assertFalse(gate.update(999, 0, true))
        assertTrue(gate.update(1000, 100, true))
        assertFalse(gate.update(1999, 200, true))
        assertTrue(gate.update(2000, 300, true))
        assertTrue(gate.update(1999, 400, true))
        assertFalse(gate.update(1000, 500, true))
        assertTrue(gate.update(999, 600, true))
    }

    @Test fun `large jumps emit a single tick and never replay missed seconds`() {
        val gate = TimelineHapticGate()
        gate.begin(0)
        assertTrue(gate.update(3_600_000, 0, true))
        assertFalse(gate.update(3_600_001, 500, true))
        assertTrue(gate.update(0, 600, true))
        assertFalse(gate.update(0, 1200, true))
    }

    @Test fun `rapid scrubbing coalesces pulses and drops rather than queues crossings`() {
        val gate = TimelineHapticGate()
        gate.begin(0)
        assertTrue(gate.update(1000, 0, true))
        assertFalse(gate.update(2000, 10, true))
        assertFalse(gate.update(100_000, 20, true))
        assertFalse(gate.update(100_000, 80, true))
        assertTrue(gate.update(101_000, 81, true))
        assertFalse(gate.update(102_000, 130, true))
        assertTrue(gate.update(103_000, 131, true))
    }

    @Test fun `a long rapid drag has a bounded pulse rate`() {
        val gate = TimelineHapticGate()
        gate.begin(0)
        val pulses = (1L..2000L).count { gate.update(it * 1000, it, true) }
        assertEquals(40, pulses)
        assertFalse(gate.update(2_000_000, 10_000, true))
    }

    @Test fun `playback position updates outside a drag never tick`() {
        val gate = TimelineHapticGate()
        assertFalse(gate.update(1000, 0, true))
        assertFalse(gate.update(2000, 100, true))
        gate.begin(2000)
        assertTrue(gate.update(3000, 200, true))
        gate.reset()
        assertFalse(gate.update(4000, 300, true))
    }

    @Test fun `finish or cancellation clears position and cooldown for a fresh drag`() {
        val gate = TimelineHapticGate()
        gate.begin(0)
        assertTrue(gate.update(1000, 100, true))
        gate.reset()
        assertFalse(gate.update(9000, 101, true))
        gate.begin(8000)
        assertFalse(gate.update(8999, 102, true))
        assertTrue(gate.update(9000, 103, true))
    }

    @Test fun `a new episode starts from its own position with no inherited cooldown`() {
        val gate = TimelineHapticGate()
        gate.begin(3_000_000)
        assertTrue(gate.update(3_001_000, 100, true))
        gate.begin(0)
        assertFalse(gate.update(999, 101, true))
        assertTrue(gate.update(1000, 102, true))
    }

    @Test fun `disabled feedback discards crossings and enabling does not catch up`() {
        val gate = TimelineHapticGate()
        gate.begin(0)
        assertFalse(gate.update(1000, 0, false))
        assertFalse(gate.update(20_000, 100, false))
        assertFalse(gate.update(20_999, 200, true))
        assertTrue(gate.update(21_000, 300, true))
    }

    @Test fun `profile preference changes apply immediately to every effect`() {
        var enabled = false
        val effects = mutableListOf<CinemaHaptic>()
        val haptics = CinemaHaptics({ enabled }, { false }) { effects += it }
        CinemaHaptic.entries.forEach(haptics::perform)
        assertTrue(effects.isEmpty())
        enabled = true
        haptics.perform(CinemaHaptic.SeriesOpen)
        enabled = false
        haptics.perform(CinemaHaptic.TimelineTick)
        assertEquals(listOf(CinemaHaptic.SeriesOpen), effects)
    }

    @Test fun `television suppresses all effects even when the profile enables haptics`() {
        var television = true
        val effects = mutableListOf<CinemaHaptic>()
        val haptics = CinemaHaptics({ true }, { television }) { effects += it }
        CinemaHaptic.entries.forEach(haptics::perform)
        assertFalse(haptics.enabled)
        assertTrue(effects.isEmpty())
        television = false
        haptics.perform(CinemaHaptic.TimelineTick)
        assertEquals(listOf(CinemaHaptic.TimelineTick), effects)
    }
}
