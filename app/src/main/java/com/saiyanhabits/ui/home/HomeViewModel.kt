package com.saiyanhabits.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.BondRepository
import com.saiyanhabits.data.CompletionEntity
import com.saiyanhabits.data.DialogueRepository
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.data.SagaWithMilestones
import com.saiyanhabits.data.mentor
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.DayResult
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MentorDialogue
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.ReactionContext
import com.saiyanhabits.domain.ReactionRules
import com.saiyanhabits.domain.RivalRules
import com.saiyanhabits.domain.RivalStanding
import com.saiyanhabits.domain.Weeks
import com.saiyanhabits.domain.StreakRules
import com.saiyanhabits.domain.WeekSchedule
import com.saiyanhabits.domain.WeeklyGoals
import com.saiyanhabits.data.toScheduled
import com.saiyanhabits.reminders.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A habit scheduled for today, with its check-off if it has one. */
data class TodayHabit(
    val habit: HabitEntity,
    val completion: CompletionEntity?,
    /** The habit's own streak, counting today's check-off. */
    val streak: Int,
    /** True when Master's Day Off was used on it today: it no longer counts for today. */
    val excused: Boolean = false,
    /**
     * True when it counts towards today being perfect. Always true for a habit
     * scheduled today; for a weekly goal, only when it is due today or done today.
     */
    val countsToday: Boolean = true,
    /** For a counted habit: how many so far today. */
    val count: Int = 0,
    /** For a weekly goal: check-offs this week, today included. */
    val weekDone: Int = 0,
    /** For a weekly goal: this week's target. Null for other habits. */
    val weekTarget: Int? = null,
) {
    val isDone: Boolean get() = completion != null
}

/** The mentor one-liner at the top of the home screen. */
data class MentorLine(val mentor: Mentor, val text: String)

data class HomeUiState(
    val date: LocalDate,
    val powerLevel: Long,
    val today: List<TodayHabit>,
    /** True when there are no habits at all, as opposed to none scheduled today. */
    val hasNoHabits: Boolean,
    /** Days in a row with every scheduled habit done. */
    val overallStreak: Int,
    /** Highest form unlocked (0 = base form). */
    val form: Int,
    /** Streak that unlocks the next form, or null when every form is unlocked. */
    val nextFormAt: Int?,
    val beans: Int,
    val beanCapacity: Int,
    val daysUntilNextBean: Int,
    /** A missed day waiting for a yes or no on spending a bean. */
    val beanOffer: BeanOffer?,
    /** The perks in force, for showing what a check-off will pay. */
    val perks: Perks,
    val wishOrbs: Int,
    /** This week's power gain against last week's. */
    val rival: RivalStanding,
    /** What a mentor has to say today, or null if the dialogue files gave nothing. */
    val mentorLine: MentorLine?,
) {
    /** Today's habits that count towards a perfect day: not excused, and due today. */
    val counted: List<TodayHabit> get() = today.filter { !it.excused && it.countsToday }
    val doneCount: Int get() = counted.count { it.isDone }
}

/** The habit-side inputs to the home screen, bundled so they travel as one flow. */
private data class HabitInputs(
    val habits: List<HabitEntity>,
    /** This week's check-offs, today's included. */
    val weekCompletions: List<CompletionEntity>,
    val power: Long,
    val dailyPoints: Map<Long, Int>,
    val counts: Map<Long, Int>,
)

/** The bond-side inputs to the home screen, bundled so they travel as one flow. */
private data class BondInputs(
    val perks: Perks,
    val excused: Set<Long>,
    val recentDays: List<DayResult>,
    val defeatedSagas: List<SagaWithMilestones>,
    val dialogues: Map<Mentor, MentorDialogue>,
)

