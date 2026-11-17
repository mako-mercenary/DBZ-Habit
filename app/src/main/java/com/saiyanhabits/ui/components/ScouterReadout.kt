package com.saiyanhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import com.saiyanhabits.art.ArtKey
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * the readout skips the animation. [aura] is the current form's colour: it
 * glows behind the panel and breathes slowly.
 */
@Composable
fun ScouterReadout(
    powerLevel: Long,
    gains: Flow<Int>,
    modifier: Modifier = Modifier,
    formName: String? = null,
    aura: Color = SaiyanColors.Blue,
    numberColor: Color = SaiyanColors.Orange,
    /** Which of the player's portraits sits inside the reticle: 0 is the base form. */
    avatarForm: Int = 0,
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

    val breath by rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )

    val panelShape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 46.dp, bottomEnd = 46.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            // The form's aura glows out past the panel's edges.
            .drawBehind {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(aura.copy(alpha = 0.22f + 0.14f * breath + 0.3f * surge), Color.Transparent),
                        center = center,
                        radius = size.width * 0.62f,
                    ),
                    topLeft = Offset(-size.width * 0.12f, -size.height * 0.3f),
                    size = Size(size.width * 1.24f, size.height * 1.6f),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip animation",
            ) {
                countTime = 1f
                scope.launch { gainProgress.snapTo(1f) }
            }
            .semantics(mergeDescendants = true) { contentDescription = "Power level $powerLevel" },
    ) {
        // The green scouter screen, leaving room on the right for the device's earpiece.
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 34.dp)
                .drawBehind {
                    drawPanel(PanelStyle.Green, panelShape)
                    drawScouterScreen(surge, breath, panelShape)
                },
        ) {
            Column(Modifier.padding(start = 22.dp, top = 16.dp, bottom = 14.dp)) {
                OutlinedText(
                    "POWER LEVEL",
                    MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.5.sp),
                    fill = GoldLettering,
                    outlineWidth = 4.dp,
                )
                val digits = String.format(Locale.US, "%,d", shown)
                OutlinedText(
                    digits,
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = when {
                            digits.length <= 5 -> 72.sp
                            digits.length <= 7 -> 60.sp
                            digits.length <= 10 -> 46.sp
                            else -> 34.sp
                        },
                        lineHeight = 74.sp,
                    ),
                    fill = GoldLettering,
                    outlineWidth = 6.dp,
                    modifier = Modifier.graphicsLayer {
                        val scale = 1f + 0.06f * surge
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                )
                if (formName != null) TabLabel(formName, red = true, fontSize = 18)
            }

            // Your portrait in the targeting reticle.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 26.dp)
                    .size(112.dp),
            ) {
                PlayerPortrait(form = avatarForm, modifier = Modifier.size(86.dp))
            }
        }

        // The scouter's earpiece, over the panel's right end.
        ArtImage(
            key = ArtKey.SCOUTER_DEVICE,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(70.dp),
        )

        val label = gainLabel
        if (label != null && gainProgress.value < 1f) {
            OutlinedText(
                "+$label",
                MaterialTheme.typography.headlineMedium,
                fill = Brush.verticalGradient(listOf(Color.White, SaiyanColors.ScouterLine)),
                outlineWidth = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 120.dp, top = 8.dp)
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

/**
 * The scouter screen's display: a grid and scan lines, a line of alien
 * readout glyphs, signal bars, and the targeting reticle on the right.
 */
private fun DrawScope.drawScouterScreen(surge: Float, breath: Float, shape: Shape) {
    val inset = 5.dp.toPx()
    val outline = shape.createOutline(Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
    val clip = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
        translate(Offset(inset, inset))
    }
    val line = SaiyanColors.ScouterLine
    clipPath(clip) {
        // Grid.
        val step = 14.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(line.copy(alpha = 0.16f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        // Energy swelling behind the number while it counts up.
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFFFE066).copy(alpha = 0.10f + 0.35f * surge), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.6f),
                radius = size.width * (0.5f + 0.25f * surge),
            ),
        )
    }

    // Reticle, centred where the portrait sits.
    val centre = Offset(size.width - 26.dp.toPx() - 56.dp.toPx(), size.height / 2f)
    val ring = line.copy(alpha = 0.75f + 0.25f * surge)
    val radius = 50.dp.toPx() * (1f + 0.06f * surge)
    val stroke = 2.dp.toPx()
    drawCircle(ring, radius, centre, style = Stroke(stroke))
    drawCircle(ring.copy(alpha = 0.4f), radius * 1.12f, centre, style = Stroke(1.dp.toPx()))
    val tick = 14.dp.toPx()
    drawLine(ring, Offset(centre.x - radius - tick, centre.y), Offset(centre.x - radius + tick * 0.6f, centre.y), stroke)
    drawLine(ring, Offset(centre.x + radius - tick * 0.6f, centre.y), Offset(centre.x + radius + tick, centre.y), stroke)
    drawLine(ring, Offset(centre.x, centre.y - radius - tick), Offset(centre.x, centre.y - radius + tick * 0.6f), stroke)
    drawLine(ring, Offset(centre.x, centre.y + radius - tick * 0.6f), Offset(centre.x, centre.y + radius + tick), stroke)

    // A line of alien readout glyphs above the reticle, with a blinking cursor.
    val glyphTop = 12.dp.toPx()
    val glyphH = 10.dp.toPx()
    var gx = centre.x - radius - 10.dp.toPx()
    val pattern = listOf(3, 1, 2, 4, 1, 3, 2, 2, 4, 1)
    drawRect(Color(0xFFFFE066).copy(alpha = if (breath > 0.5f) 1f else 0.3f), Offset(gx - 14.dp.toPx(), glyphTop), Size(glyphH * 0.9f, glyphH))
    for (strokes in pattern) {
        for (k in 0 until strokes) {
            val w = 1.6.dp.toPx()
            val h = glyphH * (0.45f + 0.55f * ((k + strokes) % 3) / 2f)
            drawRect(Color(0xFFFFE066).copy(alpha = 0.85f), Offset(gx, glyphTop + glyphH - h), Size(w, h))
            gx += w + 1.4.dp.toPx()
        }
        gx += 4.dp.toPx()
        if (gx > size.width - 12.dp.toPx()) break
    }

    // Signal bars along the bottom, rising and falling gently.
    val barsBottom = size.height - 10.dp.toPx()
    val barW = 4.dp.toPx()
    val gap = 2.dp.toPx()
    var bx = centre.x - radius * 1.05f
    var i = 0
    while (bx < centre.x + radius * 1.05f) {
        val wave = 0.35f + 0.65f * ((kotlin.math.sin(i * 1.3f + breath * 6.28f) + 1f) / 2f)
        val h = 18.dp.toPx() * wave * (0.6f + 0.4f * ((i * 7) % 5) / 4f)
        drawRect(line.copy(alpha = 0.85f), Offset(bx, barsBottom - h), Size(barW, h))
        bx += barW + gap
        i++
    }
}
