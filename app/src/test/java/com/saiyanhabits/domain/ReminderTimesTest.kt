package com.saiyanhabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTimesTest {

    // Monday 16 Nov 2026.
    private fun monday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 16, hour, minute)

    private val at0730 = 7 * 60 + 30

    @Test
    fun `minutes after midnight become a time of day`() {
        assertEquals(LocalTime.of(0, 0), ReminderTimes.toLocalTime(0))
        assertEquals(LocalTime.of(7, 30), ReminderTimes.toLocalTime(at0730))
        assertEquals(LocalTime.of(23, 59), ReminderTimes.toLocalTime(23 * 60 + 59))
    }

    @Test
    fun `out of range minutes are clamped into the day`() {
        assertEquals(LocalTime.of(0, 0), ReminderTimes.toLocalTime(-5))
        assertEquals(LocalTime.of(23, 59), ReminderTimes.toLocalTime(5000))
    }

    @Test
    fun `a reminder later today is due today`() {
        val next = ReminderTimes.nextOccurrence(monday(6), WeekSchedule.EVERY_DAY, at0730)
        assertEquals(monday(7, 30), next)
    }

    @Test
    fun `a reminder already past today is due on the next scheduled day`() {
        val next = ReminderTimes.nextOccurrence(monday(8), WeekSchedule.EVERY_DAY, at0730)
        assertEquals(monday(7, 30).plusDays(1), next)
    }

    @Test
    fun `a reminder due this very minute waits for the next day`() {
        val next = ReminderTimes.nextOccurrence(monday(7, 30), WeekSchedule.EVERY_DAY, at0730)
        assertEquals(monday(7, 30).plusDays(1), next)
    }

    @Test
    fun `unscheduled days are skipped`() {
        val thursdays = WeekSchedule.of(DayOfWeek.THURSDAY)
        val next = ReminderTimes.nextOccurrence(monday(6), thursdays, at0730)
        assertEquals(monday(7, 30).plusDays(3), next)
    }

    @Test
    fun `a once-a-week reminder already past today is due a week later`() {
        val mondays = WeekSchedule.of(DayOfWeek.MONDAY)
        val next = ReminderTimes.nextOccurrence(monday(9), mondays, at0730)
        assertEquals(monday(7, 30).plusWeeks(1), next)
    }

    @Test
    fun `no scheduled days means no reminder`() {
        assertNull(ReminderTimes.nextOccurrence(monday(6), WeekSchedule.NONE, at0730))
    }

    @Test
    fun `the morning check is at the next eight o'clock`() {
        assertEquals(monday(8), ReminderTimes.nextDailyAt(monday(6), hour = 8))
        assertEquals(monday(8).plusDays(1), ReminderTimes.nextDailyAt(monday(8), hour = 8))
        assertEquals(monday(8).plusDays(1), ReminderTimes.nextDailyAt(monday(21), hour = 8))
    }
}
