package com.saiyanhabits.ui.games

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.saiyanhabits.R
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

private enum class Phase { READY, RIDING, OVER }

private val CarColors = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFFFFFFFF), Color(0xFF8E24AA), Color(0xFFFF7043),
)

/** Bulma's game: an endless hover-bike run through West City. */
@Composable
fun HoverBikeScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.READY) }
    var state by remember { mutableStateOf(HoverBikeState()) }
    var random by remember { mutableStateOf(Random(System.nanoTime())) }
    var useTilt by remember { mutableStateOf(true) }
    var touchSteer by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<PlayResult?>(null) }
    var best by remember { mutableStateOf<Long?>(null) }
    var radio by remember { mutableStateOf<String?>(null) }
    val lines by produceState(emptyList<String>()) { value = app.dialogue.all()[Mentor.BULMA]?.game.orEmpty() }
    val tilt = rememberTilt(enabled = phase == Phase.RIDING && useTilt)

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
        var nextRadioAt = 250f
        while (phase == Phase.RIDING) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val steer = if (useTilt) tilt.value else touchSteer
            val before = state
            state = HoverBike.step(state, dt, steer, random)
            if (state.capsules > before.capsules) sounds?.play(SoundKey.CHECK_OFF, 0.5f)
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
                    result = app.gameRepository.record(MiniGame.HOVER_BIKE, distance, "capsules=${state.capsules}")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2B2F3A))) {
        Canvas(
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
        ) { drawRun(state) }

        // Heads-up display.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White) }
            OutlinedText(
                String.format(Locale.US, "%,d m", state.distance.toLong()),
                MaterialTheme.typography.headlineSmall,
                fill = GoldLettering,
                outlineWidth = 4.dp,
                modifier = Modifier.weight(1f),
            )
            Text("${state.capsules} capsules  ·  ${(state.speed * 3.6f).toInt()} km/h", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }

        if (phase == Phase.RIDING) {
            radio?.let { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                        .background(Color(0xCC0B1F5C), RoundedCornerShape(14.dp))
                        .padding(8.dp),
                ) {
                    MentorPortrait(Mentor.BULMA, Modifier.size(36.dp))
                    Text(line, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        when (phase) {
            Phase.READY -> Box(Modifier.align(Alignment.Center).padding(20.dp)) {
                DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TabLabel("Hover-bike run")
                        Text(
                            "Weave through West City traffic. Grab capsules, hit an orange boost to smash through anything, " +
                                "and don't touch the cars. It gets faster the further you go.",
                            color = Color.White,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.saiyanhabits.ui.components.ChoiceTile(useTilt, { useTilt = true }, "Tilt", "lean the phone", Modifier.weight(1f))
                            com.saiyanhabits.ui.components.ChoiceTile(!useTilt, { useTilt = false }, "Touch", "hold left or right", Modifier.weight(1f))
                        }
                        DbzPrimaryButton(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Ride") }
                    }
                }
            }
            Phase.OVER -> Box(Modifier.align(Alignment.Center).padding(20.dp).navigationBarsPadding()) {
                GameResultCard(
                    game = MiniGame.HOVER_BIKE,
                    headline = "Crashed!",
                    score = state.distance.toLong(),
                    best = best,
                    result = result,
                    onAgain = { start() },
                    onDone = onBack,
                    extra = { Text("${state.capsules} capsules collected", color = Color.White) },
                )
            }
            Phase.RIDING -> Unit
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

/** Draws the road from above: buildings either side, lane lines, traffic and the bike. */
private fun DrawScope.drawRun(state: HoverBikeState) {
    val w = size.width
    val h = size.height
    val roadLeft = w * 0.14f
    val roadWidth = w * 0.72f
    val bikeY = h * 0.8f
    val perMetre = (bikeY - h * 0.08f) / HoverBike.VIEW_AHEAD
    fun xOf(x: Float) = roadLeft + x * roadWidth
    fun yOf(ahead: Float) = bikeY - ahead * perMetre

    // Pavement and buildings, scrolling.
    drawRect(Color(0xFF8D8F99), Offset.Zero, Size(roadLeft, h))
    drawRect(Color(0xFF8D8F99), Offset(roadLeft + roadWidth, 0f), Size(w - roadLeft - roadWidth, h))
    val block = 26f * perMetre
    val shift = (state.distance * perMetre) % block
    var y = -block + shift
    var i = 0
    while (y < h) {
        val tint = if (i % 2 == 0) Color(0xFF5C6BC0) else Color(0xFFB0BEC5)
        drawRoundRect(tint, Offset(4f, y + 6f), Size(roadLeft - 12f, block - 12f), CornerRadius(10f))
        drawRoundRect(if (i % 3 == 0) Color(0xFFFFCA28) else Color(0xFF90A4AE), Offset(roadLeft + roadWidth + 8f, y + 6f), Size(w - roadLeft - roadWidth - 12f, block - 12f), CornerRadius(10f))
        y += block
        i++
    }
    // Road and lane lines.
    drawRect(Color(0xFF3A3D47), Offset(roadLeft, 0f), Size(roadWidth, h))
    val dash = 8f * perMetre
    val dashShift = (state.distance * perMetre) % (dash * 2)
    for (lane in 1..2) {
        val lx = roadLeft + roadWidth * lane / 3f
        var dy = -dash * 2 + dashShift
        while (dy < h) {
            drawRect(Color(0xCCFFFFFF), Offset(lx - 3f, dy), Size(6f, dash))
            dy += dash * 2
        }
    }
    drawRect(SaiyanColors.FrameGold, Offset(roadLeft - 4f, 0f), Size(4f, h))
    drawRect(SaiyanColors.FrameGold, Offset(roadLeft + roadWidth, 0f), Size(4f, h))

    for (item in state.items) drawItem(item, xOf(item.x), yOf(item.ahead), roadWidth, perMetre)

    // The bike.
    val bx = xOf(state.bikeX)
    val bw = HoverBike.BIKE_WIDTH * roadWidth
    val bl = HoverBike.BIKE_LENGTH * perMetre * 1.6f
    if (state.boosting) {
        drawCircle(Brush.radialGradient(listOf(Color(0xAAFFB300), Color.Transparent), Offset(bx, bikeY), bl * 1.6f), bl * 1.6f, Offset(bx, bikeY))
    }
    drawOval(Color(0x55000000), Offset(bx - bw * 0.6f, bikeY - bl * 0.4f), Size(bw * 1.2f, bl * 1.1f))
    drawRoundRect(Color(0xFFF5F5F5), Offset(bx - bw / 2, bikeY - bl / 2), Size(bw, bl), CornerRadius(bw / 2))
    drawRoundRect(Color(0xFF1565C0), Offset(bx - bw * 0.3f, bikeY - bl * 0.3f), Size(bw * 0.6f, bl * 0.35f), CornerRadius(bw / 3))
    drawCircle(Color(0xFF4FC3F7), bw * 0.18f, Offset(bx, bikeY + bl * 0.42f))
}

private fun DrawScope.drawItem(item: RoadItem, x: Float, y: Float, roadWidth: Float, perMetre: Float) {
    val iw = item.thing.width * roadWidth
    val il = item.thing.length * perMetre
    when (item.thing) {
        RoadThing.CAR, RoadThing.TRUCK -> {
            val color = CarColors[item.id % CarColors.size]
            drawRoundRect(Color(0x55000000), Offset(x - iw / 2 + 4f, y - il / 2 + 6f), Size(iw, il), CornerRadius(iw / 4))
            drawRoundRect(color, Offset(x - iw / 2, y - il / 2), Size(iw, il), CornerRadius(iw / 4))
            drawRoundRect(Color(0xAA0D1B2A), Offset(x - iw * 0.35f, y - il * 0.32f), Size(iw * 0.7f, il * 0.22f), CornerRadius(6f))
            if (item.thing == RoadThing.TRUCK) {
                drawRect(Color(0x33000000), Offset(x - iw / 2, y), Size(iw, 3f))
            }
        }
        RoadThing.ROBOT -> {
            drawCircle(Color(0xFF546E7A), iw / 2, Offset(x, y))
            drawCircle(Color(0xFFFF1744), iw / 6, Offset(x, y))
        }
        RoadThing.CAPSULE -> {
            drawRoundRect(Color.White, Offset(x - iw / 2, y - iw / 4), Size(iw, iw / 2), CornerRadius(iw / 4))
            drawRoundRect(Color(0xFFE53935), Offset(x - iw / 2, y - iw / 4), Size(iw / 2, iw / 2), CornerRadius(iw / 4))
        }
        RoadThing.BOOST -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF176), Color(0xFFFF6F00)), Offset(x, y), iw), iw / 2, Offset(x, y))
            drawCircle(Color.White, iw / 6, Offset(x, y))
        }
    }
}
