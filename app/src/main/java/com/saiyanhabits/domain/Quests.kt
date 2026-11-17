package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** The bosses a saga can be fought against. Names are stored in the database: do not rename. */
enum class Boss(val displayName: String) {
    RADITZ("Raditz"),
    NAPPA("Nappa"),
    FRIEZA("Frieza"),
    CELL("Cell"),
    BUU("Majin Buu"),
    BROLY("Broly"),
}

/** Where a saga or spirit bomb stands. Stored in the database: do not rename. */
enum class QuestStatus { ACTIVE, DONE, ABANDONED }

/** What earned an entry in the power ledger. Stored in the database: do not rename. */
enum class PowerSource {
    /** Time chamber bonus on a habit check-off. */
    FOCUS_HABIT,
    MILESTONE,
    SAGA_VICTORY,
    BOMB_LOG,
    BOMB_THROW,
}

/** What a time chamber session can be linked to. Stored in the database: do not rename. */
enum class ChamberTargetType { HABIT, MILESTONE, BOMB }

/** The parts of a milestone the boss rules care about. */
data class MilestoneState(val weight: Int, val damageDealt: Int, val done: Boolean)

/** Boss fight rules. Pure functions, no Android. */
object SagaRules {

    fun clampWeight(weight: Int): Int = weight.coerceIn(1, GameConfig.MAX_MILESTONE_WEIGHT)

    /** A boss's full HP: the weights of all the saga's milestones added up. */
    fun totalHp(milestones: List<MilestoneState>): Int = milestones.sumOf { it.weight }

    /**
     * Damage a milestone deals when it is finished: its weight, plus
     * [assistDamage] from mentors who have joined the fight.
     */
    fun damageFor(weight: Int, assistDamage: Int = 0): Int = weight.coerceAtLeast(0) + assistDamage.coerceAtLeast(0)

    /**
     * HP knocked off so far: the weights of the finished milestones. Assist
     * damage is extra on top and never shortens the fight, so the boss only
     * falls when every milestone is done.
     */
    fun damageTaken(milestones: List<MilestoneState>): Int =
        milestones.filter { it.done }.sumOf { it.weight }

    /** Extra damage the assists have dealt so far, beyond the milestones' own weights. */
    fun bonusDamage(milestones: List<MilestoneState>): Int =
        milestones.filter { it.done }.sumOf { (it.damageDealt - it.weight).coerceAtLeast(0) }

    /** HP the boss has left. Never below zero. */
    fun remainingHp(milestones: List<MilestoneState>): Int =
        (totalHp(milestones) - damageTaken(milestones)).coerceAtLeast(0)

    /** A boss is beaten at 0 HP. A saga with no milestones has no boss to beat yet. */
    fun isDefeated(milestones: List<MilestoneState>): Boolean =
        milestones.isNotEmpty() && remainingHp(milestones) == 0

    /**
     * Points for finishing a milestone: a set amount per point of damage,
     * assist damage included. A [charged] milestone (one that had a time
     * chamber session spent on it) earns the focus multiplier on top.
     */
    fun milestonePoints(weight: Int, charged: Boolean, perks: Perks = Perks.NONE): Int {
        val damage = damageFor(weight, perks.assistDamage)
        val base = PowerCalculator.boosted(GameConfig.MILESTONE_POINTS_PER_WEIGHT * damage, perks.milestoneBoostPercent)
        return if (charged) base + PowerCalculator.focusBonus(base, perks.focusMultiplier) else base
    }

    /**
     * The power bonus for bringing a boss with [totalHp] down to zero.
     * [bonusDamage] dealt by assists along the way counts like extra HP.
     */
    fun victoryBonus(totalHp: Int, bonusDamage: Int = 0, boostPercent: Int = 0): Int = PowerCalculator.boosted(
        GameConfig.SAGA_VICTORY_BONUS_BASE +
            GameConfig.SAGA_VICTORY_BONUS_PER_HP * (totalHp.coerceAtLeast(0) + bonusDamage.coerceAtLeast(0)),
        boostPercent,
    )
}

/** Spirit bomb rules. Pure functions, no Android. */
object SpiritBombRules {
    const val MIN_SIZE = 1
    val MAX_SIZE: Int get() = GameConfig.SPIRIT_BOMB_SIZE_POINTS.size

    private val sizeNames = listOf("Small", "Medium", "Large")

    fun sizeName(size: Int): String = sizeNames.getOrElse(size - 1) { "Size $size" }

    /** Total points a bomb of this size pays out as it fills. */
    fun sizePoints(size: Int): Int =
        GameConfig.SPIRIT_BOMB_SIZE_POINTS[(size - 1).coerceIn(GameConfig.SPIRIT_BOMB_SIZE_POINTS.indices)]

    /** How much of [amount] can still be added before the bomb is full. */
    fun applicableAmount(amount: Int, progress: Int, target: Int): Int =
        amount.coerceIn(0, (target - progress).coerceAtLeast(0))

    /** How full the bomb is, from 0 to 1. */
    fun fraction(progress: Int, target: Int): Float =
        if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)

    /**
     * Points for moving a bomb's progress from [before] to [after]. Works on
     * running totals, so many small logs pay exactly what one big log would.
     */
    fun pointsForProgress(size: Int, target: Int, before: Int, after: Int): Int {
        if (target <= 0) return 0
        val total = sizePoints(size).toLong()
        fun paidAt(progress: Int) = (total * progress.coerceIn(0, target) / target).toInt()
        return (paidAt(after) - paidAt(before)).coerceAtLeast(0)
    }

    fun isReadyToThrow(progress: Int, target: Int): Boolean = target > 0 && progress >= target

    /** Bonus for throwing a full bomb. */
    fun throwBonus(size: Int, multiplier: Int = 1): Int =
        sizePoints(size) * GameConfig.SPIRIT_BOMB_THROW_BONUS_PERCENT / 100 * multiplier.coerceAtLeast(1)

    /** True when a time chamber session can log its own length to a bomb with this unit. */
    fun isMinutesUnit(unit: String): Boolean = unit.trim().lowercase() in setOf("minutes", "minute", "min", "mins")

    /** True when a time chamber session counts as one of this bomb's units. */
    fun isSessionsUnit(unit: String): Boolean = unit.trim().lowercase() in setOf("sessions", "session")
}
