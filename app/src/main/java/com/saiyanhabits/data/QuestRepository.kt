package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.domain.ChamberTargetType
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.QuestStatus
import com.saiyanhabits.domain.SagaRules
import com.saiyanhabits.domain.SpiritBombRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate

sealed interface QuestEvent {
    /** A boss just hit 0 HP. */
    data class SagaDefeated(val sagaName: String, val boss: Boss, val bonus: Int) : QuestEvent
}

/** What one milestone hit did. */
data class StrikeResult(
    val milestoneName: String,
    /** The milestone's own damage: what came off the boss's HP. */
    val damage: Int,
    /** Bonus damage from assisting mentors, on top of [damage]. */
    val assistDamage: Int,
    val assists: List<Mentor>,
    val points: Int,
    /** True when the hit earned the time chamber multiplier. */
    val charged: Boolean,
    val hpAfter: Int,
    /** The victory bonus, if this hit finished the boss. */
    val victoryBonus: Int?,
)

/** What one spirit bomb progress log did. */
data class BombLogResult(
    val amount: Int,
    val points: Int,
    val focusBonus: Int,
    val progress: Int,
    val target: Int,
)

/** A milestone name and weight, before it is saved. */
data class MilestoneDraft(val name: String, val weight: Int)

/** Sagas (boss fights) and spirit bombs. */
class QuestRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val questDao = db.questDao()
    private val powerDao = db.powerDao()

    val activeSagas: Flow<List<SagaWithMilestones>> = questDao.observeActiveSagas()
    val defeatedSagas: Flow<List<SagaWithMilestones>> = questDao.observeDefeatedSagas()
    val activeBombs: Flow<List<SpiritBombEntity>> = questDao.observeActiveBombs()
    val thrownBombs: Flow<List<SpiritBombEntity>> = questDao.observeThrownBombs()

    private val _events = MutableSharedFlow<QuestEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<QuestEvent> = _events

    fun saga(id: Long): Flow<SagaWithMilestones?> = questDao.observeSaga(id)
    fun bomb(id: Long): Flow<SpiritBombEntity?> = questDao.observeBomb(id)
    fun lastBombLog(bombId: Long): Flow<BombLogEntity?> = questDao.observeLastBombLog(bombId)

    suspend fun getBomb(id: Long): SpiritBombEntity? = questDao.getBomb(id)

    // ---- Sagas ----

    suspend fun createSaga(name: String, boss: Boss, milestones: List<MilestoneDraft>): Long = db.withTransaction {
        val sagaId = questDao.insertSaga(
            SagaEntity(name = name.trim(), boss = boss, createdAtEpochDay = today().toEpochDay()),
        )
        questDao.insertMilestones(
            milestones.mapIndexed { index, draft ->
                MilestoneEntity(
                    sagaId = sagaId,
                    name = draft.name.trim(),
                    weight = SagaRules.clampWeight(draft.weight),
                    position = index,
                )
            },
        )
        sagaId
    }

    /** Adds a milestone to a fight in progress. The boss gains that much HP. */
    suspend fun addMilestone(sagaId: Long, draft: MilestoneDraft) = db.withTransaction {
        val saga = questDao.getSaga(sagaId)
        if (saga == null || saga.status != QuestStatus.ACTIVE) return@withTransaction
        val nextPosition = (questDao.getMilestones(sagaId).maxOfOrNull { it.position } ?: -1) + 1
        questDao.insertMilestones(
            listOf(
                MilestoneEntity(
                    sagaId = sagaId,
                    name = draft.name.trim(),
                    weight = SagaRules.clampWeight(draft.weight),
                    position = nextPosition,
                ),
            ),
        )
    }

    /** Removes a milestone that has not been finished. The last open one cannot be removed. */
    suspend fun removeMilestone(milestoneId: Long) = db.withTransaction {
        val milestone = questDao.getMilestone(milestoneId) ?: return@withTransaction
        val open = questDao.getMilestones(milestone.sagaId).count { !it.isDone }
        if (!milestone.isDone && open > 1) questDao.deleteOpenMilestone(milestoneId)
    }

    /** Marks a milestone as worked on in the time chamber. False if it can no longer be charged. */
    suspend fun chargeMilestone(milestoneId: Long): Boolean = db.withTransaction {
        val milestone = questDao.getMilestone(milestoneId)
        val saga = milestone?.let { questDao.getSaga(it.sagaId) }
        if (milestone == null || milestone.isDone || saga?.status != QuestStatus.ACTIVE) {
            false
        } else {
            questDao.updateMilestone(milestone.copy(focusSessions = milestone.focusSessions + 1))
            true
        }
    }

    /**
     * Finishes a milestone: the boss takes its weight in damage. At 0 HP the
     * saga is won and pays its victory bonus. Null if there was nothing to hit.
     */
    suspend fun strike(milestoneId: Long): StrikeResult? {
        var defeated: QuestEvent.SagaDefeated? = null
        val result = db.withTransaction {
            defeated = null
            val milestone = questDao.getMilestone(milestoneId) ?: return@withTransaction null
            val saga = questDao.getSaga(milestone.sagaId) ?: return@withTransaction null
            if (milestone.isDone || saga.status != QuestStatus.ACTIVE) return@withTransaction null

            val day = today().toEpochDay()
            val perks = db.readPerks(day)
            val charged = milestone.focusSessions > 0
            val damage = SagaRules.damageFor(milestone.weight, perks.assistDamage)
            val points = SagaRules.milestonePoints(milestone.weight, charged, perks)
            questDao.updateMilestone(
                milestone.copy(completedAtEpochDay = day, damageDealt = damage, points = points),
            )
            powerDao.insert(PowerEventEntity(0, day, points, PowerSource.MILESTONE, milestone.id, nowMillis()))

            val states = questDao.getMilestones(saga.id).map { it.toState() }
            val hpAfter = SagaRules.remainingHp(states)
            var bonus: Int? = null
            if (SagaRules.isDefeated(states)) {
                bonus = SagaRules.victoryBonus(
                    totalHp = SagaRules.totalHp(states),
                    bonusDamage = SagaRules.bonusDamage(states),
                    boostPercent = perks.victoryBoostPercent,
                )
                questDao.updateSaga(
                    saga.copy(status = QuestStatus.DONE, finishedAtEpochDay = day, victoryBonus = bonus),
                )
                powerDao.insert(PowerEventEntity(0, day, bonus, PowerSource.SAGA_VICTORY, saga.id, nowMillis()))
                defeated = QuestEvent.SagaDefeated(saga.name, saga.boss, bonus)
            }
            StrikeResult(
                milestoneName = milestone.name,
                damage = milestone.weight,
                assistDamage = damage - milestone.weight,
                assists = perks.assistMentors,
                points = points,
                charged = charged,
                hpAfter = hpAfter,
                victoryBonus = bonus,
            )
        }
        defeated?.let { _events.emit(it) }
        return result
    }

    /** Gives up on a saga. Points already earned stay. */
    suspend fun abandonSaga(sagaId: Long) = db.withTransaction {
        val saga = questDao.getSaga(sagaId) ?: return@withTransaction
        if (saga.status == QuestStatus.ACTIVE) {
            questDao.updateSaga(saga.copy(status = QuestStatus.ABANDONED, finishedAtEpochDay = today().toEpochDay()))
        }
    }

    // ---- Spirit bombs ----

    suspend fun createBomb(name: String, unit: String, target: Int, size: Int): Long = questDao.insertBomb(
        SpiritBombEntity(
            name = name.trim(),
            unit = unit.trim(),
            target = target.coerceAtLeast(1),
            size = size.coerceIn(SpiritBombRules.MIN_SIZE, SpiritBombRules.MAX_SIZE),
            createdAtEpochDay = today().toEpochDay(),
        ),
    )

    /**
     * Adds progress to a bomb, up to its target. Null if nothing could be
     * added: the bomb is gone, already full, or [amount] was not positive.
     */
    suspend fun logBombProgress(bombId: Long, amount: Int, viaTimeChamber: Boolean = false): BombLogResult? =
        db.withTransaction {
            val bomb = questDao.getBomb(bombId) ?: return@withTransaction null
            if (bomb.status != QuestStatus.ACTIVE) return@withTransaction null
            val applied = SpiritBombRules.applicableAmount(amount, bomb.progress, bomb.target)
            if (applied <= 0) return@withTransaction null

            val day = today().toEpochDay()
            val after = bomb.progress + applied
            val points = SpiritBombRules.pointsForProgress(bomb.size, bomb.target, bomb.progress, after)
            val bonus = if (viaTimeChamber) {
                PowerCalculator.focusBonus(points, db.readPerks(day).focusMultiplier)
            } else {
                0
            }
            val logId = questDao.insertBombLog(BombLogEntity(0, bombId, applied, day, nowMillis(), viaTimeChamber))
            if (points + bonus > 0) {
                powerDao.insert(PowerEventEntity(0, day, points + bonus, PowerSource.BOMB_LOG, logId, nowMillis()))
            }
            questDao.updateBomb(bomb.copy(progress = after))
            BombLogResult(applied, points, bonus, after, bomb.target)
        }

    /** Takes back the latest progress log of a bomb that has not been thrown, with its points. */
    suspend fun undoLastBombLog(bombId: Long) = db.withTransaction {
        val bomb = questDao.getBomb(bombId) ?: return@withTransaction
        if (bomb.status != QuestStatus.ACTIVE) return@withTransaction
        val log = questDao.getLastBombLog(bombId) ?: return@withTransaction
        questDao.deleteBombLog(log.id)
        powerDao.delete(PowerSource.BOMB_LOG, log.id)
        questDao.updateBomb(bomb.copy(progress = (bomb.progress - log.amount).coerceAtLeast(0)))
    }

    /** Throws a full bomb: the task is finished and pays its bonus. Null if it was not ready. */
    suspend fun throwBomb(bombId: Long): Int? = db.withTransaction {
        val bomb = questDao.getBomb(bombId) ?: return@withTransaction null
        if (bomb.status != QuestStatus.ACTIVE || !SpiritBombRules.isReadyToThrow(bomb.progress, bomb.target)) {
            return@withTransaction null
        }
        val day = today().toEpochDay()
        val bonus = SpiritBombRules.throwBonus(bomb.size, db.readPerks(day).bombThrowMultiplier)
        questDao.updateBomb(bomb.copy(status = QuestStatus.DONE, finishedAtEpochDay = day))
        powerDao.insert(PowerEventEntity(0, day, bonus, PowerSource.BOMB_THROW, bomb.id, nowMillis()))
        bonus
    }

    /** Gives up on a bomb. Points already earned stay. */
    suspend fun abandonBomb(bombId: Long) = db.withTransaction {
        val bomb = questDao.getBomb(bombId) ?: return@withTransaction
        if (bomb.status == QuestStatus.ACTIVE) {
            questDao.updateBomb(bomb.copy(status = QuestStatus.ABANDONED, finishedAtEpochDay = today().toEpochDay()))
        }
    }

    // ---- Time chamber ----

    suspend fun recordFocusSession(minutes: Int, targetType: ChamberTargetType, targetId: Long) {
        questDao.insertFocusSession(
            FocusSessionEntity(0, today().toEpochDay(), minutes, targetType, targetId, nowMillis()),
        )
    }
}
