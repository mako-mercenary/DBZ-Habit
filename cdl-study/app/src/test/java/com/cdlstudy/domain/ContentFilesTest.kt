package com.cdlstudy.domain

import java.io.File
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
}
