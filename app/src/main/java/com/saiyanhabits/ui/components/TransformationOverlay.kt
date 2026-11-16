package com.saiyanhabits.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.Forms
import com.saiyanhabits.ui.theme.formAura
import com.saiyanhabits.ui.theme.formSpark
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Full-screen power-up shown when a new form unlocks: aura burst, rising
 * energy, a flash, screen shake and haptics. Runs once, then calls
 * [onFinished]; a tap or the back button ends it early.
 *
 * [onShake] receives the offset, in pixels, to shove the screen underneath by.
 */
@Composable
fun TransformationOverlay(
    form: Int,
    streak: Int,
    onShake: (Offset) -> Unit,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val shake by rememberUpdatedState(onShake)
    val haptics = LocalHapticFeedback.current
    val shakeReach = with(LocalDensity.current) { 14.dp.toPx() }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(GameConfig.TRANSFORMATION_MILLIS, easing = LinearEasing))
        shake(Offset.Zero)
        finish()
    }
    LaunchedEffect(Unit) {
        repeat(5) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(150)
        }
    }
    // The shake is strongest at the start and has settled by 60% of the way through.
    LaunchedEffect(Unit) {
        snapshotFlow { progress.value }.collect { t ->
            val strength = (1f - t / 0.6f).coerceAtLeast(0f) * shakeReach
            shake(Offset(sin(t * 95f) * strength, cos(t * 71f) * strength))
        }
    }
    BackHandler {
        shake(Offset.Zero)
        finish()
    }

    val aura = formAura(form)
    val spark = formSpark(form)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawPowerUp(progress.value, aura, spark, lightning = form >= 2) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip",
            ) {
                shake(Offset.Zero)
                finish()
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val t = progress.value
                    // Slams in at 25%, holds, fades with everything else at the end.
                    val arrive = ((t - 0.25f) / 0.12f).coerceIn(0f, 1f)
                    val leave = ((1f - t) / 0.12f).coerceIn(0f, 1f)
                    alpha = arrive * leave
                    val scale = 1f + 0.9f * (1f - arrive)
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Text(
                text = "FORM $form UNLOCKED",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 4.sp,
                color = spark,
            )
            Text(
                text = Forms.name(form).uppercase(),
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = aura,
            )
            Text(
                text = "$streak-day streak",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

private fun DrawScope.drawPowerUp(t: Float, aura: Color, spark: Color, lightning: Boolean) {
    val fadeIn = (t / 0.08f).coerceIn(0f, 1f)
    val fadeOut = ((1f - t) / 0.12f).coerceIn(0f, 1f)
    val visible = fadeIn * fadeOut
    val reach = max(size.width, size.height)

    drawRect(Color.Black.copy(alpha = 0.86f * visible))

    // Aura swelling out from the centre.
    val swell = 0.25f + 0.85f * t
    drawRect(
        Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f * visible),
                aura.copy(alpha = 0.6f * visible),
                aura.copy(alpha = 0.12f * visible),
                Color.Transparent,
            ),
            center = center,
            radius = reach * swell,
        ),
    )

    // Shock rings, one after another.
    for (ring in 0 until 3) {
        val ringT = ((t - 0.12f * ring) / 0.55f).coerceIn(0f, 1f)
        if (ringT <= 0f || ringT >= 1f) continue
        drawCircle(
            color = aura.copy(alpha = (1f - ringT) * 0.8f * visible),
            radius = reach * 0.75f * ringT,
            center = center,
            style = Stroke(width = (10.dp.toPx()) * (1f - ringT) + 1f),
        )
    }

    // Streaks of energy rising up the screen. Positions are fixed per streak
    // so they don't jitter between frames.
    val streaks = 18
    for (i in 0 until streaks) {
        val seed = (i * 0.618034f) % 1f
        val x = size.width * ((i + 0.5f) / streaks + (seed - 0.5f) * 0.04f)
        val travel = (t * (1.6f + seed) + seed) % 1f
        val top = size.height * (1.1f - 1.3f * travel)
        val length = size.height * (0.10f + 0.12f * seed)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, (if (i % 3 == 0) spark else aura).copy(alpha = 0.75f * visible)),
                startY = top + length,
                endY = top,
            ),
            start = Offset(x, top + length),
            end = Offset(x, top),
            strokeWidth = (2.dp.toPx()) * (1f + 2f * seed),
            cap = StrokeCap.Round,
        )
    }

    // Lightning for the higher forms: jagged bolts that flicker on and off.
    if (lightning) {
        val frame = (t * 14f).toInt()
        for (bolt in 0 until 3) {
            if ((frame + bolt) % 3 != 0) continue
            val path = Path()
            val angle = (bolt * 2.1f + frame * 0.7f)
            var point = center + Offset(cos(angle), sin(angle)) * (reach * 0.08f)
            path.moveTo(point.x, point.y)
            for (step in 1..6) {
                val jitter = sin((frame * 3 + bolt * 7 + step * 5).toFloat()) * 0.5f
                val direction = angle + jitter
                point += Offset(cos(direction), sin(direction)) * (reach * 0.07f)
                path.lineTo(point.x, point.y)
            }
            drawPath(path, spark.copy(alpha = 0.9f * visible), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }

    // White flash as the form name lands.
    val flash = (1f - kotlin.math.abs(t - 0.27f) / 0.1f).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.8f * flash * sin(flash * PI.toFloat() / 2f)))
}
