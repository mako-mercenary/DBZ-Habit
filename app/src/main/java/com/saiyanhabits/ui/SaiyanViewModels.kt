package com.saiyanhabits.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.ui.habits.HabitsViewModel
import com.saiyanhabits.ui.home.HomeViewModel

fun saiyanViewModelFactory(repository: HabitRepository): ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(repository) }
    initializer { HabitsViewModel(repository) }
}
