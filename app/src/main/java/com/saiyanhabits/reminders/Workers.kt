package com.saiyanhabits.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.saiyanhabits.SaiyanApp
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Shows one habit's reminder if the habit still needs doing today, then queues the next one. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SaiyanApp
        val habitId = inputData.getLong(KEY_HABIT_ID, -1)
        val habit = app.habitRepository.getHabit(habitId)
        if (habit == null || habit.archived || habit.reminderMinutes == null) return Result.success()

        val now = LocalDateTime.now()
        // The phone may have been off: a reminder that comes out hours late is just noise.
        val lateBy = System.currentTimeMillis() - inputData.getLong(KEY_DUE_AT_MILLIS, 0)
        val onTime = lateBy < TimeUnit.HOURS.toMillis(MAX_LATE_HOURS)
        if (onTime && app.habitRepository.needsDoingToday(habit)) {
            Notifications.showHabitReminder(applicationContext, habit)
        }

        // A minute's margin so a job that started a moment early can't pick the same time again.
        app.reminders.scheduleReminder(habit, now.plusMinutes(1), ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        const val KEY_HABIT_ID = "habitId"
        const val KEY_DUE_AT_MILLIS = "dueAtMillis"
        private const val MAX_LATE_HOURS = 3L
    }
}

/**
 * Runs each morning: closes off the days that have ended and, if yesterday
 * was missed and a healing bean could save the streak, says so.
 */
class MorningCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SaiyanApp
        app.habitRepository.syncDays()
        val offer = app.habitRepository.pendingBeanOffer()
        if (offer != null) Notifications.showBeanOffer(applicationContext, offer)

        app.reminders.scheduleMorningCheck(
            LocalDateTime.now().plusMinutes(1),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
        return Result.success()
    }
}
