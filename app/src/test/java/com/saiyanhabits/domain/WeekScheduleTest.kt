package com.saiyanhabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekScheduleTest {

    @Test
    fun `every day covers all seven days`() {
        for (day in DayOfWeek.entries) {
            assertTrue(WeekSchedule.isScheduled(WeekSchedule.EVERY_DAY, day))
            assertFalse(WeekSchedule.isScheduled(WeekSchedule.NONE, day))
        }
    }

    @Test
    fun `weekdays and weekend split the week`() {
        assertEquals(
            WeekSchedule.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            WeekSchedule.WEEKDAYS,
        )
        assertEquals(WeekSchedule.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), WeekSchedule.WEEKEND)
        assertEquals(WeekSchedule.EVERY_DAY, WeekSchedule.WEEKDAYS or WeekSchedule.WEEKEND)
    }

    @Test
    fun `only the chosen days are scheduled`() {
        val mask = WeekSchedule.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val scheduled = DayOfWeek.entries.filter { WeekSchedule.isScheduled(mask, it) }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), scheduled)
    }

    @Test
    fun `a date is matched by its day of week`() {
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        val mondaysOnly = WeekSchedule.of(DayOfWeek.MONDAY)
        assertTrue(WeekSchedule.isScheduled(mondaysOnly, monday))
        assertFalse(WeekSchedule.isScheduled(mondaysOnly, monday.plusDays(1)))
        assertTrue(WeekSchedule.isScheduled(mondaysOnly, monday.plusWeeks(1)))
    }

    @Test
    fun `toggle switches one day on and off`() {
        var mask = WeekSchedule.NONE
        mask = WeekSchedule.toggle(mask, DayOfWeek.SUNDAY)
        assertTrue(WeekSchedule.isScheduled(mask, DayOfWeek.SUNDAY))
        assertFalse(WeekSchedule.isScheduled(mask, DayOfWeek.SATURDAY))
        mask = WeekSchedule.toggle(mask, DayOfWeek.SUNDAY)
        assertEquals(WeekSchedule.NONE, mask)
    }

    @Test
    fun `schedules are described in words`() {
        assertEquals("Every day", WeekSchedule.describe(WeekSchedule.EVERY_DAY))
        assertEquals("Weekdays", WeekSchedule.describe(WeekSchedule.WEEKDAYS))
        assertEquals("Weekends", WeekSchedule.describe(WeekSchedule.WEEKEND))
        assertEquals("No days", WeekSchedule.describe(WeekSchedule.NONE))
        assertEquals(
            "Mon, Wed, Fri",
            WeekSchedule.describe(WeekSchedule.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        )
    }
}
