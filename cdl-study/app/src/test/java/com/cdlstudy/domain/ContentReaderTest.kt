package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentReaderTest {

    private val json = """
        {
          "section": 5,
          "title": "Air Brakes",
          "track": "air_brakes",
          "readingPages": 10,
          "subsections": [
            {
              "id": "5.1",
              "title": "The Parts of an Air Brake System",
              "manualPages": "5-1",
              "facts": [
                { "text": "First fact", "critical": true, "page": "5-1", "keywords": ["first"] },
                { "id": "custom", "text": "Second fact" }
              ],
              "cards": [ { "q": "Q", "a": "A", "factId": "5.1.1" } ],
              "exam": [
                { "q": "Question", "choices": ["a", "b"], "answer": 1, "why": "Because", "factId": "5.1.1" }
              ]
            }
          ],
          "exam": [ { "q": "Section question", "choices": ["a", "b", "c"], "answer": 0 } ]
        }
    """.trimIndent()

    @Test
    fun `reads a section with its facts, cards and questions`() {
        val section = ContentReader.readSection(json)
        assertEquals(5, section.number)
        assertEquals(Track.AIR_BRAKES, section.track)
        assertEquals(1, section.subsections.size)
        assertEquals(2, section.factCount)
        assertEquals(1, section.cardCount)
        assertEquals(2, section.examCount)
        assertEquals(false, section.placeholder)
    }

    @Test
    fun `a fact with no id gets one from its position`() {
        val section = ContentReader.readSection(json)
        val facts = section.subsections[0].facts
        assertEquals("5.1.1", facts[0].id)
        assertEquals("custom", facts[1].id)
    }

    @Test
    fun `a critical fact keeps its page and keywords`() {
        val fact = ContentReader.readSection(json).subsections[0].facts[0]
        assertTrue(fact.critical)
        assertEquals("5-1", fact.page)
        assertEquals(listOf("first"), fact.keywords)
    }

    @Test
    fun `a question with an answer outside its choices is refused`() {
        val bad = """
            { "section": 1, "title": "T", "track": "general_knowledge",
              "exam": [ { "q": "Q", "choices": ["a", "b"], "answer": 5 } ] }
        """.trimIndent()
        val error = runCatching { ContentReader.readSection(bad) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("outside its choices"))
    }

    @Test
    fun `a question with one choice is refused`() {
        val bad = """
            { "section": 1, "title": "T", "track": "general_knowledge",
              "exam": [ { "q": "Q", "choices": ["a"], "answer": 0 } ] }
        """.trimIndent()
        assertTrue(runCatching { ContentReader.readSection(bad) }.isFailure)
    }

    @Test
    fun `an unknown track is refused`() {
        val bad = """{ "section": 1, "title": "T", "track": "trucks" }"""
        assertTrue(runCatching { ContentReader.readSection(bad) }.isFailure)
    }

    @Test
    fun `a section with no subsections is allowed`() {
        val lean = """{ "section": 3, "title": "Cargo", "track": "general_knowledge" }"""
        val section = ContentReader.readSection(lean)
        assertEquals(0, section.factCount)
        assertTrue(section.subsections.isEmpty())
    }

    @Test
    fun `the exam pool of a track holds the section and subsection questions`() {
        val content = Content(listOf(ContentReader.readSection(json)))
        val pool = content.examPool(Track.AIR_BRAKES)
        assertEquals(2, pool.size)
        assertEquals("Section question", pool[0].question)
        assertTrue(content.examPool(Track.ON_ROAD).isEmpty())
    }

    @Test
    fun `a fact is found by id across every section`() {
        val content = Content(listOf(ContentReader.readSection(json)))
        assertEquals("First fact", content.fact("5.1.1")?.text)
        assertNull(content.fact("nothing"))
        assertEquals("5.1", content.sectionOf("5.1")?.subsections?.get(0)?.id)
    }
}
