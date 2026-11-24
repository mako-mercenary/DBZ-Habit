package com.cdlstudy.domain

/**
 * Renders a diagram's geometry to SVG.
 *
 * This exists so a diagram can be looked at before it ships. There is no
 * emulator in the build environment, so the only way to see whether a drawing
 * reads as a fifth wheel and not as a pile of boxes is to render the same
 * coordinates the app draws and open the result.
 *
 * It is in the test source set on purpose: it is a development tool, not part
 * of the app.
 */
object DiagramSvg {

    private val INK = mapOf(
        Ink.LINE to "#e8e6e3",
        Ink.MARK to "#e3b341",
        Ink.FAINT to "#6b7280",
        Ink.SERVICE to "#60a5fa",
        Ink.EMERGENCY to "#f87171",
    )

    fun render(diagram: Diagram, scale: Int = 6): String = buildString {
        val size = 100 * scale
        append("""<svg xmlns="http://www.w3.org/2000/svg" width="$size" """)
        append("""height="$size" viewBox="0 0 100 100">""")
        append("""<rect width="100" height="100" fill="#0e1013"/>""")
        diagram.shapes.forEach { append(shape(it)) }
        append("</svg>")
    }

    private fun color(ink: Ink) = INK.getValue(ink)

    private fun shape(s: Shape): String = when (s) {
        is Shape.Line -> {
            val dash = if (s.dashed) """ stroke-dasharray="2 1.5"""" else ""
            """<line x1="${s.x1}" y1="${s.y1}" x2="${s.x2}" y2="${s.y2}" """ +
                """stroke="${color(s.ink)}" stroke-width="${0.5f * s.weight}"$dash/>"""
        }

        is Shape.Arrow -> {
            val head = arrowHead(s.x1, s.y1, s.x2, s.y2, color(s.ink))
            """<line x1="${s.x1}" y1="${s.y1}" x2="${s.x2}" y2="${s.y2}" """ +
                """stroke="${color(s.ink)}" stroke-width="${0.5f * s.weight}"/>""" + head
        }

        is Shape.Box -> {
            val fill = if (s.fill) color(s.ink) else "none"
            """<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" """ +
                """rx="${s.round}" fill="$fill" stroke="${color(s.ink)}" stroke-width="0.5"/>"""
        }

        is Shape.Dot -> {
            val fill = if (s.fill) color(s.ink) else "none"
            """<circle cx="${s.cx}" cy="${s.cy}" r="${s.r}" fill="$fill" """ +
                """stroke="${color(s.ink)}" stroke-width="0.4"/>"""
        }

        is Shape.Label -> {
            val anchor = when (s.align) {
                Shape.Label.Align.LEFT -> "start"
                Shape.Label.Align.CENTER -> "middle"
                Shape.Label.Align.RIGHT -> "end"
            }
            val weight = if (s.bold) """ font-weight="700"""" else ""
            """<text x="${s.x}" y="${s.y}" fill="${color(s.ink)}" font-size="${s.size}" """ +
                """font-family="Inter, system-ui, sans-serif" text-anchor="$anchor"$weight>""" +
                escape(s.text) + "</text>"
        }
    }

    /** A small triangle at the far end, pointing along the line. */
    private fun arrowHead(x1: Float, y1: Float, x2: Float, y2: Float, color: String): String {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length == 0f) return ""
        val ux = dx / length
        val uy = dy / length
        val size = 2f
        val bx = x2 - ux * size
        val by = y2 - uy * size
        val px = -uy * size * 0.5f
        val py = ux * size * 0.5f
        return """<polygon points="$x2,$y2 ${bx + px},${by + py} ${bx - px},${by - py}" """ +
            """fill="$color"/>"""
    }

    private fun escape(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
