package com.saiyanhabits.ui.chamber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.chamber.ChamberState
import com.saiyanhabits.chamber.ChamberTarget
import com.saiyanhabits.chamber.TimeChamber
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.domain.ChamberTargetType
import com.saiyanhabits.domain.SpiritBombRules
import com.saiyanhabits.domain.WeekSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything a session can be linked to right now, grouped for the picker. */
data class ChamberTargets(
    val habits: List<ChamberTarget> = emptyList(),
    val milestones: List<ChamberTarget> = emptyList(),
    val bombs: List<ChamberTarget> = emptyList(),
) {
    val isEmpty: Boolean get() = habits.isEmpty() && milestones.isEmpty() && bombs.isEmpty()
    val all: List<ChamberTarget> get() = habits + milestones + bombs
}

class ChamberViewModel(
    private val timeChamber: TimeChamber,
    habits: HabitRepository,
    quests: QuestRepository,
) : ViewModel() {

    val state: StateFlow<ChamberState> = timeChamber.state

    /** Null until the first database read comes back. */
    val targets: StateFlow<ChamberTargets?> = combine(
        habits.activeHabits,
        quests.activeSagas,
        quests.activeBombs,
    ) { activeHabits, sagas, bombs ->
        val today = LocalDate.now()
        ChamberTargets(
            habits = activeHabits
                .filter { WeekSchedule.isScheduled(it.scheduleDays, today) }
                .map { ChamberTarget(ChamberTargetType.HABIT, it.id, it.name) },
            milestones = sagas.flatMap { saga ->
                saga.milestones.filterNot { it.isDone }
                    .map { ChamberTarget(ChamberTargetType.MILESTONE, it.id, "${saga.saga.name}: ${it.name}") }
            },
            bombs = bombs
                .filterNot { SpiritBombRules.isReadyToThrow(it.progress, it.target) }
                .map { ChamberTarget(ChamberTargetType.BOMB, it.id, it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start(minutes: Int, target: ChamberTarget) = timeChamber.start(minutes, target)

    fun giveUp() = timeChamber.cancel()

    fun dismissResult() = timeChamber.dismissResult()

    fun logBombAmount(amount: Int) {
        viewModelScope.launch { timeChamber.logBombAmount(amount) }
    }

    fun answerMilestoneFinished(isFinished: Boolean) {
        viewModelScope.launch { timeChamber.answerMilestoneFinished(isFinished) }
    }
}
