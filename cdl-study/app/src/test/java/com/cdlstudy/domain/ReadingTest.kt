package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {

    private val json = """
        {
          "section": 5, "title": "Air Brakes", "track": "air_brakes", "pdfPages": "77-86",
          "subsections": [
            {
              "id": "5.1.2", "title": "Air Compressor Governor", "pdfPages": "77",
              "reading": [
                { "h": "What it does" },
                { "p": "The governor decides when the compressor pumps air." },
                { "ul": ["Cut-out stops the pumping.", "Cut-in starts it again."] },
                { "numbers": [
                  { "value": "125 psi", "what": "cut-out" },
                  { "value": "100 psi", "what": "cut-in" }
                ] },
                { "note": "Learn both numbers." },
                { "nonsense": "ignored" }
              ],
              "facts": [ { "text": "Cut-out is about 125 psi.", "critical": true } ]
            },
            {
              "id": "5.1.3", "title": "Air Storage Tanks", "pdfPages": "77",
              "facts": [
                { "text": "The tanks hold enough air for several brake uses.", "critical": true },
                { "text": "Tank number and size vary by vehicle." }
              ]
            }
          ]
        }
    """.trimIndent()

    private val section = ContentReader.readSection(json)
    private val withLesson = section.subsections[0]
    private val withoutLesson = section.subsections[1]

    @Test
    fun `every block type is read, and an unknown block is left out`() {
        val blocks = withLesson.reading
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is ReadBlock.Heading)
        assertTrue(blocks[1] is ReadBlock.Paragraph)
        assertTrue(blocks[2] is ReadBlock.Bullets)
        assertTrue(blocks[3] is ReadBlock.Numbers)
        assertTrue(blocks[4] is ReadBlock.Note)
    }

    @Test
    fun `a numbers block keeps the value with what it is for`() {
        val numbers = withLesson.reading[3] as ReadBlock.Numbers
        assertEquals(2, numbers.rows.size)
        assertEquals("125 psi", numbers.rows[0].value)
        assertEquals("cut-out", numbers.rows[0].what)
    }

    @Test
    fun `a subsection with a lesson reports one`() {
        assertTrue(withLesson.hasLesson)
        assertFalse(withoutLesson.hasLesson)
        assertEquals(1, section.withLesson)
    }

    @Test
    fun `a subsection with no lesson falls back to its facts, and says so`() {
        val lesson = withoutLesson.lesson
        assertTrue(lesson.isNotEmpty())
        val note = lesson.first() as ReadBlock.Note
        assertTrue(note.text.contains("not written"))
        assertTrue(lesson.any { it is ReadBlock.Heading && it.text == "Must memorize" })
        assertTrue(lesson.any { it is ReadBlock.Heading && it.text == "Also know" })
    }

    @Test
    fun `a subsection with neither a lesson nor facts has nothing to read`() {
        val bare = Subsection(id = "9.9", title = "Nothing")
        assertTrue(bare.lesson.isEmpty())
    }

    @Test
    fun `the reading time is at least one minute`() {
        assertEquals(1, Reading.minutes(withLesson.reading))
        val long = List(500) { ReadBlock.Paragraph("word word word word word") }
        assertEquals(13, Reading.minutes(long))
    }

    @Test
    fun `the word count covers every kind of block`() {
        assertEquals(3, Reading.wordCount(listOf(ReadBlock.Heading("one two three"))))
        assertEquals(4, Reading.wordCount(listOf(ReadBlock.Bullets(listOf("one two", "three four")))))
        assertEquals(
            4,
            Reading.wordCount(
                listOf(ReadBlock.Numbers(listOf(ReadBlock.Numbers.Row("125 psi", "cut out"))))
            ),
        )
    }
}
