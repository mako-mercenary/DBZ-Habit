package com.saiyanhabits.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.Technique
import kotlinx.coroutines.flow.Flow

/** When an activated technique was last used. One row per technique that has been used at all. */
@Entity(tableName = "technique_uses")
data class TechniqueUseEntity(
    @PrimaryKey val technique: Technique,
    val lastUsedEpochDay: Long,
)

/** A habit excused for one day with Master's Day Off: it does not count as scheduled that day. */
@Entity(
    tableName = "habit_excuses",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["epochDay"])],
)
data class HabitExcuseEntity(
    val habitId: Long,
    val epochDay: Long,
)

/** Query result: total bond XP with one mentor. */
data class MentorXp(val mentor: Mentor, val xp: Int)

@Dao
interface BondDao {

    /** Bond XP per mentor: the XP of every check-off of a habit that mentor trained. */
    @Query(BOND_XP)
    fun observeBondXp(): Flow<List<MentorXp>>

    @Query(BOND_XP)
    suspend fun getBondXp(): List<MentorXp>

    @Query("SELECT * FROM technique_uses")
    fun observeTechniqueUses(): Flow<List<TechniqueUseEntity>>

    @Query("SELECT * FROM technique_uses")
    suspend fun getTechniqueUses(): List<TechniqueUseEntity>

    @Upsert
    suspend fun saveTechniqueUse(use: TechniqueUseEntity)

    @Query("SELECT habitId FROM habit_excuses WHERE epochDay = :epochDay")
    fun observeExcusedOn(epochDay: Long): Flow<List<Long>>

    @Query("SELECT * FROM habit_excuses WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun getExcusesBetween(fromDay: Long, toDay: Long): List<HabitExcuseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExcuse(excuse: HabitExcuseEntity)
}

/** Bond XP per mentor: from check-offs, finished mini games, journal entries (Bulma's) and calls. */
private const val BOND_XP =
    "SELECT mentor AS mentor, SUM(xp) AS xp FROM (" +
        "SELECT mentor, bondXp AS xp FROM completions WHERE mentor IS NOT NULL " +
        "UNION ALL SELECT mentor, bondXp AS xp FROM minigame_plays " +
        "UNION ALL SELECT 'BULMA' AS mentor, bondXp AS xp FROM journal_entries " +
        "UNION ALL SELECT mentor, bondXp AS xp FROM mentor_calls) GROUP BY mentor"

internal fun List<MentorXp>.toLevels(): Map<Mentor, Int> =
    Mentor.entries.associateWith { mentor -> BondRules.levelForXp(firstOrNull { it.mentor == mentor }?.xp ?: 0) }

internal fun perksFrom(xp: List<MentorXp>, uses: List<TechniqueUseEntity>, todayEpochDay: Long): Perks {
    val levels = xp.toLevels()
    val perks = Perks.from(levels, doublePointsToday = false)
    val kaiokenToday = Technique.KAIOKEN in perks.techniques &&
        uses.any { it.technique == Technique.KAIOKEN && it.lastUsedEpochDay == todayEpochDay }
    return perks.copy(doublePointsToday = kaiokenToday)
}

/** The perks in force on [todayEpochDay]. Call inside a transaction when the result feeds a write. */
internal suspend fun AppDatabase.readPerks(todayEpochDay: Long): Perks =
    perksFrom(bondDao().getBondXp(), bondDao().getTechniqueUses(), todayEpochDay)
