package com.saiyanhabits.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.saiyanhabits.domain.CallBook
import com.saiyanhabits.domain.CallParser
import com.saiyanhabits.domain.CallRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MentorCall
import com.saiyanhabits.domain.ScriptRunner
import com.saiyanhabits.domain.ScriptStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/** A call ready to play: who, which, and the numbers it can mention. */
data class ReadyCall(val mentor: Mentor, val call: MentorCall, val stats: ScriptStats, val ringing: Boolean)

/** What answering a call earned. */
data class CallResult(val bondXp: Int, val pickedUpQuickly: Boolean, val bondLevelUp: Int?)

/**
 * Daily calls from the mentors: one call per mentor per day, picked from
 * their file, filled in with your real numbers. One mentor rings each day;
 * picking up soon after gives a bonus.
 */
class CallRepository(
    context: Context,
    private val db: AppDatabase,
    private val habits: HabitRepository,
    private val journal: JournalRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val appContext = context.applicationContext
    private val dao = db.callDao()
    private val prefs = appContext.getSharedPreferences("calls", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var books: Map<Mentor, CallBook>? = null

    private val _levelUps = MutableSharedFlow<GameEvent.BondLevelUp>(extraBufferCapacity = 4)

    /** Bond levels gained from calls, as they happen. */
    val levelUps: SharedFlow<GameEvent.BondLevelUp> = _levelUps

    /** Every answered call, newest first. */
    val log: Flow<List<MentorCallEntity>> = dao.observeAll()

    suspend fun book(mentor: Mentor): CallBook? = lock.withLock {
        val loaded = books ?: withContext(Dispatchers.IO) {
            Mentor.entries.mapNotNull { m ->
                val file = "dialogue/calls_${m.name.lowercase()}.json"
                try {
                    m to CallParser.parse(appContext.assets.open(file).bufferedReader().use { it.readText() })
                } catch (e: Exception) {
                    Log.e("Calls", "Could not read $file: ${e.message}")
                    null
                }
            }.toMap()
        }
        books = loaded
        loaded[mentor]
    }

    /** Mentors already talked to today. */
    suspend fun answeredToday(): Set<Mentor> = dao.onDay(today().toEpochDay()).map { it.mentor }.toSet()

    /** The mentor ringing right now, if one rang today and hasn't been answered. */
    suspend fun ringing(): Mentor? {
        if (prefs.getLong(KEY_RING_DAY, -1) != today().toEpochDay()) return null
        val mentor = prefs.getString(KEY_RING_MENTOR, null)?.let { name -> Mentor.entries.firstOrNull { it.name == name } } ?: return null
        return mentor.takeIf { it !in answeredToday() }
    }

    /** Picks who rings today and when, once a day. Returns the mentor and the minute of the day. */
    suspend fun planRing(): Pair<Mentor, Int>? {
        val day = today().toEpochDay()
        if (prefs.getLong(KEY_PLAN_DAY, -1) == day) {
            val m = prefs.getString(KEY_PLAN_MENTOR, null)?.let { n -> Mentor.entries.firstOrNull { it.name == n } } ?: return null
            return m to prefs.getInt(KEY_PLAN_MINUTE, 0)
        }
        val mentor = CallRules.whoRings(answeredToday(), random) ?: return null
        val minute = CallRules.ringMinute(random)
        prefs.edit {
            putLong(KEY_PLAN_DAY, day)
            putString(KEY_PLAN_MENTOR, mentor.name)
            putInt(KEY_PLAN_MINUTE, minute)
        }
        return mentor to minute
    }

    /** Records that [mentor]'s phone rang now (the notification went out). */
    fun markRang(mentor: Mentor) {
        prefs.edit {
            putLong(KEY_RING_DAY, today().toEpochDay())
            putString(KEY_RING_MENTOR, mentor.name)
            putLong(KEY_RING_AT, nowMillis())
        }
    }

    /** The notification line for [mentor] ringing. */
    suspend fun ringLine(mentor: Mentor): String =
        book(mentor)?.ring?.randomOrNull(random) ?: "${mentor.displayName} is calling."

    /**
     * Today's call from [mentor], or null if you've already talked today or
     * there are no calls. The same call is kept all day until answered.
     */
    suspend fun todaysCall(mentor: Mentor): ReadyCall? {
        if (mentor in answeredToday()) return null
        val book = book(mentor) ?: return null
        val stats = stats(mentor)
        val day = today().toEpochDay()
        val keyDay = "day_${mentor.name}"
        val keyId = "call_${mentor.name}"
        val kept = if (prefs.getLong(keyDay, -1) == day) prefs.getString(keyId, null)?.let(book::call) else null
        val call = kept ?: run {
            val usable = book.calls.filter { canPlay(it, stats) }
            val heard = dao.heardCallIds(mentor).toSet()
            CallRules.pickCall(usable, heard, random, last = heard.lastOrNull(), daysUsing = stats.number("days_using"))
        } ?: return null
        prefs.edit {
            putLong(keyDay, day)
            putString(keyId, call.id)
        }
        return ReadyCall(mentor, call, stats, ringing = ringing() == mentor)
    }

    /** A past call, replayed with today's numbers (the numbers aren't stored). */
    suspend fun replay(entry: MentorCallEntity): ReadyCall? {
        val call = book(entry.mentor)?.call(entry.callId) ?: return null
        return ReadyCall(entry.mentor, call, stats(entry.mentor), ringing = false)
    }

    /** Saves a finished call with the replies picked, and gives its bond XP. */
    suspend fun finish(ready: ReadyCall, picked: List<Int>): CallResult {
        val now = nowMillis()
        val ringAt = if (prefs.getLong(KEY_RING_DAY, -1) == today().toEpochDay() &&
            prefs.getString(KEY_RING_MENTOR, null) == ready.mentor.name
        ) prefs.getLong(KEY_RING_AT, 0) else null
        val quick = CallRules.isQuick(ringAt, now)
        val xp = CallRules.bondXp(quick)
        val levelUp = db.withTransaction {
            if (ready.mentor in answeredToday()) return@withTransaction null
            val before = db.bondDao().getBondXp().toLevels().getValue(ready.mentor)
            dao.insert(
                MentorCallEntity(
                    mentor = ready.mentor,
                    epochDay = today().toEpochDay(),
                    callId = ready.call.id,
                    choices = picked.joinToString(","),
                    bondXp = xp,
                    pickedUpQuickly = quick,
                    answeredAtMillis = now,
                ),
            )
            val after = db.bondDao().getBondXp().toLevels().getValue(ready.mentor)
            after.takeIf { it > before }
        }
        levelUp?.let { _levelUps.emit(GameEvent.BondLevelUp(ready.mentor, it)) }
        return CallResult(xp, quick, levelUp)
    }

    /** True if every number and word the call mentions is known. */
    private fun canPlay(call: MentorCall, stats: ScriptStats): Boolean =
        ScriptRunner.statsUsed(call.script).all { it in stats.numbers || it in stats.words }

    /** Your numbers, for check-ins. Habit names are only known when there are habits to name. */
    suspend fun stats(mentor: Mentor): ScriptStats {
        val todayDay = today().toEpochDay()
        val week = db.completionDao().getBetween(todayDay - 6, todayDay)
        val fortnight = db.completionDao().getBetween(todayDay - 13, todayDay)
        val active = habits.getActiveHabits()
        val counts = fortnight.groupingBy { it.habitId }.eachCount()
        val top = active.filter { (counts[it.id] ?: 0) > 0 }.maxByOrNull { counts[it.id] ?: 0 }
        // The weakest is the habit done least, but only among habits older than a few days.
        val weak = active
            .filter { it.id != top?.id }
            .minByOrNull { counts[it.id] ?: 0 }
            ?.takeIf { (counts[it.id] ?: 0) < (counts[top?.id] ?: 0) }
        val first = dao.firstCheckOffDay()
        val numbers = mapOf(
            "streak" to habits.currentStreak(),
            "power" to habits.powerLevelNow().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            "week_checkoffs" to week.size,
            "week_missed" to dao.missesBetween(todayDay - 6, todayDay),
            "journal_streak" to journal.streak(),
            "bond_level" to db.bondDao().getBondXp().toLevels().getValue(mentor),
            "habits" to active.size,
            "days_using" to if (first == null) 0 else (todayDay - first + 1).toInt(),
        )
        val words = buildMap {
            top?.let { put("top_habit", it.name) }
            weak?.let { put("weak_habit", it.name) }
        }
        return ScriptStats(numbers, words)
    }

    private companion object {
        const val KEY_PLAN_DAY = "plan_day"
        const val KEY_PLAN_MENTOR = "plan_mentor"
        const val KEY_PLAN_MINUTE = "plan_minute"
        const val KEY_RING_DAY = "ring_day"
        const val KEY_RING_MENTOR = "ring_mentor"
        const val KEY_RING_AT = "ring_at"
    }
}
