package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionWalkTest {

    private val content = RealContent.load()

    @Test
    fun `the real section 11 gives a walk of several areas, in file order`() {
        val areas = InspectionWalk.areas(content)
        assertTrue("only ${areas.size} areas", areas.size >= 8)
        val section = content.section(11)!!
        val expected = section.parts.filter { it.facts.isNotEmpty() }.map { it.id }
        assertEquals("the walk must keep the section's own order", expected, areas.map { it.subsectionId })
    }

    @Test
    fun `every area names its item count and its must-memorize count`() {
        InspectionWalk.areas(content).forEach { area ->
            assertTrue("${area.subsectionId} has no items", area.items > 0)
            assertTrue(
                "${area.subsectionId} claims more must-memorize than items",
                area.mustMemorize <= area.items,
            )
            assertTrue("${area.subsectionId} has no title", area.title.isNotBlank())
        }
    }

    @Test
    fun `the combination areas are marked, so a straight truck driver can skip them`() {
        val areas = InspectionWalk.areas(content)
        val combination = areas.filter { it.combinationOnly }.map { it.subsectionId }
        assertTrue("no combination area found: $combination", combination.isNotEmpty())
        combination.forEach { assertTrue(it.startsWith("11.6")) }
        assertTrue(areas.any { !it.combinationOnly })
    }

    @Test
    fun `an empty walk is handled, for a content set with no section 11`() {
        val empty = Content(emptyList())
        assertEquals(emptyList<InspectionWalk.Area>(), InspectionWalk.areas(empty))
        val progress = InspectionWalk.progress(empty, setOf("11.1.1"))
        assertEquals(0, progress.total)
        assertEquals(0, progress.percent)
        assertFalse(progress.complete)
        assertNull(progress.next)
    }

    @Test
    fun `progress counts the areas you have recalled and points at the next one`() {
        val areas = InspectionWalk.areas(content)
        val first = areas[0].subsectionId
        val progress = InspectionWalk.progress(content, setOf(first))
        assertEquals(1, progress.cleared)
        assertEquals(areas[1].subsectionId, progress.next?.subsectionId)
        assertFalse(progress.complete)
    }

    @Test
    fun `a recall of something outside the walk does not count toward it`() {
        val progress = InspectionWalk.progress(content, setOf("5.1.2", "2.1.5"))
        assertEquals(0, progress.cleared)
    }

    @Test
    fun `walking every area completes it`() {
        val all = InspectionWalk.areas(content).map { it.subsectionId }.toSet()
        val progress = InspectionWalk.progress(content, all)
        assertTrue(progress.complete)
        assertEquals(100, progress.percent)
        assertNull(progress.next)
    }
}
