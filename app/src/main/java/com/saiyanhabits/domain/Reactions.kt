package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** What a mentor's home-screen one-liner is reacting to. The keys are the names used in the dialogue files. */
enum class ReactionTrigger(val key: String) {
    MISSED_DAY("missed_day"),
    COMEBACK("comeback"),
    STREAK_MILESTONE("streak_milestone"),
    BOSS_DEFEATED("boss_defeated"),
    IDLE("idle"),
}

/** What the mentors can see when deciding what to say. */
data class ReactionContext(
    val epochDay: Long,
    /** Finished days, yesterday first. Only the most recent few are needed. */
    val pastNewestFirst: List<DayOutcome>,
    /** Habits checked off so far today. */
    val completedToday: Int,
    val todayPerfect: Boolean,
    val overallStreak: Int,
    val bossDefeatedToday: Boolean,
    /** Mentors who currently train at least one habit. */
    val activeMentors: List<Mentor>,
)

data class Reaction(val trigger: ReactionTrigger, val mentor: Mentor)

/** Picks which mentor speaks on the home screen, and about what. Pure functions, no Android. */
object ReactionRules {

    /** True if the last day that had anything scheduled was missed. */
    fun lastTrainingDayWasMissed(pastNewestFirst: List<DayOutcome>): Boolean =
        when (pastNewestFirst.firstOrNull { it != DayOutcome.REST }) {
            DayOutcome.MISSED, DayOutcome.BROKEN -> true
            else -> false
        }

    /** The most newsworthy thing going on, in order: boss, streak milestone, comeback, miss, nothing special. */
    fun trigger(context: ReactionContext): ReactionTrigger = when {
        context.bossDefeatedToday -> ReactionTrigger.BOSS_DEFEATED
        context.todayPerfect && context.overallStreak in GameConfig.STREAK_REACTION_MILESTONES ->
            ReactionTrigger.STREAK_MILESTONE
        lastTrainingDayWasMissed(context.pastNewestFirst) ->
            if (context.completedToday > 0) ReactionTrigger.COMEBACK else ReactionTrigger.MISSED_DAY
        else -> ReactionTrigger.IDLE
    }

    /**
     * Who speaks. The mentors with habits take turns day by day; with no habits
     * yet it is Roshi, the first teacher.
     */
    fun mentor(context: ReactionContext): Mentor {
        val speakers = context.activeMentors.distinct().sortedBy { it.ordinal }
        if (speakers.isEmpty()) return Mentor.ROSHI
        return speakers[Math.floorMod(context.epochDay, speakers.size.toLong()).toInt()]
    }

    fun pick(context: ReactionContext): Reaction = Reaction(trigger(context), mentor(context))

    /**
     * Which of a mentor's [lineCount] lines to use. Steps through them one per
     * day, so a line only comes back once all the others have been used.
     */
    fun lineIndex(epochDay: Long, mentor: Mentor, lineCount: Int): Int =
        if (lineCount <= 0) 0 else Math.floorMod(epochDay + mentor.ordinal * 3L, lineCount.toLong()).toInt()

    /** Fills in the {streak} and {boss} placeholders a line may contain. */
    fun fill(line: String, streak: Int, bossName: String?): String =
        line.replace("{streak}", streak.toString()).replace("{boss}", bossName ?: "the boss")
}
