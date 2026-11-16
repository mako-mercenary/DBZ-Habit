package com.saiyanhabits.domain

import java.time.LocalDateTime
import java.time.LocalTime

/** When reminders should fire. Pure functions, no Android. */
object ReminderTimes {
    const val MINUTES_PER_DAY = 24 * 60

    fun toLocalTime(minutesAfterMidnight: Int): LocalTime {
        val minutes = minutesAfterMidnight.coerceIn(0, MINUTES_PER_DAY - 1)
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    /**
     * The next time after [after] that a habit's reminder is due: the reminder
     * time on the next scheduled day. Null if no day is scheduled.
     */
    fun nextOccurrence(after: LocalDateTime, scheduleDays: Int, reminderMinutes: Int): LocalDateTime? {
        val time = toLocalTime(reminderMinutes)
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (!WeekSchedule.isScheduled(scheduleDays, date)) continue
            val candidate = date.atTime(time)
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    /** The next time after [after] that the clock reads [hour]:00. */
    fun nextDailyAt(after: LocalDateTime, hour: Int): LocalDateTime {
        val today = after.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        return if (today.isAfter(after)) today else today.plusDays(1)
    }
}
