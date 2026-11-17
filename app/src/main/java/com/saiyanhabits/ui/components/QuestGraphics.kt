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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A boss, inside its aura.
 *
 * [hit] runs from 0 to 1 while the boss is taking a hit (flash and shake) and
 * is 1 at rest. A [defeated] boss is shown greyed out.
 */
@Composable
fun BossFigure(
    boss: Boss,
    modifier: Modifier = Modifier,
    hit: Float = 1f,
    defeated: Boolean = false,
) {
    val tint = if (defeated) SaiyanColors.TextDim else boss.color
    val recoil = (1f - hit).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.drawBehind {
            val radius = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(tint.copy(alpha = if (defeated) 0.12f else 0.38f + 0.3f * recoil), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
        },
    ) {
        ArtImage(
            key = ArtKey.boss(boss),
            contentDescription = boss.displayName,
            contentScale = ContentScale.Fit,
            alpha = if (defeated) 0.6f else 1f,
            colorFilter = if (defeated) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            modifier = Modifier
                .fillMaxSize(0.86f)
                .graphicsLayer {
                    translationX = sin(hit * 40f) * recoil * size.width * 0.12f
                    val squash = 1f - 0.08f * recoil
                    scaleX = squash
                    scaleY = squash
                    // Lets the flash below tint only the image's own pixels.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (recoil > 0f) {
                        drawRect(Color.White.copy(alpha = 0.85f * recoil * recoil), blendMode = BlendMode.SrcAtop)
                    }
                },
        )
    }
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
