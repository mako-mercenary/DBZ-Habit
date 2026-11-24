package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamModeTest {

    @Test
    fun `practice is weighted and its length is yours`() {
        assertTrue(ExamMode.PRACTICE.weighted)
        assertTrue(ExamMode.PRACTICE.lengthIsYours)
    }

    @Test
    fun `test day is not weighted and its length is fixed`() {
        assertFalse(ExamMode.TEST_DAY.weighted)
        assertFalse(ExamMode.TEST_DAY.lengthIsYours)
    }

    private fun pool(n: Int) = (1..n).map { i ->
        ExamItem(
            id = "q$i",
            question = "Question $i?",
            choices = listOf("a", "b"),
            answerIndex = 0,
            factId = "f$i",
        )
    }

    @Test
    fun `an unweighted build gives a weak fact no extra chance`() {
        // Twelve questions, one of them weak, and a round of ten. Weighted, the
        // weak one is in nearly every round. Unweighted it is sometimes left
        // out, which is the whole point of the mode.
        val items = pool(12)
        val weak = setOf("f1")
        var weightedHits = 0
        var plainHits = 0
        repeat(200) { seed ->
            if (Exams.build(items, 10, weak, Random(seed)).any { it.factId == "f1" }) {
                weightedHits++
            }
            if (Exams.build(items, 10, emptySet(), Random(seed)).any { it.factId == "f1" }) {
                plainHits++
            }
        }
        assertTrue(
            "weighting should favour the weak fact: $weightedHits vs $plainHits",
            weightedHits > plainHits,
        )
    }

    @Test
    fun `a build never repeats a question, weighted or not`() {
        val items = pool(30)
        listOf(emptySet(), setOf("f1", "f2", "f3")).forEach { weak ->
            repeat(50) { seed ->
                val round = Exams.build(items, 20, weak, Random(seed))
                assertEquals(20, round.size)
                assertEquals(20, round.map { it.id }.distinct().size)
            }
        }
    }

    @Test
    fun `a pass is 80 percent and mastery is 90, whatever the mode`() {
        val items = pool(10)
        val answers = items.take(8).associate { it.id to 0 }
        val result = Exams.grade(Track.GENERAL_KNOWLEDGE, items, answers)
        assertEquals(80, result.percent)
        assertTrue(result.passed)
        assertFalse(result.mastered)
    }
}
