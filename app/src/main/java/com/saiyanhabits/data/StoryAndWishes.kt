package com.saiyanhabits.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.saiyanhabits.domain.StoryChapter
import com.saiyanhabits.domain.StoryParser
import com.saiyanhabits.domain.WishRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The story chapters, read from assets/dialogue/story.json the first time
 * they are needed. A missing or broken file gives an empty story rather than
 * crashing the app; the unit tests check the file that ships.
 */
class StoryRepository(private val context: Context) {
    private val lock = Mutex()
    private var cached: List<StoryChapter>? = null

    suspend fun chapters(): List<StoryChapter> = lock.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private fun load(): List<StoryChapter> =
        try {
            StoryParser.parse(context.assets.open(FILE).bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e("Story", "Could not read $FILE: ${e.message}")
            emptyList()
        }

    companion object {
        const val FILE = "dialogue/story.json"
    }
}

/** The wishes typed in for the dragon, and granting them. */
class WishRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val wishDao = db.wishDao()

    /** Every wish: those still waiting first, then the granted ones. */
    val wishes: Flow<List<WishEntity>> = wishDao.observeAll()

    suspend fun addWish(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) wishDao.insert(WishEntity(text = trimmed, createdAtEpochDay = today().toEpochDay()))
    }

    /** Removes a wish that has not been granted. Granted wishes stay as history. */
    suspend fun removeWish(id: Long) = wishDao.deleteWaiting(id)

    /**
     * Grants a waiting wish: it is marked as granted today and the orbs are
     * used up. False if there are not enough orbs or the wish is not waiting.
     */
    suspend fun grantWish(id: Long): Boolean = db.withTransaction {
        val state = db.streakDao().getPlayerState() ?: PlayerStateEntity()
        val wish = wishDao.get(id)
        if (wish == null || wish.grantedAtEpochDay != null || !WishRules.canSummon(state.wishOrbs)) {
            false
        } else {
            wishDao.update(wish.copy(grantedAtEpochDay = today().toEpochDay()))
            db.streakDao().savePlayerState(state.copy(wishOrbs = 0))
            true
        }
    }
}
