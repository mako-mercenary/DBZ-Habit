package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGradingTest {

    private val subsection = Subsection(
        id = "5.1.2",
        title = "Air Compressor Governor",
        facts = listOf(
            Fact("5.1.2.1", "Cut-out is about 125 psi.", critical = true),
            Fact("5.1.2.2", "Cut-in is about 100 psi.", critical = true),
            Fact("5.1.2.3", "The governor controls the compressor.", critical = false),
        ),
    )

    @Test
    fun `the prompt carries every fact, its id and the must-memorize flag`() {
        val text = AiGrading.prompt(subsection, "The governor cuts out at 125.")
        assertTrue(text.contains("id=5.1.2.1 [must memorize]: Cut-out is about 125 psi."))
        assertTrue(text.contains("id=5.1.2.3: The governor controls the compressor."))
        assertTrue(text.contains("The governor cuts out at 125."))
    }

    @Test
    fun `an empty recall is sent as nothing, not as an empty prompt`() {
        assertTrue(AiGrading.prompt(subsection, "   ").contains("(nothing)"))
    }

    @Test
    fun `the prompt holds no manual text, only the app's own facts`() {
        val text = AiGrading.prompt(subsection, "anything")
        // Everything after the fact list is the student's words, nothing else.
        assertEquals(1, text.split("WHAT THE STUDENT WROTE").size - 1)
        assertTrue(text.startsWith("Subsection 5.1.2"))
    }

    private val reply = """
        {
          "marks": [
            {"factId": "5.1.2.1", "mark": "HIT", "why": "You gave 125 psi."},
            {"factId": "5.1.2.2", "mark": "PARTIAL", "why": "You said cut-in but gave 110."},
            {"factId": "5.1.2.3", "mark": "MISS", "why": "Not mentioned."}
          ],
          "errors": ["You said cut-in is 110 psi. It is about 100."],
          "note": "Numbers need another pass."
        }
    """.trimIndent()

    @Test
    fun `a good reply is read into marks, errors and a note`() {
        val opinion = AiGrading.parse(reply, subsection)
        assertEquals(3, opinion.marks.size)
        assertEquals(Mark.HIT, opinion.markOf("5.1.2.1"))
        assertEquals(Mark.PARTIAL, opinion.markOf("5.1.2.2"))
        assertEquals(Mark.MISS, opinion.markOf("5.1.2.3"))
        assertEquals("You gave 125 psi.", opinion.whyOf("5.1.2.1"))
        assertEquals(1, opinion.errors.size)
        assertEquals("Numbers need another pass.", opinion.note)
    }

    @Test
    fun `a mark for a fact that is not in this subsection is dropped`() {
        val stray = reply.replace("\"5.1.2.3\"", "\"9.9.9\"")
        val opinion = AiGrading.parse(stray, subsection)
        assertEquals(2, opinion.marks.size)
        assertNull(opinion.markOf("9.9.9"))
    }

    @Test
    fun `an unknown mark word is dropped rather than guessed at`() {
        val odd = reply.replace("\"PARTIAL\"", "\"ALMOST\"")
        val opinion = AiGrading.parse(odd, subsection)
        assertEquals(2, opinion.marks.size)
        assertNull(opinion.markOf("5.1.2.2"))
    }

    @Test
    fun `a fact the model left out stays unmarked, instead of being called a miss`() {
        val partial = """{"marks":[{"factId":"5.1.2.1","mark":"HIT","why":"ok"}],"errors":[],"note":""}"""
        val opinion = AiGrading.parse(partial, subsection)
        assertEquals(1, opinion.marks.size)
        assertNull(opinion.markOf("5.1.2.2"))
    }

    @Test
    fun `a reply that is not JSON is refused with a readable reason`() {
        listOf("", "I think you did well!", "{") .forEach { text ->
            val error = runCatching { AiGrading.parse(text, subsection) }.exceptionOrNull()
            assertTrue("'$text' should be refused", error is AiGrading.BadReply)
        }
    }

    @Test
    fun `a reply that grades nothing is refused`() {
        val empty = """{"marks":[],"errors":[],"note":"no idea"}"""
        assertTrue(runCatching { AiGrading.parse(empty, subsection) }.exceptionOrNull() is AiGrading.BadReply)
    }

    @Test
    fun `disagreements are only the facts where the model differs from you`() {
        val opinion = AiGrading.parse(reply, subsection)
        val yours = mapOf(
            "5.1.2.1" to Mark.HIT,      // agree
            "5.1.2.2" to Mark.HIT,      // you were kinder than the model
            "5.1.2.3" to Mark.MISS,     // agree
        )
        val differs = opinion.disagreements(yours)
        assertEquals(1, differs.size)
        assertEquals("5.1.2.2", differs[0].factId)
        assertEquals(Mark.PARTIAL, differs[0].mark)
    }

    @Test
    fun `an unmarked fact counts as a miss when disagreements are worked out`() {
        val opinion = AiGrading.parse(reply, subsection)
        // You marked nothing at all: the model's two non-MISS marks disagree.
        assertEquals(2, opinion.disagreements(emptyMap()).size)
    }

    @Test
    fun `the schema asks for exactly the three fields the app reads`() {
        val schema = AiGrading.schema()
        listOf("marks", "errors", "note", "factId", "HIT", "PARTIAL", "MISS").forEach {
            assertTrue("schema should mention $it", schema.contains(it))
        }
    }
}
