package com.saiyanhabits.domain

import com.saiyanhabits.domain.games.Fish
import com.saiyanhabits.domain.games.Fishing
import com.saiyanhabits.domain.games.FishingPhase
import com.saiyanhabits.domain.games.FishingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FishingTest {

    private fun hooked(fish: Fish = Fish.TROUT) =
        FishingState(phase = FishingPhase.REELING, fish = fish, kg = fish.minKg, distance = 1f, tension = 0.35f)

    @Test
    fun `charge, cast, wait, bite`() {
        val r = Random(3)
        var s = Fishing.startCharging(FishingState())
        assertEquals(FishingPhase.CHARGING, s.phase)
        s = Fishing.step(s, 0.5f, false, r)
        assertTrue(s.castPower > 0f)
        s = Fishing.cast(s, r)
        assertEquals(FishingPhase.WAITING, s.phase)
        assertTrue(s.timer in Fishing.MIN_WAIT..Fishing.MAX_WAIT)
        s = Fishing.step(s, Fishing.MAX_WAIT + 0.1f, false, r)
        assertEquals(FishingPhase.BITE, s.phase)
    }

    @Test
    fun `striking too early spooks, striking in time hooks, too late escapes`() {
        val r = Random(4)
        assertEquals(FishingPhase.SPOOKED, Fishing.strike(FishingState(phase = FishingPhase.WAITING, timer = 3f), r).phase)
        val bite = FishingState(phase = FishingPhase.BITE, timer = Fishing.STRIKE_WINDOW, castPower = 0.5f)
        val hooked = Fishing.strike(bite, r)
        assertEquals(FishingPhase.REELING, hooked.phase)
        val fish = hooked.fish!!
        assertTrue(hooked.kg in fish.minKg..fish.maxKg)
        assertEquals(FishingPhase.ESCAPED, Fishing.step(bite, Fishing.STRIKE_WINDOW + 0.1f, false, r).phase)
    }

    @Test
    fun `the power bar sweeps up and back down`() {
        val r = Random(1)
        var s = Fishing.startCharging(FishingState())
        s = Fishing.step(s, 1f / Fishing.CHARGE_RATE, false, r)
        assertEquals(1f, s.castPower, 0.01f)
        s = Fishing.step(s, 0.5f / Fishing.CHARGE_RATE, false, r)
        assertEquals(0.5f, s.castPower, 0.01f)
    }

    @Test
    fun `holding the reel forever snaps the line, letting go forever loses the fish`() {
        val r = Random(5)
        var held = hooked(Fish.PIKE)
        repeat(400) { if (held.phase == FishingPhase.REELING) held = Fishing.step(held, 0.05f, true, r) }
        assertTrue(held.phase == FishingPhase.SNAPPED || held.phase == FishingPhase.CAUGHT)
        var slack = hooked()
        repeat(400) { if (slack.phase == FishingPhase.REELING) slack = Fishing.step(slack, 0.05f, false, r) }
        assertEquals(FishingPhase.ESCAPED, slack.phase)
    }

    @Test
    fun `pumping the reel to keep the tension in the middle lands the fish`() {
        val r = Random(6)
        var s = hooked(Fish.BASS)
        var steps = 0
        while (s.phase == FishingPhase.REELING && steps < 5000) {
            s = Fishing.step(s, 0.05f, s.tension < 0.6f, r)
            steps++
        }
        assertEquals(FishingPhase.CAUGHT, s.phase)
    }

    @Test
    fun `long casts bring bigger fish more often and every fish can bite`() {
        fun share(power: Float) = (1..4000).count { Fishing.pick(power, Random(it)).rarity >= com.saiyanhabits.domain.games.Rarity.RARE }
        assertTrue(share(1f) > share(0.1f))
        val seen = (1..20000).map { Fishing.pick(1f, Random(it)) }.toSet()
        assertEquals(Fish.entries.toSet(), seen)
    }
}
