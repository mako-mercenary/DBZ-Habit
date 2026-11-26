package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextStepTest {

    private fun part(id: String, title: String) = Subsection(
        id = id,
        title = title,
        facts = listOf(Fact("$id.f1", "A fact", critical = true)),
    )

    private fun heading(id: String, title: String) = Subsection(id = id, title = title)

    private fun item(id: String, factId: String?) = ExamItem(
        id = id,
        question = "Q $id",
        choices = listOf("a", "b"),
        answerIndex = 0,
        factId = factId,
    )

    private val content = Content(
        listOf(
            Section(
                1, "Introduction", Track.GENERAL_KNOWLEDGE,
                subsections = listOf(heading("1.1", "Tests"), part("1.1.1", "Knowledge Tests"), part("1.1.2", "Skills Tests")),
                exam = listOf(item("g1", null)),
            ),
            Section(
                5, "Air Brakes", Track.AIR_BRAKES,
                subsections = listOf(part("5.1", "Parts")),
                exam = listOf(item("a1", null)),
            ),
        )
    )

    private fun next(
        statuses: Map<String, String> = emptyMap(),
        bests: Map<Track, Int> = emptyMap(),
        pending: Int = 0,
        from: Content = content,
    ) = NextStepRules.next(from, { statuses[it] }, { bests[it] }, pending)

    @Test
    fun `it points at the first part, not the heading above it`() {
        val step = next()
        assertTrue(step is NextStep.Study)
        assertEquals("1.1.1", (step as NextStep.Study).subsectionId)
        assertTrue(step.firstTime)
    }

    @Test
    fun `a part that was read but never recalled is still the next one`() {
        val step = next(mapOf("1.1.1" to "reading"))
        assertEquals("1.1.1", (step as NextStep.Study).subsectionId)
        assertEquals(false, step.firstTime)
        assertTrue(NextStepRules.label(step).startsWith("Again:"))
    }

    @Test
    fun `it moves on once a part is recalled`() {
        val step = next(mapOf("1.1.1" to "recalled"))
        assertEquals("1.1.2", (step as NextStep.Study).subsectionId)
    }

    @Test
    fun `it finishes a section before it starts the next one`() {
        val step = next(mapOf("1.1.1" to "mastered"))
        assertEquals("1.1.2", (step as NextStep.Study).subsectionId)
    }

    @Test
    fun `it crosses into the next section when the first is done`() {
        val step = next(mapOf("1.1.1" to "recalled", "1.1.2" to "mastered"))
        assertEquals("5.1", (step as NextStep.Study).subsectionId)
        assertEquals(5, step.sectionNumber)
    }

    private val allRecalled =
        mapOf("1.1.1" to "recalled", "1.1.2" to "recalled", "5.1" to "mastered")

    @Test
    fun `cards that have come due come before everything, even the reading`() {
        // A card at the edge of forgetting cannot wait a week. A subsection
        // can wait an hour.
        val step = next(emptyMap(), pending = 7)
        assertEquals(NextStep.ReviewCards(7), step)
        assertEquals("Next: 7 cards due", NextStepRules.label(step))
        assertEquals("Next: 1 card due", NextStepRules.label(NextStep.ReviewCards(1)))
    }

    @Test
    fun `with the reading done, due cards still come first`() {
        assertEquals(NextStep.ReviewCards(3), next(allRecalled, pending = 3))
    }

    @Test
    fun `with the reading done and no cards waiting, it offers an exam`() {
        val step = next(allRecalled)
        assertTrue(step is NextStep.TakeExam)
        assertEquals(Track.GENERAL_KNOWLEDGE, (step as NextStep.TakeExam).track)
        assertEquals("Next: General Knowledge exam", NextStepRules.label(step))
    }

    @Test
    fun `an exam below mastery is offered again, with the score to beat`() {
        val step = next(allRecalled, mapOf(Track.GENERAL_KNOWLEDGE to 84))
        assertEquals(Track.GENERAL_KNOWLEDGE, (step as NextStep.TakeExam).track)
        assertEquals("Beat 84%: General Knowledge exam", NextStepRules.label(step))
    }

    @Test
    fun `an exam at mastery is left alone and the next track comes up`() {
        val step = next(allRecalled, mapOf(Track.GENERAL_KNOWLEDGE to 92))
        assertEquals(Track.AIR_BRAKES, (step as NextStep.TakeExam).track)
    }

    @Test
    fun `everything at mastery ends the list`() {
        val step = next(
            allRecalled,
            mapOf(Track.GENERAL_KNOWLEDGE to 92, Track.AIR_BRAKES to 90),
        )
        assertEquals(NextStep.AllDone, step)
    }

    @Test
    fun `a track with no questions is skipped instead of offered`() {
        val noQuestions = Content(
            listOf(
                Section(
                    5, "Air Brakes", Track.AIR_BRAKES,
                    subsections = listOf(part("5.1", "Parts")),
                )
            )
        )
        assertEquals(NextStep.AllDone, next(mapOf("5.1" to "recalled"), from = noQuestions))
    }

    @Test
    fun `nothing written says so, and the button is not offered`() {
        val empty = Content(
            listOf(Section(2, "Driving Safely", Track.GENERAL_KNOWLEDGE,
                subsections = listOf(heading("2.1", "Inspection"))))
        )
        assertEquals(NextStep.NothingWritten, next(from = empty))
        assertEquals("Nothing written to study yet", NextStepRules.label(NextStep.NothingWritten))
    }

    @Test
    fun `no content at all does not crash`() {
        assertEquals(NextStep.NothingWritten, next(from = Content(emptyList())))
    }
}
