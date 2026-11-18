package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.domain.DayResult
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.mentorFor
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.ScheduledHabit
import com.saiyanhabits.domain.StoryRules
import com.saiyanhabits.domain.StreakRules
import com.saiyanhabits.domain.Weeks
import com.saiyanhabits.domain.WishRules
import com.saiyanhabits.domain.WeekSchedule
import com.saiyanhabits.domain.WeeklyGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Something worth celebrating that just happened. */
sealed interface GameEvent {
    /** The overall streak reached a new form's threshold. */
    data class FormUnlocked(val form: Int, val streak: Int) : GameEvent

    /** The overall streak earned a healing bean; [beans] is how many are now held, out of [capacity]. */
    data class BeanEarned(val beans: Int, val capacity: Int) : GameEvent

    /** A check-off took the bond with [mentor] up to [level]. */
    data class BondLevelUp(val mentor: Mentor, val level: Int) : GameEvent

    /** A perfect week earned a wish orb; [orbs] is how many are now held. */
    data class OrbEarned(val orbs: Int) : GameEvent
}

/** What a time chamber session did for a habit. */
data class FocusHabitResult(
    val habitName: String,
    /** Points for the check-off the session made, or 0 if it made none. */
    val checkOffPoints: Int,
    /** The focus bonus added, or 0 if today's was already earned. */
    val focusBonus: Int,
)

class HabitRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val habitDao = db.habitDao()
    private val completionDao = db.completionDao()
    private val streakDao = db.streakDao()
    private val powerDao = db.powerDao()
    private val bondDao = db.bondDao()
    private val countDao = db.countDao()

    val activeHabits: Flow<List<HabitEntity>> = habitDao.observeActive()

    val powerLevel: Flow<Long> = powerDao.observeTotalPoints().map(PowerCalculator::powerLevel)

    /** Current streak per habit id. A habit that is missing has a streak of 0. */
    val habitStreaks: Flow<Map<Long, Int>> = completionDao.observeHabitStreaks()
        .map { rows -> rows.associate { it.habitId to it.streak } }

    /** Finished days since the streak last broke, newest first. */
    val openDays: Flow<List<DayResult>> = streakDao.observeOpenRecords()
        .map { records -> records.map { it.toResult() } }

    /** The last few finished days whatever their outcome, newest first. */
    val recentDays: Flow<List<DayResult>> = streakDao.observeRecentRecords()
        .map { records -> records.map { it.toResult() } }

    val playerState: Flow<PlayerStateEntity> = streakDao.observePlayerState()
        .map { it ?: PlayerStateEntity() }

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)

    /** Form unlocks and bean awards, as they happen. */
    val events: SharedFlow<GameEvent> = _events

    fun completionsOn(date: LocalDate): Flow<List<CompletionEntity>> =
        completionDao.observeForDay(date.toEpochDay())

    /** Check-offs from [from] to [to], both included. */
    fun completionsBetween(from: LocalDate, to: LocalDate): Flow<List<CompletionEntity>> =
        completionDao.observeBetween(from.toEpochDay(), to.toEpochDay())

    /** How far each counted habit has got on [date], by habit id. */
    fun countsOn(date: LocalDate): Flow<Map<Long, Int>> =
        countDao.observeForDay(date.toEpochDay()).map { rows -> rows.associate { it.habitId to it.count } }

    /**
     * Adds one to a counted habit's count for today, short of its target: the
     * last one is a check-off, made with [complete]. Returns the new count, or
     * null if nothing changed (it would reach the target, it is done, or the
     * day has changed).
     */
    suspend fun addToCount(habit: HabitEntity, date: LocalDate): Int? = db.withTransaction {
        val day = today().toEpochDay()
        if (date.toEpochDay() != day || !habit.isCounted) return@withTransaction null
        val count = countDao.get(habit.id, day) ?: 0
        if (count + 1 >= habit.dailyTarget) return@withTransaction null
        countDao.save(HabitCountEntity(habit.id, day, count + 1))
        count + 1
    }

    /** Takes one off a counted habit's count for today, unless it is already checked off. */
    suspend fun removeFromCount(habit: HabitEntity, date: LocalDate) {
        db.withTransaction {
            val day = today().toEpochDay()
            if (date.toEpochDay() != day) return@withTransaction
            if (completionDao.getBetween(day, day).any { it.habitId == habit.id }) return@withTransaction
            val count = countDao.get(habit.id, day) ?: 0
            if (count > 0) countDao.save(HabitCountEntity(habit.id, day, count - 1))
        }
    }

    /**
     * True when [habit] still wants doing today, for its reminder: not done yet,
     * and scheduled today, or for a weekly goal, short of this week's target.
     */
    suspend fun needsDoingToday(habit: HabitEntity): Boolean {
        val date = today()
        val day = date.toEpochDay()
        val week = completedByDay(Weeks.startOf(day), day)
        if (habit.id in week[day].orEmpty()) return false
        val scheduled = habit.toScheduled()
        return if (habit.isWeekly) {
            WeeklyGoals.doneBefore(habit.id, day, week) < WeeklyGoals.targetFor(scheduled, day)
        } else {
            StreakRules.scheduledOn(listOf(scheduled), day).isNotEmpty()
        }
    }

    /** Ids of the habits checked off each day from [from] to [to]. */
    private suspend fun completedByDay(from: Long, to: Long): Map<Long, Set<Long>> =
        completionDao.getBetween(from, to)
            .groupBy({ it.epochDay }, { it.habitId })
            .mapValues { it.value.toSet() }

    suspend fun getHabit(id: Long): HabitEntity? = habitDao.getById(id)

    suspend fun isCompletedOn(habitId: Long, date: LocalDate): Boolean =
        completionDao.getBetween(date.toEpochDay(), date.toEpochDay()).any { it.habitId == habitId }

    suspend fun getActiveHabits(): List<HabitEntity> = habitDao.getActive()

    /**
     * Writes a record for every day that has ended since the last one, and ends
     * the streak for missed days that no bean can save. Safe to call any time,
     * as often as you like.
     */
    suspend fun syncDays() = celebrating { events ->
        syncDaysInTransaction(today().toEpochDay(), events)
    }

    /** Power earned per day between two dates (inclusive). Days with none are missing from the map. */
    fun dailyPoints(from: LocalDate, to: LocalDate): Flow<Map<Long, Int>> =
        powerDao.observeDailyPoints(from.toEpochDay(), to.toEpochDay())
            .map { rows -> rows.associate { it.epochDay to it.points } }

    suspend fun markChapterRead(chapter: Int) {
        db.withTransaction {
            val state = streakDao.getPlayerState() ?: PlayerStateEntity()
            val read = StoryRules.markRead(state.chaptersRead, chapter)
            if (read != state.chaptersRead) streakDao.savePlayerState(state.copy(chaptersRead = read))
        }
    }

    suspend fun saveHabit(habit: HabitEntity): Long = celebrating { events ->
        val todayDay = today().toEpochDay()
        syncDaysInTransaction(todayDay, events)
        val id = habitDao.save(habit)
        events += checkMilestones(todayDay)
        id
    }

    suspend fun archiveHabit(id: Long) = celebrating { events ->
        val todayDay = today().toEpochDay()
        syncDaysInTransaction(todayDay, events)
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
        syncDaysInTransaction(todayDay, events)
        val points = insertCompletion(habit, todayDay, gravity, events)
        if (points > 0) {
            if (habit.isCounted) countDao.save(HabitCountEntity(habit.id, todayDay, habit.dailyTarget))
            events += checkMilestones(todayDay)
        }
        points
    }

    /**
     * Applies a finished time chamber session to a habit: checks it off for
     * today if it is scheduled and not done yet, and adds the focus bonus on
     * top of its points, once per habit per day. Null if the habit is gone.
     */
    suspend fun completeViaTimeChamber(habitId: Long): FocusHabitResult? = celebrating { events ->
        val habit = habitDao.getById(habitId)
        if (habit == null || habit.archived) return@celebrating null
        val date = today()
        val todayDay = date.toEpochDay()
        syncDaysInTransaction(todayDay, events)

        var checkOffPoints = 0
        val existing = completionDao.getBetween(todayDay, todayDay).firstOrNull { it.habitId == habitId }
        if (existing == null && (habit.isWeekly || WeekSchedule.isScheduled(habit.scheduleDays, date))) {
            checkOffPoints = insertCompletion(habit, todayDay, Gravity.NORMAL, events)
            // A whole session on a counted habit finishes it for the day.
            if (habit.isCounted) countDao.save(HabitCountEntity(habit.id, todayDay, habit.dailyTarget))
            events += checkMilestones(todayDay)
        }

        val perks = db.readPerks(todayDay)
        val basis = existing?.points
            ?: checkOffPoints.takeIf { it > 0 }
            ?: PowerCalculator.completionPoints(habit.difficulty, Gravity.NORMAL, habit.mentor, perks)
        val alreadyEarned = powerDao.countOnDay(PowerSource.FOCUS_HABIT, habitId, todayDay) > 0
        val bonus = if (alreadyEarned) 0 else PowerCalculator.focusBonus(basis, perks.focusMultiplier)
        if (bonus > 0) {
            powerDao.insert(PowerEventEntity(0, todayDay, bonus, PowerSource.FOCUS_HABIT, habitId, nowMillis()))
        }
        FocusHabitResult(habit.name, checkOffPoints, bonus)
    }

    /** Takes back a check-off made by mistake, along with its points and any time chamber bonus. */
    suspend fun undoCompletion(habitId: Long, date: LocalDate) {
        if (date != today()) return
        db.withTransaction {
            completionDao.delete(habitId, date.toEpochDay())
            powerDao.deleteOnDay(PowerSource.FOCUS_HABIT, habitId, date.toEpochDay())
            // A counted habit goes back to one short of its target.
            val habit = habitDao.getById(habitId)
            if (habit != null && habit.isCounted) {
                countDao.save(HabitCountEntity(habitId, date.toEpochDay(), habit.dailyTarget - 1))
            }
        }
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

    /**
     * Hands out anything the streak has earned that has not been handed out
     * yet. Call after something other than a check-off may have completed
     * today, such as excusing its last habit.
     */
    suspend fun recheckMilestones() = celebrating { events ->
        events += checkMilestones(today().toEpochDay())
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

    /**
     * Records a check-off, with its points and the bond XP for the habit's
     * mentor. Returns the points, or 0 if the habit was already checked off
     * that day. A bond level gained is added to [events].
     */
    private suspend fun insertCompletion(
        habit: HabitEntity,
        epochDay: Long,
        gravity: Gravity,
        events: MutableList<GameEvent>,
    ): Int {
        val mentor = habit.mentor
        val perks = db.readPerks(epochDay)
        val points = PowerCalculator.completionPoints(habit.difficulty, gravity, mentor, perks)
        val levelBefore = bondDao.getBondXp().toLevels().getValue(mentor)
        val rowId = completionDao.insert(
            CompletionEntity(
                habitId = habit.id,
                epochDay = epochDay,
                gravity = gravity,
                points = points,
                completedAtMillis = nowMillis(),
                mentor = mentor,
                bondXp = GameConfig.BOND_XP_PER_CHECK_OFF,
            ),
        )
        if (rowId == -1L) return 0
        val levelAfter = bondDao.getBondXp().toLevels().getValue(mentor)
        if (levelAfter > levelBefore) events += GameEvent.BondLevelUp(mentor, levelAfter)
        return points
    }

    private suspend fun syncDaysInTransaction(todayDay: Long, events: MutableList<GameEvent>) {
        val habits = habitDao.getActive()
        val firstDay = streakDao.latestRecordedDay()?.plus(1)
            ?: habits.minOfOrNull { it.createdAtEpochDay }
            ?: return
        val lastDay = todayDay - 1
        if (firstDay <= lastDay) {
            // From the Monday, so weekly goals know what was done earlier in the week.
            val completedByDay = completionDao.getBetween(Weeks.startOf(firstDay), lastDay)
                .groupBy({ it.epochDay }, { it.habitId })
                .mapValues { it.value.toSet() }
            val excusedByDay = bondDao.getExcusesBetween(firstDay, lastDay)
                .groupBy({ it.epochDay }, { it.habitId })
                .mapValues { it.value.toSet() }
            val plans = StreakRules.planDays(
                habits.map { it.toScheduled() },
                completedByDay,
                firstDay,
                lastDay,
                excusedByDay,
            )
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

        awardWishOrbs(todayDay, events)
    }

    /** Judges each week that has finished since the last one judged, and awards an orb per perfect week. */
    private suspend fun awardWishOrbs(todayDay: Long, events: MutableList<GameEvent>) {
        val before = streakDao.getPlayerState() ?: PlayerStateEntity()
        val weeks = WishRules.weeksToJudge(before.lastOrbWeekEpochDay, streakDao.firstRecordedDay(), todayDay)
        if (weeks.isEmpty()) return
        var state = before
        for (weekStart in weeks) {
            val outcomes = streakDao.getRecordsBetween(weekStart, weekStart + Weeks.DAYS - 1).map { it.outcome }
            if (WishRules.isPerfectWeek(outcomes)) {
                val orbs = WishRules.orbsAfterPerfectWeek(state.wishOrbs)
                if (orbs > state.wishOrbs) events += GameEvent.OrbEarned(orbs)
                state = state.copy(wishOrbs = orbs)
            }
            state = state.copy(lastOrbWeekEpochDay = weekStart)
        }
        streakDao.savePlayerState(state)
    }

    /** If today is perfect, hands out whatever the streak has now earned. */
    private suspend fun checkMilestones(todayDay: Long): List<GameEvent> {
        val excused = bondDao.getExcusesBetween(todayDay, todayDay).map { it.habitId }.toSet()
        val week = completedByDay(Weeks.startOf(todayDay), todayDay)
        val scheduled = StreakRules.countedOn(habitDao.getActive().map { it.toScheduled() }, todayDay, week)
            .filterNot { it in excused }
        val completed = week[todayDay].orEmpty()
        if (!StreakRules.isPerfect(scheduled, completed)) return emptyList()

        val streak = StreakRules.streak(streakDao.getOpenRecords().map { it.outcome }, todayPerfect = true)
        val before = streakDao.getPlayerState() ?: PlayerStateEntity()
        var state = before
        val events = mutableListOf<GameEvent>()

        val perks = db.readPerks(todayDay)
        if (StreakRules.earnsBean(streak, perks.beanStreakInterval) && state.lastBeanAwardEpochDay != todayDay) {
            state = state.copy(
                healingBeans = StreakRules.beansAfterEarning(state.healingBeans, perks.beanCapacity),
                lastBeanAwardEpochDay = todayDay,
            )
            if (state.healingBeans > before.healingBeans) {
                events += GameEvent.BeanEarned(state.healingBeans, perks.beanCapacity)
            }
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

internal fun HabitEntity.toScheduled() = ScheduledHabit(id, scheduleDays, createdAtEpochDay, weeklyTarget)

/** The mentor who trains this habit: Vegeta for the hardest one, otherwise its category's mentor. */
val HabitEntity.mentor: Mentor get() = mentorFor(category, isHardest)

private fun DayRecordEntity.toResult() = DayResult(epochDay, outcome)
