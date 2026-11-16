package com.saiyanhabits.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Outline of each placeholder boss: a star with this many points and this much pinch. */
private data class BossShape(val points: Int, val pinch: Float)

private val Boss.shape: BossShape
    get() = when (this) {
        Boss.RADITZ -> BossShape(points = 3, pinch = 0.55f)
        Boss.NAPPA -> BossShape(points = 4, pinch = 1f)
        Boss.FRIEZA -> BossShape(points = 6, pinch = 1f)
        Boss.CELL -> BossShape(points = 5, pinch = 0.72f)
        Boss.BUU -> BossShape(points = 28, pinch = 1f)
        Boss.BROLY -> BossShape(points = 8, pinch = 0.72f)
    }

/**
 * Placeholder boss art: a coloured shape with eyes, inside an aura.
 *
 * [hit] runs from 0 to 1 while the boss is taking a hit (flash and shake) and
 * is 1 at rest. A [defeated] boss is drawn greyed out.
 */
@Composable
fun BossFigure(
    boss: Boss,
    modifier: Modifier = Modifier,
    hit: Float = 1f,
    defeated: Boolean = false,
) {
    val tint = if (defeated) SaiyanColors.TextDim else boss.color
    Canvas(modifier.semantics { contentDescription = boss.displayName }) {
        val radius = min(size.width, size.height) / 2f
        val recoil = (1f - hit).coerceIn(0f, 1f)
        val shake = sin(hit * 40f) * recoil * radius * 0.12f

        drawCircle(
            Brush.radialGradient(
                listOf(tint.copy(alpha = if (defeated) 0.12f else 0.38f + 0.3f * recoil), Color.Transparent),
                center = center,
                radius = radius,
            ),
        )

        translate(left = shake) {
            val bodyRadius = radius * 0.62f * (1f - 0.08f * recoil)
            val outline = starPath(center, bodyRadius, boss.shape)
            drawPath(outline, Color(0xFF0C0F1A))
            drawPath(outline, tint, style = Stroke(width = radius * 0.07f))

            // Two slanted eyes.
            val eyeY = center.y - bodyRadius * 0.08f
            val eyeReach = bodyRadius * 0.34f
            val eyeStroke = radius * 0.06f
            for (side in listOf(-1f, 1f)) {
                drawLine(
                    color = if (defeated) tint else Color.White,
                    start = Offset(center.x + side * eyeReach * 0.35f, eyeY + eyeReach * 0.22f),
                    end = Offset(center.x + side * eyeReach, eyeY - eyeReach * 0.12f),
                    strokeWidth = eyeStroke,
                    cap = StrokeCap.Round,
                )
            }
            // White flash at the moment of impact.
            if (recoil > 0f) drawPath(outline, Color.White.copy(alpha = 0.85f * recoil * recoil))
        }
    }
}

private fun starPath(center: Offset, radius: Float, shape: BossShape): Path {
    val path = Path()
    val corners = shape.points * 2
    for (i in 0 until corners) {
        val r = if (i % 2 == 0) radius else radius * shape.pinch * cos(PI / shape.points).toFloat()
        val angle = -PI / 2 + i * PI / shape.points
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A bar that drains or fills smoothly as [fraction] changes. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "meter",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(SaiyanColors.PanelRaised),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * The spirit bomb: a glowing ball that grows with [fraction] (0 to 1), with
 * specks of energy drifting into it until it is full.
 */
@Composable
fun SpiritBombOrb(fraction: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "orb",
    )
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "drift",
    )
    Canvas(modifier.semantics { contentDescription = "Spirit bomb, ${(fraction * 100).toInt()} percent charged" }) {
        val reach = min(size.width, size.height) / 2f
        val core = reach * (0.16f + 0.5f * shown)
        val glow = core * (1.5f + 0.12f * pulse)
        val full = shown >= 1f

        drawCircle(
            Brush.radialGradient(
                listOf(SaiyanColors.Blue.copy(alpha = 0.45f + 0.2f * pulse), Color.Transparent),
                center = center,
                radius = glow,
            ),
            radius = glow,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color(0xFFBFE6FF), SaiyanColors.Blue.copy(alpha = 0.9f)),
                center = center - Offset(core * 0.25f, core * 0.25f),
                radius = core * 1.3f,
            ),
            radius = core,
        )
        if (full) {
            drawCircle(Color.White.copy(alpha = 0.5f + 0.4f * pulse), core * 1.12f, style = Stroke(reach * 0.02f))
        } else {
            // Specks spiralling inward. Each has a fixed lane so they don't jump about.
            val specks = 10
            for (i in 0 until specks) {
                val lane = (i * 0.618034f) % 1f
                val travel = (drift + lane) % 1f
                val distance = core + (reach - core) * (1f - travel)
                val angle = lane * 2f * PI.toFloat() + travel * 1.2f
                drawCircle(
                    color = Color(0xFFBFE6FF).copy(alpha = 0.9f * travel),
                    radius = reach * 0.018f * (1f + lane),
                    center = center + Offset(cos(angle), sin(angle)) * distance,
                )
            }
        }
    }
}
