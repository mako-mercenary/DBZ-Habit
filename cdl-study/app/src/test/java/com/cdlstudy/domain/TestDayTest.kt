package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDayTest {

    @Test
    fun `every item has a stable unique id`() {
        val ids = TestDay.allItems.map { it.id }
        assertEquals("an id is used twice", ids.size, ids.distinct().size)
        ids.forEach { assertTrue("empty id", it.isNotBlank()) }
    }

    @Test
    fun `every item names the page it came from, or says it came from none`() {
        TestDay.allItems.forEach { item ->
            if (item.source.isNotEmpty()) {
                assertTrue(
                    "${item.id} has a source that is not a dld.utah.gov path: ${item.source}",
                    item.source.startsWith("/") && item.source.endsWith("/"),
                )
            }
        }
        // The items with no source are the ones under "Not on the DLD pages
        // read". They must be the only ones.
        val sourceless = TestDay.allItems.filter { it.source.isEmpty() }.map { it.id }
        assertEquals(listOf("gap-medical", "gap-experience", "gap-time-limit"), sourceless)
    }

    @Test
    fun `a fee or a scoring rule is not something you tick off`() {
        TestDay.allItems.filter { it.id.startsWith("fee-") || it.id.startsWith("score-") }
            .forEach { assertTrue("${it.id} should not be tickable", !it.tickable) }
    }

    @Test
    fun `the things you must take and do are tickable`() {
        listOf("doc-identity", "doc-ssn", "doc-address", "skills-eldt", "skills-vehicle")
            .forEach { id ->
                val item = TestDay.allItems.first { it.id == id }
                assertTrue("$id should be tickable", item.tickable)
            }
    }

    @Test
    fun `progress counts only the tickable items`() {
        assertEquals(0, TestDay.done(emptySet()))
        assertEquals(0, TestDay.percent(emptySet()))
        // Ticking an untickable id must not move the count.
        assertEquals(0, TestDay.done(setOf("fee-written")))
        assertEquals(1, TestDay.done(setOf("doc-ssn")))
        assertEquals(100, TestDay.percent(TestDay.tickable.map { it.id }.toSet()))
    }

    @Test
    fun `the date it was checked is stated, because these numbers go stale`() {
        assertTrue(TestDay.CHECKED_ON.isNotBlank())
        assertTrue(TestDay.NOTICE.contains(TestDay.CHECKED_ON))
    }

    @Test
    fun `the knowledge test lengths match the ones the exams use`() {
        // Read off dld.utah.gov/cdl-written-test/: general 50, combination 20,
        // air brakes 25. If someone changes ExamStandards, this fails.
        assertEquals(50, ExamStandards.defaultLength(Track.GENERAL_KNOWLEDGE))
        assertEquals(20, ExamStandards.defaultLength(Track.COMBINATION))
        assertEquals(25, ExamStandards.defaultLength(Track.AIR_BRAKES))
        assertEquals(80, ExamStandards.PASS_PERCENT)
        assertTrue(TestDay.allItems.any { it.text.contains("50 questions") })
        assertTrue(TestDay.allItems.any { it.text.contains("20 questions") })
        assertTrue(TestDay.allItems.any { it.text.contains("25 questions") })
    }
}
