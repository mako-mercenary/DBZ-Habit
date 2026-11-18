package com.saiyanhabits.data

import android.content.Context
import androidx.core.content.edit
import com.saiyanhabits.domain.Achievement
import com.saiyanhabits.domain.AchievementRules
import com.saiyanhabits.domain.AchievementStats
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.domain.HistoryRules
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.Weeks
import com.saiyanhabits.domain.WishRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

/** Achievements as they stand. */
data class AchievementState(val stats: AchievementStats, val unlocked: Set<Achievement>) {
    val title: String get() = AchievementRules.title(unlocked.size)
}

/** What was newly unlocked since the last look. */
sealed interface AchievementNews {
    data class Unlocked(val achievements: List<Achievement>) : AchievementNews

    /** The first look after the update that added achievements: past training already earned these. */
    data class Backdated(val count: Int) : AchievementNews
}

/**
 * Works achievements out from the data already in the database, so nothing
 * extra is stored with them and an export carries them along. The only thing
 * kept is which ones have been announced, so an undo never takes one back.
 */
class AchievementRepository(
    context: Context,
    private val db: AppDatabase,
    private val habits: HabitRepository,
) {
    private val dao = db.achievementDao()
    private val prefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<AchievementState> = dao.observeChanges().mapLatest {
        val stats = stats()
        AchievementState(stats, AchievementRules.unlocked(stats, seen()))
    }

    /** Marks [state]'s achievements as announced and says which ones are new. Null if none are. */
    @Synchronized
    fun takeNews(state: AchievementState): AchievementNews? {
        val seen = seen()
        val names = state.unlocked.map { it.name }.toSet()
        val fresh = state.unlocked.filter { it.name !in seen }
        if (fresh.isEmpty() && prefs.getBoolean(KEY_STARTED, false)) return null
        val first = !prefs.getBoolean(KEY_STARTED, false)
        prefs.edit {
            putStringSet(KEY_SEEN, seen + names)
            putBoolean(KEY_STARTED, true)
        }
        return when {
            fresh.isEmpty() -> null
            first -> AchievementNews.Backdated(fresh.size)
            else -> AchievementNews.Unlocked(fresh.sortedBy { it.ordinal })
        }
    }

    private fun seen(): Set<String> = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()

    suspend fun stats(): AchievementStats {
        val records = db.streakDao().getAllRecords()
        val outcomes = records.map { it.outcome }
        val byWeek = records.groupBy { Weeks.startOf(it.epochDay) }
        val sources = db.powerDao().countBySource(Long.MIN_VALUE, Long.MAX_VALUE).associate { it.source to it.count }
        val player = db.streakDao().getPlayerState() ?: PlayerStateEntity()
        return AchievementStats(
            checkOffs = dao.checkOffs(),
            perfectDays = outcomes.count { it == DayOutcome.PERFECT },
            bestStreak = maxOf(HistoryRules.streakAfterEach(outcomes).maxOrNull() ?: 0, habits.currentStreak()),
            perfectWeeks = byWeek.values.count { week -> WishRules.isPerfectWeek(week.map { it.outcome }) },
            beanSaves = outcomes.count { it == DayOutcome.BEAN_COVERED },
            powerLevel = PowerCalculator.powerLevel(dao.totalPoints()),
            highestForm = player.highestForm,
            bossesDefeated = dao.defeatedBosses(),
            milestonesHit = sources[PowerSource.MILESTONE] ?: 0,
            bombsThrown = sources[PowerSource.BOMB_THROW] ?: 0,
            chamberSessions = dao.chamberSessions(),
            bondLevels = db.bondDao().getBondXp().toLevels(),
            chaptersRead = Integer.bitCount(player.chaptersRead),
            wishesGranted = dao.wishesGranted(),
            notesWritten = dao.notesWritten(),
            bestHabitStreak = habits.habitStreaks.first().values.maxOrNull() ?: 0,
        )
    }

    private companion object {
        const val KEY_SEEN = "seen"
        const val KEY_STARTED = "started"
    }
}
