package com.saiyanhabits.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Full-screen victory when a boss reaches 0 HP. The burst plays for
 * GameConfig.VICTORY_MILLIS, then the result stays up until it is tapped.
 */
@Composable
fun VictoryOverlay(sagaName: String, boss: Boss, bonus: Int, onDismiss: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val sounds = com.saiyanhabits.sound.LocalSoundPlayer.current
    LaunchedEffect(Unit) {
        sounds?.play(com.saiyanhabits.sound.SoundKey.VICTORY)
        progress.animateTo(1f, tween(GameConfig.VICTORY_MILLIS, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(180)
        }
    }
    BackHandler(onBack = onDismiss)

    val tint = boss.color
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val t = progress.value
                drawRect(Color.Black.copy(alpha = 0.9f * (t / 0.1f).coerceIn(0f, 1f)))
                val reach = max(size.width, size.height)
                // Soft glow behind the text.
                drawRect(
                    Brush.radialGradient(
                        listOf(SaiyanColors.Orange.copy(alpha = 0.32f), Color.Transparent),
                        center = center,
                        radius = reach * 0.45f,
                    ),
                )
                // Rays turning slowly around the text. They start away from the
                // centre so they never run through the words.
                val rays = 16
                for (i in 0 until rays) {
                    val angle = i * 2f * PI.toFloat() / rays + t * 0.6f
                    val direction = Offset(cos(angle), sin(angle))
                    val start = center + direction * (reach * 0.3f)
                    val end = center + direction * (reach * (0.45f + 0.5f * t) * (if (i % 2 == 0) 1f else 0.8f))
                    drawLine(
                        brush = Brush.linearGradient(
                            listOf((if (i % 2 == 0) SaiyanColors.Orange else tint).copy(alpha = 0.6f), Color.Transparent),
                            start = start,
                            end = end,
                        ),
                        start = start,
                        end = end,
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val flash = (1f - t / 0.18f).coerceIn(0f, 1f)
                if (flash > 0f) drawRect(Color.White.copy(alpha = 0.7f * flash))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Continue",
                onClick = onDismiss,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val arrive = ((progress.value - 0.1f) / 0.15f).coerceIn(0f, 1f)
                    alpha = arrive
                    val scale = 1f + 0.8f * (1f - arrive)
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            BossFigure(boss, Modifier.size(120.dp), defeated = true)
            Text(
                "VICTORY",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = SaiyanColors.Orange,
            )
            Text(
                "${boss.displayName} is defeated",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                textAlign = TextAlign.Center,
            )
            Text(
                sagaName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text("+$bonus", fontSize = 40.sp, fontWeight = FontWeight.Black, color = SaiyanColors.Gold)
            Text("victory bonus", style = MaterialTheme.typography.labelLarge, color = SaiyanColors.TextDim)
            Spacer(Modifier.height(24.dp))
            Text("Tap to continue", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
        }
    }
}
