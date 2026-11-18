package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniGameRulesTest {

    @Test
    fun `games give bond XP up to the daily cap and no further`() {
        assertEquals(GameConfig.MINIGAME_BOND_XP_PER_PLAY, MiniGameRules.bondXp(0))
        var earned = 0
        repeat(10) { earned += MiniGameRules.bondXp(earned) }
        assertEquals(GameConfig.MINIGAME_DAILY_BOND_XP_CAP, earned)
        assertEquals(0, MiniGameRules.bondXp(GameConfig.MINIGAME_DAILY_BOND_XP_CAP + 5))
    }

    @Test
    fun `every mentor has exactly one game`() {
        assertEquals(Mentor.entries.toSet(), MiniGame.entries.map { it.mentor }.toSet())
        assertEquals(Mentor.entries.size, MiniGame.entries.size)
        for (mentor in Mentor.entries) assertEquals(mentor, MiniGame.of(mentor).mentor)
    }
}
