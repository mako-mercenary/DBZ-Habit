package com.saiyanhabits.ui.games

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.saiyanhabits.ui.components.drawStar
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzSecondaryButton
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.saiyanhabits.GameConfig
import com.saiyanhabits.R
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.games.HoverBike
import com.saiyanhabits.domain.games.HoverBikeState
import com.saiyanhabits.domain.games.RoadItem
import com.saiyanhabits.domain.games.RoadThing
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private enum class Phase { READY, RIDING, PAUSED, OVER }

/** Bulma's game: an endless hover-bike run through West City, seen from behind the bike. */
@Composable
fun HoverBikeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.READY) }
    var state by remember { mutableStateOf(HoverBikeState()) }
    var random by remember { mutableStateOf(Random(System.nanoTime())) }
    var useTilt by remember { mutableStateOf(true) }
    var touchSteer by remember { mutableFloatStateOf(0f) }
    var lean by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<PlayResult?>(null) }
    var best by remember { mutableStateOf<Long?>(null) }
    var radio by remember { mutableStateOf<String?>(null) }
    val lines by produceState(emptyList<String>()) { value = app.dialogue.all()[Mentor.BULMA]?.game.orEmpty() }
    val world = remember { HoverBikeWorld() }
    var points by remember { mutableStateOf(ScreenPoints()) }
    // The 2D art: the rider over the scene and the skyline behind it; both can be swapped in Settings > Art.
    val artVersion by app.art.version.collectAsState()
    val rider by produceState<ImageBitmap?>(null, artVersion) { value = app.art.image(ArtKey.BIKE_RIDER) }
    val skyline by produceState<ImageBitmap?>(null, artVersion) { value = app.art.image(ArtKey.BIKE_SKYLINE) }
    val haze = remember(skyline) { skyline?.let { footColour(it) } ?: Color(0xFFB8DCF7) }
    world.showModelRider = rider == null
    var camera by remember { mutableStateOf<io.github.sceneview.node.CameraNode?>(null) }
    val tilt = rememberTilt(enabled = phase == Phase.RIDING && useTilt)
    val view = LocalView.current
    DisposableEffect(phase) {
        view.keepScreenOn = phase == Phase.RIDING
        onDispose { view.keepScreenOn = false }
    }

    fun start() {
        state = HoverBikeState()
        random = Random(System.nanoTime())
        result = null
        radio = lines.randomOrNull()
        phase = Phase.RIDING
    }

    // The game loop: one step per frame while riding.
    LaunchedEffect(phase) {
        if (phase != Phase.RIDING) return@LaunchedEffect
        var last = withFrameNanos { it }
        var nextRadioAt = state.distance + 250f
        while (phase == Phase.RIDING) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            clock += dt
            val steer = if (useTilt) tilt.value else touchSteer
            lean += (steer - lean) * (dt * 8f).coerceAtMost(1f)
            val before = state
            state = HoverBike.step(state, dt, steer, random)
            camera?.let { cam -> world.update(state, lean, clock, cam)?.let { points = it } }
            if (state.balls > before.balls) sounds?.play(SoundKey.CHECK_OFF, 0.5f)
            if (state.hearts < before.hearts && !state.crashed) sounds?.play(SoundKey.BOSS_HIT, 0.7f)
            if (state.boosting && !before.boosting) sounds?.play(SoundKey.BOMB_THROW, 0.6f)
            if (state.distance >= nextRadioAt) {
                radio = lines.randomOrNull()
                nextRadioAt += 300f
            }
            if (state.crashed) {
                sounds?.play(SoundKey.BOSS_HIT)
                phase = Phase.OVER
                val distance = state.distance.toLong()
                scope.launch {
                    best = app.gameRepository.bests.first()[MiniGame.HOVER_BIKE]
                    result = app.gameRepository.record(MiniGame.HOVER_BIKE, distance, "balls=${state.balls};score=${state.score}")
                }
            }
        }
    }

    // Before a run, keep the scene posed so it shows behind the menu.
    LaunchedEffect(phase, camera) {
        val cam = camera ?: return@LaunchedEffect
        while (phase != Phase.RIDING) {
            withFrameNanos { }
            world.update(state, lean, clock, cam)?.let { points = it }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2E7BE6), Color(0xFF8CCBFF), Color(0xFFB8DCF7)))),
    ) {
        skyline?.let { Skyline(it, points, haze) }
        HoverBikeScene(
            world,
            onCamera = { camera = it },
            modifier = Modifier.fillMaxSize(),
            fogColor = floatArrayOf(haze.red, haze.green, haze.blue),
        )
        rider?.let { RiderSprite(it, points, state, lean, clock) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(useTilt) {
                    if (useTilt) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touchSteer = if (down.position.x < size.width / 2) -1f else 1f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed }
                            touchSteer = when {
                                pressed == null -> 0f
                                pressed.position.x < size.width / 2 -> -1f
                                else -> 1f
                            }
                        } while (event.changes.any { it.pressed })
                        touchSteer = 0f
                    }
                },
        )

        Hud(state, radio, onPause = { if (phase == Phase.RIDING) phase = Phase.PAUSED })

        when (phase) {
            Phase.READY -> Box(Modifier.align(Alignment.Center).padding(20.dp)) {
                DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TabLabel("Hover-bike run")
                        Text(
                            "Ride through West City. Grab dragon balls, hit a blue boost to smash through anything, " +
                                "and dodge the traffic and barriers: three hits and you're out. It gets faster the further you go.",
                            color = Color.White,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceTile(useTilt, { useTilt = true }, "Tilt", "lean the phone", Modifier.weight(1f))
                            ChoiceTile(!useTilt, { useTilt = false }, "Touch", "hold left or right", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DbzSecondaryButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                            DbzPrimaryButton(onClick = { start() }, modifier = Modifier.weight(1f)) { Text("Ride") }
                        }
                    }
                }
            }
            Phase.PAUSED -> Box(Modifier.align(Alignment.Center).padding(20.dp)) {
                DbzPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TabLabel("Paused")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DbzSecondaryButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Quit") }
                            DbzPrimaryButton(onClick = { phase = Phase.RIDING }, modifier = Modifier.weight(1f)) { Text("Resume") }
                        }
                    }
                }
            }
            Phase.OVER -> Box(Modifier.align(Alignment.Center).padding(20.dp).navigationBarsPadding()) {
                GameResultCard(
                    game = MiniGame.HOVER_BIKE,
                    headline = "Wiped out!",
                    score = state.distance.toLong(),
                    best = best,
                    result = result,
                    onAgain = { start() },
                    onDone = onBack,
                    extra = {
                        Text(
                            String.format(Locale.US, "Score %,d  ·  %d dragon balls", state.score, state.balls),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
            }
            Phase.RIDING -> Unit
        }
    }
}

