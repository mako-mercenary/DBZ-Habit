package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import kotlin.random.Random

/** One of Bulma's journal prompts. */
data class JournalPrompt(val id: Int, val topic: String, val text: String)

/** What Bulma says after an entry is saved, by situation. */
enum class JournalReaction(val key: String) {
    /** The first finished entry ever. */
    FIRST("first"),

    /** A finished entry, nothing special about it. */
    DONE("done"),

    /** A long one. */
    LONG("long"),

    /** Finished entries several days running. */
    STREAK("streak"),

    /** Another entry on a day that already had one. */
    EXTRA("extra"),

    /** Saved before reaching the minimum: kept, but no reward. */
    SHORT("short"),
}

/** Bulma's prompts and lines for the journal (assets/dialogue/journal.json). */
data class JournalBook(
    val prompts: List<JournalPrompt>,
    val reactions: Map<JournalReaction, List<String>>,
    /** What she says when you ask for a different prompt. */
    val swap: List<String>,
    /** What she says when the swaps are used up. */
    val noSwap: List<String>,
) {
    fun prompt(id: Int): JournalPrompt? = prompts.firstOrNull { it.id == id }
}

/** The reward for saving an entry. */
data class JournalReward(val bondXp: Int, val power: Int) {
    val any: Boolean get() = bondXp > 0 || power > 0
}

/** Journal rules. Pure functions, no Android. */
object JournalRules {

    /** Words in [text]: runs of characters between spaces that hold at least one letter or digit. */
    fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    fun isFinished(text: String): Boolean = wordCount(text) >= GameConfig.JOURNAL_MIN_WORDS

    /**
     * A prompt for today: a random one not written to yet, or, once every prompt
     * has been used, any of them except [avoid] (the one being swapped away).
     */
    fun pickPrompt(ids: List<Int>, used: Set<Int>, random: Random, avoid: Int? = null): Int? {
        if (ids.isEmpty()) return null
        val fresh = ids.filter { it !in used && it != avoid }
        val pool = fresh.ifEmpty { ids.filter { it != avoid }.ifEmpty { ids } }
        return pool[random.nextInt(pool.size)]
    }

    /** The reward for saving an entry of [words] words, when the day [alreadyRewarded] or not. */
    fun reward(words: Int, alreadyRewarded: Boolean): JournalReward =
        if (alreadyRewarded || words < GameConfig.JOURNAL_MIN_WORDS) {
            JournalReward(0, 0)
        } else {
            JournalReward(GameConfig.JOURNAL_BOND_XP, GameConfig.JOURNAL_POWER)
        }

    /**
     * Days in a row with a finished entry, counting back from [today], or from
     * yesterday when today has none yet. [days] are epoch days, any order.
     */
    fun streak(days: Collection<Long>, today: Long): Int {
        val set = days.toSet()
        var day = if (today in set) today else today - 1
        var run = 0
        while (day in set) {
            run++
            day--
        }
        return run
    }

    /** Which reaction fits a save. */
    fun reaction(words: Int, alreadyRewarded: Boolean, finishedBefore: Int, streakAfter: Int): JournalReaction = when {
        words < GameConfig.JOURNAL_MIN_WORDS -> JournalReaction.SHORT
        alreadyRewarded -> JournalReaction.EXTRA
        finishedBefore == 0 -> JournalReaction.FIRST
        streakAfter >= 3 && streakAfter % 3 == 0 -> JournalReaction.STREAK
        words >= GameConfig.JOURNAL_LONG_WORDS -> JournalReaction.LONG
        else -> JournalReaction.DONE
    }
}

/** Reads assets/dialogue/journal.json. Throws IllegalArgumentException if it is malformed. */
object JournalParser {
    fun parse(json: String): JournalBook {
        val root = MiniJson.parse(json).asObject("the file")
        val prompts = root["prompts"].asArray("\"prompts\"").map { p ->
            val o = p.asObject("a prompt")
            JournalPrompt(o["id"].asInt("prompt id"), o["topic"].asString("prompt topic"), o["text"].asString("prompt text"))
        }
        require(prompts.map { it.id }.toSet().size == prompts.size) { "Two prompts share an id" }
        require(prompts.none { it.id <= 0 }) { "Prompt ids start at 1" }
        val reactionsJson = root["reactions"].asObject("\"reactions\"")
        val reactions = JournalReaction.entries.associateWith { r ->
            reactionsJson[r.key].asArray("reactions.${r.key}").map { it.asString("a ${r.key} line") }
        }
        return JournalBook(
            prompts = prompts,
            reactions = reactions,
            swap = root["swap"].asArray("\"swap\"").map { it.asString("a swap line") },
            noSwap = root["no_swap"].asArray("\"no_swap\"").map { it.asString("a no_swap line") },
        )
    }
}
