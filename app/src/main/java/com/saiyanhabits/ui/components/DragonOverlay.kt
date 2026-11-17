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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs

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

    // The image is the whole scene: the dragon rising above its ring of orbs.
    // The golden glow is drawn under where that ring sits.
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val imageHeight = size.width * DRAGON_WIDTH * ArtKey.DRAGON.height / ArtKey.DRAGON.width
                drawSummoning(rise.value, ring = Offset(size.width / 2f, statusTop + imageHeight * ORB_RING_DOWN))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip",
            ) { scope.launch { rise.snapTo(1f) } },
    ) {
        ArtImage(
            key = ArtKey.DRAGON,
            contentDescription = "The dragon",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth(DRAGON_WIDTH)
                .aspectRatio(ArtKey.DRAGON.width.toFloat() / ArtKey.DRAGON.height)
                .graphicsLayer {
                    // Fades in after the second flash, rising a little and swelling to full size.
                    val grown = ((rise.value - 0.3f) / 0.55f).coerceIn(0f, 1f)
                    alpha = grown
                    translationY = size.height * 0.06f * (1f - grown)
                    val scale = 0.9f + 0.1f * grown
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, ORB_RING_DOWN)
                },
        )
        DbzPanel(
            style = PanelStyle.Blue,
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
                        DbzPrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Farewell") }
                    }
                    asked != null -> {
                        Text("IS THIS YOUR WISH?", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = DragonGreen)
                        Text(asked.text, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Granting it uses all ${GameConfig.WISH_ORBS_NEEDED} orbs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaiyanColors.TextDim,
                        )
                        DbzPrimaryButton(
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
                        DbzPrimaryButton(onClick = onWriteWish, modifier = Modifier.fillMaxWidth()) { Text("Write a wish") }
                    }
                    else -> {
                        Text("SPEAK YOUR WISH", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = DragonGreen)
                        choices.forEach { wish ->
                            DbzPanel(
                                style = PanelStyle.Blue,
                                onClick = { confirming = wish },
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

/**
 * Behind the dragon image: the sky turning dark, two lightning flashes, and
 * golden light swelling up from where the image's ring of orbs sits.
 */
private fun DrawScope.drawSummoning(t: Float, ring: Offset) {
    val sky = (t / 0.15f).coerceIn(0f, 1f)
    drawRect(Color(0xFF02040A).copy(alpha = 0.97f * sky))

    // Two lightning flashes as the sky turns.
    val flash = maxOf(1f - abs(t - 0.2f) / 0.04f, 1f - abs(t - 0.33f) / 0.03f).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.55f * flash))

    // Light from the orbs: a wide glow on the ground and a tall soft column above it.
    val light = ((t - 0.1f) / 0.3f).coerceIn(0f, 1f)
    if (light > 0f) {
        val radius = size.width * 0.45f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(SaiyanColors.Gold.copy(alpha = 0.45f * light), Color.Transparent),
                center = ring,
                radius = radius,
            ),
            radius = radius,
            center = ring,
        )
        clipRect(top = 0f, bottom = ring.y) {
            scale(scaleX = 0.6f, scaleY = 3f, pivot = ring) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SaiyanColors.Gold.copy(alpha = 0.3f * light), Color.Transparent),
                        center = ring,
                        radius = radius,
                    ),
                    radius = radius,
                    center = ring,
                )
            }
        }
    }
}

/** The dragon image's width as a share of the screen's: a little under full, so its ring of orbs clears the wish panel. */
private const val DRAGON_WIDTH = 0.9f

/** How far down the dragon image its ring of orbs sits, as a fraction of the image's height. */
private const val ORB_RING_DOWN = 0.85f
