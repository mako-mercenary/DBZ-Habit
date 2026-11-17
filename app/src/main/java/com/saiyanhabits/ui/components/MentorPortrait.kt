package com.saiyanhabits.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.ui.theme.color
import kotlin.math.min

/** Placeholder mentor art: a head-and-shoulders silhouette in the mentor's colour, inside a circle. */
@Composable
fun MentorPortrait(mentor: Mentor, modifier: Modifier = Modifier) {
    val tint = mentor.color
    Canvas(modifier.semantics { contentDescription = mentor.displayName }) {
        val radius = min(size.width, size.height) / 2f
        val circle = Path().apply { addOval(Rect(center, radius)) }
        drawCircle(tint.copy(alpha = 0.18f), radius)
        clipPath(circle) {
            // Shoulders: a wide oval sitting below the centre, cut off by the circle.
            drawOval(
                color = tint,
                topLeft = Offset(center.x - radius * 0.78f, center.y + radius * 0.30f),
                size = Size(radius * 1.56f, radius * 1.5f),
            )
            // Head.
            drawCircle(tint, radius * 0.34f, Offset(center.x, center.y - radius * 0.18f))
        }
    }
}
