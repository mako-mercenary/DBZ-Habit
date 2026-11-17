package com.saiyanhabits.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.ui.bonds.BondsViewModel
import com.saiyanhabits.ui.chamber.ChamberViewModel
import com.saiyanhabits.ui.habits.HabitsViewModel
import com.saiyanhabits.ui.home.HomeViewModel
import com.saiyanhabits.ui.quests.QuestsViewModel

fun saiyanViewModelFactory(app: SaiyanApp): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomeViewModel(app.habitRepository, app.reminders, app.bondRepository, app.questRepository, app.dialogue)
    }
    initializer { HabitsViewModel(app.habitRepository, app.reminders) }
    initializer { QuestsViewModel(app.questRepository) }
    initializer { BondsViewModel(app.bondRepository, app.habitRepository, app.dialogue) }
    initializer { ChamberViewModel(app.timeChamber, app.habitRepository, app.questRepository) }
}