/**
 * The far-off skyline, drawn behind the 3D street with its foot just below the
 * horizon. It fills the sky above and slides a little as the bike steers.
 */
@Composable
private fun Skyline(image: ImageBitmap, points: ScreenPoints, haze: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val foot = size.height * (points.horizonY + GameConfig.BIKE_SKYLINE_OVERLAP)
        val aspect = image.width.toFloat() / image.height
        // Tall enough to reach the top, and wide enough to slide without showing an edge.
        val height = maxOf(foot, size.width * 1.3f / aspect)
        val width = height * aspect
        val slide = -points.bikeSideways * GameConfig.BIKE_SKYLINE_PARALLAX * size.width
        drawImage(
            image,
            dstOffset = IntOffset(((size.width - width) / 2 + slide).toInt(), (foot - height).toInt()),
            dstSize = IntSize(width.toInt(), height.toInt()),
        )
        // Haze below the skyline's foot, where the ground ends before the horizon.
        drawRect(haze, topLeft = Offset(0f, foot - 1f), size = Size(size.width, size.height * 0.08f))
    }
}

/** The rider on the bike, from behind: standing on the bike's spot on the road, tilting into turns. */
@Composable
private fun RiderSprite(image: ImageBitmap, points: ScreenPoints, state: HoverBikeState, lean: Float, clock: Float) {
    val flashing = state.invulnerable > 0f && ((state.invulnerable * 10).toInt() % 2 == 0)
    Canvas(Modifier.fillMaxSize()) {
        val width = size.width * GameConfig.BIKE_SPRITE_WIDTH
        val height = width * image.height / image.width
        val x = size.width * points.bikeX
        val ground = size.height * points.bikeY
        val bob = kotlin.math.sin(clock * 6f) * size.height * 0.004f
        // A soft shadow on the road under the bike.
        drawOval(
            Color(0x55000000),
            topLeft = Offset(x - width * 0.3f, ground - width * 0.05f),
            size = Size(width * 0.6f, width * 0.1f),
        )
        if (flashing) return@Canvas
        val bottom = ground - width * 0.06f + bob
        rotate(lean * GameConfig.BIKE_SPRITE_LEAN_DEGREES, pivot = Offset(x, bottom)) {
            drawImage(
                image,
                dstOffset = IntOffset((x - width / 2).toInt(), (bottom - height).toInt()),
                dstSize = IntSize(width.toInt(), height.toInt()),
            )
        }
    }
}

