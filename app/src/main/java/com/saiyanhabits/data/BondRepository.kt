package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.StreakRules
import com.saiyanhabits.domain.Weeks
import com.saiyanhabits.domain.Technique
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Where the bond with one mentor stands. */
data class Bond(val mentor: Mentor, val xp: Int) {
    val level: Int get() = BondRules.levelForXp(xp)
}

/** Mentor bonds, techniques and the perks they give. */
class BondRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val bondDao = db.bondDao()

    /** One entry per mentor, in mentor order, including those with no XP yet. */
    val bonds: Flow<List<Bond>> = bondDao.observeBondXp().map { rows ->
        Mentor.entries.map { mentor -> Bond(mentor, rows.firstOrNull { it.mentor == mentor }?.xp ?: 0) }
    }

    /** The day each activated technique was last used. */
    val techniqueUses: Flow<Map<Technique, Long>> = bondDao.observeTechniqueUses()
        .map { rows -> rows.associate { it.technique to it.lastUsedEpochDay } }

    /** The perks in force right now. */
    val perks: Flow<Perks> = combine(bondDao.observeBondXp(), bondDao.observeTechniqueUses()) { xp, uses ->
        perksFrom(xp, uses, today().toEpochDay())
    }

    /** Ids of the habits excused on [date]. */
    fun excusedOn(date: LocalDate): Flow<Set<Long>> =
        bondDao.observeExcusedOn(date.toEpochDay()).map { it.toSet() }

    /** Uses Kaio-ken: check-offs pay double for the rest of today. False if it is locked or cooling down. */
    suspend fun useKaioken(): Boolean = db.withTransaction { markUsed(Technique.KAIOKEN) }

    /**
     * Uses Master's Day Off on one of today's habits: it stops counting for
     * today. False if the technique is locked or cooling down, or the habit is
     * not waiting to be done today.
     */
    suspend fun useDayOff(habitId: Long): Boolean = db.withTransaction {
        val date = today()
        val day = date.toEpochDay()
        val habit = db.habitDao().getById(habitId)
        val week = db.completionDao().getBetween(Weeks.startOf(day), day)
            .groupBy({ it.epochDay }, { it.habitId }).mapValues { it.value.toSet() }
        val scheduledToday = habit != null && !habit.archived &&
            habitId in StreakRules.countedOn(listOf(habit.toScheduled()), day, week)
        val doneToday = habitId in week[day].orEmpty()
        if (!scheduledToday || doneToday) return@withTransaction false
        if (!markUsed(Technique.DAY_OFF)) return@withTransaction false
        bondDao.insertExcuse(HabitExcuseEntity(habitId, day))
        true
    }

    /** Records that [technique] was used today, if it is unlocked and off cooldown. */
    private suspend fun markUsed(technique: Technique): Boolean {
        val day = today().toEpochDay()
        val unlocked = BondRules.unlockedTechniques(bondDao.getBondXp().toLevels())
        val lastUsed = bondDao.getTechniqueUses().firstOrNull { it.technique == technique }?.lastUsedEpochDay
        if (technique !in unlocked || BondRules.cooldownDaysLeft(technique, lastUsed, day) > 0) return false
        bondDao.saveTechniqueUse(TechniqueUseEntity(technique, day))
        return true
    }
}
