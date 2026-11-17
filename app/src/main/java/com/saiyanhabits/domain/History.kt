package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import java.time.LocalDate
import kotlin.math.roundToInt

/** A finished day as the history sees it. */
data class PastDay(
    val epochDay: Long,
    val scheduled: Int,
    val completed: Int,
    val outcome: DayOutcome,
)

/** All-time numbers for the history screen. */
data class HistoryStats(
    /** The longest overall streak ever reached, counting the current one. */
    val bestStreak: Int,
    val perfectDays: Int,
    /** Finished days on record. */
    val daysTracked: Int,
    /** Share of scheduled habits that were done, 0 to 100, or null before anything was scheduled. */
    val completionPercent: Int?,
)

/** History rules. Pure functions, no Android. */
object HistoryRules {

    /**
     * The overall streak at the end of each day in [oldestFirst], counted the
     * way the home screen counts it: a perfect day adds one, rest days and
     * bean-covered days carry it over, and a missed day ends it.
     */
    fun streakAfterEach(oldestFirst: List<DayOutcome>): List<Int> {
        var streak = 0
        return oldestFirst.map { outcome ->
            streak = when (outcome) {
                DayOutcome.PERFECT -> streak + 1
                DayOutcome.REST, DayOutcome.BEAN_COVERED -> streak
                DayOutcome.MISSED, DayOutcome.BROKEN -> 0
            }
            streak
        }
    }

    /** [days] oldest first. [currentStreak] is the streak shown on the home screen, which can include today. */
    fun stats(days: List<PastDay>, currentStreak: Int): HistoryStats = HistoryStats(
        bestStreak = maxOf(streakAfterEach(days.map { it.outcome }).maxOrNull() ?: 0, currentStreak),
        perfectDays = days.count { it.outcome == DayOutcome.PERFECT },
        daysTracked = days.size,
        completionPercent = completionPercent(days),
    )

    fun completionPercent(days: List<PastDay>): Int? {
        val scheduled = days.sumOf { it.scheduled }
        if (scheduled <= 0) return null
        val completed = days.sumOf { it.completed.coerceIn(0, it.scheduled) }
        return (completed * 100.0 / scheduled).roundToInt()
    }

    /**
     * The cells of a month calendar, a row of seven per week from Monday to
     * Sunday: the epoch day of each date in the month, with nulls before the
     * 1st and after the last day so every row is full.
     */
    fun monthCells(year: Int, month: Int): List<Long?> {
        val first = LocalDate.of(year, month, 1)
        val lead = first.dayOfWeek.value - 1 // Monday = 1
        val days = (0 until first.lengthOfMonth()).map { first.toEpochDay() + it }
        val cells = List<Long?>(lead) { null } + days
        val trail = (Weeks.DAYS - cells.size % Weeks.DAYS) % Weeks.DAYS
        return cells + List<Long?>(trail) { null }
    }

    /**
     * Power level at the end of each day from [fromDay] to [toDay] (inclusive).
     * [pointsByDay] is the power earned per day for all time; days with none
     * may be missing.
     */
    fun powerSeries(pointsByDay: Map<Long, Int>, fromDay: Long, toDay: Long): List<Long> {
        if (toDay < fromDay) return emptyList()
        var total = pointsByDay.filterKeys { it < fromDay }.values.sumOf { it.toLong() }
        return (fromDay..toDay).map { day ->
            total += pointsByDay[day] ?: 0
            PowerCalculator.powerLevel(total)
        }
    }
}

/** A habit and how often it was checked off in a week. */
data class TrainedHabit(val name: String, val checkOffs: Int)

/** How one Monday-to-Sunday week went. */
data class WeekRecap(
    /** The Monday. */
    val weekStart: Long,
    /** Monday to Sunday; null for a day with no record (before you started). */
    val days: List<DayOutcome?>,
    val scheduled: Int,
    val completed: Int,
    val powerGained: Int,
    /** What the week before earned: the rival this week was up against. */
    val rivalPower: Int,
    /** The overall streak at the end of Sunday. */
    val streakAtEnd: Int,
    /** The habit checked off most often, if any was. */
    val topHabit: TrainedHabit?,
    /** Bond XP each mentor gained. Mentors who gained none are left out. */
    val bondXp: Map<Mentor, Int>,
    val milestonesHit: Int,
    val bossesDefeated: Int,
    val bombsThrown: Int,
    /** Wish orbs held now. */
    val wishOrbs: Int,
) {
    val perfectDays: Int get() = days.count { it == DayOutcome.PERFECT }
    val perfectWeek: Boolean get() = days.none { it == null } && WishRules.isPerfectWeek(days.filterNotNull())
    val hasRival: Boolean get() = rivalPower > 0
    val beatRival: Boolean get() = hasRival && powerGained > rivalPower
    val completionPercent: Int? get() = if (scheduled > 0) (completed.coerceAtMost(scheduled) * 100.0 / scheduled).roundToInt() else null
    val topMentor: Pair<Mentor, Int>? get() = bondXp.maxByOrNull { it.value }?.toPair()
    val hadBattles: Boolean get() = milestonesHit + bossesDefeated + bombsThrown > 0
}

/** Weekly recap rules. Pure functions, no Android. */
object RecapRules {

    /**
     * The Monday of the week whose recap should open by itself on [todayEpochDay]:
     * last week, if it has not been shown yet and anything happened in it.
     * [lastShownWeek] is the Monday of the last recap shown, or null if none has been.
     */
    fun dueWeek(todayEpochDay: Long, lastShownWeek: Long?, lastWeekHadActivity: Boolean): Long? {
        val lastWeek = Weeks.startOf(todayEpochDay) - Weeks.DAYS
        if (lastShownWeek != null && lastShownWeek >= lastWeek) return null
        return if (lastWeekHadActivity) lastWeek else null
    }

    /** The big word at the top of the recap. */
    fun headline(recap: WeekRecap): String = when {
        recap.perfectWeek -> "Perfect week!"
        recap.beatRival -> "Rival beaten!"
        recap.perfectDays >= GameConfig.RECAP_STRONG_WEEK_PERFECT_DAYS -> "Strong week"
        recap.completed == 0 && recap.powerGained == 0 -> "A quiet week"
        else -> "Week done"
    }

    /** A line under the headline. */
    fun subline(recap: WeekRecap): String = when {
        recap.perfectWeek -> "Every scheduled habit, every day. The dragon takes notice."
        recap.beatRival -> "You out-trained the week before. Keep the pressure on."
        recap.perfectDays >= GameConfig.RECAP_STRONG_WEEK_PERFECT_DAYS -> "Close to perfect. One more push next week."
        recap.completed == 0 && recap.powerGained == 0 -> "No training on record. A new week starts now."
        else -> "Every week of training counts. Go again."
    }

    /** The streak at the end of [day], from finished days oldest first. */
    fun streakAt(day: Long, days: List<PastDay>): Int {
        val upTo = days.filter { it.epochDay <= day }
        return HistoryRules.streakAfterEach(upTo.map { it.outcome }).lastOrNull() ?: 0
    }
}
