package com.saiyanhabits.ui.games

import android.media.MediaPlayer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.data.Song
import com.saiyanhabits.data.SongLibrary
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.games.ChartMaker
import com.saiyanhabits.domain.games.Judgement
import com.saiyanhabits.domain.games.Note
import com.saiyanhabits.domain.games.RhythmLevel
import com.saiyanhabits.domain.games.RhythmScoring
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.launch
import java.util.Locale

private val LaneColors = listOf(Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFFEF5350))

private enum class Step { PICK, LOADING, PLAYING, RESULT }

/** Vegeta's game: hit the beats of your music as the gravity climbs. */
@Composable
fun RhythmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SaiyanApp
    val library = remember { SongLibrary(context) }
    val songs = remember { library.songs() }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.PICK) }
    var song by remember { mutableStateOf(songs.firstOrNull()) }
    var level by remember { mutableStateOf(RhythmLevel.NORMAL) }
    var loading by remember { mutableFloatStateOf(0f) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var result by remember { mutableStateOf<PlayResult?>(null) }
    var finalScore by remember { mutableLongStateOf(0L) }
    var counts by remember { mutableStateOf<Map<Judgement, Int>>(emptyMap()) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var previousBest by remember { mutableStateOf<Long?>(null) }
    val dialogue by produceState<com.saiyanhabits.domain.MentorDialogue?>(null) { value = app.dialogue.all()[Mentor.VEGETA] }
    val plays by remember { app.gameRepository.plays(MiniGame.RHYTHM) }.collectAsState(initial = emptyList())

    fun bestFor(s: Song, l: RhythmLevel): Long? = plays.filter { it.detail == "${s.file}|${l.name}" }.maxOfOrNull { it.score }

    fun load() {
        val s = song ?: return
        step = Step.LOADING
        loading = 0f
        scope.launch {
            notes = ChartMaker.make(library.onsets(s) { loading = it }, level)
            step = Step.PLAYING
        }
    }

    when (step) {
        Step.PICK -> Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0B0B), Color(0xFF3A0D0D))))
                .statusBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White) }
                OutlinedText("GRAVITY ROOM", MaterialTheme.typography.titleLarge, outlineWidth = 4.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MentorPortrait(Mentor.VEGETA, Modifier.size(64.dp))
                Text(dialogue?.game?.firstOrNull() ?: "", color = Color.White, modifier = Modifier.padding(start = 12.dp))
            }
            TabLabel("Song")
            if (songs.isEmpty()) Text("No songs found in assets/music.", color = SaiyanColors.TextDim)
            songs.forEach { s ->
                ChoiceTile(song == s, { song = s }, s.title, bestFor(s, level)?.let { "Best ${String.format(Locale.US, "%,d", it)}" } ?: "Not played yet", Modifier.fillMaxWidth())
            }
            TabLabel("Gravity")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RhythmLevel.entries.forEach { l -> ChoiceTile(level == l, { level = l }, l.title, l.caption, Modifier.weight(1f)) }
            }
            Text("Tap the lane as each note hits the line. A song is analysed the first time you play it, which takes a moment.", color = SaiyanColors.TextDim)
            DbzPrimaryButton(onClick = { load() }, enabled = song != null, modifier = Modifier.fillMaxWidth()) { Text("Enter the gravity room") }
        }

        Step.LOADING -> Box(Modifier.fillMaxSize().background(Color(0xFF1A0B0B)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Reading the beat...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                MeterBar(loading, SaiyanColors.Danger)
            }
        }

        Step.PLAYING -> Playfield(
            song = song!!,
            notes = notes,
            level = level,
            lines = dialogue?.game.orEmpty(),
            onQuit = { step = Step.PICK },
            onFinished = { score, judged, maxCombo ->
                finalScore = score
                counts = judged
                bestCombo = maxCombo
                step = Step.RESULT
                scope.launch {
                    previousBest = bestFor(song!!, level)
                    // A song with nothing hit isn't counted.
                    result = if (score > 0) app.gameRepository.record(MiniGame.RHYTHM, score, "${song!!.file}|${level.name}") else null
                }
            },
        )

        Step.RESULT -> Box(
            Modifier.fillMaxSize().background(Color(0xFF1A0B0B)).statusBarsPadding().navigationBarsPadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val total = counts.values.sum().coerceAtLeast(1)
            val accuracy = (counts[Judgement.PERFECT] ?: 0) * 100 + (counts[Judgement.GREAT] ?: 0) * 70 + (counts[Judgement.GOOD] ?: 0) * 40
            val percent = accuracy / total
            val good = percent >= 60
            GameResultCard(
                game = MiniGame.RHYTHM,
                headline = if (good) "Survived!" else "Crushed",
                score = finalScore,
                best = previousBest,
                result = result,
                onAgain = { step = Step.PICK },
                onDone = onBack,
                extra = {
                    Text(
                        Judgement.entries.joinToString("  ·  ") { "${it.label} ${counts[it] ?: 0}" },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Accuracy $percent%  ·  Best combo $bestCombo", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text((if (good) dialogue?.gameDone else dialogue?.gameLost)?.randomOrNull() ?: "", color = Color.White.copy(alpha = 0.9f))
                },
            )
        }
    }
}

