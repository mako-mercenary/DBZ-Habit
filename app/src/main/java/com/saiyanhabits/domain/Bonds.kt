package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/**
 * Perks unlocked at bond levels 3, 6 and 9 with each mentor. Names are stored
 * in the database: do not rename existing values.
 *
 * Most are always on once unlocked. The two with a [cooldownDays] are used by
 * hand and then need that many days before they can be used again.
 */
enum class Technique(
    val mentor: Mentor,
    val bondLevel: Int,
    val title: String,
    val cooldownDays: Int? = null,
) {
    TURTLE_BASICS(Mentor.ROSHI, 3, "Turtle School Basics"),
    SENZU_POUCH(Mentor.ROSHI, 6, "Senzu Pouch"),
    DAY_OFF(Mentor.ROSHI, 9, "Master's Day Off", GameConfig.TECHNIQUE_DAY_OFF_COOLDOWN_DAYS),

    ONE_MORE_REP(Mentor.GOKU, 3, "One More Rep"),
    KAIOKEN(Mentor.GOKU, 6, "Kaio-ken", GameConfig.TECHNIQUE_KAIOKEN_COOLDOWN_DAYS),
    SPIRIT_BOMB_MASTERY(Mentor.GOKU, 9, "Spirit Bomb Mastery"),

    WEIGHTED_TRAINING(Mentor.PICCOLO, 3, "Weighted Training"),
    DEEP_MEDITATION(Mentor.PICCOLO, 6, "Deep Meditation"),
    REGENERATION(Mentor.PICCOLO, 9, "Regeneration"),

    BLUEPRINTS(Mentor.BULMA, 3, "Blueprints"),
    CAPSULE_TECH(Mentor.BULMA, 6, "Capsule Tech"),
    DRAGON_RADAR(Mentor.BULMA, 9, "Dragon Radar"),

    SAIYAN_PRIDE(Mentor.VEGETA, 3, "Saiyan Pride"),
    GRAVITY_CHAMBER(Mentor.VEGETA, 6, "Gravity Chamber"),
    FINAL_FLASH(Mentor.VEGETA, 9, "Final Flash"),
    ;

    /** True for techniques that are used by hand rather than always on. */
    val isActivated: Boolean get() = cooldownDays != null

    /** What it does, with the numbers taken from GameConfig so the text stays true when they change. */
    val description: String
        get() = when (this) {
            TURTLE_BASICS, ONE_MORE_REP, WEIGHTED_TRAINING, BLUEPRINTS ->
                "+${GameConfig.TECHNIQUE_HABIT_BOOST_PERCENT}% points on every habit ${mentor.displayName} trains."
            SAIYAN_PRIDE ->
                "+${GameConfig.TECHNIQUE_HARDEST_BOOST_PERCENT}% points on your hardest habit."
            SENZU_POUCH ->
                "Hold ${GameConfig.MAX_HEALING_BEANS + GameConfig.TECHNIQUE_EXTRA_BEAN_SLOTS} healing beans " +
                    "instead of ${GameConfig.MAX_HEALING_BEANS}."
            DAY_OFF ->
                "Excuse one of today's habits. It stops counting for today, so your streaks are safe. " +
                    "Ready again after ${GameConfig.TECHNIQUE_DAY_OFF_COOLDOWN_DAYS} days."
            KAIOKEN ->
                "Every check-off for the rest of today pays ${GameConfig.TECHNIQUE_KAIOKEN_MULTIPLIER}x points. " +
                    "Ready again after ${GameConfig.TECHNIQUE_KAIOKEN_COOLDOWN_DAYS} days."
            SPIRIT_BOMB_MASTERY ->
                "Throwing a spirit bomb pays ${GameConfig.TECHNIQUE_BOMB_THROW_MULTIPLIER}x the usual bonus."
            DEEP_MEDITATION ->
                "Time chamber sessions pay ${GameConfig.TECHNIQUE_FOCUS_POINT_MULTIPLIER}x points " +
                    "instead of ${GameConfig.FOCUS_POINT_MULTIPLIER}x."
            REGENERATION ->
                "Earn a healing bean every ${GameConfig.TECHNIQUE_BEAN_STREAK_INTERVAL} streak days " +
                    "instead of every ${GameConfig.BEAN_STREAK_INTERVAL}."
            CAPSULE_TECH ->
                "+${GameConfig.TECHNIQUE_MILESTONE_BOOST_PERCENT}% points on every saga milestone."
            DRAGON_RADAR ->
                "+${GameConfig.TECHNIQUE_VICTORY_BOOST_PERCENT}% victory bonus when a boss falls."
            GRAVITY_CHAMBER ->
                "100x gravity pays ${GameConfig.TECHNIQUE_GRAVITY_100X_MULTIPLIER}x points " +
                    "instead of ${GameConfig.GRAVITY_100X_MULTIPLIER}x."
            FINAL_FLASH ->
                "Every mentor's assist deals ${GameConfig.TECHNIQUE_ASSIST_DAMAGE_MULTIPLIER}x bonus damage in boss fights."
        }

    companion object {
        fun of(mentor: Mentor): List<Technique> = entries.filter { it.mentor == mentor }.sortedBy { it.bondLevel }
    }
}

