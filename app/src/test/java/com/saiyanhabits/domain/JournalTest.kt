package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class JournalTest {

    private fun words(n: Int) = List(n) { "word" }.joinToString(" ")

    @Test
    fun `words are runs with a letter or digit`() {
        assertEquals(0, JournalRules.wordCount(""))
        assertEquals(0, JournalRules.wordCount("   \n\t "))
        assertEquals(3, JournalRules.wordCount("one two  three"))
        assertEquals(2, JournalRules.wordCount("hello — world"))
        assertEquals(4, JournalRules.wordCount("line one\nline two"))
        assertEquals(2, JournalRules.wordCount("it's 2026"))
    }

    @Test
    fun `an entry is finished at the minimum`() {
        assertFalse(JournalRules.isFinished(words(GameConfig.JOURNAL_MIN_WORDS - 1)))
        assertTrue(JournalRules.isFinished(words(GameConfig.JOURNAL_MIN_WORDS)))
    }

    @Test
    fun `only the first finished entry of the day is rewarded`() {
        val min = GameConfig.JOURNAL_MIN_WORDS
        assertEquals(JournalReward(GameConfig.JOURNAL_BOND_XP, GameConfig.JOURNAL_POWER), JournalRules.reward(min, false))
        assertFalse(JournalRules.reward(min, true).any)
        assertFalse(JournalRules.reward(min - 1, false).any)
    }

    @Test
    fun `prompts don't repeat until all are used`() {
        val ids = (1..5).toList()
        val random = Random(1)
        val used = mutableSetOf<Int>()
        repeat(5) {
            val id = JournalRules.pickPrompt(ids, used, random)!!
            assertFalse("prompt $id repeated", id in used)
            used += id
        }
        // All used: any prompt again.
        assertTrue(JournalRules.pickPrompt(ids, used, random) in ids)
    }

    @Test
    fun `a swap never gives back the same prompt`() {
        val ids = listOf(1, 2)
        repeat(20) { seed ->
            assertNotEquals(1, JournalRules.pickPrompt(ids, emptySet(), Random(seed), avoid = 1))
            assertNotEquals(1, JournalRules.pickPrompt(ids, setOf(1, 2), Random(seed), avoid = 1))
        }
        assertEquals(1, JournalRules.pickPrompt(listOf(1), emptySet(), Random(0), avoid = 1))
        assertEquals(null, JournalRules.pickPrompt(emptyList(), emptySet(), Random(0)))
    }

    @Test
    fun `the streak counts back from today or yesterday`() {
        assertEquals(0, JournalRules.streak(emptyList(), 100))
        assertEquals(3, JournalRules.streak(listOf(98, 99, 100), 100))
        assertEquals(2, JournalRules.streak(listOf(98, 99), 100))
        assertEquals(1, JournalRules.streak(listOf(96, 100), 100))
        assertEquals(0, JournalRules.streak(listOf(97, 98), 100))
    }

    @Test
    fun `reactions fit the save`() {
        val min = GameConfig.JOURNAL_MIN_WORDS
        assertEquals(JournalReaction.SHORT, JournalRules.reaction(min - 1, false, 5, 1))
        assertEquals(JournalReaction.EXTRA, JournalRules.reaction(min, true, 5, 1))
        assertEquals(JournalReaction.FIRST, JournalRules.reaction(min, false, 0, 1))
        assertEquals(JournalReaction.STREAK, JournalRules.reaction(min, false, 5, 3))
        assertEquals(JournalReaction.LONG, JournalRules.reaction(GameConfig.JOURNAL_LONG_WORDS, false, 5, 2))
        assertEquals(JournalReaction.DONE, JournalRules.reaction(min, false, 5, 2))
    }

    @Test
    fun `the journal file has 150 distinct prompts and every reaction`() {
        val book = JournalParser.parse(File("src/main/assets/dialogue/journal.json").readText())
        assertEquals(150, book.prompts.size)
        assertEquals(150, book.prompts.map { it.text }.toSet().size)
        assertEquals((1..150).toList(), book.prompts.map { it.id }.sorted())
        assertEquals(10, book.prompts.map { it.topic }.toSet().size)
        for (r in JournalReaction.entries) assertTrue("no ${r.key} lines", book.reactions.getValue(r).isNotEmpty())
        assertTrue(book.swap.isNotEmpty())
        assertTrue(book.noSwap.isNotEmpty())
        assertTrue(book.prompts.all { it.text.isNotBlank() && it.text.length < 200 })
    }
}
