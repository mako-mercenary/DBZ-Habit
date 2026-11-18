package com.saiyanhabits.domain

import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import com.saiyanhabits.domain.DayOutcome.MISSED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyGoalsTest {

    // Monday 16 Nov 2026.
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()
    private val sunday = monday + 6

    /** 3 times a week, made well before this week. */
    private val threeAWeek = ScheduledHabit(id = 1, scheduleDays = WeekSchedule.EVERY_DAY, createdAtEpochDay = monday - 30, weeklyTarget = 3)
    private val daily = ScheduledHabit(id = 2, scheduleDays = WeekSchedule.EVERY_DAY, createdAtEpochDay = monday - 30)

    private fun done(vararg pairs: Pair<Long, Long>): Map<Long, Set<Long>> =
        pairs.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    @Test
    fun `days left counts from the day to Sunday`() {
        assertEquals(7, WeeklyGoals.daysLeft(monday))
        assertEquals(1, WeeklyGoals.daysLeft(sunday))
        assertEquals(3, WeeklyGoals.daysLeft(monday + 4))
    }

    @Test
    fun `a weekly goal is only due once the week has no slack left`() {
        // Nothing done: due from Friday (3 needed, 3 days left).
        for (day in monday..monday + 3) assertFalse(WeeklyGoals.isDue(threeAWeek, day, doneBefore = 0))
        assertTrue(WeeklyGoals.isDue(threeAWeek, monday + 4, doneBefore = 0))
        // One done by Saturday: due Saturday (2 needed, 2 days left).
        assertFalse(WeeklyGoals.isDue(threeAWeek, monday + 4, doneBefore = 1))
        assertTrue(WeeklyGoals.isDue(threeAWeek, monday + 5, doneBefore = 1))
        // Out of reach (2 needed, 1 day left): no longer due.
        assertFalse(WeeklyGoals.isDue(threeAWeek, sunday, doneBefore = 1))
        // Goal reached: never due.
        assertFalse(WeeklyGoals.isDue(threeAWeek, sunday, doneBefore = 3))
    }

    @Test
    fun `the first week's target is cut to the days left after the habit was made`() {
        val madeSaturday = threeAWeek.copy(createdAtEpochDay = monday + 5)
        assertEquals(2, WeeklyGoals.targetFor(madeSaturday, monday + 5))
        assertTrue(WeeklyGoals.isDue(madeSaturday, monday + 5, doneBefore = 0))
        assertFalse(WeeklyGoals.isDue(madeSaturday, monday + 4, doneBefore = 0))
        // The week after, the full target applies.
        assertEquals(3, WeeklyGoals.targetFor(madeSaturday, monday + 7))
        val madeWednesday = threeAWeek.copy(createdAtEpochDay = monday + 2)
        assertEquals(3, WeeklyGoals.targetFor(madeWednesday, monday + 2))
    }

    @Test
    fun `done before counts only earlier days of the same week`() {
        val completed = done(monday - 1 to 1L, monday to 1L, monday + 2 to 1L, monday + 2 to 2L, monday + 3 to 1L)
        assertEquals(2, WeeklyGoals.doneBefore(1, monday + 3, completed))
        assertEquals(0, WeeklyGoals.doneBefore(1, monday, completed))
        assertEquals(3, WeeklyGoals.doneBefore(1, monday + 6, completed))
    }

    @Test
    fun `a weekly goal counts on a day it is due or done, and set-day habits as before`() {
        // Tuesday, done: counts. Wednesday, not done and not due: doesn't.
        val completed = done(monday + 1 to 1L)
        assertEquals(listOf(2L, 1L), StreakRules.countedOn(listOf(threeAWeek, daily), monday + 1, completed))
        assertEquals(listOf(2L), StreakRules.countedOn(listOf(threeAWeek, daily), monday + 2, completed))
        // Saturday with one done: due.
        assertEquals(listOf(1L), StreakRules.countedOn(listOf(threeAWeek), monday + 5, completed))
        // A weekly goal is never in the plain weekday schedule.
        assertTrue(StreakRules.scheduledOn(listOf(threeAWeek), monday).isEmpty())
    }

    @Test
    fun `a week of a weekly goal plays out as rest, perfect and missed days`() {
        // Done Monday only, then nothing: due Saturday (2 needed, 2 days left) and missed there.
        // After that the goal is out of reach, so Sunday is a rest day: one slip, one missed day.
        val completed = done(monday to 1L)
        val plans = StreakRules.planDays(listOf(threeAWeek), completed, monday, sunday)
        assertEquals(
            listOf(PERFECT, REST, REST, REST, REST, MISSED, REST),
            plans.map { it.outcome },
        )
        assertEquals(listOf(1L), plans[5].missedHabitIds)
    }

    @Test
    fun `a week with nothing done costs one missed day`() {
        val plans = StreakRules.planDays(listOf(threeAWeek), emptyMap(), monday, sunday)
        assertEquals(List(4) { REST } + MISSED + List(2) { REST }, plans.map { it.outcome })
    }

    @Test
    fun `keeping up on the tight days makes each of them count`() {
        // Due Friday with none done; done Friday, Saturday and Sunday: three perfect days.
        val completed = done(monday + 4 to 1L, monday + 5 to 1L, monday + 6 to 1L)
        val plans = StreakRules.planDays(listOf(threeAWeek), completed, monday, sunday)
        assertEquals(List(4) { REST } + List(3) { PERFECT }, plans.map { it.outcome })
    }

    @Test
    fun `a goal met early leaves the rest of the week as rest days`() {
        val completed = done(monday to 1L, monday + 1 to 1L, monday + 2 to 1L)
        val plans = StreakRules.planDays(listOf(threeAWeek), completed, monday, sunday)
        assertEquals(List(3) { PERFECT } + List(4) { REST }, plans.map { it.outcome })
    }

    @Test
    fun `days planned mid-week still see the check-offs from earlier in the week`() {
        // Planning only Saturday and Sunday, with Monday and Tuesday done earlier.
        val completed = done(monday to 1L, monday + 1 to 1L)
        val plans = StreakRules.planDays(listOf(threeAWeek), completed, monday + 5, sunday)
        // One more needed, two days left: not due Saturday; due Sunday and missed.
        assertEquals(listOf(REST, MISSED), plans.map { it.outcome })
    }

    @Test
    fun `habit descriptions cover weekly goals and counts`() {
        assertEquals("Every day", WeekSchedule.describeHabit(WeekSchedule.EVERY_DAY, null, 1, ""))
        assertEquals("3 times a week", WeekSchedule.describeHabit(WeekSchedule.EVERY_DAY, 3, 1, ""))
        assertEquals("Once a week", WeekSchedule.describeHabit(WeekSchedule.EVERY_DAY, 1, 1, ""))
        assertEquals("Weekdays · 8 glasses each time", WeekSchedule.describeHabit(WeekSchedule.WEEKDAYS, null, 8, "glasses"))
        assertEquals("Twice a week · 5 times each time", WeekSchedule.describeHabit(WeekSchedule.EVERY_DAY, 2, 5, " "))
    }
}
