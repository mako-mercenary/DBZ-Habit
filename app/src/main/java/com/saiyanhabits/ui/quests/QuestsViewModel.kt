package com.saiyanhabits.ui.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.BombLogEntity
import com.saiyanhabits.data.MilestoneDraft
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.data.SagaWithMilestones
import com.saiyanhabits.data.SpiritBombEntity
import com.saiyanhabits.data.StrikeResult
import com.saiyanhabits.domain.Boss
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Wraps a value read from the database, so "not read yet" (null) differs from "not there" (Loaded(null)). */
data class Loaded<T>(val value: T?)

/** Everything on the Sagas tab. */
data class QuestsOverview(
    val activeSagas: List<SagaWithMilestones>,
    val activeBombs: List<SpiritBombEntity>,
    val defeatedSagas: List<SagaWithMilestones>,
    val thrownBombs: List<SpiritBombEntity>,
)

class QuestsViewModel(private val quests: QuestRepository) : ViewModel() {

    /** Null until the first database read comes back. */
    val overview: StateFlow<QuestsOverview?> = combine(
        quests.activeSagas,
        quests.activeBombs,
        quests.defeatedSagas,
        quests.thrownBombs,
        ::QuestsOverview,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saga(id: Long): Flow<Loaded<SagaWithMilestones>> = quests.saga(id).map { Loaded(it) }
    fun bomb(id: Long): Flow<Loaded<SpiritBombEntity>> = quests.bomb(id).map { Loaded(it) }
    fun lastBombLog(bombId: Long): Flow<BombLogEntity?> = quests.lastBombLog(bombId)

    fun createSaga(name: String, boss: Boss, milestones: List<MilestoneDraft>, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(quests.createSaga(name, boss, milestones)) }
    }

    fun addMilestone(sagaId: Long, draft: MilestoneDraft) {
        viewModelScope.launch { quests.addMilestone(sagaId, draft) }
    }

    fun removeMilestone(milestoneId: Long) {
        viewModelScope.launch { quests.removeMilestone(milestoneId) }
    }

    fun strike(milestoneId: Long, onHit: (StrikeResult) -> Unit) {
        viewModelScope.launch { quests.strike(milestoneId)?.let(onHit) }
    }

    fun abandonSaga(sagaId: Long) {
        viewModelScope.launch { quests.abandonSaga(sagaId) }
    }

    fun createBomb(name: String, unit: String, target: Int, size: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(quests.createBomb(name, unit, target, size)) }
    }

    fun logBombProgress(bombId: Long, amount: Int) {
        viewModelScope.launch { quests.logBombProgress(bombId, amount) }
    }

    fun undoLastBombLog(bombId: Long) {
        viewModelScope.launch { quests.undoLastBombLog(bombId) }
    }

    fun throwBomb(bombId: Long, onThrown: (bonus: Int) -> Unit) {
        viewModelScope.launch { quests.throwBomb(bombId)?.let(onThrown) }
    }

    fun abandonBomb(bombId: Long) {
        viewModelScope.launch { quests.abandonBomb(bombId) }
    }
}
