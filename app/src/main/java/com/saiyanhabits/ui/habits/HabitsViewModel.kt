package com.saiyanhabits.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitsViewModel(private val repository: HabitRepository) : ViewModel() {

    /** Null until the first database read comes back. */
    val habits: StateFlow<List<HabitEntity>?> = repository.activeHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(habit: HabitEntity) {
        viewModelScope.launch { repository.saveHabit(habit) }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch { repository.archiveHabit(habit.id) }
    }
}
