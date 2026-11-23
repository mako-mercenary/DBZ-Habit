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
    fun `a written section leaves nothing missing on a part`() {
        // A heading such as 1.1 is allowed to hold nothing: the manual gives it
        // no text of its own, and it exists to group the parts under it. The
        // rule is about the parts, which are what you actually read.
        val written = setOf(1, 3, 5)
        val bare = mutableListOf<String>()
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            if (section.number !in written) return@forEach
            section.parts
                .filterNot { it.hasLesson || it.canRecall }
                .forEach { bare.add("${section.number}: ${it.id} ${it.title}") }
        }
        assertTrue("These parts have neither a lesson nor facts: $bare", bare.isEmpty())
    }

    @Test
    fun `a heading is told apart from a part`() {
        val section = ContentReader.readSection(
            files().first { it.name == "section01.json" }.readText()
        )
        val headings = section.subsections.filter { section.isHeading(it) }.map { it.id }
        assertTrue("1.1 groups 1.1.1 to 1.1.3, so it is a heading", "1.1" in headings)
        assertTrue("1.3 groups 1.3.1 to 1.3.7, so it is a heading", "1.3" in headings)
        assertFalse("1.1.1 has no parts under it", "1.1.1" in headings)
        assertFalse("1.6 has no parts under it", "1.6" in headings)
        assertTrue(section.parts.none { section.isHeading(it) })
        assertEquals(section.subsections.size, section.parts.size + headings.size)
    }

    @Test
    fun `an empty heading is left empty, because that is what a heading is`() {
        // The app showed "0 facts | 0 must memorize" on 1.1 and the first fix
        // put an invented overview there. A heading holds nothing on purpose.
        val section = ContentReader.readSection(
            files().first { it.name == "section01.json" }.readText()
        )
        val oneOne = section.subsections.first { it.id == "1.1" }
        assertTrue(section.isHeading(oneOne))
        assertFalse("1.1 should carry no lesson of its own", oneOne.hasLesson)
        assertTrue("1.1 should carry no facts of its own", oneOne.facts.isEmpty())
    }

    @Test
    fun `a keyword can be matched against a recall that repeats the fact`() {
        // A keyword nobody could ever match is a dead keyword. Reading the fact
        // back word for word must suggest a hit.
        val weak = mutableListOf<String>()
        files().forEach { file ->
            val section = ContentReader.readSection(file.readText())
            section.subsections.forEach { sub ->
                sub.facts.filter { it.keywords.isNotEmpty() }.forEach { fact ->
                    val suggestion = BlurtMatcher.suggest(listOf(fact), fact.text)[fact.id]!!
                    if (suggestion.mark == Mark.MISS) weak.add("${fact.id}: ${suggestion.missing}")
                }
            }
        }
        // A keyword may be an abbreviation the driver would write, such as GVWR
        // for gross vehicle weight rating, so some will not be in the fact text.
        // None should match nothing at all.
        assertTrue("These facts match nothing in their own text: $weak", weak.isEmpty())
    }

    @Test
    fun `the content is written in American spelling, as a US exam needs`() {
        // The list grew every time one slipped through: centre and colour after
        // Section 11, cancelled and travelling after Section 2. Add to it rather
        // than fixing one and waiting for the next.
        val british = Regex(
            "\\b(licence|licences|tyre|tyres|colour\\w*|programme|programmes|recognis\\w+|" +
                "organis(e|ed|ing|ation)|realis(e|ed|ing)|manoeuvr\\w*|centre|centres|" +
                "metre|metres|litre|litres|" +
                "defence|behaviour\\w*|travell(ed|ing|er|ers)|signall(ed|ing)|" +
                "labell(ed|ing)|cancell(ed|ing)|" +
                "practis\\w+|judgement|kerb\\w*|grey)\\b",
            RegexOption.IGNORE_CASE,
        )
        val found = mutableListOf<String>()
        files().forEach { file ->
            british.findAll(file.readText()).forEach { found.add("${file.name}: ${it.value}") }
        }
        assertTrue("British spellings: ${found.take(10)}", found.isEmpty())
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
