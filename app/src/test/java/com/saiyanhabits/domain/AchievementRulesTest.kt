package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementRulesTest {

    @Test
    fun `a fresh start has nothing unlocked and the first title`() {
        val stats = AchievementStats()
        assertTrue(AchievementRules.unlocked(stats, emptySet()).isEmpty())
        assertEquals(GameConfig.TITLES.first().second, AchievementRules.title(0))
    }

    @Test
    fun `achievements unlock at their targets`() {
        assertFalse(Achievement.WARMED_UP.isUnlocked(AchievementStats(checkOffs = 49)))
        assertTrue(Achievement.WARMED_UP.isUnlocked(AchievementStats(checkOffs = 50)))
        assertTrue(Achievement.SCOUTER_BREAKER.isUnlocked(AchievementStats(powerLevel = 9001)))
        assertFalse(Achievement.SCOUTER_BREAKER.isUnlocked(AchievementStats(powerLevel = 9000)))
        assertEquals(0.5f, Achievement.WARMED_UP.fraction(AchievementStats(checkOffs = 25)))
        assertEquals(1f, Achievement.WARMED_UP.fraction(AchievementStats(checkOffs = 500)))
    }

    @Test
    fun `every kind of boss needs each one, not just six fights`() {
        val sixRaditz = AchievementStats(bossesDefeated = List(Boss.entries.size) { Boss.RADITZ })
        assertTrue(Achievement.FIVE_BOSSES.isUnlocked(sixRaditz))
        assertFalse(Achievement.EVERY_BOSS.isUnlocked(sixRaditz))
        assertTrue(Achievement.EVERY_BOSS.isUnlocked(AchievementStats(bossesDefeated = Boss.entries.toList())))
    }

    @Test
    fun `bond achievements count mentors, and a mentor with no bond is level 1`() {
        val oneAtFive = AchievementStats(bondLevels = mapOf(Mentor.GOKU to 5))
        assertTrue(Achievement.FIRST_ASSIST.isUnlocked(oneAtFive))
        assertFalse(Achievement.WHOLE_TEAM.isUnlocked(oneAtFive))
        assertTrue(Achievement.WHOLE_TEAM.isUnlocked(AchievementStats(bondLevels = Mentor.entries.associateWith { 6 })))
        assertFalse(Achievement.ALL_MASTERED.isUnlocked(AchievementStats(bondLevels = Mentor.entries.associateWith { 6 })))
    }

    @Test
    fun `an achievement seen before stays unlocked after an undo`() {
        val stats = AchievementStats(checkOffs = 0)
        assertEquals(setOf(Achievement.FIRST_STEP), AchievementRules.unlocked(stats, setOf("FIRST_STEP", "NOT_A_THING")))
    }

    @Test
    fun `titles climb with the count and the top one needs everything`() {
        val all = Achievement.entries.size
        assertEquals(GameConfig.TOP_TITLE, AchievementRules.title(all))
        assertEquals(GameConfig.TITLES.last().second, AchievementRules.title(all - 1))
        assertNull(AchievementRules.nextTitle(all))
        assertEquals(GameConfig.TITLES[1], AchievementRules.nextTitle(0))
        // Titles are in order and each needs more than the last.
        val needs = AchievementRules.titles.map { it.first }
        assertEquals(needs.sorted(), needs)
        assertEquals(needs.distinct(), needs)
    }

    @Test
    fun `achievement titles are unique and every group is used`() {
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.title }.distinct().size)
        assertEquals(AchievementGroup.entries.toSet(), Achievement.entries.map { it.group }.toSet())
        assertTrue(Achievement.entries.all { it.target > 0 })
    }
}
