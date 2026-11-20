package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamsTest {

    private fun item(id: String, factId: String? = null) = ExamItem(
        id = id,
        question = "Question $id",
        choices = listOf("right", "wrong"),
        answerIndex = 0,
        factId = factId,
    )

    private val pool = (1..30).map { item("q$it", "f$it") }

    @Test
    fun `an exam holds the length asked for, with no repeat`() {
        val exam = Exams.build(pool, 20, random = Random(1))
        assertEquals(20, exam.size)
        assertEquals(20, exam.map { it.id }.toSet().size)
    }

    @Test
    fun `an exam cannot be longer than the pool`() {
        assertEquals(30, Exams.build(pool, 50, random = Random(1)).size)
    }

    @Test
    fun `an empty pool gives an empty exam`() {
        assertTrue(Exams.build(emptyList(), 10).isEmpty())
        assertTrue(Exams.build(pool, 0).isEmpty())
    }

    @Test
    fun `a weak fact appears more often across many exams`() {
        val weak = setOf("f1")
        var withWeak = 0
        repeat(200) { seed ->
            val exam = Exams.build(pool, 5, weak, Random(seed))
            if (exam.any { it.factId == "f1" }) withWeak++
        }
        // A fair draw of 5 from 30 would hit one question about 33 times in 200.
        assertTrue("weak facts came up only $withWeak times", withWeak > 45)
    }

    @Test
    fun `grading counts the right answers and keeps the misses`() {
        val items = pool.take(10)
        val answers = items.associate { it.id to if (it.id == "q1" || it.id == "q2") 1 else 0 }
        val result = Exams.grade(Track.AIR_BRAKES, items, answers)
        assertEquals(10, result.total)
        assertEquals(8, result.correct)
        assertEquals(80, result.percent)
        assertTrue(result.passed)
        assertFalse(result.mastered)
        assertEquals(listOf("q1", "q2"), result.missed.map { it.id })
        assertEquals(listOf("f1", "f2"), result.weakFactIds)
    }

    @Test
    fun `a question left unanswered is wrong`() {
        val items = pool.take(4)
        val result = Exams.grade(Track.ON_ROAD, items, mapOf("q1" to 0))
        assertEquals(1, result.correct)
        assertEquals(25, result.percent)
    }

    @Test
    fun `90 percent is mastery`() {
        val items = pool.take(10)
        val answers = items.associate { it.id to if (it.id == "q1") 1 else 0 }
        assertTrue(Exams.grade(Track.COMBINATION, items, answers).mastered)
    }

    @Test
    fun `an empty exam does not divide by zero`() {
        assertEquals(0, Exams.grade(Track.INSPECTION, emptyList(), emptyMap()).percent)
    }

    @Test
    fun `a missed question makes a card from its fact`() {
        val subsection = Subsection(
            id = "5.1",
            title = "Air brakes",
            facts = listOf(Fact("f1", "Fact one", critical = true)),
            cards = listOf(Card("Card for f1", "Answer", "f1")),
        )
        val content = Content(
            listOf(
                Section(5, "Air Brakes", Track.AIR_BRAKES, subsections = listOf(subsection))
            )
        )
        val items = listOf(item("q1", "f1"))
        val result = Exams.grade(Track.AIR_BRAKES, items, mapOf("q1" to 1))
        val cards = Exams.cardsForMissed(content, result)
        assertEquals(listOf("Card for f1"), cards.map { it.question })
    }

    @Test
    fun `a missed question with no card in the content becomes a card from the question`() {
        val content = Content(
            listOf(
                Section(
                    5, "Air Brakes", Track.AIR_BRAKES,
                    subsections = listOf(
                        Subsection("5.1", "Air brakes", facts = listOf(Fact("f1", "Fact one")))
                    ),
                )
            )
        )
        val items = listOf(item("q1", "f1"))
        val result = Exams.grade(Track.AIR_BRAKES, items, mapOf("q1" to 1))
        val cards = Exams.cardsForMissed(content, result)
        assertEquals(1, cards.size)
        assertEquals("right", cards[0].answer)
    }

    @Test
    fun `the default lengths are the ones the study plan names`() {
        assertEquals(50, ExamStandards.defaultLength(Track.GENERAL_KNOWLEDGE))
        assertEquals(25, ExamStandards.defaultLength(Track.AIR_BRAKES))
        assertEquals(20, ExamStandards.defaultLength(Track.COMBINATION))
        assertTrue(ExamStandards.needsRoadPractice(Track.BASIC_CONTROL))
        assertTrue(ExamStandards.needsRoadPractice(Track.ON_ROAD))
        assertFalse(ExamStandards.needsRoadPractice(Track.INSPECTION))
    }
}
