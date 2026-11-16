package com.saiyanhabits.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.CompletionEntity
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.StreakRules
import com.saiyanhabits.domain.WeekSchedule
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
) {
    val isDone: Boolean get() = completion != null
}

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
    val daysUntilNextBean: Int,
    /** A missed day waiting for a yes or no on spending a bean. */
    val beanOffer: BeanOffer?,
) {
    val doneCount: Int get() = today.count { it.isDone }
}

class HomeViewModel(
    private val repository: HabitRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    /** Null until the first database read comes back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState?> = today
        .flatMapLatest { date ->
            combine(
                combine(repository.activeHabits, repository.completionsOn(date), repository.powerLevel, ::Triple),
                combine(repository.openDays, repository.playerState, repository.habitStreaks, ::Triple),
            ) { (habits, completions, power), (openDays, player, habitStreaks) ->
                val byHabit = completions.associateBy { it.habitId }
                val todays = habits
                    .filter { WeekSchedule.isScheduled(it.scheduleDays, date) }
                    .map { TodayHabit(it, byHabit[it.id], habitStreaks[it.id] ?: 0) }
                val streak = StreakRules.streak(
                    pastNewestFirst = openDays.map { it.outcome },
                    todayPerfect = todays.isNotEmpty() && todays.all { it.isDone },
                )
                HomeUiState(
                    date = date,
                    powerLevel = power,
                    today = todays,
                    hasNoHabits = habits.isEmpty(),
                    overallStreak = streak,
                    form = player.highestForm,
                    nextFormAt = StreakRules.nextFormThreshold(player.highestForm),
                    beans = player.healingBeans,
                    daysUntilNextBean = StreakRules.daysUntilNextBean(streak),
                    beanOffer = StreakRules.beanOffer(openDays, player.healingBeans),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
