package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {

    private val facts = listOf(
        Fact("f1", "Critical one", critical = true),
        Fact("f2", "Critical two", critical = true),
        Fact("f3", "Extra one"),
        Fact("f4", "Extra two"),
    )

    private val subsection = Subsection(
        id = "1.1",
        title = "Test",
        facts = facts,
        cards = listOf(
            Card("Q1", "A1", "f1"),
            Card("Q2", "A2", "f2"),
            Card("Q3", "A3", "f3"),
            Card("Q4", "A4", "f4"),
        ),
    )

    @Test
    fun `an unmarked fact counts as a miss`() {
        val score = Grading.score(facts, emptyMap())
        assertEquals(4, score.misses)
        assertEquals(0, score.overall)
        assertEquals(0, score.critical)
    }

    @Test
    fun `a part said fact is worth half`() {
        val score = Grading.score(
            facts,
            mapOf("f1" to Mark.HIT, "f2" to Mark.PARTIAL, "f3" to Mark.HIT, "f4" to Mark.MISS),
        )
        // 1 + 0.5 + 1 + 0 = 2.5 of 4
        assertEquals(63, score.overall)
        // the critical facts alone: 1 + 0.5 = 1.5 of 2
        assertEquals(75, score.critical)
        assertEquals(2, score.hits)
        assertEquals(1, score.partials)
        assertEquals(1, score.misses)
    }

    @Test
    fun `mastery needs 90 percent of the critical facts and 80 overall`() {
        val all = facts.associate { it.id to Mark.HIT }
        assertTrue(Grading.score(facts, all).mastered)

        val oneCriticalPartial = all + mapOf("f1" to Mark.PARTIAL)
        // critical: 0.5 + 1 of 2 = 75%
        assertFalse(Grading.score(facts, oneCriticalPartial).mastered)
    }

    @Test
    fun `no fact gives a zero score, not a crash`() {
        val score = Grading.score(emptyList(), emptyMap())
        assertEquals(0, score.overall)
        assertEquals(0, score.critical)
        assertEquals(0, score.factCount)
    }

    @Test
    fun `cards come from missed facts and from every critical fact`() {
        val marks = mapOf(
            "f1" to Mark.HIT,      // critical: card still added
            "f2" to Mark.MISS,     // critical and missed
            "f3" to Mark.HIT,      // not critical and said: no card
            "f4" to Mark.PARTIAL,  // not critical, part said: card added
        )
        val cards = Grading.cardsToAdd(subsection, marks).map { it.question }
        assertEquals(listOf("Q1", "Q2", "Q4"), cards)
    }

    @Test
    fun `the same question is not added twice`() {
        val twins = subsection.copy(
            cards = listOf(Card("Same question", "A", "f2"), Card("same  QUESTION ", "A", "f1")),
        )
        val cards = Grading.cardsToAdd(twins, mapOf("f2" to Mark.MISS))
        assertEquals(1, cards.size)
    }

    @Test
    fun `a card with no fact tag is always added`() {
        val extra = subsection.copy(cards = listOf(Card("General", "A", null)))
        val cards = Grading.cardsToAdd(extra, facts.associate { it.id to Mark.HIT })
        assertEquals(1, cards.size)
    }

    @Test
    fun `facts to relearn are the missed critical facts only`() {
        val marks = mapOf(
            "f1" to Mark.HIT,
            "f2" to Mark.PARTIAL,
            "f3" to Mark.MISS,
            "f4" to Mark.MISS,
        )
        assertEquals(listOf("f2"), Grading.factsToRelearn(subsection, marks).map { it.id })
    }
}
