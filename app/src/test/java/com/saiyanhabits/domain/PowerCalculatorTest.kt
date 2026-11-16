package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCalculatorTest {

    @Test
    fun `points are base x difficulty x gravity multiplier`() {
        // difficulty 1..3 down, gravity normal / 10x / 100x across
        val expected = mapOf(
            1 to listOf(10, 20, 30),
            2 to listOf(20, 40, 60),
            3 to listOf(30, 60, 90),
        )
        for ((difficulty, row) in expected) {
            assertEquals(row[0], PowerCalculator.completionPoints(difficulty, Gravity.NORMAL))
            assertEquals(row[1], PowerCalculator.completionPoints(difficulty, Gravity.X10))
            assertEquals(row[2], PowerCalculator.completionPoints(difficulty, Gravity.X100))
        }
    }

    @Test
    fun `gravity multipliers are 1x 2x 3x`() {
        assertEquals(listOf(1, 2, 3), Gravity.entries.map { it.multiplier })
    }

    @Test
    fun `difficulty outside 1 to 3 is clamped`() {
        assertEquals(
            PowerCalculator.completionPoints(GameConfig.MIN_DIFFICULTY, Gravity.NORMAL),
            PowerCalculator.completionPoints(0, Gravity.NORMAL),
        )
        assertEquals(
            PowerCalculator.completionPoints(GameConfig.MIN_DIFFICULTY, Gravity.X10),
            PowerCalculator.completionPoints(-4, Gravity.X10),
        )
        assertEquals(
            PowerCalculator.completionPoints(GameConfig.MAX_DIFFICULTY, Gravity.X100),
            PowerCalculator.completionPoints(99, Gravity.X100),
        )
    }

    @Test
    fun `every check-off is worth something`() {
        for (difficulty in GameConfig.MIN_DIFFICULTY..GameConfig.MAX_DIFFICULTY) {
            for (gravity in Gravity.entries) {
                assertTrue(PowerCalculator.completionPoints(difficulty, gravity) > 0)
            }
        }
    }

    @Test
    fun `power level starts at 5`() {
        assertEquals(5L, PowerCalculator.powerLevel(totalPointsEarned = 0))
    }

    @Test
    fun `power level is the start plus all points earned`() {
        val earned = PowerCalculator.completionPoints(1, Gravity.NORMAL) +
            PowerCalculator.completionPoints(3, Gravity.X100) +
            PowerCalculator.completionPoints(2, Gravity.X10)
        assertEquals(10 + 90 + 40, earned)
        assertEquals(145L, PowerCalculator.powerLevel(earned.toLong()))
    }

    @Test
    fun `power level never goes below the start`() {
        assertEquals(5L, PowerCalculator.powerLevel(totalPointsEarned = -50))
    }

    @Test
    fun `power level handles totals beyond the int range`() {
        assertEquals(5L + 5_000_000_000L, PowerCalculator.powerLevel(5_000_000_000L))
    }
}
