package com.saiyanhabits.ui.bonds

import com.saiyanhabits.data.toScheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.Bond
import com.saiyanhabits.data.BondRepository
import com.saiyanhabits.data.DialogueRepository
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MentorDialogue
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.Technique
import com.saiyanhabits.domain.WeekSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BondsUiState(
    val todayEpochDay: Long,
    /** One per mentor, in mentor order. */
    val bonds: List<Bond>,
    /** The day each activated technique was last used. */
    val techniqueUses: Map<Technique, Long>,
    val dialogues: Map<Mentor, MentorDialogue>,
    /** Today's habits that Master's Day Off could still excuse: scheduled, not done, not excused. */
    val excusable: List<HabitEntity>,
) {
    fun bond(mentor: Mentor): Bond = bonds.firstOrNull { it.mentor == mentor } ?: Bond(mentor, 0)

    fun cooldownDaysLeft(technique: Technique): Int =
        BondRules.cooldownDaysLeft(technique, techniqueUses[technique], todayEpochDay)

    /** True on the day an activated technique was used. */
    fun usedToday(technique: Technique): Boolean = techniqueUses[technique] == todayEpochDay
}

class BondsViewModel(
    private val bonds: BondRepository,
    private val habits: HabitRepository,
    dialogue: DialogueRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val dialogues = flow { emit(dialogue.all()) }

    /** The perks in force right now, for screens that show boosted numbers. */
    val perks: StateFlow<Perks> = bonds.perks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Perks.NONE)

    /** Null until the first read comes back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BondsUiState?> = today
        .flatMapLatest { date ->
            val excusable = combine(
                habits.activeHabits,
                habits.completionsBetween(java.time.LocalDate.ofEpochDay(com.saiyanhabits.domain.Weeks.startOf(date.toEpochDay())), date),
                bonds.excusedOn(date),
            ) { active, completions, excused ->
                val day = date.toEpochDay()
                val week = completions.groupBy({ it.epochDay }, { it.habitId }).mapValues { it.value.toSet() }
                val done = week[day].orEmpty()
                val due = com.saiyanhabits.domain.StreakRules.countedOn(active.map { it.toScheduled() }, day, week).toSet()
                active.filter { it.id in due && it.id !in done && it.id !in excused }
            }
            combine(bonds.bonds, bonds.techniqueUses, dialogues, excusable) { bondList, uses, lines, canExcuse ->
                BondsUiState(date.toEpochDay(), bondList, uses, lines, canExcuse)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Call when a bonds screen comes to the front, so cooldowns count from the right day. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun useKaioken() {
        viewModelScope.launch { bonds.useKaioken() }
    }

    fun useDayOff(habitId: Long) {
        viewModelScope.launch {
            // Excusing the last habit left can complete the day.
            if (bonds.useDayOff(habitId)) habits.recheckMilestones()
        }
    }
}
