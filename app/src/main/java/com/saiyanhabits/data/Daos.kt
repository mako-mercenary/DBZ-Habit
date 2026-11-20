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

    /** Every habit, archived ones included, for naming past check-offs. */
    @Query("SELECT * FROM habits ORDER BY id")
    abstract suspend fun getAll(): List<HabitEntity>

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
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<CompletionEntity>>

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

    /** Every finished day on record, oldest first. */
    @Query("SELECT * FROM day_records ORDER BY epochDay")
    fun observeAllRecords(): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_records ORDER BY epochDay")
    suspend fun getAllRecords(): List<DayRecordEntity>

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

@Dao
interface CountDao {

    @Query("SELECT * FROM habit_counts WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<HabitCountEntity>>

    @Query("SELECT count FROM habit_counts WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun get(habitId: Long, epochDay: Long): Int?

    @Upsert
    suspend fun save(count: HabitCountEntity)
}

@Dao
interface NoteDao {

    @Query("SELECT * FROM day_notes ORDER BY epochDay")
    fun observeAll(): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DayNoteEntity?>

    @Query("SELECT * FROM day_notes WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DayNoteEntity?

    @Upsert
    suspend fun save(note: DayNoteEntity)

    @Query("DELETE FROM day_notes WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}

@Dao
interface AchievementDao {

    /**
     * A number that changes whenever anything achievements look at does, so
     * observing it re-checks them at the right moments.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM completions)
             + (SELECT COALESCE(SUM(points), 0) FROM power_events)
             + (SELECT COUNT(*) FROM power_events)
             + (SELECT COUNT(*) FROM day_records) * 7
             + (SELECT COUNT(*) FROM day_records WHERE outcome = 'BEAN_COVERED') * 13
             + (SELECT COUNT(*) FROM wishes WHERE grantedAtEpochDay IS NOT NULL) * 17
             + (SELECT COUNT(*) FROM day_notes) * 19
             + (SELECT COUNT(*) FROM focus_sessions) * 23
             + (SELECT COALESCE(SUM(chaptersRead * 29 + highestForm * 31), 0) FROM player_state)
             + (SELECT COUNT(*) FROM sagas WHERE status = 'DONE') * 37
             + (SELECT COUNT(*) FROM minigame_plays) * 41
             + (SELECT COUNT(*) FROM journal_entries) * 43
             + (SELECT COUNT(*) FROM mentor_calls) * 47
             + (SELECT COUNT(*) FROM day_routine_items WHERE doneAtMillis IS NOT NULL) * 53
             + (SELECT COUNT(*) FROM day_routine_items) * 59
             + (SELECT COALESCE(SUM(count), 0) FROM card_collection) * 61
             + (SELECT COUNT(*) FROM card_matches) * 67
        """,
    )
    fun observeChanges(): Flow<Long>

    @Query("SELECT COUNT(*) FROM completions")
    suspend fun checkOffs(): Int

    /** A number that changes whenever today's list or the numbers on the widget can. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM completions) * 3
             + (SELECT COALESCE(SUM(count), 0) FROM habit_counts) * 5
             + (SELECT COUNT(*) FROM habits WHERE archived = 0) * 7
             + (SELECT COALESCE(SUM(scheduleDays + difficulty + dailyTarget + COALESCE(weeklyTarget, 0)), 0) FROM habits) * 11
             + (SELECT COUNT(*) FROM habit_excuses) * 13
             + (SELECT COUNT(*) FROM day_records) * 17
             + (SELECT COALESCE(SUM(points), 0) FROM power_events)
        """,
    )
    fun observeWidgetChanges(): Flow<Long>

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun chamberSessions(): Int

    @Query("SELECT boss FROM sagas WHERE status = 'DONE'")
    suspend fun defeatedBosses(): List<com.saiyanhabits.domain.Boss>

    @Query("SELECT COUNT(*) FROM wishes WHERE grantedAtEpochDay IS NOT NULL")
    suspend fun wishesGranted(): Int

    @Query("SELECT COUNT(*) FROM day_notes")
    suspend fun notesWritten(): Int

    @Query("SELECT (SELECT COALESCE(SUM(points), 0) FROM completions) + (SELECT COALESCE(SUM(points), 0) FROM power_events)")
    suspend fun totalPoints(): Long
}

@Dao
interface GameDao {

    @Query("SELECT * FROM minigame_plays WHERE game = :game ORDER BY createdAtMillis DESC")
    fun observePlays(game: com.saiyanhabits.domain.MiniGame): Flow<List<MinigamePlayEntity>>

    @Query("SELECT * FROM minigame_plays ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MinigamePlayEntity>>

    @Query("SELECT * FROM minigame_plays")
    suspend fun getAll(): List<MinigamePlayEntity>

    @Query("SELECT COALESCE(SUM(bondXp), 0) FROM minigame_plays WHERE mentor = :mentor AND epochDay = :epochDay")
    suspend fun bondXpOn(mentor: com.saiyanhabits.domain.Mentor, epochDay: Long): Int

    @Insert
    suspend fun insert(play: MinigamePlayEntity): Long
}

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY epochDay DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE epochDay = :epochDay ORDER BY createdAtMillis")
    suspend fun onDay(epochDay: Long): List<JournalEntryEntity>

    @Query("SELECT DISTINCT promptId FROM journal_entries WHERE promptId > 0")
    suspend fun usedPromptIds(): List<Int>

    @Query("SELECT DISTINCT epochDay FROM journal_entries WHERE bondXp > 0 ORDER BY epochDay DESC")
    suspend fun rewardedDays(): List<Long>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: Long): JournalEntryEntity?

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @androidx.room.Update
    suspend fun update(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CallDao {

    @Query("SELECT * FROM mentor_calls ORDER BY answeredAtMillis DESC")
    fun observeAll(): Flow<List<MentorCallEntity>>

    @Query("SELECT * FROM mentor_calls WHERE epochDay = :epochDay")
    suspend fun onDay(epochDay: Long): List<MentorCallEntity>

    @Query("SELECT callId FROM mentor_calls WHERE mentor = :mentor")
    suspend fun heardCallIds(mentor: com.saiyanhabits.domain.Mentor): List<String>

    @Insert
    suspend fun insert(call: MentorCallEntity): Long

    @Query("SELECT COUNT(*) FROM habit_misses WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun missesBetween(fromDay: Long, toDay: Long): Int

    @Query("SELECT MIN(epochDay) FROM completions")
    suspend fun firstCheckOffDay(): Long?
}

@Dao
interface RoutineDao {

    // ---- Templates ----

    @Query("SELECT * FROM routine_templates ORDER BY name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<RoutineTemplateEntity>>

    @Query("SELECT * FROM routine_template_items ORDER BY templateId, position")
    fun observeTemplateItems(): Flow<List<RoutineTemplateItemEntity>>

    @Query("SELECT * FROM routine_templates WHERE id = :id")
    suspend fun template(id: Long): RoutineTemplateEntity?

    @Query("SELECT * FROM routine_template_items WHERE templateId = :templateId ORDER BY position")
    suspend fun templateItems(templateId: Long): List<RoutineTemplateItemEntity>

    @Insert
    suspend fun insertTemplate(template: RoutineTemplateEntity): Long

    @androidx.room.Update
    suspend fun updateTemplate(template: RoutineTemplateEntity)

    @Insert
    suspend fun insertTemplateItems(items: List<RoutineTemplateItemEntity>)

    @Query("DELETE FROM routine_template_items WHERE templateId = :templateId")
    suspend fun clearTemplateItems(templateId: Long)

    @Query("DELETE FROM routine_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    // ---- Day routines ----

    @Query("SELECT * FROM day_routines WHERE epochDay >= :fromDay ORDER BY epochDay, position, id")
    fun observeRoutinesFrom(fromDay: Long): Flow<List<DayRoutineEntity>>

    @Query("SELECT * FROM day_routines WHERE epochDay = :epochDay ORDER BY position, id")
    fun observeRoutinesOn(epochDay: Long): Flow<List<DayRoutineEntity>>

    @Query("SELECT i.* FROM day_routine_items i JOIN day_routines r ON r.id = i.routineId WHERE r.epochDay >= :fromDay ORDER BY i.routineId, i.position")
    fun observeItemsFrom(fromDay: Long): Flow<List<DayRoutineItemEntity>>

    @Query("SELECT * FROM day_routines WHERE id = :id")
    suspend fun routine(id: Long): DayRoutineEntity?

    @Query("SELECT * FROM day_routine_items WHERE routineId = :routineId ORDER BY position")
    suspend fun routineItems(routineId: Long): List<DayRoutineItemEntity>

    @Query("SELECT * FROM day_routine_items WHERE id = :id")
    suspend fun item(id: Long): DayRoutineItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM day_routines WHERE epochDay = :epochDay")
    suspend fun lastPositionOn(epochDay: Long): Int

    @Insert
    suspend fun insertRoutine(routine: DayRoutineEntity): Long

    @androidx.room.Update
    suspend fun updateRoutine(routine: DayRoutineEntity)

    @Insert
    suspend fun insertItems(items: List<DayRoutineItemEntity>): List<Long>

    @androidx.room.Update
    suspend fun updateItem(item: DayRoutineItemEntity)

    @Query("DELETE FROM day_routine_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM day_routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)
}

@Dao
interface CardDao {

    @Query("SELECT * FROM card_collection WHERE count > 0")
    fun observeCollection(): Flow<List<CardOwnedEntity>>

    @Query("SELECT * FROM card_collection WHERE count > 0")
    suspend fun collection(): List<CardOwnedEntity>

    @Query("SELECT * FROM card_collection WHERE cardId = :cardId")
    suspend fun owned(cardId: String): CardOwnedEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun saveOwned(card: CardOwnedEntity)

    @Query("SELECT * FROM card_held WHERE count > 0")
    fun observeHeld(): Flow<List<CardHeldEntity>>

    @Query("SELECT * FROM card_held WHERE challengerId = :challengerId AND count > 0")
    suspend fun heldBy(challengerId: String): List<CardHeldEntity>

    @Query("SELECT * FROM card_held WHERE challengerId = :challengerId AND cardId = :cardId")
    suspend fun held(challengerId: String, cardId: String): CardHeldEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun saveHeld(held: CardHeldEntity)

    @Insert
    suspend fun insertMatch(match: CardMatchEntity): Long

    @Query("SELECT * FROM card_matches WHERE epochDay = :epochDay")
    suspend fun matchesOn(epochDay: Long): List<CardMatchEntity>

    @Query("SELECT * FROM card_matches ORDER BY createdAtMillis DESC")
    fun observeMatches(): Flow<List<CardMatchEntity>>

    @Query("SELECT * FROM card_packs WHERE epochDay = :epochDay")
    suspend fun pack(epochDay: Long): CardPackEntity?

    @Insert
    suspend fun insertPack(pack: CardPackEntity)

    @Query("SELECT * FROM card_pending WHERE outcome = ''")
    suspend fun openPending(): List<CardPendingEntity>

    @Query("SELECT * FROM card_pending WHERE outcome = ''")
    fun observeOpenPending(): Flow<List<CardPendingEntity>>

    @Insert
    suspend fun insertPending(pending: CardPendingEntity): Long

    @androidx.room.Update
    suspend fun updatePending(pending: CardPendingEntity)

    @Query("SELECT COUNT(*) FROM card_grants WHERE grantId = :grantId")
    suspend fun granted(grantId: String): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun grant(grant: CardGrantEntity)
}

