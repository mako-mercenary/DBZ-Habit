package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** The points rules. Pure functions, no Android, so they are easy to test. */
object PowerCalculator {

    /** Points for one check-off: base x difficulty x gravity multiplier. */
    fun completionPoints(difficulty: Int, gravity: Gravity): Int {
        val clamped = difficulty.coerceIn(GameConfig.MIN_DIFFICULTY, GameConfig.MAX_DIFFICULTY)
        return GameConfig.BASE_POINTS_PER_COMPLETION * clamped * gravity.multiplier
    }

    /** Power level for a given total of points earned so far. */
    fun powerLevel(totalPointsEarned: Long): Long =
        GameConfig.STARTING_POWER_LEVEL + totalPointsEarned.coerceAtLeast(0)
}
