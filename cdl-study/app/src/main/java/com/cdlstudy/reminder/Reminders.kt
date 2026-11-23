package com.cdlstudy.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cdlstudy.CdlApp
import com.cdlstudy.MainActivity
import com.cdlstudy.R
import com.cdlstudy.domain.NextStep
import com.cdlstudy.domain.NextStepRules
import com.cdlstudy.domain.Streak
import com.cdlstudy.domain.Track
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One notification a day, at a time you choose, naming the next thing to do.
 *
 * It is off until you switch it on. The work is scheduled with WorkManager, so
 * it survives a reboot, and it runs even with the app closed.
 */
object Reminders {

    const val CHANNEL = "study-reminder"
    private const val WORK = "daily-study-reminder"
    const val NOTIFICATION_ID = 1

    /** Milliseconds from now until the next time the clock reads [at]. */
    fun delayUntil(at: LocalTime, now: LocalDateTime): Long {
        var next = now.toLocalDate().atTime(at)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    fun schedule(context: Context, at: LocalTime, now: LocalDateTime = LocalDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.ofMillis(delayUntil(at, now)))
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            "Study reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "One reminder a day to do your recall." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The two lines the notification shows. */
    fun text(step: NextStep, streak: Streak.Result): Pair<String, String> {
        val title = when {
            streak.atRisk && streak.current == 1 -> "Keep your streak: 1 day"
            streak.atRisk -> "Keep your streak: ${streak.current} days"
            streak.studiedToday -> "Another one today?"
            else -> "Time to study"
        }
        val body = when (step) {
            is NextStep.Study -> "${step.subsectionId} ${step.title}"
            is NextStep.TakeExam -> "${step.track.label} exam"
            is NextStep.ExportCards -> "${step.count} cards to export to Anki"
            NextStep.AllDone -> "Everything is at mastery. Keep it warm."
            NextStep.NothingWritten -> "Open the app to start."
        }
        return title to body
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String) {
        val allowed = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Runs once a day and puts up the reminder. */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CdlApp ?: return Result.success()
        val repo = app.repository
        val progress = repo.progressNow().associate { it.subsectionId to it.status }
        val bests = Track.entries.associateWith { repo.bestPercentNow(it) }
        val step = NextStepRules.next(
            repo.content,
            { progress[it] },
            { bests[it] },
            repo.pendingCardCountNow(),
        )
        val streak = Streak.of(repo.recallTimesNow(), LocalDate.now(ZoneId.systemDefault()))
        val (title, body) = Reminders.text(step, streak)
        Reminders.show(applicationContext, title, body)
        return Result.success()
    }
}
