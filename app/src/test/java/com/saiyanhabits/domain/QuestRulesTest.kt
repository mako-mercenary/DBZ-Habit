package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestRulesTest {

    private fun open(weight: Int) = MilestoneState(weight, damageDealt = 0, done = false)
    private fun done(weight: Int) = MilestoneState(weight, damageDealt = SagaRules.damageFor(weight), done = true)

    // ---- Boss HP and damage ----

    @Test
    fun `boss HP is the total weight of the milestones`() {
        assertEquals(9, SagaRules.totalHp(listOf(open(1), open(3), open(5))))
        assertEquals(0, SagaRules.totalHp(emptyList()))
    }

    @Test
    fun `a milestone deals its weight in damage`() {
        for (weight in 1..GameConfig.MAX_MILESTONE_WEIGHT) {
            assertEquals(weight, SagaRules.damageFor(weight))
        }
    }

    @Test
    fun `finished milestones take their damage off the boss`() {
        val fight = listOf(done(3), open(2), done(1), open(4))
        assertEquals(10, SagaRules.totalHp(fight))
        assertEquals(4, SagaRules.damageTaken(fight))
        assertEquals(6, SagaRules.remainingHp(fight))
    }

    @Test
    fun `an untouched boss is at full HP`() {
        val fight = listOf(open(2), open(2))
        assertEquals(SagaRules.totalHp(fight), SagaRules.remainingHp(fight))
        assertFalse(SagaRules.isDefeated(fight))
    }

    @Test
    fun `the boss falls when the last milestone lands`() {
        assertFalse(SagaRules.isDefeated(listOf(done(3), done(2), open(1))))
        val finished = listOf(done(3), done(2), done(1))
        assertEquals(0, SagaRules.remainingHp(finished))
        assertTrue(SagaRules.isDefeated(finished))
    }

    @Test
    fun `HP never goes below zero`() {
        val overkill = listOf(MilestoneState(weight = 2, damageDealt = 10, done = true))
        assertEquals(0, SagaRules.remainingHp(overkill))
        assertTrue(SagaRules.isDefeated(overkill))
    }

    @Test
    fun `a saga with no milestones has no boss to beat`() {
        assertFalse(SagaRules.isDefeated(emptyList()))
    }

    @Test
    fun `adding a milestone mid-fight raises the boss's HP`() {
        val before = listOf(done(3), open(2))
        val after = before + open(4)
        assertEquals(2, SagaRules.remainingHp(before))
        assertEquals(6, SagaRules.remainingHp(after))
    }

    @Test
    fun `milestone weights are kept between 1 and the maximum`() {
        assertEquals(1, SagaRules.clampWeight(0))
        assertEquals(1, SagaRules.clampWeight(-3))
        assertEquals(3, SagaRules.clampWeight(3))
        assertEquals(GameConfig.MAX_MILESTONE_WEIGHT, SagaRules.clampWeight(99))
    }

    // ---- Saga points ----

    @Test
    fun `a milestone pays ten points per weight`() {
        assertEquals(10, SagaRules.milestonePoints(weight = 1, charged = false))
        assertEquals(30, SagaRules.milestonePoints(weight = 3, charged = false))
        assertEquals(50, SagaRules.milestonePoints(weight = 5, charged = false))
    }

    @Test
    fun `a milestone charged in the time chamber pays one and a half times`() {
        assertEquals(15, SagaRules.milestonePoints(weight = 1, charged = true))
        assertEquals(45, SagaRules.milestonePoints(weight = 3, charged = true))
        assertEquals(75, SagaRules.milestonePoints(weight = 5, charged = true))
    }

    @Test
    fun `the victory bonus grows with the boss's HP`() {
        assertEquals(100 + 20 * 10, SagaRules.victoryBonus(totalHp = 10))
        assertEquals(100 + 20 * 1, SagaRules.victoryBonus(totalHp = 1))
        assertTrue(SagaRules.victoryBonus(20) > SagaRules.victoryBonus(10))
    }

    @Test
    fun `beating a boss is worth more than its milestones alone`() {
        val weights = listOf(1, 2, 3, 4)
        val fromMilestones = weights.sumOf { SagaRules.milestonePoints(it, charged = false) }
        assertTrue(SagaRules.victoryBonus(weights.sum()) > fromMilestones)
    }

    // ---- Time chamber bonus ----

    @Test
    fun `the focus bonus tops points up to one and a half times`() {
        assertEquals(1.5, GameConfig.FOCUS_POINT_MULTIPLIER, 0.0)
        assertEquals(5, PowerCalculator.focusBonus(10))
        assertEquals(10, PowerCalculator.focusBonus(20))
        assertEquals(45, PowerCalculator.focusBonus(90))
        assertEquals(0, PowerCalculator.focusBonus(0))
        assertEquals(0, PowerCalculator.focusBonus(-10))
    }

    // ---- Spirit bombs ----

    @Test
    fun `bomb sizes pay 100, 250 and 500 points`() {
        assertEquals(listOf(100, 250, 500), (1..3).map { SpiritBombRules.sizePoints(it) })
        assertEquals(100, SpiritBombRules.sizePoints(0))
        assertEquals(500, SpiritBombRules.sizePoints(9))
    }

    @Test
    fun `progress pays its share of the bomb's points`() {
        // Medium bomb (250 points), target 100.
        assertEquals(25, SpiritBombRules.pointsForProgress(size = 2, target = 100, before = 0, after = 10))
        assertEquals(125, SpiritBombRules.pointsForProgress(size = 2, target = 100, before = 50, after = 100))
        assertEquals(250, SpiritBombRules.pointsForProgress(size = 2, target = 100, before = 0, after = 100))
    }

    @Test
    fun `many small logs pay the same as one big one`() {
        // Small bomb (100 points) over 300 pages: one page is a third of a point.
        var paid = 0
        for (page in 0 until 300) {
            paid += SpiritBombRules.pointsForProgress(size = 1, target = 300, before = page, after = page + 1)
        }
        assertEquals(100, paid)
        assertEquals(0, SpiritBombRules.pointsForProgress(1, 300, before = 0, after = 2))
        assertEquals(1, SpiritBombRules.pointsForProgress(1, 300, before = 0, after = 3))
    }

    @Test
    fun `progress past the target earns nothing more`() {
        assertEquals(0, SpiritBombRules.pointsForProgress(size = 1, target = 10, before = 10, after = 15))
        assertEquals(50, SpiritBombRules.pointsForProgress(size = 1, target = 10, before = 5, after = 99))
    }

    @Test
    fun `a log is cut off at the target`() {
        assertEquals(5, SpiritBombRules.applicableAmount(amount = 5, progress = 10, target = 100))
        assertEquals(3, SpiritBombRules.applicableAmount(amount = 5, progress = 97, target = 100))
        assertEquals(0, SpiritBombRules.applicableAmount(amount = 5, progress = 100, target = 100))
        assertEquals(0, SpiritBombRules.applicableAmount(amount = -4, progress = 10, target = 100))
    }

    @Test
    fun `the bomb grows from empty to full`() {
        assertEquals(0f, SpiritBombRules.fraction(0, 200), 0f)
        assertEquals(0.25f, SpiritBombRules.fraction(50, 200), 0f)
        assertEquals(1f, SpiritBombRules.fraction(200, 200), 0f)
        assertEquals(1f, SpiritBombRules.fraction(999, 200), 0f)
        assertEquals(0f, SpiritBombRules.fraction(5, 0), 0f)
    }

    @Test
    fun `a bomb can be thrown only when full`() {
        assertFalse(SpiritBombRules.isReadyToThrow(progress = 99, target = 100))
        assertTrue(SpiritBombRules.isReadyToThrow(progress = 100, target = 100))
        assertFalse(SpiritBombRules.isReadyToThrow(progress = 0, target = 0))
    }

    @Test
    fun `throwing pays half the bomb's size on top`() {
        assertEquals(listOf(50, 125, 250), (1..3).map { SpiritBombRules.throwBonus(it) })
    }

    @Test
    fun `time chamber sessions know how to log to minute and session bombs`() {
        assertTrue(SpiritBombRules.isMinutesUnit("minutes"))
        assertTrue(SpiritBombRules.isMinutesUnit(" Minutes "))
        assertTrue(SpiritBombRules.isSessionsUnit("sessions"))
        assertFalse(SpiritBombRules.isMinutesUnit("pages"))
        assertFalse(SpiritBombRules.isSessionsUnit("pages"))
    }

    @Test
    fun `the time chamber offers 25 and 50 minute sessions`() {
        assertEquals(listOf(25, 50), GameConfig.FOCUS_SESSION_MINUTES)
    }
}
