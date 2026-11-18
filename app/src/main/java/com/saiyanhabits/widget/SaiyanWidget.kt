package com.saiyanhabits.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.saiyanhabits.MainActivity
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.TodayEntry
import com.saiyanhabits.domain.Gravity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * The home-screen widget: power level, streak and today's habits. Tapping a
 * habit checks it off at normal gravity (or adds one to a counted habit);
 * tapping anything else opens the app.
 */
class SaiyanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        inBackground { WidgetUpdater.update(context, closeFinishedDays = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resized: show as many habits as now fit.
        inBackground { WidgetUpdater.update(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CHECK) return
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1)
        inBackground {
            check(context, habitId)
            WidgetUpdater.update(context)
        }
    }

    /** Runs [block] off the main thread, keeping the receiver alive until it ends. */
    private fun inBackground(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun check(context: Context, habitId: Long) {
        val app = context.applicationContext as SaiyanApp
        val habit = app.habitRepository.getHabit(habitId) ?: return
        if (habit.archived) return
        val entry = app.habitRepository.todaySnapshot().firstOrNull { it.habit.id == habitId } ?: return
        if (entry.done) return
        val today = LocalDate.now()
        if (habit.isCounted && entry.count + 1 < habit.dailyTarget) {
            app.habitRepository.addToCount(habit, today)
        } else if (app.habitRepository.complete(habit, today, Gravity.NORMAL) > 0) {
            app.reminders.onHabitCompleted(habit.id)
        }
    }

    companion object {
        const val ACTION_CHECK = "com.saiyanhabits.widget.CHECK"
        const val EXTRA_HABIT_ID = "habitId"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** Draws every placed widget from the current data. */
object WidgetUpdater {

    /** Height of the header, the progress line and the frame, in dp. */
    private const val CHROME_DP = 92

    /** Height of one habit row with its gap, in dp. */
    private const val ROW_DP = 46

    suspend fun update(context: Context, closeFinishedDays: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SaiyanWidget::class.java))
        if (ids.isEmpty()) return
        val app = context.applicationContext as SaiyanApp
        // After midnight, yesterday needs recording before the streak is right.
        if (closeFinishedDays) app.habitRepository.syncDays()
        val entries = app.habitRepository.todaySnapshot()
        val power = app.habitRepository.powerLevelNow()
        val streak = app.habitRepository.currentStreak()
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            // The tallest the launcher may draw it (portrait); fall back to the default size.
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 180
            val rows = ((height - CHROME_DP) / ROW_DP).coerceIn(1, 8)
            manager.updateAppWidget(id, views(context, entries, power, streak, rows))
        }
    }

    private fun views(context: Context, entries: List<TodayEntry>, power: Long, streak: Int, maxRows: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_power, "POWER " + String.format(Locale.US, "%,d", power))
        views.setTextViewText(R.id.widget_streak, "STREAK $streak")
        val counted = entries.filter { it.countsToday }
        val done = counted.count { it.done }
        views.setTextViewText(
            R.id.widget_progress,
            when {
                entries.isEmpty() -> "Nothing to train today"
                counted.isEmpty() -> "Rest day · weekly goals below"
                done == counted.size -> "All done today!"
                else -> "$done of ${counted.size} done today"
            },
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_header, openApp)

        views.removeAllViews(R.id.widget_list)
        val shown = entries.take(maxRows)
        for (entry in shown) views.addView(R.id.widget_list, row(context, entry, openApp))

        val more = entries.size - shown.size
        val footer = when {
            entries.isEmpty() -> "Tap to open the app"
            more > 0 -> "+$more more in the app"
            else -> null
        }
        views.setViewVisibility(R.id.widget_footer, if (footer == null) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_footer, footer ?: "")
        return views
    }

    private fun row(context: Context, entry: TodayEntry, openApp: PendingIntent): RemoteViews {
        val habit = entry.habit
        val row = RemoteViews(context.packageName, R.layout.widget_row)
        row.setTextViewText(R.id.row_name, habit.name)
        row.setTextColor(R.id.row_name, if (entry.done) 0xFFB4C1E4.toInt() else 0xFFFFFFFF.toInt())
        val detail = when {
            habit.isCounted && !entry.done -> "${entry.count} of ${habit.dailyTarget} ${habit.unit.ifBlank { "times" }}"
            entry.weekTarget != null -> "${entry.weekDone} of ${entry.weekTarget} this week" +
                if (entry.countsToday && !entry.done) " · due today" else ""
            else -> null
        }
        row.setViewVisibility(R.id.row_detail, if (detail == null) View.GONE else View.VISIBLE)
        row.setTextViewText(R.id.row_detail, detail ?: "")
        row.setImageViewResource(R.id.row_check, if (entry.done) R.drawable.widget_check_done else R.drawable.widget_check_open)
        if (entry.done) {
            row.setOnClickPendingIntent(R.id.row_root, openApp)
        } else {
            val check = PendingIntent.getBroadcast(
                context,
                // One pending intent per habit, so each row checks off its own.
                (habit.id % Int.MAX_VALUE).toInt(),
                Intent(context, SaiyanWidget::class.java)
                    .setAction(SaiyanWidget.ACTION_CHECK)
                    .putExtra(SaiyanWidget.EXTRA_HABIT_ID, habit.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            row.setOnClickPendingIntent(R.id.row_root, check)
        }
        return row
    }
}
