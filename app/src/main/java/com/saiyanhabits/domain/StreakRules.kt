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
data class ScheduledHabit(val id: Long, val scheduleDays: Int, val createdAtEpochDay: Long)

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

    /** Habits that count on [epochDay]: scheduled for that weekday and already created by then. */
    fun scheduledOn(habits: List<ScheduledHabit>, epochDay: Long): List<ScheduledHabit> {
        val date = LocalDate.ofEpochDay(epochDay)
        return habits.filter {
            it.createdAtEpochDay <= epochDay && WeekSchedule.isScheduled(it.scheduleDays, date)
        }
    }

    /** A day is perfect when something was scheduled and all of it was done. */
    fun isPerfect(scheduledHabitIds: Collection<Long>, completedHabitIds: Set<Long>): Boolean =
        scheduledHabitIds.isNotEmpty() && completedHabitIds.containsAll(scheduledHabitIds)

    /**
     * Works out how each day from [fromDay] to [toDay] (inclusive) went.
     * [completedByDay] maps a day to the ids of the habits checked off that day.
     */
    fun planDays(
        habits: List<ScheduledHabit>,
        completedByDay: Map<Long, Set<Long>>,
        fromDay: Long,
        toDay: Long,
    ): List<DayPlan> = (fromDay..toDay).map { day ->
        val scheduled = scheduledOn(habits, day).map { it.id }
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

    /** True when reaching [streak] earns a healing bean. */
    fun earnsBean(streak: Int): Boolean =
        streak > 0 && streak % GameConfig.BEAN_STREAK_INTERVAL == 0

    fun beansAfterEarning(beans: Int, capacity: Int = GameConfig.MAX_HEALING_BEANS): Int =
        (beans + 1).coerceAtMost(capacity)

    fun daysUntilNextBean(streak: Int): Int =
        GameConfig.BEAN_STREAK_INTERVAL - streak.coerceAtLeast(0) % GameConfig.BEAN_STREAK_INTERVAL

    /** The highest form a streak of this length unlocks (0 = base form). */
    fun formForStreak(streak: Int): Int = GameConfig.FORM_STREAK_THRESHOLDS.count { streak >= it }

    /** Streak needed for the form after [form], or null if [form] is the last one. */
    fun nextFormThreshold(form: Int): Int? = GameConfig.FORM_STREAK_THRESHOLDS.getOrNull(form)
}
