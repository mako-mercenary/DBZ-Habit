package com.saiyanhabits.data

import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.PowerCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HabitRepository(
    private val habitDao: HabitDao,
    private val completionDao: CompletionDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    val activeHabits: Flow<List<HabitEntity>> = habitDao.observeActive()

    val powerLevel: Flow<Long> = completionDao.observeTotalPoints().map(PowerCalculator::powerLevel)

    fun completionsOn(date: LocalDate): Flow<List<CompletionEntity>> =
        completionDao.observeForDay(date.toEpochDay())

    suspend fun saveHabit(habit: HabitEntity): Long = habitDao.save(habit)

    suspend fun archiveHabit(id: Long) = habitDao.archive(id)

    /**
     * Checks a habit off for [date]. Returns the points awarded, or 0 if it
     * was already checked off that day.
     */
    suspend fun complete(habit: HabitEntity, date: LocalDate, gravity: Gravity): Int {
        val points = PowerCalculator.completionPoints(habit.difficulty, gravity)
        val rowId = completionDao.insert(
            CompletionEntity(
                habitId = habit.id,
                epochDay = date.toEpochDay(),
                gravity = gravity,
                points = points,
                completedAtMillis = nowMillis(),
            ),
        )
        return if (rowId == -1L) 0 else points
    }

    /** Takes back a check-off made by mistake, along with its points. */
    suspend fun undoCompletion(habitId: Long, date: LocalDate) =
        completionDao.delete(habitId, date.toEpochDay())
}
