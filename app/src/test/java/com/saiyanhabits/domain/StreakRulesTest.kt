package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.DayOutcome.BEAN_COVERED
import com.saiyanhabits.domain.DayOutcome.BROKEN
import com.saiyanhabits.domain.DayOutcome.MISSED
import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakRulesTest {

    // Monday 16 Nov 2026 and the days after it.
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()
    private val tuesday = monday + 1
    private val wednesday = monday + 2
    private val thursday = monday + 3

    private fun everyDay(id: Long, createdOn: Long = monday) =
        ScheduledHabit(id, WeekSchedule.EVERY_DAY, createdOn)

    /** Finished days, newest first, numbered back from a made-up "yesterday". */
    private fun days(vararg outcomes: DayOutcome): List<DayResult> =
        outcomes.mapIndexed { index, outcome -> DayResult(epochDay = 100L - index, outcome = outcome) }

    // ---- How one day went ----

    @Test
    fun `a day with everything done is perfect`() {
        assertEquals(PERFECT, StreakRules.outcomeFor(scheduledCount = 3, completedCount = 3))
    }

    @Test
    fun `a day with something left undone is missed`() {
        assertEquals(MISSED, StreakRules.outcomeFor(scheduledCount = 3, completedCount = 2))
        assertEquals(MISSED, StreakRules.outcomeFor(scheduledCount = 1, completedCount = 0))
    }

    @Test
    fun `a day with nothing scheduled is a rest day`() {
        assertEquals(REST, StreakRules.outcomeFor(scheduledCount = 0, completedCount = 0))
    }

    @Test
    fun `today is perfect only when something was scheduled and all of it is done`() {
        assertTrue(StreakRules.isPerfect(listOf(1L, 2L), setOf(1L, 2L, 9L)))
        assertFalse(StreakRules.isPerfect(listOf(1L, 2L), setOf(1L)))
        assertFalse(StreakRules.isPerfect(emptyList(), setOf(1L)))
    }

    // ---- Working out finished days ----

    @Test
    fun `finished days list which scheduled habits were missed`() {
        val plans = StreakRules.planDays(
            habits = listOf(everyDay(1), everyDay(2)),
            completedByDay = mapOf(monday to setOf(1L, 2L), tuesday to setOf(2L)),
            fromDay = monday,
            toDay = wednesday,
        )
        assertEquals(listOf(monday, tuesday, wednesday), plans.map { it.epochDay })
        assertEquals(listOf(PERFECT, MISSED, MISSED), plans.map { it.outcome })
        assertEquals(emptyList<Long>(), plans[0].missedHabitIds)
        assertEquals(listOf(1L), plans[1].missedHabitIds)
        assertEquals(listOf(1L, 2L), plans[2].missedHabitIds)
        assertEquals(listOf(2, 1, 0), plans.map { it.completedCount })
    }

    @Test
    fun `a habit only counts on the weekdays it is scheduled`() {
        val mondaysAndWednesdays = ScheduledHabit(
            id = 1,
            scheduleDays = WeekSchedule.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            createdAtEpochDay = monday,
        )
        val plans = StreakRules.planDays(
            habits = listOf(mondaysAndWednesdays),
            completedByDay = mapOf(monday to setOf(1L)),
            fromDay = monday,
            toDay = wednesday,
        )
        assertEquals(listOf(PERFECT, REST, MISSED), plans.map { it.outcome })
    }

    @Test
    fun `a habit does not count on days before it was created`() {
        val plans = StreakRules.planDays(
            habits = listOf(everyDay(1), everyDay(2, createdOn = wednesday)),
            completedByDay = mapOf(monday to setOf(1L), tuesday to setOf(1L), wednesday to setOf(1L)),
            fromDay = monday,
            toDay = wednesday,
        )
        assertEquals(listOf(PERFECT, PERFECT, MISSED), plans.map { it.outcome })
        assertEquals(listOf(2L), plans[2].missedHabitIds)
    }

    @Test
    fun `check-offs of unscheduled habits do not make a rest day perfect`() {
        val weekendsOnly = ScheduledHabit(1, WeekSchedule.WEEKEND, monday)
        val plans = StreakRules.planDays(listOf(weekendsOnly), mapOf(monday to setOf(1L)), monday, monday)
        assertEquals(REST, plans.single().outcome)
    }

    @Test
    fun `no days are planned when the range is empty`() {
        assertTrue(StreakRules.planDays(listOf(everyDay(1)), emptyMap(), thursday, monday).isEmpty())
    }

    // ---- Overall streak ----

    @Test
    fun `streak counts perfect days back from yesterday`() {
        val past = listOf(PERFECT, PERFECT, PERFECT)
        assertEquals(3, StreakRules.streak(past, todayPerfect = false))
    }

    @Test
    fun `today adds one once it is perfect and never breaks the streak before then`() {
        val past = listOf(PERFECT, PERFECT)
        assertEquals(2, StreakRules.streak(past, todayPerfect = false))
        assertEquals(3, StreakRules.streak(past, todayPerfect = true))
    }

    @Test
    fun `a brand new player has no streak`() {
        assertEquals(0, StreakRules.streak(emptyList(), todayPerfect = false))
        assertEquals(1, StreakRules.streak(emptyList(), todayPerfect = true))
    }

    @Test
    fun `a missed day stops the count`() {
        assertEquals(2, StreakRules.streak(listOf(PERFECT, PERFECT, MISSED, PERFECT, PERFECT, PERFECT), false))
        assertEquals(0, StreakRules.streak(listOf(MISSED, PERFECT, PERFECT), false))
        assertEquals(1, StreakRules.streak(listOf(BROKEN, PERFECT, PERFECT), true))
    }

    @Test
    fun `rest days carry the streak without adding to it`() {
        assertEquals(3, StreakRules.streak(listOf(REST, REST, PERFECT, REST, PERFECT, PERFECT), false))
    }

    @Test
    fun `a bean-covered day carries the streak without adding to it`() {
        assertEquals(4, StreakRules.streak(listOf(PERFECT, BEAN_COVERED, PERFECT, PERFECT, PERFECT), false))
    }

    @Test
    fun `bridging shows what the streak would be if open missed days were covered`() {
        val past = listOf(PERFECT, MISSED, PERFECT, PERFECT, BROKEN, PERFECT)
        assertEquals(1, StreakRules.streak(past, todayPerfect = false))
        assertEquals(3, StreakRules.streak(past, todayPerfect = false, bridgeMissed = true))
    }

    // ---- Earning beans ----

    @Test
    fun `a bean is earned at every seventh day of a streak`() {
        val earningStreaks = (0..30).filter { StreakRules.earnsBean(it) }
        assertEquals(listOf(7, 14, 21, 28), earningStreaks)
    }

    @Test
    fun `at most three beans are held`() {
        assertEquals(1, StreakRules.beansAfterEarning(0))
        assertEquals(2, StreakRules.beansAfterEarning(1))
        assertEquals(3, StreakRules.beansAfterEarning(2))
        assertEquals(3, StreakRules.beansAfterEarning(3))
        assertEquals(3, GameConfig.MAX_HEALING_BEANS)
    }

    @Test
    fun `days until the next bean counts down through the week`() {
        assertEquals(7, StreakRules.daysUntilNextBean(0))
        assertEquals(1, StreakRules.daysUntilNextBean(6))
        assertEquals(7, StreakRules.daysUntilNextBean(7))
        assertEquals(4, StreakRules.daysUntilNextBean(10))
    }

    // ---- Spending beans ----

    @Test
    fun `a missed day with a streak behind it is offered a bean`() {
        val offer = StreakRules.beanOffer(days(MISSED, PERFECT, PERFECT, PERFECT), beans = 1)
        assertEquals(BeanOffer(days = listOf(100L), streakSaved = 3), offer)
        assertEquals(1, offer?.cost)
    }

    @Test
    fun `no offer without a bean`() {
        assertNull(StreakRules.beanOffer(days(MISSED, PERFECT, PERFECT), beans = 0))
    }

    @Test
    fun `no offer when there is no streak to save`() {
        assertNull(StreakRules.beanOffer(days(MISSED), beans = 3))
        assertNull(StreakRules.beanOffer(days(MISSED, BROKEN, PERFECT, PERFECT), beans = 3))
        assertNull(StreakRules.beanOffer(days(MISSED, REST, REST), beans = 3))
    }

    @Test
    fun `no offer when nothing was missed`() {
        assertNull(StreakRules.beanOffer(days(PERFECT, PERFECT), beans = 3))
        assertNull(StreakRules.beanOffer(emptyList(), beans = 3))
    }

    @Test
    fun `several missed days cost one bean each`() {
        val past = days(MISSED, MISSED, PERFECT, PERFECT)
        assertNull(StreakRules.beanOffer(past, beans = 1))
        val offer = StreakRules.beanOffer(past, beans = 2)
        assertEquals(2, offer?.cost)
        assertEquals(listOf(99L, 100L), offer?.days)
        assertEquals(2, offer?.streakSaved)
    }

    @Test
    fun `rest days after a miss do not hide the offer`() {
        // Missed Friday, nothing scheduled over the weekend, app opened Monday.
        val offer = StreakRules.beanOffer(days(REST, REST, MISSED, PERFECT, PERFECT), beans = 1)
        assertEquals(listOf(98L), offer?.days)
        assertEquals(2, offer?.streakSaved)
    }

    @Test
    fun `an older open miss can still be covered after a good day`() {
        val offer = StreakRules.beanOffer(days(PERFECT, MISSED, PERFECT, PERFECT), beans = 1)
        assertEquals(listOf(99L), offer?.days)
        assertEquals(3, offer?.streakSaved)
    }

    @Test
    fun `misses from before the streak last broke are never offered`() {
        assertNull(StreakRules.beanOffer(days(PERFECT, BROKEN, MISSED, PERFECT), beans = 3))
    }

    @Test
    fun `covering the offered days gives the streak the offer promised`() {
        val past = days(MISSED, PERFECT, PERFECT, PERFECT, PERFECT)
        val offer = StreakRules.beanOffer(past, beans = 1)!!
        val covered = past.map { if (it.epochDay in offer.days) BEAN_COVERED else it.outcome }
        assertEquals(offer.streakSaved, StreakRules.streak(covered, todayPerfect = false))
        val declined = past.map { if (it.epochDay in offer.days) BROKEN else it.outcome }
        assertEquals(0, StreakRules.streak(declined, todayPerfect = false))
    }

    // ---- Transformations ----

    @Test
    fun `forms unlock at streaks of 7, 30 and 100`() {
        assertEquals(listOf(7, 30, 100), GameConfig.FORM_STREAK_THRESHOLDS)
        assertEquals(0, StreakRules.formForStreak(0))
        assertEquals(0, StreakRules.formForStreak(6))
        assertEquals(1, StreakRules.formForStreak(7))
        assertEquals(1, StreakRules.formForStreak(29))
        assertEquals(2, StreakRules.formForStreak(30))
        assertEquals(2, StreakRules.formForStreak(99))
        assertEquals(3, StreakRules.formForStreak(100))
        assertEquals(3, StreakRules.formForStreak(365))
    }

    @Test
    fun `next form threshold follows the current form`() {
        assertEquals(7, StreakRules.nextFormThreshold(0))
        assertEquals(30, StreakRules.nextFormThreshold(1))
        assertEquals(100, StreakRules.nextFormThreshold(2))
        assertNull(StreakRules.nextFormThreshold(3))
    }

    @Test
    fun `every form has a name`() {
        assertEquals(3, Forms.HIGHEST)
        assertEquals(4, (Forms.BASE..Forms.HIGHEST).map { Forms.name(it) }.toSet().size)
    }
}
