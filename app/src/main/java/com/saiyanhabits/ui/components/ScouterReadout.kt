package com.saiyanhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The big power level display. When [powerLevel] rises it counts up with a
 * glow, and each value from [gains] floats up as a "+points" label. Tapping
 * the readout skips the animation.
 */
@Composable
fun ScouterReadout(
    powerLevel: Long,
    gains: Flow<Int>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // The number shown runs from countFrom to countTo as countTime goes 0 -> 1.
    // Kept as three plain states (not an Animatable) so a new target can be set
    // in one step, with no frame in between showing a wrong number.
    var countFrom by remember { mutableLongStateOf(powerLevel) }
    var countTo by remember { mutableLongStateOf(powerLevel) }
    var countTime by remember { mutableFloatStateOf(1f) }
    val shown = countFrom +
        ((countTo - countFrom) * FastOutSlowInEasing.transform(countTime)).roundToLong()

    LaunchedEffect(powerLevel) {
        if (powerLevel == countTo) return@LaunchedEffect
        countFrom = shown
        countTo = powerLevel
        countTime = 0f
        val durationNanos = GameConfig.POWER_COUNT_UP_MILLIS * 1_000_000f
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            if (countTime >= 1f) break // finished, or skipped by a tap
            countTime = ((now - start) / durationNanos).coerceIn(0f, 1f)
        }
    }

    var gainLabel by remember { mutableStateOf<Int?>(null) }
    val gainProgress = remember { Animatable(1f) }
    LaunchedEffect(gains) {
        gains.collect { points ->
            gainLabel = points
            // Launched separately so a second gain can restart the label mid-flight.
            launch {
                gainProgress.snapTo(0f)
                gainProgress.animateTo(1f, tween(GameConfig.POWER_GAIN_LABEL_MILLIS, easing = LinearOutSlowInEasing))
            }
        }
    }

    // 0 at rest, swells to 1 mid count-up, back to 0.
    val surge = if (countTime < 1f) sin(PI * countTime).toFloat() else 0f

    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawScouterPanel(surge) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip animation",
            ) {
                countTime = 1f
                scope.launch { gainProgress.snapTo(1f) }
            }
            .semantics(mergeDescendants = true) { contentDescription = "Power level $powerLevel" }
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .drawBehind { drawRect(SaiyanColors.Blue) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "POWER LEVEL",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                    color = SaiyanColors.Blue,
                )
            }
            Spacer(Modifier.height(6.dp))
            val digits = String.format(Locale.US, "%,d", shown)
            Text(
                text = digits,
                fontSize = when {
                    digits.length <= 7 -> 68.sp
                    digits.length <= 10 -> 52.sp
                    else -> 38.sp
                },
                fontWeight = FontWeight.Black,
                color = SaiyanColors.Orange,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + 0.06f * surge
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
        }

        val label = gainLabel
        if (label != null && gainProgress.value < 1f) {
            Text(
                text = "+$label",
                style = MaterialTheme.typography.headlineSmall,
                color = SaiyanColors.Blue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val p = gainProgress.value
                        translationY = (28.dp.toPx()) * (1f - p)
                        // Fade in fast, hold, fade out over the last third.
                        alpha = when {
                            p < 0.1f -> p / 0.1f
                            p > 0.66f -> (1f - p) / 0.34f
                            else -> 1f
                        }
                    },
            )
        }
    }
}

private fun DrawScope.drawScouterPanel(surge: Float) {
    drawRect(SaiyanColors.Panel)

    // Blue wash from the top-left, orange energy from behind the number.
    drawRect(
        Brush.linearGradient(
            colors = listOf(SaiyanColors.Blue.copy(alpha = 0.16f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(size.width * 0.7f, size.height),
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(SaiyanColors.Orange.copy(alpha = 0.14f + 0.38f * surge), Color.Transparent),
            center = Offset(size.width * 0.3f, size.height * 0.65f),
            radius = size.width * (0.55f + 0.25f * surge),
        ),
    )

    // Scan lines.
    val lineGap = 5.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += lineGap
    }

    // Targeting reticle on the right.
    val centre = Offset(size.width - 54.dp.toPx(), size.height / 2f)
    val ring = SaiyanColors.Blue.copy(alpha = 0.35f + 0.4f * surge)
    val radius = 30.dp.toPx() * (1f + 0.12f * surge)
    val stroke = Stroke(width = 1.5.dp.toPx())
    drawCircle(ring, radius, centre, style = stroke)
    drawCircle(ring, radius * 0.45f, centre, style = stroke)
    val tick = 8.dp.toPx()
    drawLine(ring, Offset(centre.x - radius - tick, centre.y), Offset(centre.x - radius + tick, centre.y), stroke.width)
    drawLine(ring, Offset(centre.x + radius - tick, centre.y), Offset(centre.x + radius + tick, centre.y), stroke.width)
    drawLine(ring, Offset(centre.x, centre.y - radius - tick), Offset(centre.x, centre.y - radius + tick), stroke.width)
    drawLine(ring, Offset(centre.x, centre.y + radius - tick), Offset(centre.x, centre.y + radius + tick), stroke.width)
}
