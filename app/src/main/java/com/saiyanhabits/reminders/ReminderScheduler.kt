package com.saiyanhabits.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.ReminderTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps one pending WorkManager job per habit reminder, plus one for the
 * morning streak check. Each job, when it runs, queues the next one.
 * WorkManager keeps the jobs across restarts of the phone.
 */
class ReminderScheduler(private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Call after a habit is added or edited: sets, moves or removes its reminder. */
    fun onHabitSaved(habit: HabitEntity) {
        if (habit.archived || habit.reminderMinutes == null) {
            onHabitRemoved(habit.id)
        } else {
            scheduleReminder(habit, LocalDateTime.now(), ExistingWorkPolicy.REPLACE)
        }
    }

    fun onHabitRemoved(habitId: Long) {
        workManager.cancelUniqueWork(reminderWorkName(habitId))
        Notifications.cancelHabitReminder(context, habitId)
    }

    /** Call when a habit is checked off: its reminder for today is no longer needed. */
    fun onHabitCompleted(habitId: Long) {
        Notifications.cancelHabitReminder(context, habitId)
    }

    /** Call when the healing-bean offer has been accepted or turned down. */
    fun onBeanOfferAnswered() {
        Notifications.cancelBeanOffer(context)
    }

    /**
     * Call when the app starts. Queues anything that is missing and leaves jobs
     * that are already queued or running alone.
     */
    fun ensureScheduled(habits: List<HabitEntity>) {
        val now = LocalDateTime.now()
        habits.filter { !it.archived && it.reminderMinutes != null }
            .forEach { scheduleReminder(it, now, ExistingWorkPolicy.KEEP) }
        scheduleMorningCheck(now, ExistingWorkPolicy.KEEP)
    }

    /**
     * Queues [habit]'s next reminder after [after]. A worker queueing its own
     * successor passes APPEND_OR_REPLACE, so the new job waits for it to finish.
     */
    fun scheduleReminder(habit: HabitEntity, after: LocalDateTime, policy: ExistingWorkPolicy) {
        val minutes = habit.reminderMinutes ?: return
        val dueAt = ReminderTimes.nextOccurrence(after, habit.scheduleDays, minutes)
        if (dueAt == null) {
            if (policy == ExistingWorkPolicy.REPLACE) workManager.cancelUniqueWork(reminderWorkName(habit.id))
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntil(dueAt))
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_HABIT_ID to habit.id,
                    ReminderWorker.KEY_DUE_AT_MILLIS to dueAt.toEpochMillis(),
                ),
            )
            .build()
        workManager.enqueueUniqueWork(reminderWorkName(habit.id), policy, request)
    }

    fun scheduleMorningCheck(after: LocalDateTime, policy: ExistingWorkPolicy) {
        val dueAt = ReminderTimes.nextDailyAt(after, GameConfig.MORNING_CHECK_HOUR)
        val request = OneTimeWorkRequestBuilder<MorningCheckWorker>()
            .setInitialDelay(delayUntil(dueAt))
            .build()
        workManager.enqueueUniqueWork(MORNING_CHECK_WORK, policy, request)
    }

    /**
     * Turns the evening nudge on at [minutes] after midnight, or off. With
     * [replace] a nudge already queued is moved to the new time; without it,
     * one already queued is left alone (for app start).
     */
    fun updateEveningNudge(on: Boolean, minutes: Int, replace: Boolean) {
        if (!on) {
            workManager.cancelUniqueWork(EVENING_NUDGE_WORK)
            Notifications.cancelEveningNudge(context)
            return
        }
        scheduleEveningNudge(minutes, LocalDateTime.now(), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
    }

    fun scheduleEveningNudge(minutes: Int, after: LocalDateTime, policy: ExistingWorkPolicy) {
        val dueAt = ReminderTimes.nextOccurrence(after, com.saiyanhabits.domain.WeekSchedule.EVERY_DAY, minutes) ?: return
        val request = OneTimeWorkRequestBuilder<EveningNudgeWorker>()
            .setInitialDelay(delayUntil(dueAt))
            .setInputData(workDataOf(EveningNudgeWorker.KEY_DUE_AT_MILLIS to dueAt.toEpochMillis()))
            .build()
        workManager.enqueueUniqueWork(EVENING_NUDGE_WORK, policy, request)
    }

    private fun delayUntil(time: LocalDateTime): Duration =
        Duration.between(LocalDateTime.now(), time).coerceAtLeast(Duration.ZERO)

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val MORNING_CHECK_WORK = "morning-check"
        const val EVENING_NUDGE_WORK = "evening-nudge"
        fun reminderWorkName(habitId: Long) = "habit-reminder-$habitId"
    }
}
