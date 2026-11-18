package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import java.time.LocalDate

/**
 * How a finished day went. Stored in the database: do not rename existing values.
 *
 * - PERFECT: every scheduled habit was done. Adds one to the overall streak.
 * - REST: nothing was scheduled. The streak carries over, unchanged.
 * - MISSED: something was left undone and a healing bean could still cover it.
 * - BEAN_COVERED: a missed day a bean was spent on. The streak carries over, unchanged.
 * - BROKEN: a missed day that ended the streak.
 */
enum class DayOutcome { PERFECT, REST, MISSED, BEAN_COVERED, BROKEN }

/** The parts of a habit the streak rules care about. */
data class ScheduledHabit(
    val id: Long,
    val scheduleDays: Int,
    val createdAtEpochDay: Long,
    /** Times a week on any days, or null for a habit done on [scheduleDays]. */
    val weeklyTarget: Int? = null,
)

/**
 * Habits done a number of times a week, on any days. Pure functions, no Android.
 *
 * Such a habit is "due" on a day only when the rest of the week has exactly
 * enough days left for it: 3 times a week with 1 done by Saturday makes it
 * due on Saturday. Done then, it is due again on Sunday; missed, the goal can
 * no longer be reached and it stops being due, so a goal that slips costs one
 * missed day, the same as a missed daily habit, and the streak rules need
 * nothing new. On any other day it can still be done, and counts like any
 * other check-off.
 */
object WeeklyGoals {

    /** The target for the week [epochDay] falls in: the weekly target, cut down in the week the habit was made. */
    fun targetFor(habit: ScheduledHabit, epochDay: Long): Int {
        val target = habit.weeklyTarget ?: return 0
        val weekStart = Weeks.startOf(epochDay)
        val available = weekStart + Weeks.DAYS - maxOf(weekStart, habit.createdAtEpochDay)
        return target.coerceAtMost(available.toInt()).coerceAtLeast(0)
    }

    /** Days from [epochDay] to the Sunday, both counted. */
    fun daysLeft(epochDay: Long): Int = (Weeks.startOf(epochDay) + Weeks.DAYS - epochDay).toInt()

    /** True when [habit] must be done on [epochDay] to reach this week's target, with [doneBefore] done earlier in the week. */
    fun isDue(habit: ScheduledHabit, epochDay: Long, doneBefore: Int): Boolean {
        if (habit.weeklyTarget == null || habit.createdAtEpochDay > epochDay) return false
        val remaining = targetFor(habit, epochDay) - doneBefore
        return remaining > 0 && remaining == daysLeft(epochDay)
    }

    /** Check-offs of [habitId] in the week of [epochDay], before that day. */
    fun doneBefore(habitId: Long, epochDay: Long, completedByDay: Map<Long, Set<Long>>): Int =
        (Weeks.startOf(epochDay) until epochDay).count { habitId in completedByDay[it].orEmpty() }
}

/** A finished day: which habits were scheduled and which of those were not done. */
data class DayPlan(
    val epochDay: Long,
    val scheduledHabitIds: List<Long>,
    val missedHabitIds: List<Long>,
) {
    val completedCount: Int get() = scheduledHabitIds.size - missedHabitIds.size
    val outcome: DayOutcome get() = StreakRules.outcomeFor(scheduledHabitIds.size, completedCount)
}

data class DayResult(val epochDay: Long, val outcome: DayOutcome)

/** An offer to spend beans on missed days: one bean per day in [days]. */
data class BeanOffer(val days: List<Long>, val streakSaved: Int) {
    val cost: Int get() = days.size
}

/** Streak, healing bean and transformation rules. Pure functions, no Android. */
object StreakRules {

    fun outcomeFor(scheduledCount: Int, completedCount: Int): DayOutcome = when {
        scheduledCount <= 0 -> DayOutcome.REST
        completedCount >= scheduledCount -> DayOutcome.PERFECT
        else -> DayOutcome.MISSED
    }

    /**
     * Habits set for certain weekdays that are scheduled on [epochDay] and were
     * already created by then. Weekly-target habits are left out: see [countedOn].
     */
    fun scheduledOn(habits: List<ScheduledHabit>, epochDay: Long): List<ScheduledHabit> {
        val date = LocalDate.ofEpochDay(epochDay)
        return habits.filter {
            it.weeklyTarget == null && it.createdAtEpochDay <= epochDay && WeekSchedule.isScheduled(it.scheduleDays, date)
        }
    }

