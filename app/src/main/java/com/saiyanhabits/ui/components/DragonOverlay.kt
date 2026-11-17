package com.saiyanhabits.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.data.WishEntity
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val DragonGreen = Color(0xFF3FBF6A)

/**
 * The dragon, summoned with a full set of wish orbs. The sky goes dark, the
 * orbs light up and the dragon rises; then one of the [waiting] wishes can be
 * chosen. A tap skips the rising.
 *
 * [onGrant] is called with the chosen wish's id; the overlay then shows it as
 * granted until it is dismissed.
 */
@Composable
fun DragonOverlay(
    waiting: List<WishEntity>,
    onGrant: (Long) -> Unit,
    onWriteWish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rise = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Kept here so the list of choices does not empty out the moment a wish is granted.
    val choices = remember { waiting }
    var confirming by remember { mutableStateOf<WishEntity?>(null) }
    var granted by remember { mutableStateOf<WishEntity?>(null) }

    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(GameConfig.DRAGON_SUMMON_MILLIS, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        repeat(4) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(220)
        }
    }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSummoning(rise.value) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip",
            ) { scope.launch { rise.snapTo(1f) } },
    ) {
        // The dragon rises out of the ring of orbs, which sits at mid-screen.
        ArtImage(
            key = ArtKey.DRAGON,
            contentDescription = "The dragon",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .fillMaxWidth(0.8f)
                .aspectRatio(ArtKey.DRAGON.width.toFloat() / ArtKey.DRAGON.height)
                .graphicsLayer {
                    val grown = ((rise.value - 0.3f) / 0.6f).coerceIn(0f, 1f)
                    alpha = grown
                    translationY = size.height * 0.35f * (1f - grown)
                    val scale = 0.7f + 0.3f * grown
                    scaleX = scale
                    scaleY = scale
                },
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SaiyanColors.Panel.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, DragonGreen.copy(alpha = 0.7f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .graphicsLayer {
                    val arrive = ((rise.value - 0.8f) / 0.2f).coerceIn(0f, 1f)
                    alpha = arrive
                    translationY = 40.dp.toPx() * (1f - arrive)
                },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(20.dp)) {
                val done = granted
                val asked = confirming
                when {
                    done != null -> {
                        Text("YOUR WISH IS GRANTED", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = DragonGreen)
                        Text(done.text, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "You earned it. The orbs have scattered: perfect weeks will gather them again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaiyanColors.TextDim,
                        )
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Farewell") }
                    }
                    asked != null -> {
                        Text("IS THIS YOUR WISH?", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = DragonGreen)
                        Text(asked.text, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Granting it uses all ${GameConfig.WISH_ORBS_NEEDED} orbs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaiyanColors.TextDim,
                        )
                        Button(
                            onClick = {
                                onGrant(asked.id)
                                granted = asked
                                confirming = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Grant it") }
                        TextButton(onClick = { confirming = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose another")
                        }
                    }
                    choices.isEmpty() -> {
                        Text("I HAVE COME. BUT YOU HAVE NO WISH.", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = DragonGreen)
                        Text(
                            "Write down a reward first. Your orbs will keep.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaiyanColors.TextDim,
                        )
                        Button(onClick = onWriteWish, modifier = Modifier.fillMaxWidth()) { Text("Write a wish") }
                    }
                    else -> {
                        Text("SPEAK YOUR WISH", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = DragonGreen)
                        choices.forEach { wish ->
                            Surface(
                                onClick = { confirming = wish },
                                shape = RoundedCornerShape(14.dp),
                                color = SaiyanColors.PanelRaised,
                                border = BorderStroke(1.dp, SaiyanColors.Outline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    wish.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Not now", color = SaiyanColors.TextDim)
                        }
                    }
                }
            }
        }
    }
}

/** Placeholder dragon art, drawn in code: dark sky, lit orbs, a green serpent rising from them. */
private fun DrawScope.drawSummoning(t: Float) {
    val sky = (t / 0.15f).coerceIn(0f, 1f)
    drawRect(Color(0xFF02040A).copy(alpha = 0.97f * sky))

    // Two lightning flashes as the sky turns.
    val flash = maxOf(1f - abs(t - 0.2f) / 0.04f, 1f - abs(t - 0.33f) / 0.03f).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.55f * flash))

    val ring = Offset(size.width / 2f, size.height * 0.5f)
    val ringRadius = size.width * 0.17f
    val orbRadius = size.width * 0.045f

    // Light pouring up out of the orbs: a tall soft glow, cut off below the ring.
    val beam = ((t - 0.15f) / 0.25f).coerceIn(0f, 1f)
    if (beam > 0f) {
        val halfWidth = ringRadius * 1.5f
        clipRect(top = 0f, bottom = ring.y) {
            scale(scaleX = 1f, scaleY = 2.8f, pivot = ring) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SaiyanColors.Gold.copy(alpha = 0.45f * beam), Color.Transparent),
                        center = ring,
                        radius = halfWidth,
                    ),
                    radius = halfWidth,
                    center = ring,
                )
            }
        }
    }

    // The seven orbs, in a ring, glowing brighter as the sky darkens.
    val count = GameConfig.WISH_ORBS_NEEDED
    for (i in 0 until count) {
        val angle = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / count
        val at = ring + Offset(cos(angle), sin(angle)) * ringRadius
        drawCircle(
            Brush.radialGradient(
                listOf(SaiyanColors.Gold.copy(alpha = 0.55f * sky), Color.Transparent),
                center = at,
                radius = orbRadius * 2.6f,
            ),
            radius = orbRadius * 2.6f,
            center = at,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE9A8), SaiyanColors.Orange, SaiyanColors.OrangeDeep),
                center = at - Offset(orbRadius * 0.3f, orbRadius * 0.3f),
                radius = orbRadius * 1.4f,
            ),
            radius = orbRadius,
            center = at,
        )
    }
}
