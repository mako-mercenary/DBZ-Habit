package com.cdlstudy.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffTest {

    @Test
    fun `the summary names the date, the subsections, the exams and the weak facts`() {
        val text = Handoff.build(
            isoDate = "2026-11-20",
            subsections = listOf(
                Handoff.SubsectionLine("1.1", "CDL Tests", "mastered", 95, 2)
            ),
            exams = listOf(Handoff.ExamLine(Track.AIR_BRAKES, 3, 88)),
            weak = listOf(Handoff.WeakLine("5.1.2", "Governor cut-out pressure", 4)),
            cardsMade = 40,
            cardsDue = 6,
        )
        assertTrue(text.contains("2026-11-20"))
        assertTrue(text.contains("1.1 CDL Tests - mastered, best 95%"))
        assertTrue(text.contains("Air Brakes - 3 attempts, best 88%"))
        assertTrue(text.contains("Governor cut-out pressure"))
        assertTrue(text.contains("In the deck: 40. Due now: 6."))
    }

    @Test
    fun `an empty record says so instead of showing nothing`() {
        val text = Handoff.build("2026-11-20", emptyList(), emptyList(), emptyList(), 0, 0)
        assertTrue(text.contains("None recalled yet."))
        assertTrue(text.contains("No exam taken yet."))
        assertTrue(text.contains("None recorded yet."))
    }
}
