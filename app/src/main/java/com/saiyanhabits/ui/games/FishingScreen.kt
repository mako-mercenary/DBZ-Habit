package com.saiyanhabits.ui.games

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.games.Fish
import com.saiyanhabits.domain.games.Fishing
import com.saiyanhabits.domain.games.FishingPhase
import com.saiyanhabits.domain.games.FishingState
import com.saiyanhabits.domain.games.Rarity
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private val Rarity.color: Color
    get() = when (this) {
        Rarity.COMMON -> Color(0xFFB0BEC5)
        Rarity.UNCOMMON -> Color(0xFF66BB6A)
        Rarity.RARE -> Color(0xFF42A5F5)
        Rarity.LEGENDARY -> Color(0xFFFFB300)
    }

private fun kg(value: Float) = if (value < 1f) String.format(Locale.US, "%.0f g", value * 1000) else String.format(Locale.US, "%.1f kg", value)

/** Goku's game: fishing on the river. */
@Composable
fun FishingScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    val random = remember { Random(System.nanoTime()) }
    var state by remember { mutableStateOf(FishingState()) }
    var holding by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<PlayResult?>(null) }
    var best by remember { mutableStateOf<Long?>(null) }
    var showingLog by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf<String?>(null) }
    val dialogue by produceState<com.saiyanhabits.domain.MentorDialogue?>(null) { value = app.dialogue.all()[Mentor.GOKU] }
    val plays by remember { app.gameRepository.plays(MiniGame.FISHING) }.collectAsState(initial = emptyList())

    LaunchedEffect(dialogue) { say = dialogue?.game?.firstOrNull() }

    // One loop for the whole visit: the river keeps moving.
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            clock += dt
            val before = state
            state = Fishing.step(state, dt, holding, random)
            if (before.phase != state.phase) {
                val lines = dialogue?.game.orEmpty()
                when (state.phase) {
                    FishingPhase.BITE -> { say = lines.getOrNull(2); sounds?.play(SoundKey.CHECK_OFF, 0.6f) }
                    FishingPhase.WAITING -> say = lines.getOrNull(1)
                    FishingPhase.CAUGHT -> {
                        sounds?.play(SoundKey.LEVEL_UP)
                        say = dialogue?.gameDone?.randomOrNull()
                        val fish = state.fish!!
                        val grams = (state.kg * 1000).toLong()
                        scope.launch {
                            best = app.gameRepository.bests.first()[MiniGame.FISHING]
                            result = app.gameRepository.record(MiniGame.FISHING, grams, fish.name)
                        }
                    }
                    FishingPhase.SNAPPED -> say = "Aw, the line snapped! Too much pull. Ease off when it runs!"
                    FishingPhase.ESCAPED -> say = if (before.phase == FishingPhase.BITE) "Too slow! Tap the moment it dips!" else "It got away... keep the line tight next time!"
                    FishingPhase.SPOOKED -> say = "Whoa, too early! You scared it off."
                    else -> Unit
                }
            }
        }
    }

    if (showingLog) {
        FishLog(plays.mapNotNull { play -> Fish.entries.firstOrNull { it.name == play.detail }?.let { it to play.score } }, onBack = { showingLog = false })
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF87CEEB))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        when (state.phase) {
                            FishingPhase.WAITING, FishingPhase.BITE -> state = Fishing.strike(state, random)
                            FishingPhase.REELING -> holding = true
                            else -> {
                                result = null
                                state = Fishing.startCharging(state)
                            }
                        }
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        holding = false
                        if (state.phase == FishingPhase.CHARGING) state = Fishing.cast(state, random)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val water = h * 0.42f
            // Sky, far hills, trees, water.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6EC6FF), Color(0xFFB3E5FC)), endY = water), Offset.Zero, Size(w, water))
            drawCircle(Color(0xFF7CB342), w * 0.6f, Offset(w * 0.2f, water + w * 0.45f))
            drawCircle(Color(0xFF689F38), w * 0.7f, Offset(w * 0.95f, water + w * 0.55f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF29B6F6), Color(0xFF01579B)), startY = water), Offset(0f, water), Size(w, h - water))
            for (i in 0 until 8) {
                val y = water + 30f + i * (h - water) / 8f
                val x = ((clock * 40f + i * 137f) % (w + 200f)) - 100f
                drawLine(Color(0x55FFFFFF), Offset(x, y), Offset(x + 90f, y), 3f)
            }
            // Bank and Goku's rod.
            drawRect(Color(0xFF8D6E63), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
            val rodTip = Offset(w * 0.32f, h * 0.62f)
            drawLine(Color(0xFF5D4037), Offset(w * 0.08f, h * 0.9f), rodTip, 10f)
            // Line and bobber.
            val out = state.phase in listOf(FishingPhase.WAITING, FishingPhase.BITE, FishingPhase.REELING)
            if (out) {
                val reach = if (state.phase == FishingPhase.REELING) state.distance else 1f
                val bx = rodTip.x + (w * 0.6f - rodTip.x) * (0.3f + 0.7f * state.castPower) * reach + (1 - reach) * 0f
                val by = water + (h * 0.3f) * (1f - reach * 0.6f)
                val bob = when (state.phase) {
                    FishingPhase.BITE -> 18f
                    FishingPhase.REELING -> sin(clock * 20f) * 6f * (if (state.run > 0f) 2f else 1f)
                    else -> sin(clock * 3f) * 3f
                }
                drawLine(Color(0xCCFFFFFF), rodTip, Offset(bx, by + bob), 2.5f)
                drawCircle(Color.White, 12f, Offset(bx, by + bob))
                drawCircle(Color(0xFFE53935), 12f, Offset(bx, by + bob - 6f))
                if (state.phase == FishingPhase.BITE) {
                    drawCircle(Color(0x88FFFFFF), 40f + sin(clock * 15f) * 8f, Offset(bx, by + 10f), style = Stroke(4f))
                }
            }
        }

        // Top bar.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White) }
            OutlinedText("FISHING", MaterialTheme.typography.titleLarge, outlineWidth = 4.dp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showingLog = true }) { Text("Fish log (${plays.map { it.detail }.toSet().size}/${Fish.entries.size})", color = Color.White) }
        }

        // Goku says something.
        say?.let { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC0B1F5C), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                MentorPortrait(Mentor.GOKU, Modifier.size(40.dp))
                Text(line, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }

        // What to do now, and the meters.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.phase) {
                FishingPhase.CHARGING -> {
                    Text("Let go to cast", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    MeterBar(state.castPower, SaiyanColors.Orange)
                }
                FishingPhase.WAITING -> Text("Wait for the bite...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                FishingPhase.BITE -> OutlinedText("TAP NOW!", MaterialTheme.typography.headlineMedium, outlineWidth = 4.dp)
                FishingPhase.REELING -> {
                    Text("Hold to reel. Ease off when it runs.", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text("Line tension", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    MeterBar(state.tension, if (state.tension > 0.8f) SaiyanColors.Danger else if (state.tension < 0.12f) SaiyanColors.TextDim else SaiyanColors.Bean)
                    Text("Distance", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    MeterBar(1f - state.distance, SaiyanColors.Gold)
                }
                FishingPhase.CAUGHT -> state.fish?.let { fish ->
                    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TabLabel(fish.rarity.label)
                            OutlinedText(fish.title.uppercase(), MaterialTheme.typography.headlineSmall, outlineWidth = 4.dp)
                            Text(kg(state.kg), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            best?.let { b ->
                                if (state.kg * 1000 >= b) Text("Your biggest catch yet!", color = SaiyanColors.Gold)
                            }
                            result?.let { r ->
                                Text(if (r.bondXp > 0) "+${r.bondXp} bond XP with Goku" else "No more bond XP from games with Goku today.", color = Color.White)
                            }
                            Text("Hold anywhere to cast again.", color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                else -> Text(
                    "Hold anywhere to charge a cast, let go when the bar is high.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(10.dp)).padding(10.dp),
                )
            }
        }
    }
}

/** Every kind of fish: how many caught and the biggest, or a mystery. */
@Composable
private fun FishLog(catches: List<Pair<Fish, Long>>, onBack: () -> Unit) {
    SubScreen(title = "Fish log", onBack = onBack) {
        Fish.entries.forEach { fish ->
            val mine = catches.filter { it.first == fish }
            DbzPanel(Modifier.fillMaxWidth(), style = if (mine.isEmpty()) PanelStyle.Dim else PanelStyle.Blue) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(fish.rarity.color, RoundedCornerShape(7.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (mine.isEmpty()) "???" else fish.title, style = MaterialTheme.typography.titleMedium)
                        Text(fish.rarity.label, color = fish.rarity.color, style = MaterialTheme.typography.bodySmall)
                    }
                    if (mine.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${mine.size} caught", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("Biggest ${kg(mine.maxOf { it.second } / 1000f)}", color = SaiyanColors.Gold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
