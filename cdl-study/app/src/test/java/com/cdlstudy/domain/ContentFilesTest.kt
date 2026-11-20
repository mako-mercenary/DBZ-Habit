package com.cdlstudy.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the real content files, so a bad file fails the build and not your study
 * session. Every new section file is checked by this test without any change here.
 */
class ContentFilesTest {

    private val folder = File("src/main/assets/content")

    private fun files(): List<File> =
        folder.listFiles { f: File -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()

    @Test
    fun `the content folder is there and holds at least one file`() {
        assertTrue("No content folder at ${folder.absolutePath}", folder.isDirectory)
        assertTrue("No section file in ${folder.absolutePath}", files().isNotEmpty())
    }

    @Test
    fun `every file parses`() {
        files().forEach { file ->
            try {
                ContentReader.readSection(file.readText())
            } catch (e: Exception) {
                throw AssertionError("${file.name} will not parse: ${e.message}", e)
            }
        }
    }

    @Test
    fun `a fact id is used once only`() {
        val sections = files().map { ContentReader.readSection(it.readText()) }
        val ids = sections.flatMap { it.subsections }.flatMap { it.facts }.map { it.id }
        val twice = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("These fact ids are used more than once: $twice", twice.isEmpty())
    }

    @Test
    fun `a card and a question point at a fact that exists`() {
        val sections = files().map { ContentReader.readSection(it.readText()) }
        val content = Content(sections)
        val missing = mutableListOf<String>()
        sections.flatMap { it.subsections }.forEach { sub ->
            sub.cards.forEach { card ->
                val id = card.factId
                if (id != null && content.fact(id) == null) missing.add("card '${card.question}' -> $id")
            }
        }
        sections.forEach { section ->
            (section.exam + section.subsections.flatMap { it.exam }).forEach { item ->
                val id = item.factId
                if (id != null && content.fact(id) == null) missing.add("question ${item.id} -> $id")
            }
        }
        assertTrue("These point at a fact that is not there: $missing", missing.isEmpty())
    }

    @Test
    fun `every subsection has a must-memorize fact, because the score is graded on them`() {
        val sections = files().map { ContentReader.readSection(it.readText()) }
        val bare = sections.flatMap { it.subsections }
            .filter { it.facts.isNotEmpty() && it.criticalFacts.isEmpty() }
            .map { it.id }
        assertTrue("These subsections have no must-memorize fact: $bare", bare.isEmpty())
    }

    @Test
    fun `a sample file says so, so the app can warn`() {
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            if (file.name.contains("sample")) {
                assertTrue("${file.name} must set placeholder true", section.placeholder)
            } else {
                assertFalse("${file.name} must not be a placeholder", section.placeholder)
            }
        }
    }

    @Test
    fun `only the sections Utah requires for a Class A are here`() {
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            assertTrue(
                "${file.name} holds section ${section.number}, which is not in the Class A plan",
                StudyPlan.isRequired(section.number),
            )
        }
    }

    @Test
    fun `all eight required sections are here`() {
        val numbers = files().map { ContentReader.readSection(it.readText()).number }.toSet()
        assertEquals(StudyPlan.requiredNumbers, numbers)
    }

    @Test
    fun `every section and subsection is mapped to pages of the manual PDF`() {
        val unmapped = mutableListOf<String>()
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            if (section.pdfPages == null) unmapped.add("section ${section.number}")
            section.subsections.filterNot { it.canRead }.forEach { unmapped.add(it.id) }
        }
        assertTrue("These have no manual pages: $unmapped", unmapped.isEmpty())
    }

    @Test
    fun `a subsection stays inside its section pages`() {
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            val whole = section.pdfPages!!
            section.subsections.forEach { sub ->
                val span = sub.pdfPages!!
                assertTrue(
                    "${sub.id} is on pages $span, outside section ${section.number} ($whole)",
                    span.first >= whole.first && span.last <= whole.last,
                )
            }
        }
    }

    @Test
    fun `a lesson holds no empty block and no runaway paragraph`() {
        val problems = mutableListOf<String>()
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            section.subsections.forEach { sub ->
                sub.reading.forEach { block ->
                    when (block) {
                        is ReadBlock.Heading ->
                            if (block.text.isBlank()) problems.add("${sub.id}: empty heading")
                        is ReadBlock.Paragraph -> {
                            if (block.text.isBlank()) problems.add("${sub.id}: empty paragraph")
                            // A wall of text is what the manual's two columns already are.
                            if (block.text.length > 600) problems.add("${sub.id}: a paragraph over 600 characters")
                        }
                        is ReadBlock.Note ->
                            if (block.text.isBlank()) problems.add("${sub.id}: empty note")
                        is ReadBlock.Bullets ->
                            if (block.items.any { it.isBlank() }) problems.add("${sub.id}: empty bullet")
                        is ReadBlock.Numbers ->
                            if (block.rows.any { it.value.isBlank() || it.what.isBlank() }) {
                                problems.add("${sub.id}: empty number row")
                            }
                    }
                }
            }
        }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test
    fun `a subsection with facts can be read in the app, lesson or fact list`() {
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            section.subsections.filter { it.canRecall }.forEach { sub ->
                assertTrue("${sub.id} has facts but nothing to read", sub.lesson.isNotEmpty())
            }
        }
    }

    @Test
    fun `the subsections of a section are in manual order`() {
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            val starts = section.subsections.map { it.pdfPages!!.first }
            assertEquals(
                "Section ${section.number} subsections are out of order",
                starts.sorted(),
                starts,
            )
        }
    }
}
