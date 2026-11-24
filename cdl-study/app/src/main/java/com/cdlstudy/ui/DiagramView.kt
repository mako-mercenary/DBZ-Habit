package com.cdlstudy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.Diagram
import com.cdlstudy.domain.Ink
import com.cdlstudy.domain.Shape
import androidx.compose.ui.graphics.PathEffect
import kotlin.math.sqrt

/**
 * Draws a diagram's geometry on a canvas.
 *
 * The coordinates are the ones in [com.cdlstudy.domain.Diagrams], in a 100 by
 * 100 box, scaled to whatever width the screen gives. A unit test renders the
 * same coordinates to SVG, so what shipped is what was looked at.
 */
@Composable
fun DiagramView(diagram: Diagram, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = ink(Ink.LINE)
    val mark = ink(Ink.MARK)
    val faint = ink(Ink.FAINT)
    val service = ink(Ink.SERVICE)
    val emergency = ink(Ink.EMERGENCY)
    val colors = mapOf(
        Ink.LINE to line,
        Ink.MARK to mark,
        Ink.FAINT to faint,
        Ink.SERVICE to service,
        Ink.EMERGENCY to emergency,
    )

    Canvas(modifier.fillMaxWidth().aspectRatio(1f / diagram.aspect)) {
        val unit = size.width / 100f
        diagram.shapes.forEach { shape ->
            draw(shape, colors.getValue(shape.ink), unit, measurer)
        }
    }
}

@Composable
private fun ink(ink: Ink): Color = when (ink) {
    Ink.LINE -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    Ink.MARK -> Gold
    Ink.FAINT -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    Ink.SERVICE -> Color(0xFF60A5FA)
    Ink.EMERGENCY -> androidx.compose.material3.MaterialTheme.colorScheme.error
}

private fun DrawScope.draw(shape: Shape, color: Color, unit: Float, measurer: TextMeasurer) {
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)

    when (shape) {
        is Shape.Line -> drawLine(
            color = color,
            start = p(shape.x1, shape.y1),
            end = p(shape.x2, shape.y2),
            strokeWidth = 0.5f * shape.weight * unit,
            pathEffect = if (shape.dashed) {
                PathEffect.dashPathEffect(floatArrayOf(2f * unit, 1.5f * unit))
            } else {
                null
            },
        )

        is Shape.Arrow -> {
            drawLine(
                color = color,
                start = p(shape.x1, shape.y1),
                end = p(shape.x2, shape.y2),
                strokeWidth = 0.5f * shape.weight * unit,
            )
            // The head, a filled triangle pointing along the line.
            val dx = shape.x2 - shape.x1
            val dy = shape.y2 - shape.y1
            val length = sqrt(dx * dx + dy * dy)
            if (length > 0f) {
                val ux = dx / length
                val uy = dy / length
                val head = 2f
                val bx = shape.x2 - ux * head
                val by = shape.y2 - uy * head
                val px = -uy * head * 0.5f
                val py = ux * head * 0.5f
                val path = Path().apply {
                    moveTo(shape.x2 * unit, shape.y2 * unit)
                    lineTo((bx + px) * unit, (by + py) * unit)
                    lineTo((bx - px) * unit, (by - py) * unit)
                    close()
                }
                drawPath(path, color)
            }
        }

        is Shape.Box -> {
            val topLeft = p(shape.x, shape.y)
            val boxSize = Size(shape.w * unit, shape.h * unit)
            val radius = androidx.compose.ui.geometry.CornerRadius(shape.round * unit)
            if (shape.fill) {
                drawRoundRect(color, topLeft, boxSize, radius)
            } else {
                drawRoundRect(color, topLeft, boxSize, radius, style = Stroke(0.5f * unit))
            }
        }

        is Shape.Dot -> drawCircle(
            color = color,
            radius = shape.r * unit,
            center = p(shape.cx, shape.cy),
            style = if (shape.fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(0.4f * unit),
        )

        is Shape.Label -> {
            val style = TextStyle(
                color = color,
                fontSize = TextUnit(shape.size * unit / density, TextUnitType.Sp),
                fontWeight = if (shape.bold) FontWeight.Bold else FontWeight.Normal,
            )
            val laid = measurer.measure(shape.text, style)
            val dx = when (shape.align) {
                Shape.Label.Align.LEFT -> 0f
                Shape.Label.Align.CENTER -> -laid.size.width / 2f
                Shape.Label.Align.RIGHT -> -laid.size.width.toFloat()
            }
            // The SVG y is the text baseline; Compose draws from the top, so
            // the label is lifted by its own height to land in the same place.
            drawText(
                textLayoutResult = laid,
                topLeft = Offset(shape.x * unit + dx, shape.y * unit - laid.size.height * 0.78f),
            )
        }
    }
}
