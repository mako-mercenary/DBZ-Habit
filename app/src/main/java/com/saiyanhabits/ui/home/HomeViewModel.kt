package com.saiyanhabits.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.CompletionEntity
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.WeekSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A habit scheduled for today, with its check-off if it has one. */
data class TodayHabit(val habit: HabitEntity, val completion: CompletionEntity?) {
    val isDone: Boolean get() = completion != null
}

data class HomeUiState(
    val date: LocalDate,
    val powerLevel: Long,
    val today: List<TodayHabit>,
    /** True when there are no habits at all, as opposed to none scheduled today. */
    val hasNoHabits: Boolean,
) {
    val doneCount: Int get() = today.count { it.isDone }
}

class HomeViewModel(private val repository: HabitRepository) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    /** Null until the first database read comes back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState?> = today
        .flatMapLatest { date ->
            combine(
                repository.activeHabits,
                repository.completionsOn(date),
                repository.powerLevel,
            ) { habits, completions, power ->
                val byHabit = completions.associateBy { it.habitId }
                HomeUiState(
                    date = date,
                    powerLevel = power,
                    today = habits
                        .filter { WeekSchedule.isScheduled(it.scheduleDays, date) }
                        .map { TodayHabit(it, byHabit[it.id]) },
                    hasNoHabits = habits.isEmpty(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _gains = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** Points from each check-off as it happens, for the "+points" animation. */
    val gains: SharedFlow<Int> = _gains

    /** Call when the screen comes back to the front, so "today" rolls over after midnight. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun complete(habit: HabitEntity, gravity: Gravity) {
        val date = today.value
        viewModelScope.launch {
            val points = repository.complete(habit, date, gravity)
            if (points > 0) _gains.emit(points)
        }
    }

    fun undo(habit: HabitEntity) {
        val date = today.value
        viewModelScope.launch { repository.undoCompletion(habit.id, date) }
    }
}
