package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BondRulesTest {

    private fun levels(vararg pairs: Pair<Mentor, Int>): Map<Mentor, Int> =
        Mentor.entries.associateWith { 1 } + pairs.toMap()

    // ---- Bond levels ----

    @Test
    fun `bonds run from level 1 to level 10`() {
        assertEquals(10, BondRules.MAX_LEVEL)
        assertEquals(1, BondRules.levelForXp(0))
        assertEquals(10, BondRules.levelForXp(1_000_000))
    }

    @Test
    fun `a level is reached exactly at its XP threshold`() {
        for (level in 2..BondRules.MAX_LEVEL) {
            val threshold = BondRules.xpForLevel(level)
            assertEquals(level - 1, BondRules.levelForXp(threshold - 1))
            assertEquals(level, BondRules.levelForXp(threshold))
        }
    }

    @Test
    fun `each level needs more XP than the one before`() {
        val thresholds = GameConfig.BOND_LEVEL_XP
        assertEquals(0, thresholds.first())
        assertTrue(thresholds.zipWithNext().all { (lower, higher) -> higher > lower })
    }

    @Test
    fun `a check-off gives ten bond XP, so level 2 takes three`() {
        assertEquals(10, GameConfig.BOND_XP_PER_CHECK_OFF)
        assertEquals(1, BondRules.levelForXp(2 * GameConfig.BOND_XP_PER_CHECK_OFF))
        assertEquals(2, BondRules.levelForXp(3 * GameConfig.BOND_XP_PER_CHECK_OFF))
    }

    @Test
    fun `XP to the next level counts down and ends at the top`() {
        assertEquals(30, BondRules.xpToNextLevel(0))
        assertEquals(10, BondRules.xpToNextLevel(20))
        assertEquals(50, BondRules.xpToNextLevel(30))
        assertNull(BondRules.xpToNextLevel(BondRules.xpForLevel(BondRules.MAX_LEVEL)))
    }

    @Test
    fun `progress within a level runs from 0 to 1`() {
        assertEquals(0f, BondRules.progressInLevel(0), 0f)
        assertEquals(0.5f, BondRules.progressInLevel(15), 0.001f)
        assertEquals(0f, BondRules.progressInLevel(30), 0f)
        assertEquals(1f, BondRules.progressInLevel(BondRules.xpForLevel(BondRules.MAX_LEVEL)), 0f)
    }

    // ---- Scenes ----

    @Test
    fun `each bond level from 2 up unlocks its scene`() {
        assertFalse(BondRules.isSceneUnlocked(sceneLevel = 2, bondLevel = 1))
        assertTrue(BondRules.isSceneUnlocked(sceneLevel = 2, bondLevel = 2))
        assertTrue(BondRules.isSceneUnlocked(sceneLevel = 2, bondLevel = 7))
        assertFalse(BondRules.isSceneUnlocked(sceneLevel = 8, bondLevel = 7))
        assertFalse(BondRules.isSceneUnlocked(sceneLevel = 1, bondLevel = 10))
    }

    // ---- Techniques ----

    @Test
    fun `every mentor has a technique at bond levels 3, 6 and 9`() {
        assertEquals(listOf(3, 6, 9), GameConfig.TECHNIQUE_BOND_LEVELS)
        for (mentor in Mentor.entries) {
            assertEquals(GameConfig.TECHNIQUE_BOND_LEVELS, Technique.of(mentor).map { it.bondLevel })
        }
        assertEquals(15, Technique.entries.size)
    }

    @Test
    fun `every technique has a title and says what it does`() {
        for (technique in Technique.entries) {
            assertTrue(technique.title.isNotBlank())
            assertTrue(technique.description.length > 20)
        }
        assertEquals(Technique.entries.size, Technique.entries.map { it.title }.toSet().size)
    }

    @Test
    fun `techniques unlock when their mentor's bond reaches the level`() {
        assertTrue(BondRules.unlockedTechniques(levels()).isEmpty())
        assertEquals(setOf(Technique.ONE_MORE_REP), BondRules.unlockedTechniques(levels(Mentor.GOKU to 3)))
        assertEquals(
            setOf(Technique.ONE_MORE_REP, Technique.KAIOKEN, Technique.TURTLE_BASICS),
            BondRules.unlockedTechniques(levels(Mentor.GOKU to 8, Mentor.ROSHI to 5)),
        )
        assertEquals(15, BondRules.unlockedTechniques(Mentor.entries.associateWith { 10 }).size)
    }

    @Test
    fun `a level-up reports the technique it unlocks`() {
        assertEquals(Technique.KAIOKEN, BondRules.techniqueUnlockedAt(Mentor.GOKU, 6))
        assertEquals(Technique.FINAL_FLASH, BondRules.techniqueUnlockedAt(Mentor.VEGETA, 9))
        assertNull(BondRules.techniqueUnlockedAt(Mentor.GOKU, 5))
    }

    @Test
    fun `only Kaio-ken and Master's Day Off are used by hand`() {
        assertEquals(setOf(Technique.KAIOKEN, Technique.DAY_OFF), Technique.entries.filter { it.isActivated }.toSet())
    }

    @Test
    fun `an activated technique is ready until used, then cools down`() {
        val day = 1000L
        assertEquals(0, BondRules.cooldownDaysLeft(Technique.KAIOKEN, lastUsedEpochDay = null, todayEpochDay = day))
        assertEquals(7, BondRules.cooldownDaysLeft(Technique.KAIOKEN, lastUsedEpochDay = day, todayEpochDay = day))
        assertEquals(1, BondRules.cooldownDaysLeft(Technique.KAIOKEN, day, day + 6))
        assertEquals(0, BondRules.cooldownDaysLeft(Technique.KAIOKEN, day, day + 7))
        assertEquals(0, BondRules.cooldownDaysLeft(Technique.KAIOKEN, day, day + 100))
        assertEquals(14, BondRules.cooldownDaysLeft(Technique.DAY_OFF, day, day))
    }

    @Test
    fun `always-on techniques have no cooldown`() {
        assertEquals(0, BondRules.cooldownDaysLeft(Technique.SENZU_POUCH, lastUsedEpochDay = 5, todayEpochDay = 5))
    }

    // ---- Assists ----

    @Test
    fun `mentors join boss fights from bond level 5`() {
        assertEquals(5, GameConfig.ASSIST_MIN_BOND_LEVEL)
        assertTrue(BondRules.assistMentors(levels(Mentor.GOKU to 4)).isEmpty())
        assertEquals(
            listOf(Mentor.GOKU, Mentor.VEGETA),
            BondRules.assistMentors(levels(Mentor.VEGETA to 9, Mentor.GOKU to 5, Mentor.BULMA to 4)),
        )
    }

    // ---- Perks ----

    @Test
    fun `with no perks the numbers are the plain ones`() {
        val none = Perks.NONE
        assertEquals(20, PowerCalculator.completionPoints(2, Gravity.NORMAL, Mentor.GOKU, none))
        assertEquals(
            PowerCalculator.completionPoints(3, Gravity.X100),
            PowerCalculator.completionPoints(3, Gravity.X100, Mentor.VEGETA, none),
        )
        assertEquals(3, none.beanCapacity)
        assertEquals(7, none.beanStreakInterval)
        assertEquals(1.5, none.focusMultiplier, 0.0)
        assertEquals(0, none.assistDamage)
        assertEquals(1, none.bombThrowMultiplier)
    }

    @Test
    fun `a mentor's level 3 technique boosts only that mentor's habits`() {
        val perks = Perks(techniques = setOf(Technique.ONE_MORE_REP))
        assertEquals(24, PowerCalculator.completionPoints(2, Gravity.NORMAL, Mentor.GOKU, perks))
        assertEquals(20, PowerCalculator.completionPoints(2, Gravity.NORMAL, Mentor.ROSHI, perks))
    }

    @Test
    fun `Saiyan Pride boosts the hardest habit by 30 percent`() {
        val perks = Perks(techniques = setOf(Technique.SAIYAN_PRIDE))
        assertEquals(39, PowerCalculator.completionPoints(3, Gravity.NORMAL, Mentor.VEGETA, perks))
    }

    @Test
    fun `Gravity Chamber makes 100x gravity pay four times`() {
        val perks = Perks(techniques = setOf(Technique.GRAVITY_CHAMBER))
        assertEquals(40, PowerCalculator.completionPoints(1, Gravity.X100, Mentor.ROSHI, perks))
        assertEquals(20, PowerCalculator.completionPoints(1, Gravity.X10, Mentor.ROSHI, perks))
        assertEquals(10, PowerCalculator.completionPoints(1, Gravity.NORMAL, Mentor.ROSHI, perks))
    }

    @Test
    fun `Kaio-ken doubles check-off points on the day it is used`() {
        val active = Perks(techniques = setOf(Technique.KAIOKEN), doublePointsToday = true)
        val idle = Perks(techniques = setOf(Technique.KAIOKEN), doublePointsToday = false)
        assertEquals(60, PowerCalculator.completionPoints(3, Gravity.NORMAL, Mentor.BULMA, active))
        assertEquals(30, PowerCalculator.completionPoints(3, Gravity.NORMAL, Mentor.BULMA, idle))
    }

    @Test
    fun `perks stack on one check-off`() {
        val perks = Perks(
            techniques = setOf(Technique.SAIYAN_PRIDE, Technique.GRAVITY_CHAMBER),
            doublePointsToday = true,
        )
        // 10 x difficulty 3 x gravity 4 = 120, +30% = 156, doubled = 312.
        assertEquals(312, PowerCalculator.completionPoints(3, Gravity.X100, Mentor.VEGETA, perks))
    }

    @Test
    fun `Senzu Pouch adds a bean slot and Regeneration shortens the wait`() {
        assertEquals(4, Perks(techniques = setOf(Technique.SENZU_POUCH)).beanCapacity)
        assertEquals(5, Perks(techniques = setOf(Technique.REGENERATION)).beanStreakInterval)
    }

    @Test
    fun `Deep Meditation doubles time chamber points`() {
        val perks = Perks(techniques = setOf(Technique.DEEP_MEDITATION))
        assertEquals(2.0, perks.focusMultiplier, 0.0)
        assertEquals(20, PowerCalculator.focusBonus(20, perks.focusMultiplier))
        assertEquals(10, PowerCalculator.focusBonus(20))
    }

    @Test
    fun `Capsule Tech and Dragon Radar boost saga payouts`() {
        val capsule = Perks(techniques = setOf(Technique.CAPSULE_TECH))
        assertEquals(25, SagaRules.milestonePoints(weight = 2, charged = false, perks = capsule))
        val radar = Perks(techniques = setOf(Technique.DRAGON_RADAR))
        assertEquals(450, SagaRules.victoryBonus(totalHp = 10, boostPercent = radar.victoryBoostPercent))
    }

    @Test
    fun `Spirit Bomb Mastery doubles the throw bonus`() {
        val perks = Perks(techniques = setOf(Technique.SPIRIT_BOMB_MASTERY))
        assertEquals(250, SpiritBombRules.throwBonus(size = 2, multiplier = perks.bombThrowMultiplier))
        assertEquals(125, SpiritBombRules.throwBonus(size = 2))
    }

    @Test
    fun `Final Flash doubles every assist`() {
        val assists = listOf(Mentor.GOKU, Mentor.PICCOLO, Mentor.VEGETA)
        assertEquals(3, Perks(assistMentors = assists).assistDamage)
        assertEquals(6, Perks(techniques = setOf(Technique.FINAL_FLASH), assistMentors = assists).assistDamage)
    }

    @Test
    fun `perks are built from bond levels`() {
        val perks = Perks.from(levels(Mentor.GOKU to 6, Mentor.ROSHI to 5), doublePointsToday = false)
        assertEquals(setOf(Technique.ONE_MORE_REP, Technique.KAIOKEN, Technique.TURTLE_BASICS), perks.techniques)
        assertEquals(listOf(Mentor.ROSHI, Mentor.GOKU), perks.assistMentors)
        assertFalse(perks.doublePointsToday)
    }
}
