package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import kotlin.math.roundToInt

/** The points rules. Pure functions, no Android, so they are easy to test. */
object PowerCalculator {

    /** Points for one check-off with no perks: base x difficulty x gravity multiplier. */
    fun completionPoints(difficulty: Int, gravity: Gravity): Int {
        val clamped = difficulty.coerceIn(GameConfig.MIN_DIFFICULTY, GameConfig.MAX_DIFFICULTY)
        return GameConfig.BASE_POINTS_PER_COMPLETION * clamped * gravity.multiplier
    }

    /**
     * Points for one check-off of a habit trained by [mentor], with the perks
     * that apply: a stronger 100x gravity, that mentor's percentage boost, and
     * the doubling on a Kaio-ken day.
     */
    fun completionPoints(difficulty: Int, gravity: Gravity, mentor: Mentor, perks: Perks): Int {
        val clamped = difficulty.coerceIn(GameConfig.MIN_DIFFICULTY, GameConfig.MAX_DIFFICULTY)
        val base = GameConfig.BASE_POINTS_PER_COMPLETION * clamped * perks.gravityMultiplier(gravity)
        return boosted(base, perks.habitBoostPercent(mentor)) * perks.checkOffMultiplier
    }

    /** [points] raised by [percent] percent, rounded to the nearest point. */
    fun boosted(points: Int, percent: Int): Int =
        (points * (100 + percent.coerceAtLeast(0)) / 100.0).roundToInt()

    /**
     * The extra points a time chamber session adds on top of [points] it logged,
     * so that the total comes to points x the focus multiplier.
     */
    fun focusBonus(points: Int, multiplier: Double = GameConfig.FOCUS_POINT_MULTIPLIER): Int =
        (points.coerceAtLeast(0) * (multiplier - 1.0).coerceAtLeast(0.0)).roundToInt()

    /** Power level for a given total of points earned so far. */
    fun powerLevel(totalPointsEarned: Long): Long =
        GameConfig.STARTING_POWER_LEVEL + totalPointsEarned.coerceAtLeast(0)
}
