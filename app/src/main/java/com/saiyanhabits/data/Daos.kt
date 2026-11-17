package com.saiyanhabits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.saiyanhabits.domain.DayOutcome
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    abstract fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    abstract suspend fun getActive(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    abstract suspend fun getById(id: Long): HabitEntity?

    @Insert
    protected abstract suspend fun insert(habit: HabitEntity): Long

    @Update
    protected abstract suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET isHardest = 0 WHERE isHardest = 1 AND id != :keepId")
    protected abstract suspend fun clearHardestExcept(keepId: Long)

    @Query("UPDATE habits SET archived = 1, isHardest = 0 WHERE id = :id")
    abstract suspend fun archive(id: Long)

    /**
     * Inserts a new habit (id 0) or updates an existing one, and keeps the
     * "hardest" flag on at most one habit. Returns the habit's id.
     */
    @Transaction
    open suspend fun save(habit: HabitEntity): Long {
        val id = if (habit.id == 0L) {
            insert(habit)
        } else {
            update(habit)
            habit.id
        }
        if (habit.isHardest) clearHardestExcept(id)
        return id
    }
}

@Dao
interface CompletionDao {

    @Query("SELECT * FROM completions WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun getBetween(fromDay: Long, toDay: Long): List<CompletionEntity>

    /**
     * Each habit's current streak: its check-offs since the last day it was
     * scheduled and missed. Habits with no check-offs since then are left out.
     */
    @Query(
        """
        SELECT c.habitId AS habitId, COUNT(*) AS streak
        FROM completions c
        WHERE c.epochDay > COALESCE(
            (SELECT MAX(m.epochDay) FROM habit_misses m WHERE m.habitId = c.habitId), -1)
        GROUP BY c.habitId
        """,
    )
    fun observeHabitStreaks(): Flow<List<HabitStreak>>

    /** Returns the new row id, or -1 if that habit was already checked off that day. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: CompletionEntity): Long

    @Query("DELETE FROM completions WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun delete(habitId: Long, epochDay: Long)
}

/** Finished days since the streak last broke, newest first. */
private const val OPEN_RECORDS = """
    SELECT * FROM day_records
    WHERE epochDay > COALESCE(
        (SELECT MAX(epochDay) FROM day_records WHERE outcome = 'BROKEN'), -1)
    ORDER BY epochDay DESC
"""

@Dao
interface StreakDao {

    /** The latest finished day on record, or null if no day has been recorded yet. */
    @Query("SELECT MAX(epochDay) FROM day_records")
    suspend fun latestRecordedDay(): Long?

    /** Finished days since the streak last broke, newest first. */
    @Query(OPEN_RECORDS)
    fun observeOpenRecords(): Flow<List<DayRecordEntity>>

    @Query(OPEN_RECORDS)
    suspend fun getOpenRecords(): List<DayRecordEntity>

    /** The earliest finished day on record, or null if no day has been recorded yet. */
    @Query("SELECT MIN(epochDay) FROM day_records")
    suspend fun firstRecordedDay(): Long?

    @Query("SELECT * FROM day_records WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    suspend fun getRecordsBetween(fromDay: Long, toDay: Long): List<DayRecordEntity>

    /** The latest finished days whatever their outcome, newest first. */
    @Query("SELECT * FROM day_records ORDER BY epochDay DESC LIMIT 14")
    fun observeRecentRecords(): Flow<List<DayRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<DayRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMisses(misses: List<HabitMissEntity>)

    @Query("UPDATE day_records SET outcome = :outcome WHERE epochDay IN (:days)")
    suspend fun setOutcome(days: List<Long>, outcome: DayOutcome)

    @Query("DELETE FROM habit_misses WHERE epochDay IN (:days)")
    suspend fun deleteMissesOn(days: List<Long>)

    @Query("SELECT * FROM player_state WHERE id = 0")
    fun observePlayerState(): Flow<PlayerStateEntity?>

    @Query("SELECT * FROM player_state WHERE id = 0")
    suspend fun getPlayerState(): PlayerStateEntity?

    @Upsert
    suspend fun savePlayerState(state: PlayerStateEntity)

}

@Dao
interface WishDao {

    /** Every wish: those still waiting first, then granted ones, newest first. */
    @Query("SELECT * FROM wishes ORDER BY grantedAtEpochDay IS NOT NULL, grantedAtEpochDay DESC, id")
    fun observeAll(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishes WHERE id = :id")
    suspend fun get(id: Long): WishEntity?

    @Insert
    suspend fun insert(wish: WishEntity): Long

    @Update
    suspend fun update(wish: WishEntity)

    @Query("DELETE FROM wishes WHERE id = :id AND grantedAtEpochDay IS NULL")
    suspend fun deleteWaiting(id: Long)
}