class HomeViewModel(
    private val repository: HabitRepository,
    private val reminders: ReminderScheduler,
    private val bonds: BondRepository,
    private val quests: QuestRepository,
    dialogue: DialogueRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val dialogues = flow { emit(dialogue.all()) }

    /** Null until the first database read comes back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState?> = today
        .flatMapLatest { date ->
            combine(
                combine(
                    repository.activeHabits,
                    repository.completionsBetween(LocalDate.ofEpochDay(Weeks.startOf(date.toEpochDay())), date),
                    repository.powerLevel,
                    // Last week and this one, for the rival.
                    repository.dailyPoints(LocalDate.ofEpochDay(Weeks.startOf(date.toEpochDay()) - Weeks.DAYS), date),
                    repository.countsOn(date),
                    ::HabitInputs,
                ),
                combine(repository.openDays, repository.playerState, repository.habitStreaks, ::Triple),
                combine(
                    bonds.perks,
                    bonds.excusedOn(date),
                    repository.recentDays,
                    quests.defeatedSagas,
                    dialogues,
                    ::BondInputs,
                ),
            ) { (habits, weekCompletions, power, dailyPoints, counts), (openDays, player, habitStreaks), bond ->
                val day = date.toEpochDay()
                val byHabit = weekCompletions.filter { it.epochDay == day }.associateBy { it.habitId }
                val week = weekCompletions.groupBy({ it.epochDay }, { it.habitId }).mapValues { it.value.toSet() }
                val countsToday = StreakRules.countedOn(habits.map { it.toScheduled() }, day, week).toSet()
                val todays = habits
                    .filter { if (it.isWeekly) it.createdAtEpochDay <= day else WeekSchedule.isScheduled(it.scheduleDays, date) }
                    .map { habit ->
                        val done = byHabit[habit.id]
                        TodayHabit(
                            habit = habit,
                            completion = done,
                            streak = habitStreaks[habit.id] ?: 0,
                            excused = habit.id in bond.excused,
                            countsToday = habit.id in countsToday,
                            count = if (done != null) habit.dailyTarget else counts[habit.id] ?: 0,
                            weekDone = weekCompletions.count { it.habitId == habit.id },
                            weekTarget = if (habit.isWeekly) WeeklyGoals.targetFor(habit.toScheduled(), day) else null,
                        )
                    }
                    // Weekly goals that can wait go last.
                    .sortedBy { !it.countsToday }
                val counted = todays.filterNot { it.excused }
                val todayPerfect = counted.isNotEmpty() && counted.all { it.isDone }
                val streak = StreakRules.streak(openDays.map { it.outcome }, todayPerfect)
                val perks = bond.perks
                HomeUiState(
                    date = date,
                    powerLevel = power,
                    today = todays,
                    hasNoHabits = habits.isEmpty(),
                    overallStreak = streak,
                    form = player.highestForm,
                    nextFormAt = StreakRules.nextFormThreshold(player.highestForm),
                    beans = player.healingBeans,
                    beanCapacity = perks.beanCapacity.coerceAtLeast(player.healingBeans),
                    daysUntilNextBean = StreakRules.daysUntilNextBean(streak, perks.beanStreakInterval),
                    beanOffer = StreakRules.beanOffer(openDays, player.healingBeans),
                    perks = perks,
                    wishOrbs = player.wishOrbs,
                    rival = RivalRules.standing(dailyPoints, date.toEpochDay()),
                    mentorLine = mentorLine(
                        context = ReactionContext(
                            epochDay = date.toEpochDay(),
                            pastNewestFirst = bond.recentDays.map { it.outcome },
                            completedToday = counted.count { it.isDone },
                            todayPerfect = todayPerfect,
                            overallStreak = streak,
                            bossDefeatedToday = bond.defeatedSagas.any { it.saga.finishedAtEpochDay == date.toEpochDay() },
                            activeMentors = habits.map { it.mentor }.distinct(),
                        ),
                        bossName = bond.defeatedSagas
                            .firstOrNull { it.saga.finishedAtEpochDay == date.toEpochDay() }?.saga?.boss?.displayName,
                        dialogues = bond.dialogues,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Picks who speaks and which of their lines, and fills in the streak and boss name. */
    private fun mentorLine(
        context: ReactionContext,
        bossName: String?,
        dialogues: Map<Mentor, MentorDialogue>,
    ): MentorLine? {
        val reaction = ReactionRules.pick(context)
        val lines = dialogues[reaction.mentor]?.reactions?.get(reaction.trigger).orEmpty()
        if (lines.isEmpty()) return null
        val line = lines[ReactionRules.lineIndex(context.epochDay, reaction.mentor, lines.size)]
        return MentorLine(reaction.mentor, ReactionRules.fill(line, context.overallStreak, bossName))
    }

    private val _gains = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** Points from each check-off as it happens, for the "+points" animation. */
    val gains: SharedFlow<Int> = _gains

    init {
        // Roll over to the new day at midnight if the app is left open.
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, midnight).toMillis() + 1_000)
                refreshToday()
            }
        }
    }

    /** Call when the screen comes back to the front, so "today" rolls over after midnight. */
    fun refreshToday() {
        today.value = LocalDate.now()
        viewModelScope.launch { repository.syncDays() }
    }

    fun complete(habit: HabitEntity, gravity: Gravity) {
        val date = today.value
        viewModelScope.launch {
            val points = repository.complete(habit, date, gravity)
            if (points > 0) {
                _gains.emit(points)
                reminders.onHabitCompleted(habit.id)
            } else {
                // Nothing was recorded: most likely the day changed under us.
                refreshToday()
            }
        }
    }

    /** One more of a counted habit, short of its target. */
    fun addToCount(habit: HabitEntity) {
        val date = today.value
        viewModelScope.launch { if (repository.addToCount(habit, date) == null) refreshToday() }
    }

    fun removeFromCount(habit: HabitEntity) {
        val date = today.value
        viewModelScope.launch { repository.removeFromCount(habit, date) }
    }

    fun undo(habit: HabitEntity) {
        val date = today.value
        viewModelScope.launch { repository.undoCompletion(habit.id, date) }
    }

    fun acceptBeanOffer() {
        viewModelScope.launch {
            repository.acceptBeanOffer()
            reminders.onBeanOfferAnswered()
        }
    }

    fun declineBeanOffer() {
        viewModelScope.launch {
            repository.declineBeanOffer()
            reminders.onBeanOfferAnswered()
        }
    }

}
