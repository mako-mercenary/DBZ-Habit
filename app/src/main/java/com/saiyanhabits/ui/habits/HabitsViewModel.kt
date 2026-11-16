package com.saiyanhabits.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitsViewModel(
    private val repository: HabitRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    /** Null until the first database read comes back. */
    val habits: StateFlow<List<HabitEntity>?> = repository.activeHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current streak per habit id. */
    val streaks: StateFlow<Map<Long, Int>> = repository.habitStreaks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun save(habit: HabitEntity) {
        viewModelScope.launch {
            val id = repository.saveHabit(habit)
            reminders.onHabitSaved(habit.copy(id = id))
        }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch {
            repository.archiveHabit(habit.id)
            reminders.onHabitRemoved(habit.id)
        }
    }
}