/** The average colour along the bottom of [image], for the haze that joins it to the street. */
private fun footColour(image: ImageBitmap): Color {
    val bitmap = image.asAndroidBitmap()
    var r = 0L; var g = 0L; var b = 0L; var n = 0
    val rows = (bitmap.height * 0.03f).toInt().coerceAtLeast(1)
    for (y in bitmap.height - rows until bitmap.height) {
        for (x in 0 until bitmap.width step 8) {
            val p = bitmap.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
    }
    if (n == 0) return Color(0xFFB8DCF7)
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** Bulma's portrait and hearts on the left; score, dragon balls, distance and pause on the right. */
@Composable
private fun Hud(state: HoverBikeState, radio: String?, onPause: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xAA0B1F5C), RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                    .padding(end = 12.dp),
            ) {
                Box(Modifier.size(64.dp).border(3.dp, SaiyanColors.FrameGold, CircleShape).padding(3.dp)) {
                    MentorPortrait(Mentor.BULMA, Modifier.fillMaxSize())
                }
                Column(Modifier.padding(start = 8.dp)) {
                    OutlinedText("BULMA", MaterialTheme.typography.titleMedium, outlineWidth = 3.dp)
                    Row {
                        repeat(HoverBike.HEARTS) { i ->
                            Text(
                                "\u2665",
                                color = if (i < state.hearts) Color(0xFFE53935) else Color(0x66FFFFFF),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText(
                        String.format(Locale.US, "%06d", state.score),
                        MaterialTheme.typography.headlineSmall,
                        outlineWidth = 4.dp,
                        modifier = Modifier.background(Color(0x880B1F5C), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF1565C0), RoundedCornerShape(10.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Pause", onClick = onPause),
                    ) { Text("\u275A\u275A", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(24.dp)) {
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFE65100))))
                        drawStar(center, size.minDimension * 0.2f, Color(0xFFD50000))
                    }
                    OutlinedText(" x ${state.balls}", MaterialTheme.typography.titleLarge, fill = GoldLettering, outlineWidth = 3.dp)
                }
                OutlinedText(String.format(Locale.US, "%,d m", state.distance.toLong()), MaterialTheme.typography.titleLarge, outlineWidth = 3.dp)
            }
        }
        radio?.let { line ->
            Text(
                line,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(Color(0xAA0B1F5C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** The phone's left-right tilt as a steer from -1 to 1, while [enabled]. */
@Composable
private fun rememberTilt(enabled: Boolean): androidx.compose.runtime.State<Float> {
    val context = LocalContext.current
    val tilt = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Right side down reads negative on x; a little dead zone keeps a level phone straight.
                val x = -event.values[0]
                tilt.floatValue = if (kotlin.math.abs(x) < 0.6f) 0f else (x / 4.5f).coerceIn(-1f, 1f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (enabled && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            manager.unregisterListener(listener)
            tilt.floatValue = 0f
        }
    }
    return tilt
}