/** Bond level rules. Pure functions, no Android. */
object BondRules {
    const val FIRST_LEVEL = 1
    val MAX_LEVEL: Int get() = GameConfig.BOND_LEVEL_XP.size

    /** Bond level for a total of bond XP: 1 with none, up to [MAX_LEVEL]. */
    fun levelForXp(xp: Int): Int = GameConfig.BOND_LEVEL_XP.count { xp >= it }.coerceAtLeast(FIRST_LEVEL)

    /** Total XP at which [level] is reached. */
    fun xpForLevel(level: Int): Int = GameConfig.BOND_LEVEL_XP[(level - 1).coerceIn(GameConfig.BOND_LEVEL_XP.indices)]

    /** XP still needed for the next level, or null at the highest level. */
    fun xpToNextLevel(xp: Int): Int? {
        val level = levelForXp(xp)
        return if (level >= MAX_LEVEL) null else xpForLevel(level + 1) - xp
    }

    /** How far through the current level [xp] is, from 0 to 1. Always 1 at the highest level. */
    fun progressInLevel(xp: Int): Float {
        val level = levelForXp(xp)
        if (level >= MAX_LEVEL) return 1f
        val from = xpForLevel(level)
        val to = xpForLevel(level + 1)
        return ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    /** Scenes unlock one per level from level 2 up. */
    fun isSceneUnlocked(sceneLevel: Int, bondLevel: Int): Boolean = sceneLevel in 2..bondLevel

    fun unlockedTechniques(bondLevels: Map<Mentor, Int>): Set<Technique> =
        Technique.entries.filter { (bondLevels[it.mentor] ?: FIRST_LEVEL) >= it.bondLevel }.toSet()

    /** The technique that reaching exactly [level] with [mentor] unlocks, if any. */
    fun techniqueUnlockedAt(mentor: Mentor, level: Int): Technique? =
        Technique.entries.firstOrNull { it.mentor == mentor && it.bondLevel == level }

    /** Mentors close enough to join boss fights. */
    fun assistMentors(bondLevels: Map<Mentor, Int>): List<Mentor> =
        Mentor.entries.filter { (bondLevels[it] ?: FIRST_LEVEL) >= GameConfig.ASSIST_MIN_BOND_LEVEL }

    /**
     * Days until an activated technique can be used again: 0 when it is ready.
     * [lastUsedEpochDay] is null if it has never been used.
     */
    fun cooldownDaysLeft(technique: Technique, lastUsedEpochDay: Long?, todayEpochDay: Long): Int {
        val cooldown = technique.cooldownDays ?: return 0
        if (lastUsedEpochDay == null) return 0
        return (lastUsedEpochDay + cooldown - todayEpochDay).coerceIn(0, cooldown.toLong()).toInt()
    }
}

/**
 * Everything the unlocked techniques and bonds change about the numbers, in
 * one place. [NONE] is the game with no perks at all.
 */
data class Perks(
    val techniques: Set<Technique> = emptySet(),
    /** Mentors who join boss fights. */
    val assistMentors: List<Mentor> = emptyList(),
    /** True on a day Kaio-ken has been used. */
    val doublePointsToday: Boolean = false,
) {
    private fun has(technique: Technique) = technique in techniques

    /** Extra points, in percent, on a habit trained by [mentor]. */
    fun habitBoostPercent(mentor: Mentor): Int = when (mentor) {
        Mentor.ROSHI -> if (has(Technique.TURTLE_BASICS)) GameConfig.TECHNIQUE_HABIT_BOOST_PERCENT else 0
        Mentor.GOKU -> if (has(Technique.ONE_MORE_REP)) GameConfig.TECHNIQUE_HABIT_BOOST_PERCENT else 0
        Mentor.PICCOLO -> if (has(Technique.WEIGHTED_TRAINING)) GameConfig.TECHNIQUE_HABIT_BOOST_PERCENT else 0
        Mentor.BULMA -> if (has(Technique.BLUEPRINTS)) GameConfig.TECHNIQUE_HABIT_BOOST_PERCENT else 0
        Mentor.VEGETA -> if (has(Technique.SAIYAN_PRIDE)) GameConfig.TECHNIQUE_HARDEST_BOOST_PERCENT else 0
    }

    fun gravityMultiplier(gravity: Gravity): Int =
        if (gravity == Gravity.X100 && has(Technique.GRAVITY_CHAMBER)) {
            GameConfig.TECHNIQUE_GRAVITY_100X_MULTIPLIER
        } else {
            gravity.multiplier
        }

    val checkOffMultiplier: Int
        get() = if (doublePointsToday) GameConfig.TECHNIQUE_KAIOKEN_MULTIPLIER else 1

    val beanCapacity: Int
        get() = GameConfig.MAX_HEALING_BEANS +
            if (has(Technique.SENZU_POUCH)) GameConfig.TECHNIQUE_EXTRA_BEAN_SLOTS else 0

    val beanStreakInterval: Int
        get() = if (has(Technique.REGENERATION)) {
            GameConfig.TECHNIQUE_BEAN_STREAK_INTERVAL
        } else {
            GameConfig.BEAN_STREAK_INTERVAL
        }

    val focusMultiplier: Double
        get() = if (has(Technique.DEEP_MEDITATION)) {
            GameConfig.TECHNIQUE_FOCUS_POINT_MULTIPLIER
        } else {
            GameConfig.FOCUS_POINT_MULTIPLIER
        }

    val milestoneBoostPercent: Int
        get() = if (has(Technique.CAPSULE_TECH)) GameConfig.TECHNIQUE_MILESTONE_BOOST_PERCENT else 0

    val victoryBoostPercent: Int
        get() = if (has(Technique.DRAGON_RADAR)) GameConfig.TECHNIQUE_VICTORY_BOOST_PERCENT else 0

    val bombThrowMultiplier: Int
        get() = if (has(Technique.SPIRIT_BOMB_MASTERY)) GameConfig.TECHNIQUE_BOMB_THROW_MULTIPLIER else 1

    /** Bonus damage one assisting mentor adds to a milestone hit. */
    val assistDamageEach: Int
        get() = GameConfig.ASSIST_DAMAGE_PER_MENTOR *
            if (has(Technique.FINAL_FLASH)) GameConfig.TECHNIQUE_ASSIST_DAMAGE_MULTIPLIER else 1

    /** Bonus damage all assists together add to a milestone hit. */
    val assistDamage: Int get() = assistMentors.size * assistDamageEach

    companion object {
        val NONE = Perks()

        fun from(bondLevels: Map<Mentor, Int>, doublePointsToday: Boolean): Perks = Perks(
            techniques = BondRules.unlockedTechniques(bondLevels),
            assistMentors = BondRules.assistMentors(bondLevels),
            doublePointsToday = doublePointsToday,
        )
    }
}
