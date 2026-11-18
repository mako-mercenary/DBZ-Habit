package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.DayOutcome.BROKEN
import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FighterRulesTest {

    @Test
    fun `a new fighter is level 1 and levels follow the curve`() {
        assertEquals(1, FighterRules.level(GameConfig.STARTING_POWER_LEVEL.toLong()))
        assertEquals(1, FighterRules.level(15))
        assertEquals(2, FighterRules.level(16))
        assertEquals(10, FighterRules.level(1296))
        assertEquals(9, FighterRules.level(1295))
        // About a year of training.
        assertEquals(50, FighterRules.level(38_416))
    }

    @Test
    fun `level and the power it needs agree everywhere and level is capped`() {
        for (level in 1..GameConfig.MAX_LEVEL) {
            val power = FighterRules.powerFor(level)
            assertEquals("level $level", level, FighterRules.level(power))
            if (level > 1) assertEquals("just below level $level", level - 1, FighterRules.level(power - 1))
        }
        assertEquals(GameConfig.MAX_LEVEL, FighterRules.level(Long.MAX_VALUE / 2))
        assertEquals(1, FighterRules.level(-50))
    }

    @Test
    fun `stats grow with level and never pass the cap`() {
        assertEquals(GameConfig.STAT_BASE, FighterRules.baseStat(1))
        assertTrue(FighterRules.baseStat(50) > FighterRules.baseStat(10))
        assertTrue(FighterRules.baseStat(GameConfig.MAX_LEVEL) <= GameConfig.STAT_CAP)
        val maxed = FighterRules.sheet(Long.MAX_VALUE / 2, Mentor.entries.associateWith { 10 })
        assertTrue(maxed.stats.values.all { it.total <= GameConfig.STAT_CAP })
        assertTrue(maxed.maxHp <= GameConfig.HP_CAP)
        assertTrue(maxed.maxKi <= GameConfig.KI_CAP)
        assertNull(maxed.nextLevelPower)
        assertEquals(1f, maxed.levelProgress)
    }

    @Test
    fun `each mentor's bond raises the stats they train`() {
        val gokuFive = mapOf(Mentor.GOKU to 5)
        assertEquals(4 * GameConfig.BOND_STAT_BONUS_PER_LEVEL, FighterRules.bondBonus(Stat.STRENGTH, gokuFive))
        assertEquals(0, FighterRules.bondBonus(Stat.SPEED, gokuFive))
        val piccolo = mapOf(Mentor.PICCOLO to 3)
        assertEquals(2 * GameConfig.BOND_STAT_BONUS_PER_LEVEL, FighterRules.bondBonus(Stat.KI, piccolo))
        assertEquals(2 * GameConfig.BOND_STAT_BONUS_PER_LEVEL, FighterRules.bondBonus(Stat.SPIRIT, piccolo))
        // A mentor with no bond yet adds nothing.
        assertEquals(0, FighterRules.bondBonus(Stat.VITALITY, emptyMap()))
        // Every stat has a mentor who raises it.
        assertEquals(Stat.entries.toSet(), Mentor.entries.flatMap { it.stats }.toSet())
    }

    @Test
    fun `vitality raises HP and the ki stat raises ki`() {
        val plain = FighterRules.sheet(2000, emptyMap())
        val roshi = FighterRules.sheet(2000, mapOf(Mentor.ROSHI to 6))
        val piccolo = FighterRules.sheet(2000, mapOf(Mentor.PICCOLO to 6))
        assertEquals(5 * GameConfig.BOND_STAT_BONUS_PER_LEVEL * GameConfig.HP_PER_VITALITY, roshi.maxHp - plain.maxHp)
        assertEquals(5 * GameConfig.BOND_STAT_BONUS_PER_LEVEL * GameConfig.KI_PER_KI_STAT, piccolo.maxKi - plain.maxKi)
    }

    @Test
    fun `level progress runs from the start of the level to the next`() {
        val start = FighterRules.powerFor(10)
        val next = FighterRules.powerFor(11)
        assertEquals(0f, FighterRules.sheet(start, emptyMap()).levelProgress)
        assertEquals(0.5f, FighterRules.sheet((start + next) / 2, emptyMap()).levelProgress, 0.01f)
    }

    // ---- Zeni ----

    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    @Test
    fun `zeni pays for every point of power and for perfect days and weeks`() {
        assertEquals(
            1000L * GameConfig.ZENI_PER_POWER_POINT + 3 * GameConfig.ZENI_PERFECT_DAY + GameConfig.ZENI_PERFECT_WEEK,
            ZeniRules.earned(totalPoints = 1000, perfectDays = 3, perfectWeeks = 1),
        )
        assertEquals(0, ZeniRules.earned(-5, 0, 0))
    }

    @Test
    fun `perfect weeks are whole Monday-to-Sunday weeks with nothing missed`() {
        fun week(start: Long, vararg outcomes: DayOutcome) = outcomes.mapIndexed { i, o -> PastDay(start + i, 1, if (o == PERFECT) 1 else 0, o) }
        val perfect = week(monday, PERFECT, PERFECT, REST, PERFECT, PERFECT, PERFECT, REST)
        val broken = week(monday + 7, PERFECT, BROKEN, PERFECT, PERFECT, PERFECT, PERFECT, PERFECT)
        val partial = week(monday + 14, PERFECT, PERFECT, PERFECT)
        assertEquals(1, ZeniRules.perfectWeeks(perfect + broken + partial))
    }

    @Test
    fun `the balance adds the ledger and never shows below zero`() {
        assertEquals(700, ZeniRules.balance(1000, -300))
        assertEquals(1500, ZeniRules.balance(1000, 500))
        assertEquals(0, ZeniRules.balance(100, -300))
    }
}
