package com.saiyanhabits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.saiyanhabits.domain.PowerSource
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerDao {

    /** All points ever earned: habit check-offs plus the power ledger. */
    @Query(
        """
        SELECT (SELECT COALESCE(SUM(points), 0) FROM completions)
             + (SELECT COALESCE(SUM(points), 0) FROM power_events)
        """,
    )
    fun observeTotalPoints(): Flow<Long>

    /** Power earned per day between two days (inclusive). Days with none are left out. */
    @Query(
        """
        SELECT epochDay AS epochDay, SUM(points) AS points FROM (
            SELECT epochDay, points FROM completions
            UNION ALL
            SELECT epochDay, points FROM power_events
        )
        WHERE epochDay BETWEEN :fromDay AND :toDay
        GROUP BY epochDay
        """,
    )
    fun observeDailyPoints(fromDay: Long, toDay: Long): Flow<List<DayPoints>>

    @Insert
    suspend fun insert(event: PowerEventEntity): Long

    @Query("DELETE FROM power_events WHERE source = :source AND sourceId = :sourceId")
    suspend fun delete(source: PowerSource, sourceId: Long)

    @Query("DELETE FROM power_events WHERE source = :source AND sourceId = :sourceId AND epochDay = :epochDay")
    suspend fun deleteOnDay(source: PowerSource, sourceId: Long, epochDay: Long)

    @Query("SELECT COUNT(*) FROM power_events WHERE source = :source AND sourceId = :sourceId AND epochDay = :epochDay")
    suspend fun countOnDay(source: PowerSource, sourceId: Long, epochDay: Long): Int
}

@Dao
interface QuestDao {

    // ---- Sagas ----

    @Transaction
    @Query("SELECT * FROM sagas WHERE status = 'ACTIVE' ORDER BY id")
    fun observeActiveSagas(): Flow<List<SagaWithMilestones>>

    @Transaction
    @Query("SELECT * FROM sagas WHERE status = 'DONE' ORDER BY finishedAtEpochDay DESC, id DESC")
    fun observeDefeatedSagas(): Flow<List<SagaWithMilestones>>

    @Transaction
    @Query("SELECT * FROM sagas WHERE id = :id")
    fun observeSaga(id: Long): Flow<SagaWithMilestones?>

    @Query("SELECT * FROM sagas WHERE id = :id")
    suspend fun getSaga(id: Long): SagaEntity?

    @Insert
    suspend fun insertSaga(saga: SagaEntity): Long

    @Update
    suspend fun updateSaga(saga: SagaEntity)

    @Query("SELECT * FROM saga_milestones WHERE sagaId = :sagaId ORDER BY position")
    suspend fun getMilestones(sagaId: Long): List<MilestoneEntity>

    @Query("SELECT * FROM saga_milestones WHERE id = :id")
    suspend fun getMilestone(id: Long): MilestoneEntity?

    @Insert
    suspend fun insertMilestones(milestones: List<MilestoneEntity>)

    @Update
    suspend fun updateMilestone(milestone: MilestoneEntity)

    @Query("DELETE FROM saga_milestones WHERE id = :id AND completedAtEpochDay IS NULL")
    suspend fun deleteOpenMilestone(id: Long)

    // ---- Spirit bombs ----

    @Query("SELECT * FROM spirit_bombs WHERE status = 'ACTIVE' ORDER BY id")
    fun observeActiveBombs(): Flow<List<SpiritBombEntity>>

    @Query("SELECT * FROM spirit_bombs WHERE status = 'DONE' ORDER BY finishedAtEpochDay DESC, id DESC")
    fun observeThrownBombs(): Flow<List<SpiritBombEntity>>

    @Query("SELECT * FROM spirit_bombs WHERE id = :id")
    fun observeBomb(id: Long): Flow<SpiritBombEntity?>

    @Query("SELECT * FROM spirit_bombs WHERE id = :id")
    suspend fun getBomb(id: Long): SpiritBombEntity?

    @Insert
    suspend fun insertBomb(bomb: SpiritBombEntity): Long

    @Update
    suspend fun updateBomb(bomb: SpiritBombEntity)

    @Insert
    suspend fun insertBombLog(log: BombLogEntity): Long

    @Query("SELECT * FROM bomb_logs WHERE bombId = :bombId ORDER BY id DESC LIMIT 1")
    suspend fun getLastBombLog(bombId: Long): BombLogEntity?

    @Query("SELECT * FROM bomb_logs WHERE bombId = :bombId ORDER BY id DESC LIMIT 1")
    fun observeLastBombLog(bombId: Long): Flow<BombLogEntity?>

    @Query("DELETE FROM bomb_logs WHERE id = :id")
    suspend fun deleteBombLog(id: Long)

    // ---- Time chamber ----

    @Insert
    suspend fun insertFocusSession(session: FocusSessionEntity)
}
