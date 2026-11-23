package com.cdlstudy.domain

import com.cdlstudy.reminder.Reminders
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {

    private fun hours(ms: Long) = Duration.ofMillis(ms).toHours()

    @Test
    fun `a time later today is today`() {
        val now = LocalDateTime.of(2026, 11, 23, 9, 0)
        assertEquals(10, hours(Reminders.delayUntil(LocalTime.of(19, 0), now)))
    }

    @Test
    fun `a time already past is tomorrow`() {
        val now = LocalDateTime.of(2026, 11, 23, 21, 0)
        assertEquals(22, hours(Reminders.delayUntil(LocalTime.of(19, 0), now)))
    }

    @Test
    fun `the very same minute waits a full day, instead of firing at once`() {
        val now = LocalDateTime.of(2026, 11, 23, 19, 0)
        val delay = Reminders.delayUntil(LocalTime.of(19, 0), now)
        assertEquals(24, hours(delay))
    }

    @Test
    fun `the delay is never negative`() {
        val now = LocalDateTime.of(2026, 11, 23, 23, 59)
        (0..23).forEach { hour ->
            assertTrue(Reminders.delayUntil(LocalTime.of(hour, 0), now) > 0)
        }
    }

    @Test
    fun `a streak at risk is the headline`() {
        val streak = Streak.Result(current = 4, longest = 6, studiedToday = false, days = emptyList())
        val step = NextStep.Study("5.1.2", "Air Compressor Governor", 5, true)
        val (title, body) = Reminders.text(step, streak)
        assertEquals("Keep your streak: 4 days", title)
        assertEquals("5.1.2 Air Compressor Governor", body)
    }

    @Test
    fun `one day reads as one day, not one days`() {
        val streak = Streak.Result(1, 1, false, emptyList())
        assertEquals("Keep your streak: 1 day", Reminders.text(NextStep.AllDone, streak).first)
    }

    @Test
    fun `studying today changes the question`() {
        val streak = Streak.Result(3, 3, studiedToday = true, days = emptyList())
        assertEquals("Another one today?", Reminders.text(NextStep.AllDone, streak).first)
    }

    @Test
    fun `with no streak it simply says it is time`() {
        val streak = Streak.Result(0, 0, false, emptyList())
        assertEquals("Time to study", Reminders.text(NextStep.ExportCards(9), streak).first)
        assertEquals("9 cards to export to Anki", Reminders.text(NextStep.ExportCards(9), streak).second)
    }

    @Test
    fun `an exam and a finished plan both have a body`() {
        val streak = Streak.Result(0, 0, false, emptyList())
        assertEquals(
            "Air Brakes exam",
            Reminders.text(NextStep.TakeExam(Track.AIR_BRAKES, 70), streak).second,
        )
        assertTrue(Reminders.text(NextStep.AllDone, streak).second.contains("mastery"))
    }
}
