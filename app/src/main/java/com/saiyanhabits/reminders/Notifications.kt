package com.saiyanhabits.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saiyanhabits.MainActivity
import com.saiyanhabits.R
import com.saiyanhabits.chamber.ChamberOutcome
import com.saiyanhabits.chamber.ChamberSession
import com.saiyanhabits.chamber.TimeChamberService
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.mentorFor

object Notifications {
    private const val CHANNEL_REMINDERS = "habit_reminders"
    private const val CHANNEL_STREAK = "streak"
    private const val CHANNEL_CHAMBER = "time_chamber"
    private const val CHANNEL_CHAMBER_DONE = "time_chamber_done"
    private const val CHANNEL_EVENING = "evening_nudge"
    private const val CHANNEL_CALLS = "mentor_calls"
    private const val CALL_ID = 5
    private const val EVENING_NUDGE_ID = 4
    private const val BEAN_OFFER_ID = 1
    const val CHAMBER_RUNNING_ID = 2
    private const val CHAMBER_FINISHED_ID = 3

    // Habit ids start at 1, so reminders get ids from 1001 up and never collide with the fixed ids.
    private fun reminderId(habitId: Long): Int = 1000 + (habitId % 1_000_000).toInt()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A nudge at the time you set for each habit." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, "Streak", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "The morning offer to save your streak with a healing bean." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Mentor calls", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A mentor rings once a day. Picking up within the hour earns extra bond XP." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENING, "Evening nudge", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "One message in the evening when today's training isn't finished." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHAMBER, "Time chamber countdown", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows the time left while a focus session runs." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHAMBER_DONE, "Time chamber finished", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when a focus session is over." },
        )
    }

    /** True when the app is allowed to show notifications at all. */
    fun areEnabled(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showHabitReminder(context: Context, habit: HabitEntity) {
        val mentor = mentorFor(habit.category, habit.isHardest)
        show(
            context,
            reminderId(habit.id),
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setContentTitle(habit.name)
                .setContentText("Time to train. ${mentor.displayName} is waiting."),
        )
    }

    fun cancelHabitReminder(context: Context, habitId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId(habitId))
    }

    fun showBeanOffer(context: Context, offer: BeanOffer) {
        val missed = if (offer.cost == 1) "a day" else "${offer.cost} days"
        val beans = if (offer.cost == 1) "A healing bean" else "${offer.cost} healing beans"
        show(
            context,
            BEAN_OFFER_ID,
            NotificationCompat.Builder(context, CHANNEL_STREAK)
                .setContentTitle("You missed $missed of training")
                .setContentText("$beans can keep your ${offer.streakSaved}-day streak alive. Tap to decide."),
        )
    }

    /** A mentor's reminder that [open] habits still count today. */
    fun showEveningNudge(context: Context, mentor: com.saiyanhabits.domain.Mentor, line: String?, open: List<String>) {
        val title = if (open.size == 1) "\u201C${open.single()}\u201D is still open" else "${open.size} habits still open today"
        val said = line?.let { "${mentor.displayName}: $it" } ?: "There's still training to finish today."
        val text = if (open.size > 1) "$said\nStill open: ${open.joinToString(", ")}" else said
        show(
            context,
            EVENING_NUDGE_ID,
            NotificationCompat.Builder(context, CHANNEL_EVENING)
                .setContentTitle(title)
                .setContentText(said)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)),
        )
    }

    /** A mentor ringing. Tapping opens the app, where the call is waiting on the home screen. */
    fun showCall(context: Context, mentor: com.saiyanhabits.domain.Mentor, line: String) {
        show(
            context,
            CALL_ID,
            NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setContentTitle("${mentor.displayName} is calling")
                .setContentText(line)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$line\nPick up within the hour for extra bond XP.")),
        )
    }

    fun cancelCall(context: Context) {
        NotificationManagerCompat.from(context).cancel(CALL_ID)
    }

    fun cancelEveningNudge(context: Context) {
        NotificationManagerCompat.from(context).cancel(EVENING_NUDGE_ID)
    }

    fun cancelBeanOffer(context: Context) {
        NotificationManagerCompat.from(context).cancel(BEAN_OFFER_ID)
    }

    /** The always-there notification of a running session, with a live countdown. */
    fun chamberRunning(context: Context, session: ChamberSession?): Notification {
        val giveUp = PendingIntent.getService(
            context,
            0,
            Intent(context, TimeChamberService::class.java).setAction(TimeChamberService.ACTION_GIVE_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_CHAMBER)
            .setContentTitle("Time chamber")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (session != null) {
            builder
                .setContentText("Training: ${session.target.label}")
                .setWhen(session.endsAtMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, "Give up", giveUp)
        }
        return styled(context, builder).setAutoCancel(false).build()
    }

    fun showChamberFinished(context: Context, session: ChamberSession, outcome: ChamberOutcome) {
        val text = outcome.lines.joinToString(" ")
        show(
            context,
            CHAMBER_FINISHED_ID,
            NotificationCompat.Builder(context, CHANNEL_CHAMBER_DONE)
                .setContentTitle("${session.minutes}-minute session complete")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)),
        )
    }

    fun cancelChamberFinished(context: Context) {
        NotificationManagerCompat.from(context).cancel(CHAMBER_FINISHED_ID)
    }

    /** Icon, colour and "tap to open the app" shared by every notification. */
    private fun styled(context: Context, builder: NotificationCompat.Builder): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF8A1F.toInt())
            .setContentIntent(openApp)
            .setAutoCancel(true)
    }

    private fun show(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!areEnabled(context)) return
        val notification = styled(context, builder).build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was taken away between the check and the call: nothing to show.
        }
    }
}
