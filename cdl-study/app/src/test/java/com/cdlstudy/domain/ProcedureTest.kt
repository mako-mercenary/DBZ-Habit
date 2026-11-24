package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProcedureTest {

    private val check = Procedure(
        id = "air-brake-check",
        title = "The four-part air brake check",
        why = "Leave a part out and you fail the inspection test on the spot.",
        steps = listOf(
            Step("Air gauge and governor cutout", "11.1.2.2"),
            Step("Air leakage rate", "11.1.2.3"),
            Step("Air warning devices", "11.1.2.5"),
            Step("Emergency brakes", "11.1.2.6"),
        ),
    )

    @Test
    fun `the right order passes`() {
        val result = StepOrder.score(check.steps, check)
        assertTrue(result.passed)
        assertEquals(4, result.inPlace)
        assertEquals(100, result.percent)
    }

    @Test
    fun `one step out of place does not pass, because the examiner does not round up`() {
        val swapped = listOf(check.steps[1], check.steps[0], check.steps[2], check.steps[3])
        val result = StepOrder.score(swapped, check)
        assertFalse(result.passed)
        assertEquals(2, result.inPlace)
        assertEquals(setOf(0, 1), result.wrongPositions)
    }

    @Test
    fun `the steps out of place name their facts, so they can become cards`() {
        val swapped = listOf(check.steps[1], check.steps[0], check.steps[2], check.steps[3])
        assertEquals(
            listOf("11.1.2.2", "11.1.2.3"),
            StepOrder.score(swapped, check).missedFactIds.sorted(),
        )
    }

    @Test
    fun `a short answer leaves the rest wrong rather than crashing`() {
        val result = StepOrder.score(listOf(check.steps[0]), check)
        assertEquals(1, result.inPlace)
        assertEquals(setOf(1, 2, 3), result.wrongPositions)
    }

    @Test
    fun `the shuffle never hands you the right order`() {
        repeat(200) { seed ->
            val shuffled = StepOrder.shuffle(check, Random(seed))
            assertEquals(check.length, shuffled.size)
            assertNotEquals(check.steps.map { it.text }, shuffled.map { it.text })
            assertEquals(
                "the shuffle must keep every step",
                check.steps.map { it.text }.sorted(),
                shuffled.map { it.text }.sorted(),
            )
        }
    }

    @Test
    fun `a one-step procedure comes back as it is`() {
        val one = check.copy(steps = listOf(check.steps[0]))
        assertEquals(one.steps, StepOrder.shuffle(one, Random(1)))
    }

    @Test
    fun `the real content holds the procedures, each with a why and ordered steps`() {
        val content = RealContent.load()
        val procedures = content.sections.flatMap { it.procedures }
        assertTrue("no procedures in the content", procedures.size >= 5)
        procedures.forEach { procedure ->
            assertTrue("${procedure.id} has under two steps", procedure.length >= 2)
            assertTrue("${procedure.id} has no why", procedure.why.isNotBlank())
            assertEquals(
                "${procedure.id} repeats a step, so it cannot be ordered",
                procedure.length,
                procedure.steps.map { it.text }.distinct().size,
            )
            assertTrue(
                "${procedure.id} must pass when put in its own order",
                StepOrder.score(procedure.steps, procedure).passed,
            )
        }
    }

    @Test
    fun `every step that names a fact names one that exists`() {
        val content = RealContent.load()
        val ids = content.sections
            .flatMap { it.subsections }
            .flatMap { it.facts }
            .map { it.id }
            .toSet()
        val bad = content.sections.flatMap { it.procedures }.flatMap { procedure ->
            procedure.steps.mapNotNull { it.factId }.filterNot { it in ids }
                .map { "${procedure.id} -> $it" }
        }
        assertTrue("These steps point at a fact that is not there: $bad", bad.isEmpty())
    }
}
