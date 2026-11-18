package com.saiyanhabits.domain

import com.saiyanhabits.domain.games.BreathPattern
import com.saiyanhabits.domain.games.BreathPhase
import com.saiyanhabits.domain.games.Breathing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingTest {

    @Test
    fun `box breathing goes in, hold, out, hold, four seconds each`() {
        val p = BreathPattern.BOX
        assertEquals(16, p.cycleSeconds)
        assertEquals(BreathPhase.IN, Breathing.momentAt(p, 0f).phase)
        assertEquals(BreathPhase.HOLD_IN, Breathing.momentAt(p, 4.5f).phase)
        assertEquals(BreathPhase.OUT, Breathing.momentAt(p, 9f).phase)
        assertEquals(BreathPhase.HOLD_OUT, Breathing.momentAt(p, 13f).phase)
        assertEquals(BreathPhase.IN, Breathing.momentAt(p, 16.5f).phase)
        assertEquals(1, Breathing.momentAt(p, 16.5f).breaths)
    }

    @Test
    fun `the aura fills on the way in and empties on the way out`() {
        val p = BreathPattern.RELAX
        assertEquals(0f, Breathing.momentAt(p, 0f).fullness, 0.001f)
        assertEquals(0.5f, Breathing.momentAt(p, 2f).fullness, 0.001f)
        assertEquals(1f, Breathing.momentAt(p, 6f).fullness, 0.001f)
        assertEquals(0.5f, Breathing.momentAt(p, 15f).fullness, 0.001f)
        assertTrue(Breathing.momentAt(p, 18.9f).fullness < 0.01f)
    }

    @Test
    fun `seconds left count down within a phase and patterns skip unused phases`() {
        assertEquals(4, Breathing.momentAt(BreathPattern.CALM, 0f).phaseSecondsLeft)
        assertEquals(1, Breathing.momentAt(BreathPattern.CALM, 3.2f).phaseSecondsLeft)
        assertEquals(BreathPhase.OUT, Breathing.momentAt(BreathPattern.CALM, 4f).phase)
        for (pattern in BreathPattern.entries) {
            for (tenth in 0..2000) {
                val m = Breathing.momentAt(pattern, tenth / 10f)
                assertTrue(m.fullness in 0f..1f)
                assertTrue(m.phaseProgress in 0f..1f)
            }
        }
    }
}
