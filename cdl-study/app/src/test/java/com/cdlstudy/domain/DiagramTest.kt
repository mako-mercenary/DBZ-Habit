package com.cdlstudy.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagramTest {

    @Test
    fun `every diagram has a title, a caption and shapes`() {
        assertTrue(Diagrams.all.size >= 4)
        Diagrams.all.forEach { d ->
            assertTrue("${d.id} has no title", d.title.isNotBlank())
            assertTrue("${d.id} has no caption", d.caption.isNotBlank())
            assertTrue("${d.id} draws nothing", d.shapes.isNotEmpty())
        }
    }

    @Test
    fun `ids are unique, because a route points at one`() {
        val ids = Diagrams.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { assertEquals(it, Diagrams.of(it)?.id) }
    }

    @Test
    fun `nothing is drawn outside the box, or it would be clipped on a phone`() {
        Diagrams.all.forEach { d ->
            d.shapes.forEach { shape ->
                val points: List<Pair<Float, Float>> = when (shape) {
                    is Shape.Line -> listOf(shape.x1 to shape.y1, shape.x2 to shape.y2)
                    is Shape.Arrow -> listOf(shape.x1 to shape.y1, shape.x2 to shape.y2)
                    is Shape.Box -> listOf(
                        shape.x to shape.y,
                        (shape.x + shape.w) to (shape.y + shape.h),
                    )
                    is Shape.Dot -> listOf(
                        (shape.cx - shape.r) to (shape.cy - shape.r),
                        (shape.cx + shape.r) to (shape.cy + shape.r),
                    )
                    // A label is positioned, not bounded: its text runs from
                    // the anchor, so only the anchor can be checked.
                    is Shape.Label -> listOf(shape.x to shape.y)
                }
                points.forEach { (x, y) ->
                    assertTrue("${d.id}: x=$x is outside 0..100 in $shape", x in 0f..100f)
                    assertTrue("${d.id}: y=$y is outside 0..100 in $shape", y in 0f..100f)
                }
            }
        }
    }

    @Test
    fun `every fact a diagram claims to draw exists`() {
        val ids = RealContent.load().sections
            .flatMap { it.subsections }
            .flatMap { it.facts }
            .map { it.id }
            .toSet()
        Diagrams.all.forEach { d ->
            d.factIds.forEach { factId ->
                assertTrue("${d.id} points at a fact that is not there: $factId", factId in ids)
            }
        }
    }

    @Test
    fun `a diagram is found from the subsection whose facts it draws`() {
        val content = RealContent.load()
        // The fifth wheel diagram draws facts from 6.4.1 and 11.6.1, so both
        // subsections must offer it.
        val coupling = content.subsection("6.4.1")!!
        assertTrue(Diagrams.forSubsection(coupling).any { it.id == "fifth-wheel" })
        val inspection = content.subsection("11.6.1")!!
        assertTrue(Diagrams.forSubsection(inspection).any { it.id == "fifth-wheel" })
        // A subsection with nothing to draw offers nothing.
        val fog = content.subsection("2.12")
        if (fog != null) assertTrue(Diagrams.forSubsection(fog).isEmpty())
    }

    /**
     * Not an assertion: this writes the diagrams out so they can be looked at.
     * There is no emulator here, so this is the only way to see them.
     */
    @Test
    fun `write the previews to build slash diagrams`() {
        val out = File("build/diagrams")
        out.mkdirs()
        Diagrams.all.forEach { d ->
            File(out, "${d.id}.svg").writeText(DiagramSvg.render(d))
        }
        assertEquals(Diagrams.all.size, out.listFiles { f: File -> f.name.endsWith(".svg") }!!.size)
    }
}
