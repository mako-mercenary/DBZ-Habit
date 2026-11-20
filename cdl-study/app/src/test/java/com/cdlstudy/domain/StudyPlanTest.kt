package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanTest {

    @Test
    fun `the plan holds the eight sections Utah requires for a Class A`() {
        assertEquals(listOf(1, 2, 3, 5, 6, 11, 12, 13), StudyPlan.required.map { it.number })
    }

    @Test
    fun `the endorsement sections are left out and the reason is given`() {
        assertEquals(listOf(4, 7, 8, 9, 10), StudyPlan.excluded.map { it.number })
        StudyPlan.excluded.forEach { section ->
            assertTrue(
                "Section ${section.number} needs a reason",
                section.why.contains("endorsement"),
            )
        }
    }

    @Test
    fun `an endorsement section is not required`() {
        listOf(4, 7, 8, 9, 10).forEach { assertFalse(StudyPlan.isRequired(it)) }
        listOf(1, 2, 3, 5, 6, 11, 12, 13).forEach { assertTrue(StudyPlan.isRequired(it)) }
        assertFalse(StudyPlan.isRequired(14))
    }

    @Test
    fun `every track has a section, and every section has a track`() {
        val tracks = StudyPlan.required.map { it.track }.toSet()
        assertEquals(Track.entries.toSet(), tracks)
    }

    @Test
    fun `the general knowledge test covers sections 1, 2 and 3`() {
        val general = StudyPlan.required.filter { it.track == Track.GENERAL_KNOWLEDGE }
        assertEquals(listOf(1, 2, 3), general.map { it.number })
    }

    @Test
    fun `every section says why it is required`() {
        StudyPlan.required.forEach { section ->
            assertTrue("Section ${section.number} needs a reason", section.why.length > 20)
        }
    }
}