/** The lanes: notes fall towards the line in time with the song. */
@Composable
private fun Playfield(
    song: Song,
    notes: List<Note>,
    level: RhythmLevel,
    lines: List<String>,
    onQuit: () -> Unit,
    onFinished: (score: Long, judged: Map<Judgement, Int>, maxCombo: Int) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val travelMs = when (level) {
        RhythmLevel.EASY -> 1700L
        RhythmLevel.NORMAL -> 1350L
        RhythmLevel.HARD -> 1050L
    }
    var songMs by remember { mutableLongStateOf(-travelMs) }
    val judged = remember { HashMap<Int, Judgement>() }
    var score by remember { mutableLongStateOf(0L) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<Pair<Judgement, Long>?>(null) }
    var laneFlash by remember { mutableStateOf(LongArray(RhythmScoring.LANES)) }
    var say by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val player = remember {
        MediaPlayer().apply {
            context.assets.openFd(song.assetPath).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            prepare()
        }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player.release()
        }
    }

    fun register(j: Judgement) {
        if (j == Judgement.MISS) {
            if (combo >= 10) say = lines.getOrNull(6)
            combo = 0
        } else {
            combo++
            maxCombo = maxOf(maxCombo, combo)
            score += j.points.toLong() * RhythmScoring.multiplier(combo)
            when (combo) {
                10 -> say = lines.getOrNull(3)
                50 -> say = lines.getOrNull(4)
                100 -> say = lines.getOrNull(5)
            }
        }
        flash = j to songMs
    }

    // A short count-in, then the song; the clock follows the player.
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var started = false
        var lastPos = 0L
        var lastPosAt = 0L
        while (!finished) {
            val now = withFrameNanos { it }
            if (!started) {
                songMs = -travelMs + (now - start) / 1_000_000
                if (songMs >= 0) {
                    player.start()
                    started = true
                    lastPosAt = now
                }
            } else {
                val pos = player.currentPosition.toLong()
                // The player's position updates in steps; fill the gaps with the frame clock.
                if (pos != lastPos) {
                    lastPos = pos
                    lastPosAt = now
                }
                songMs = lastPos + if (player.isPlaying) (now - lastPosAt) / 1_000_000 else 0
            }
            // Notes that slid past the line unhit are misses.
            notes.forEachIndexed { i, note ->
                if (i !in judged && songMs - note.timeMs > Judgement.GOOD.windowMs) {
                    judged[i] = Judgement.MISS
                    register(Judgement.MISS)
                }
            }
            val lastNote = notes.lastOrNull()?.timeMs ?: 0
            if (started && (!player.isPlaying && songMs > 0 || songMs > lastNote + 2500)) {
                finished = true
                onFinished(score, judged.values.groupingBy { it }.eachCount(), maxCombo)
            }
        }
    }

    val gravity = RhythmScoring.gravity(combo)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF120606), Color(0xFF3A0D0D).copy(alpha = 1f), Color(0xFF8B1A1A).copy(alpha = (gravity / 100f).coerceIn(0.2f, 1f))),
                ),
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(notes) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val lane = (down.position.x / size.width * RhythmScoring.LANES).toInt().coerceIn(0, RhythmScoring.LANES - 1)
                        laneFlash = laneFlash.copyOf().also { it[lane] = songMs }
                        // The nearest unhit note in that lane, if it's close enough to count.
                        val target = notes.indices
                            .filter { it !in judged && notes[it].lane == lane }
                            .minByOrNull { kotlin.math.abs(notes[it].timeMs - songMs) }
                        if (target != null) {
                            val j = RhythmScoring.judge(songMs - notes[target].timeMs)
                            if (j != Judgement.MISS) {
                                judged[target] = j
                                register(j)
                            }
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val laneW = w / RhythmScoring.LANES
            val hitY = h * 0.84f
            val topY = h * 0.1f
            for (lane in 0 until RhythmScoring.LANES) {
                val x = lane * laneW
                val lit = songMs - laneFlash[lane] < 120
                drawRect(if (lit) LaneColors[lane].copy(alpha = 0.25f) else Color(0x22000000), Offset(x + 2f, 0f), Size(laneW - 4f, h))
                drawLine(Color(0x33FFFFFF), Offset(x, 0f), Offset(x, h), 2f)
                drawRoundRect(LaneColors[lane].copy(alpha = if (lit) 1f else 0.5f), Offset(x + laneW * 0.12f, hitY - 14f), Size(laneW * 0.76f, 28f), CornerRadius(14f), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
            }
            drawLine(SaiyanColors.FrameGold, Offset(0f, hitY), Offset(w, hitY), 4f)
            notes.forEachIndexed { i, note ->
                if (i in judged) return@forEachIndexed
                val untilHit = note.timeMs - songMs
                if (untilHit > travelMs || untilHit < -Judgement.GOOD.windowMs) return@forEachIndexed
                val y = hitY - (hitY - topY) * (untilHit.toFloat() / travelMs)
                val x = note.lane * laneW + laneW / 2
                drawCircle(Brush.radialGradient(listOf(Color.White, LaneColors[note.lane]), Offset(x, y), laneW * 0.32f), laneW * 0.28f, Offset(x, y))
                drawCircle(Color(0xAA000000), laneW * 0.28f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onQuit) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Quit", tint = Color.White) }
            OutlinedText(String.format(Locale.US, "%,d", score), MaterialTheme.typography.headlineSmall, fill = GoldLettering, outlineWidth = 4.dp, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("GRAVITY ${gravity}x", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Combo $combo  ·  x${RhythmScoring.multiplier(combo)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        flash?.let { (j, at) ->
            if (songMs - at < 450) {
                OutlinedText(
                    j.label.uppercase(),
                    MaterialTheme.typography.headlineMedium,
                    fill = if (j == Judgement.MISS) androidx.compose.ui.graphics.SolidColor(SaiyanColors.Danger) else GoldLettering,
                    outlineWidth = 4.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        say?.let { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC1A0B0B), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                MentorPortrait(Mentor.VEGETA, Modifier.size(36.dp))
                Text(line, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