    /**
     * The ids of the habits that count on [epochDay]: those scheduled that
     * weekday, plus weekly-target habits that are due that day or were done
     * that day. [completedByDay] must cover the week of [epochDay] up to it.
     */
    fun countedOn(habits: List<ScheduledHabit>, epochDay: Long, completedByDay: Map<Long, Set<Long>>): List<Long> {
        val doneThatDay = completedByDay[epochDay].orEmpty()
        val weekly = habits.filter { habit ->
            habit.weeklyTarget != null && habit.createdAtEpochDay <= epochDay && (
                habit.id in doneThatDay ||
                    WeeklyGoals.isDue(habit, epochDay, WeeklyGoals.doneBefore(habit.id, epochDay, completedByDay))
                )
        }
        return scheduledOn(habits, epochDay).map { it.id } + weekly.map { it.id }
    }

    /** A day is perfect when something was scheduled and all of it was done. */
    fun isPerfect(scheduledHabitIds: Collection<Long>, completedHabitIds: Set<Long>): Boolean =
        scheduledHabitIds.isNotEmpty() && completedHabitIds.containsAll(scheduledHabitIds)

    /**
     * Works out how each day from [fromDay] to [toDay] (inclusive) went.
     * [completedByDay] maps a day to the ids of the habits checked off that day;
     * for weekly-target habits it must start from the Monday of [fromDay]'s week.
     * [excusedByDay] maps a day to the habits excused that day (Master's Day
     * Off): those do not count as scheduled.
     */
    fun planDays(
        habits: List<ScheduledHabit>,
        completedByDay: Map<Long, Set<Long>>,
        fromDay: Long,
        toDay: Long,
        excusedByDay: Map<Long, Set<Long>> = emptyMap(),
    ): List<DayPlan> = (fromDay..toDay).map { day ->
        val excused = excusedByDay[day].orEmpty()
        val scheduled = countedOn(habits, day, completedByDay).filterNot { it in excused }
        val completed = completedByDay[day].orEmpty()
        DayPlan(day, scheduled, scheduled.filterNot { it in completed })
    }

    /**
     * The overall streak: perfect days in a row, counting back from today.
     *
     * [pastNewestFirst] is the finished days, yesterday first. Rest days and
     * bean-covered days are skipped over. Today only counts once it is perfect,
     * and an unfinished today never breaks the streak.
     *
     * With [bridgeMissed], days a bean could still cover are skipped over too:
     * that is what the streak would be if they were all covered.
     */
    fun streak(
        pastNewestFirst: List<DayOutcome>,
        todayPerfect: Boolean,
        bridgeMissed: Boolean = false,
    ): Int {
        var count = if (todayPerfect) 1 else 0
        for (outcome in pastNewestFirst) {
            when (outcome) {
                DayOutcome.PERFECT -> count++
                DayOutcome.REST, DayOutcome.BEAN_COVERED -> Unit
                DayOutcome.MISSED -> if (!bridgeMissed) return count
                DayOutcome.BROKEN -> return count
            }
        }
        return count
    }

    /**
     * The bean offer for the missed days still open, or null when there is
     * nothing to offer: no open missed day, not enough beans for all of them,
     * or no streak behind them worth saving.
     */
    fun beanOffer(pastNewestFirst: List<DayResult>, beans: Int): BeanOffer? {
        val open = pastNewestFirst.takeWhile { it.outcome != DayOutcome.BROKEN }
        val missed = open.filter { it.outcome == DayOutcome.MISSED }.map { it.epochDay }
        if (missed.isEmpty() || beans < missed.size) return null
        val outcomes = open.map { it.outcome }
        val ifCovered = streak(outcomes, todayPerfect = false, bridgeMissed = true)
        val asIs = streak(outcomes, todayPerfect = false)
        return if (ifCovered > asIs) BeanOffer(missed.sorted(), ifCovered) else null
    }

    /** True when reaching [streak] earns a healing bean: at every multiple of [interval]. */
    fun earnsBean(streak: Int, interval: Int = GameConfig.BEAN_STREAK_INTERVAL): Boolean =
        streak > 0 && streak % interval == 0

    /** Beans held after earning one. Never fewer than before, never raised past [capacity]. */
    fun beansAfterEarning(beans: Int, capacity: Int = GameConfig.MAX_HEALING_BEANS): Int =
        (beans + 1).coerceAtMost(capacity.coerceAtLeast(beans))

    fun daysUntilNextBean(streak: Int, interval: Int = GameConfig.BEAN_STREAK_INTERVAL): Int =
        interval - streak.coerceAtLeast(0) % interval

    /** The highest form a streak of this length unlocks (0 = base form). */
    fun formForStreak(streak: Int): Int = GameConfig.FORM_STREAK_THRESHOLDS.count { streak >= it }

    /** Streak needed for the form after [form], or null if [form] is the last one. */
    fun nextFormThreshold(form: Int): Int? = GameConfig.FORM_STREAK_THRESHOLDS.getOrNull(form)
}
