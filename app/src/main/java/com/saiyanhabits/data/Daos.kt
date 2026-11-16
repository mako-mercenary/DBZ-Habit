package com.saiyanhabits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    abstract fun observeActive(): Flow<List<HabitEntity>>

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

    @Query("SELECT COALESCE(SUM(points), 0) FROM completions")
    fun observeTotalPoints(): Flow<Long>

    /** Returns the new row id, or -1 if that habit was already checked off that day. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: CompletionEntity): Long

    @Query("DELETE FROM completions WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun delete(habitId: Long, epochDay: Long)
}
