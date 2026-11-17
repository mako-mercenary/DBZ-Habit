package com.saiyanhabits.data

import android.content.Context
import androidx.core.content.edit
import com.saiyanhabits.domain.PastDay
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.RecapRules
import com.saiyanhabits.domain.TrainedHabit
import com.saiyanhabits.domain.WeekRecap
import com.saiyanhabits.domain.Weeks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** One day looked at closely from the history calendar. */
data class DayDetail(
    val epochDay: Long,
    /** Null for today, which has not finished yet. */
    val record: PastDay?,
    val points: Int,
    /** Names of the habits checked off that day. */
    val checkOffs: List<String>,
)

/** Reads the past for the history screen and the weekly recap. Writes nothing to the database. */
class HistoryRepository(
    context: Context,
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val prefs = context.getSharedPreferences("weekly_recap", Context.MODE_PRIVATE)

    /** Every finished day, oldest first. */
    val pastDays: Flow<List<PastDay>> = db.streakDao().observeAllRecords().map { rows -> rows.map { it.toPastDay() } }

    /** Power earned per day for all time. Days with none are missing from the map. */
    val dailyPoints: Flow<Map<Long, Int>> = db.powerDao().observeDailyPoints(Long.MIN_VALUE, Long.MAX_VALUE)
        .map { rows -> rows.associate { it.epochDay to it.points } }

    suspend fun dayDetail(epochDay: Long): DayDetail {
        val names = db.habitDao().getAll().associate { it.id to it.name }
        val completions = db.completionDao().getBetween(epochDay, epochDay).sortedBy { it.completedAtMillis }
        return DayDetail(
            epochDay = epochDay,
            record = db.streakDao().getRecordsBetween(epochDay, epochDay).firstOrNull()?.toPastDay(),
            points = db.powerDao().observeDailyPoints(epochDay, epochDay).first().sumOf { it.points },
            checkOffs = completions.map { names[it.habitId] ?: "A removed habit" },
        )
    }

    /** How the week starting on Monday [weekStart] went. */
    suspend fun recap(weekStart: Long): WeekRecap {
        val sunday = weekStart + Weeks.DAYS - 1
        val days = db.streakDao().getAllRecords().map { it.toPastDay() }
        val byDay = days.associateBy { it.epochDay }
        val week = (weekStart..sunday).mapNotNull { byDay[it] }
        val completions = db.completionDao().getBetween(weekStart, sunday)
        val names = db.habitDao().getAll().associate { it.id to it.name }
        val points = db.powerDao().observeDailyPoints(weekStart - Weeks.DAYS, sunday).first()
            .associate { it.epochDay to it.points }
        val sources = db.powerDao().countBySource(weekStart, sunday).associate { it.source to it.count }

        val top = completions.groupingBy { it.habitId }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
        return WeekRecap(
            weekStart = weekStart,
            days = (weekStart..sunday).map { byDay[it]?.outcome },
            scheduled = week.sumOf { it.scheduled },
            completed = week.sumOf { it.completed },
            powerGained = (weekStart..sunday).sumOf { points[it] ?: 0 },
            rivalPower = (weekStart - Weeks.DAYS until weekStart).sumOf { points[it] ?: 0 },
            streakAtEnd = RecapRules.streakAt(sunday, days),
            topHabit = top?.let { TrainedHabit(names[it.key] ?: "A removed habit", it.value) },
            bondXp = completions
                .filter { it.mentor != null && it.bondXp > 0 }
                .groupBy { it.mentor!! }
                .mapValues { (_, list) -> list.sumOf { it.bondXp } },
            milestonesHit = sources[PowerSource.MILESTONE] ?: 0,
            bossesDefeated = sources[PowerSource.SAGA_VICTORY] ?: 0,
            bombsThrown = sources[PowerSource.BOMB_THROW] ?: 0,
            wishOrbs = db.streakDao().getPlayerState()?.wishOrbs ?: 0,
        )
    }

    /** Monday of last week, if its recap should open by itself now; see [RecapRules.dueWeek]. */
    suspend fun dueRecapWeek(): Long? {
        val todayDay = today().toEpochDay()
        val lastWeek = Weeks.startOf(todayDay) - Weeks.DAYS
        val shown = prefs.getLong(KEY_LAST_SHOWN, NONE).takeIf { it != NONE }
        if (shown != null && shown >= lastWeek) return null
        return RecapRules.dueWeek(todayDay, shown, hadActivity(lastWeek))
    }

    /** Remembers that the recap of the week starting [weekStart] has been seen. */
    fun markRecapShown(weekStart: Long) {
        val shown = prefs.getLong(KEY_LAST_SHOWN, NONE)
        if (shown == NONE || weekStart > shown) prefs.edit { putLong(KEY_LAST_SHOWN, weekStart) }
    }

    /** True when a week had something scheduled on a finished day, or earned any power. */
    private suspend fun hadActivity(weekStart: Long): Boolean {
        val sunday = weekStart + Weeks.DAYS - 1
        return db.streakDao().getRecordsBetween(weekStart, sunday).any { it.scheduledCount > 0 } ||
            db.powerDao().observeDailyPoints(weekStart, sunday).first().any { it.points > 0 }
    }

    private companion object {
        const val KEY_LAST_SHOWN = "last_shown_week"
        const val NONE = Long.MIN_VALUE
    }
}

private fun DayRecordEntity.toPastDay() = PastDay(epochDay, scheduledCount, completedCount, outcome)
