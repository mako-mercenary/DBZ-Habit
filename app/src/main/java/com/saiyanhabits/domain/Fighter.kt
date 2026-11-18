package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import kotlin.math.floor
import kotlin.math.sqrt

/** The six fighting stats. */
enum class Stat(val label: String, val what: String) {
    STRENGTH("Strength", "Damage from attacks"),
    SPEED("Speed", "How fast your turns come round"),
    VITALITY("Vitality", "HP and defence against attacks"),
    KI("Ki", "Ki and the power of ki techniques"),
    SPIRIT("Spirit", "Defence against ki techniques"),
    LUCK("Luck", "Critical hits, stealing and finds"),
}

/** The stats each mentor's bond raises. */
val Mentor.stats: List<Stat>
    get() = when (this) {
        Mentor.GOKU -> listOf(Stat.STRENGTH)
        Mentor.ROSHI -> listOf(Stat.VITALITY)
        Mentor.PICCOLO -> listOf(Stat.KI, Stat.SPIRIT)
        Mentor.BULMA -> listOf(Stat.LUCK)
        Mentor.VEGETA -> listOf(Stat.SPEED)
    }

/** One stat: what training gives and what bonds add on top. */
data class StatValue(val base: Int, val fromBonds: Int) {
    val total: Int get() = (base + fromBonds).coerceAtMost(GameConfig.STAT_CAP)
}

/** Your fighter as your training has made them. */
data class FighterSheet(
    val level: Int,
    val powerLevel: Long,
    /** Power level the current level started at. */
    val levelStartPower: Long,
    /** Power level needed for the next level, or null at the top. */
    val nextLevelPower: Long?,
    val stats: Map<Stat, StatValue>,
    val maxHp: Int,
    val maxKi: Int,
) {
    fun stat(stat: Stat): Int = stats.getValue(stat).total

    /** Progress through the current level, 0 to 1. */
    val levelProgress: Float
        get() = nextLevelPower?.let { next ->
            ((powerLevel - levelStartPower).toFloat() / (next - levelStartPower)).coerceIn(0f, 1f)
        } ?: 1f
}

/** Level and stat rules. Pure functions, no Android. */
object FighterRules {

    /** The level a power level reaches. */
    fun level(powerLevel: Long): Int {
        val raw = floor(sqrt(powerLevel.coerceAtLeast(0) / GameConfig.LEVEL_POWER_DIVISOR)).toInt() + 1
        return raw.coerceIn(1, GameConfig.MAX_LEVEL)
    }

    /** The lowest power level at which [level] is reached. */
    fun powerFor(level: Int): Long {
        val steps = (level.coerceIn(1, GameConfig.MAX_LEVEL) - 1).toDouble()
        return kotlin.math.ceil(steps * steps * GameConfig.LEVEL_POWER_DIVISOR).toLong()
    }

    /** What a stat is at [level] before bonds. */
    fun baseStat(level: Int): Int =
        (GameConfig.STAT_BASE + floor(GameConfig.STAT_GROWTH_PER_LEVEL * (level - 1)).toInt())
            .coerceAtMost(GameConfig.STAT_CAP)

    /** What the bonds add to [stat]: every mentor who raises it, by their bond level above 1. */
    fun bondBonus(stat: Stat, bondLevels: Map<Mentor, Int>): Int =
        Mentor.entries.filter { stat in it.stats }
            .sumOf { ((bondLevels[it] ?: 1) - 1).coerceAtLeast(0) * GameConfig.BOND_STAT_BONUS_PER_LEVEL }

    fun sheet(powerLevel: Long, bondLevels: Map<Mentor, Int>): FighterSheet {
        val level = level(powerLevel)
        val stats = Stat.entries.associateWith { StatValue(baseStat(level), bondBonus(it, bondLevels)) }
        val vitality = stats.getValue(Stat.VITALITY).total
        val ki = stats.getValue(Stat.KI).total
        return FighterSheet(
            level = level,
            powerLevel = powerLevel,
            levelStartPower = powerFor(level),
            nextLevelPower = if (level >= GameConfig.MAX_LEVEL) null else powerFor(level + 1),
            stats = stats,
            maxHp = (GameConfig.HP_BASE + GameConfig.HP_PER_LEVEL * (level - 1) + GameConfig.HP_PER_VITALITY * vitality)
                .coerceAtMost(GameConfig.HP_CAP),
            maxKi = (GameConfig.KI_BASE + GameConfig.KI_PER_LEVEL * (level - 1) + GameConfig.KI_PER_KI_STAT * ki)
                .coerceAtMost(GameConfig.KI_CAP),
        )
    }
}

/** Zeni rules. Pure functions, no Android. */
object ZeniRules {

    /**
     * Zeni earned from training, worked out from what is already recorded:
     * every point of power, plus the perfect-day and perfect-week bonuses.
     * So past training counts, and an undone check-off takes its zeni back.
     */
    fun earned(totalPoints: Long, perfectDays: Int, perfectWeeks: Int): Long =
        totalPoints.coerceAtLeast(0) * GameConfig.ZENI_PER_POWER_POINT +
            perfectDays.toLong() * GameConfig.ZENI_PERFECT_DAY +
            perfectWeeks.toLong() * GameConfig.ZENI_PERFECT_WEEK

    /** Weeks, Monday to Sunday, that were perfect, from finished days. */
    fun perfectWeeks(days: List<PastDay>): Int =
        days.groupBy { Weeks.startOf(it.epochDay) }.values.count { week -> WishRules.isPerfectWeek(week.map { it.outcome }) }

    /**
     * What can be spent: earnings plus the ledger (prizes in, purchases out).
     * Never shown below zero, even if an undo takes back zeni already spent.
     */
    fun balance(earned: Long, ledger: Long): Long = (earned + ledger).coerceAtLeast(0)
}
