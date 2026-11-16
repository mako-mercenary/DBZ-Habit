package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.domain.DayResult
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.ScheduledHabit
import com.saiyanhabits.domain.StreakRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Something worth celebrating that just happened. */
sealed interface GameEvent {
    /** The overall streak reached a new form's threshold. */
    data class FormUnlocked(val form: Int, val streak: Int) : GameEvent

    /** The overall streak earned a healing bean; [beans] is how many are now held. */
    data class BeanEarned(val beans: Int) : GameEvent
}

class HabitRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val habitDao = db.habitDao()
    private val completionDao = db.completionDao()
    private val streakDao = db.streakDao()

    val activeHabits: Flow<List<HabitEntity>> = habitDao.observeActive()

    val powerLevel: Flow<Long> = completionDao.observeTotalPoints().map(PowerCalculator::powerLevel)

    /** Current streak per habit id. A habit that is missing has a streak of 0. */
    val habitStreaks: Flow<Map<Long, Int>> = completionDao.observeHabitStreaks()
        .map { rows -> rows.associate { it.habitId to it.streak } }

    /** Finished days since the streak last broke, newest first. */
    val openDays: Flow<List<DayResult>> = streakDao.observeOpenRecords()
        .map { records -> records.map { it.toResult() } }

    val playerState: Flow<PlayerStateEntity> = streakDao.observePlayerState()
        .map { it ?: PlayerStateEntity() }

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)

    /** Form unlocks and bean awards, as they happen. */
    val events: SharedFlow<GameEvent> = _events

    fun completionsOn(date: LocalDate): Flow<List<CompletionEntity>> =
        completionDao.observeForDay(date.toEpochDay())

    suspend fun getHabit(id: Long): HabitEntity? = habitDao.getById(id)

    suspend fun isCompletedOn(habitId: Long, date: LocalDate): Boolean =
        completionDao.getBetween(date.toEpochDay(), date.toEpochDay()).any { it.habitId == habitId }

    suspend fun getActiveHabits(): List<HabitEntity> = habitDao.getActive()

    /**
     * Writes a record for every day that has ended since the last one, and ends
     * the streak for missed days that no bean can save. Safe to call any time,
     * as often as you like.
     */
    suspend fun syncDays() {
        db.withTransaction { syncDaysInTransaction(today().toEpochDay()) }
    }

    suspend fun saveHabit(habit: HabitEntity): Long = celebrating { events ->
        val todayDay = today().toEpochDay()
        syncDaysInTransaction(todayDay)
        val id = habitDao.save(habit)
        events += checkMilestones(todayDay)
        id
    }

    suspend fun archiveHabit(id: Long) = celebrating { events ->
        val todayDay = today().toEpochDay()
        syncDaysInTransaction(todayDay)
        habitDao.archive(id)
        events += checkMilestones(todayDay)
    }

    /**
     * Checks a habit off for [date]. Returns the points awarded, or 0 if
     * nothing was recorded: it was already checked off, or [date] is no longer
     * today (the screen was left open past midnight).
     */
    suspend fun complete(habit: HabitEntity, date: LocalDate, gravity: Gravity): Int = celebrating { events ->
        val todayDay = today().toEpochDay()
        if (date.toEpochDay() != todayDay) return@celebrating 0
        syncDaysInTransaction(todayDay)
        val points = PowerCalculator.completionPoints(habit.difficulty, gravity)
        val rowId = completionDao.insert(
            CompletionEntity(
                habitId = habit.id,
                epochDay = todayDay,
                gravity = gravity,
                points = points,
                completedAtMillis = nowMillis(),
            ),
        )
        if (rowId == -1L) return@celebrating 0
        events += checkMilestones(todayDay)
        points
    }

    /** Takes back a check-off made by mistake, along with its points. */
    suspend fun undoCompletion(habitId: Long, date: LocalDate) {
        if (date != today()) return
        completionDao.delete(habitId, date.toEpochDay())
    }

    /** The bean offer waiting for an answer, if any. */
    suspend fun pendingBeanOffer(): BeanOffer? = db.withTransaction {
        val beans = (streakDao.getPlayerState() ?: PlayerStateEntity()).healingBeans
        StreakRules.beanOffer(streakDao.getOpenRecords().map { it.toResult() }, beans)
    }

    /** Spends beans on the open missed days so the streak survives. False if there was no offer. */
    suspend fun acceptBeanOffer(): Boolean = celebrating { events ->
        val state = streakDao.getPlayerState() ?: PlayerStateEntity()
        val offer = StreakRules.beanOffer(streakDao.getOpenRecords().map { it.toResult() }, state.healingBeans)
            ?: return@celebrating false
        streakDao.setOutcome(offer.days, DayOutcome.BEAN_COVERED)
        // The habits' own streaks are saved along with the overall one.
        streakDao.deleteMissesOn(offer.days)
        streakDao.savePlayerState(state.copy(healingBeans = state.healingBeans - offer.cost))
        events += checkMilestones(today().toEpochDay())
        true
    }

    /** Turns the bean offer down: the open missed days end the streak. */
    suspend fun declineBeanOffer() {
        db.withTransaction {
            val missed = streakDao.getOpenRecords().filter { it.outcome == DayOutcome.MISSED }
            if (missed.isNotEmpty()) streakDao.setOutcome(missed.map { it.epochDay }, DayOutcome.BROKEN)
        }
    }

    /** Runs [block] in one transaction, then announces the events it collected once that has committed. */
    private suspend fun <T> celebrating(block: suspend (MutableList<GameEvent>) -> T): T {
        val events = mutableListOf<GameEvent>()
        val result = db.withTransaction {
            events.clear()
            block(events)
        }
        events.forEach { _events.emit(it) }
        return result
    }

    private suspend fun syncDaysInTransaction(todayDay: Long) {
        val habits = habitDao.getActive()
        val firstDay = streakDao.latestRecordedDay()?.plus(1)
            ?: habits.minOfOrNull { it.createdAtEpochDay }
            ?: return
        val lastDay = todayDay - 1
        if (firstDay <= lastDay) {
            val completedByDay = completionDao.getBetween(firstDay, lastDay)
                .groupBy({ it.epochDay }, { it.habitId })
                .mapValues { it.value.toSet() }
            val plans = StreakRules.planDays(habits.map { it.toScheduled() }, completedByDay, firstDay, lastDay)
            streakDao.insertRecords(
                plans.map { DayRecordEntity(it.epochDay, it.scheduledHabitIds.size, it.completedCount, it.outcome) },
            )
            streakDao.insertMisses(
                plans.flatMap { plan -> plan.missedHabitIds.map { HabitMissEntity(it, plan.epochDay) } },
            )
        }

        // Missed days stay open only while a bean offer for them stands.
        val open = streakDao.getOpenRecords()
        val missed = open.filter { it.outcome == DayOutcome.MISSED }
        if (missed.isNotEmpty()) {
            val beans = (streakDao.getPlayerState() ?: PlayerStateEntity()).healingBeans
            if (StreakRules.beanOffer(open.map { it.toResult() }, beans) == null) {
                streakDao.setOutcome(missed.map { it.epochDay }, DayOutcome.BROKEN)
            }
        }
    }

    /** If today is perfect, hands out whatever the streak has now earned. */
    private suspend fun checkMilestones(todayDay: Long): List<GameEvent> {
        val scheduled = StreakRules.scheduledOn(habitDao.getActive().map { it.toScheduled() }, todayDay)
        val completed = completionDao.getBetween(todayDay, todayDay).map { it.habitId }.toSet()
        if (!StreakRules.isPerfect(scheduled.map { it.id }, completed)) return emptyList()

        val streak = StreakRules.streak(streakDao.getOpenRecords().map { it.outcome }, todayPerfect = true)
        val before = streakDao.getPlayerState() ?: PlayerStateEntity()
        var state = before
        val events = mutableListOf<GameEvent>()

        if (StreakRules.earnsBean(streak) && state.lastBeanAwardEpochDay != todayDay) {
            state = state.copy(
                healingBeans = StreakRules.beansAfterEarning(state.healingBeans),
                lastBeanAwardEpochDay = todayDay,
            )
            if (state.healingBeans > before.healingBeans) events += GameEvent.BeanEarned(state.healingBeans)
        }
        val form = StreakRules.formForStreak(streak)
        if (form > state.highestForm) {
            state = state.copy(highestForm = form)
            events += GameEvent.FormUnlocked(form, streak)
        }
        if (state != before) streakDao.savePlayerState(state)
        return events
    }
}

private fun HabitEntity.toScheduled() = ScheduledHabit(id, scheduleDays, createdAtEpochDay)

private fun DayRecordEntity.toResult() = DayResult(epochDay, outcome)
