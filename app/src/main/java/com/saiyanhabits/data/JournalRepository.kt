package com.saiyanhabits.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.saiyanhabits.GameConfig

import com.saiyanhabits.domain.JournalBook
import com.saiyanhabits.domain.JournalParser
import com.saiyanhabits.domain.JournalPrompt
import com.saiyanhabits.domain.JournalReaction
import com.saiyanhabits.domain.JournalReward
import com.saiyanhabits.domain.JournalRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.PowerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/** What happened when an entry was saved. */
data class JournalSaveResult(
    val entryId: Long,
    val words: Int,
    val reward: JournalReward,
    /** Bulma's reply. */
    val line: String,
    val bondLevelUp: Int?,
)

/** The answer to asking for a different prompt. */
data class JournalSwap(val prompt: JournalPrompt?, val line: String, val swapped: Boolean)

/**
 * The journal: Bulma's prompt of the day, entries, and the once-a-day reward
 * (bond XP with Bulma and power). Today's prompt and swaps are kept on the
 * phone; entries are in the database and so in backups.
 */
class JournalRepository(
    context: Context,
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val appContext = context.applicationContext
    private val dao = db.journalDao()
    private val prefs = appContext.getSharedPreferences("journal", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var cachedBook: JournalBook? = null

    private val _levelUps = MutableSharedFlow<GameEvent.BondLevelUp>(extraBufferCapacity = 4)

    /** Bond levels with Bulma gained from journal entries, as they happen. */
    val levelUps: SharedFlow<GameEvent.BondLevelUp> = _levelUps

    /** Every entry, newest first. */
    val entries: Flow<List<JournalEntryEntity>> = dao.observeAll()

    /** Bulma's prompts and lines; empty if the file can't be read. */
    suspend fun book(): JournalBook = lock.withLock {
        cachedBook ?: withContext(Dispatchers.IO) {
            try {
                JournalParser.parse(appContext.assets.open(FILE).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.e("Journal", "Could not read $FILE: ${e.message}")
                JournalBook(emptyList(), emptyMap(), emptyList(), emptyList())
            }
        }.also { cachedBook = it }
    }

    /** Today's prompt: kept all day, picked fresh the first time it's asked for on a new day. */
    suspend fun todaysPrompt(): JournalPrompt? {
        val book = book()
        val day = today().toEpochDay()
        if (prefs.getLong(KEY_DAY, -1) == day) {
            book.prompt(prefs.getInt(KEY_PROMPT, 0))?.let { return it }
        }
        val id = JournalRules.pickPrompt(book.prompts.map { it.id }, dao.usedPromptIds().toSet(), random) ?: return null
        prefs.edit {
            putLong(KEY_DAY, day)
            putInt(KEY_PROMPT, id)
            putInt(KEY_SWAPS, 0)
        }
        return book.prompt(id)
    }

    /** Swaps left today. */
    fun swapsLeft(): Int {
        val used = if (prefs.getLong(KEY_DAY, -1) == today().toEpochDay()) prefs.getInt(KEY_SWAPS, 0) else 0
        return (GameConfig.JOURNAL_SWAPS_PER_DAY - used).coerceAtLeast(0)
    }

    /** Asks Bulma for a different prompt, if today's swaps aren't used up. */
    suspend fun swapPrompt(): JournalSwap {
        val book = book()
        val current = todaysPrompt()
        if (swapsLeft() <= 0) return JournalSwap(current, book.noSwap.randomOrNull(random) ?: "", swapped = false)
        val id = JournalRules.pickPrompt(book.prompts.map { it.id }, dao.usedPromptIds().toSet(), random, avoid = current?.id)
            ?: return JournalSwap(current, "", swapped = false)
        prefs.edit {
            putInt(KEY_PROMPT, id)
            putInt(KEY_SWAPS, prefs.getInt(KEY_SWAPS, 0) + 1)
        }
        return JournalSwap(book.prompt(id), book.swap.randomOrNull(random) ?: "", swapped = true)
    }

    suspend fun entry(id: Long): JournalEntryEntity? = dao.get(id)

    /** Days in a row with a finished entry. */
    suspend fun streak(): Int = JournalRules.streak(dao.rewardedDays(), today().toEpochDay())

    /**
     * Saves [text] as a new entry for [prompt] (null for a free entry), or, with
     * [entryId], updates that entry. The day's first finished entry gives the
     * reward; an unfinished one written earlier today earns it once it's finished.
     */
    suspend fun save(prompt: JournalPrompt?, text: String, entryId: Long? = null): JournalSaveResult {
        val words = JournalRules.wordCount(text)
        val day = today().toEpochDay()
        val result = db.withTransaction {
            val todays = dao.onDay(day)
            val alreadyRewarded = todays.any { it.bondXp > 0 && it.id != entryId }
            val finishedBefore = dao.rewardedDays().size
            val existing = entryId?.let { dao.get(it) }
            // Only an entry written today can earn today's reward, and only once.
            val canEarn = existing == null || (existing.epochDay == day && existing.bondXp == 0)
            val reward = if (canEarn) JournalRules.reward(words, alreadyRewarded) else JournalReward(0, 0)
            val before = db.bondDao().getBondXp().toLevels().getValue(Mentor.BULMA)
            val now = nowMillis()
            val id = if (existing == null) {
                dao.insert(
                    JournalEntryEntity(
                        epochDay = day,
                        promptId = prompt?.id ?: 0,
                        prompt = prompt?.text ?: "",
                        text = text.trim(),
                        words = words,
                        bondXp = reward.bondXp,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                )
            } else {
                dao.update(
                    existing.copy(
                        text = text.trim(),
                        words = words,
                        bondXp = existing.bondXp + reward.bondXp,
                        updatedAtMillis = now,
                    ),
                )
                existing.id
            }
            if (reward.power > 0) {
                db.powerDao().insert(PowerEventEntity(0, day, reward.power, PowerSource.JOURNAL, id, now))
            }
            val after = db.bondDao().getBondXp().toLevels().getValue(Mentor.BULMA)
            val streakAfter = JournalRules.streak(dao.rewardedDays(), day)
            val reaction = when {
                existing != null && !reward.any && words >= GameConfig.JOURNAL_MIN_WORDS -> JournalReaction.EXTRA
                else -> JournalRules.reaction(words, alreadyRewarded || !canEarn, finishedBefore, streakAfter)
            }
            Triple(id, reward, reaction) to after.takeIf { it > before }
        }
        val (saved, levelUp) = result
        val (id, reward, reaction) = saved
        levelUp?.let { _levelUps.emit(GameEvent.BondLevelUp(Mentor.BULMA, it)) }
        val line = book().reactions[reaction]?.randomOrNull(random) ?: ""
        return JournalSaveResult(id, words, reward, line, levelUp)
    }

    /** Deletes an entry, and the power it gave. Its bond XP goes with it. */
    suspend fun delete(id: Long) {
        db.withTransaction {
            dao.delete(id)
            db.powerDao().delete(PowerSource.JOURNAL, id)
        }
    }

    private companion object {
        const val FILE = "dialogue/journal.json"
        const val KEY_DAY = "prompt_day"
        const val KEY_PROMPT = "prompt_id"
        const val KEY_SWAPS = "swaps_used"
    }
}
