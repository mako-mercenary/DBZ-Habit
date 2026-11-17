package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** What it takes to unlock a story chapter. */
sealed interface StoryRequirement {
    data class PowerLevel(val level: Int) : StoryRequirement
    data class Form(val form: Int) : StoryRequirement
}

/** Story chapter rules. Pure functions, no Android. */
object StoryRules {

    /** The power-level chapters, plus the finale. */
    val CHAPTER_COUNT: Int get() = GameConfig.STORY_CHAPTER_POWER_LEVELS.size + 1

    fun requirement(chapter: Int): StoryRequirement =
        GameConfig.STORY_CHAPTER_POWER_LEVELS.getOrNull(chapter - 1)
            ?.let { StoryRequirement.PowerLevel(it) }
            ?: StoryRequirement.Form(GameConfig.STORY_FINAL_CHAPTER_FORM)

    fun isUnlocked(chapter: Int, powerLevel: Long, highestForm: Int): Boolean {
        if (chapter !in 1..CHAPTER_COUNT) return false
        return when (val needs = requirement(chapter)) {
            is StoryRequirement.PowerLevel -> powerLevel >= needs.level
            is StoryRequirement.Form -> highestForm >= needs.form
        }
    }

    // Which chapters have been read is kept as one number, a bit per chapter.

    fun isRead(chapter: Int, readMask: Int): Boolean = readMask and bit(chapter) != 0

    fun markRead(readMask: Int, chapter: Int): Int = readMask or bit(chapter)

    /** The earliest chapter that is unlocked and has not been read, if any. */
    fun firstUnread(powerLevel: Long, highestForm: Int, readMask: Int): Int? =
        (1..CHAPTER_COUNT).firstOrNull { isUnlocked(it, powerLevel, highestForm) && !isRead(it, readMask) }

    private fun bit(chapter: Int): Int = if (chapter in 1..30) 1 shl (chapter - 1) else 0
}

/** Calendar weeks, Monday to Sunday, as epoch days. */
object Weeks {
    const val DAYS = 7

    /** The Monday of the week [epochDay] falls in. (Epoch day 0 was a Thursday.) */
    fun startOf(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 3, DAYS.toLong())
}

/** Wish orb rules. Pure functions, no Android. */
object WishRules {

    /**
     * A perfect week: seven finished days, Monday to Sunday, with every
     * scheduled habit done on every one of them. Rest days are fine, but at
     * least one day must have had something scheduled, and a day saved by a
     * healing bean does not count as done.
     */
    fun isPerfectWeek(outcomes: List<DayOutcome>): Boolean =
        outcomes.size == Weeks.DAYS &&
            outcomes.all { it == DayOutcome.PERFECT || it == DayOutcome.REST } &&
            outcomes.any { it == DayOutcome.PERFECT }

    /**
     * The Mondays of the finished weeks still to be judged, oldest first.
     *
     * [lastJudgedWeekStart] is the Monday of the last week already judged, or
     * null if none has been. [firstRecordedDay] is the earliest finished day on
     * record. A week is finished once its Sunday is before [todayEpochDay].
     */
    fun weeksToJudge(lastJudgedWeekStart: Long?, firstRecordedDay: Long?, todayEpochDay: Long): List<Long> {
        val first = lastJudgedWeekStart?.plus(Weeks.DAYS)
            ?: firstRecordedDay?.let { Weeks.startOf(it) }
            ?: return emptyList()
        val thisWeek = Weeks.startOf(todayEpochDay)
        return generateSequence(first) { it + Weeks.DAYS }.takeWhile { it < thisWeek }.toList()
    }

    /** Orbs held after a perfect week. Never more than are needed for a wish. */
    fun orbsAfterPerfectWeek(orbs: Int): Int = (orbs + 1).coerceAtMost(GameConfig.WISH_ORBS_NEEDED.coerceAtLeast(orbs))

    fun canSummon(orbs: Int): Boolean = orbs >= GameConfig.WISH_ORBS_NEEDED
}

/**
 * This week against the rival: last week's you.
 *
 * [rivalTotal] is everything last week earned. [rivalByNow] is what last week
 * had earned by the same weekday, which is the fair comparison mid-week.
 */
data class RivalStanding(val rivalTotal: Int, val rivalByNow: Int, val mine: Int) {
    /** False until there has been a week with any points in it to compete against. */
    val hasRival: Boolean get() = rivalTotal > 0

    /** Points ahead of last week's pace; negative when behind. */
    val lead: Int get() = mine - rivalByNow

    /** True once this week has out-earned the whole of last week. */
    val beaten: Boolean get() = hasRival && mine > rivalTotal
}

/** Rival rules. Pure functions, no Android. */
object RivalRules {

    /** [pointsByDay] maps a day to the power earned that day; days with none may be missing. */
    fun standing(pointsByDay: Map<Long, Int>, todayEpochDay: Long): RivalStanding {
        val thisWeek = Weeks.startOf(todayEpochDay)
        val lastWeek = thisWeek - Weeks.DAYS
        val daysIn = todayEpochDay - thisWeek // 0 on Monday, 6 on Sunday
        fun sum(from: Long, to: Long) = (from..to).sumOf { pointsByDay[it] ?: 0 }
        return RivalStanding(
            rivalTotal = sum(lastWeek, thisWeek - 1),
            rivalByNow = sum(lastWeek, lastWeek + daysIn),
            mine = sum(thisWeek, todayEpochDay),
        )
    }
}
