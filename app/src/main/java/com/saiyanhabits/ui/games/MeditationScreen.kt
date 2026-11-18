package com.saiyanhabits.ui.games

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.games.BreathPattern
import com.saiyanhabits.domain.games.Breathing
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Stage { SETUP, BREATHING, DONE }

private val Lengths = listOf(1, 3, 5, 10)

/** Piccolo's game: a guided breathing session. */
@Composable
fun MeditationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SaiyanApp
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(Stage.SETUP) }
    var pattern by remember { mutableStateOf(BreathPattern.CALM) }
    var minutes by remember { mutableIntStateOf(3) }
    var vibrate by remember { mutableStateOf(true) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<PlayResult?>(null) }
    var best by remember { mutableStateOf<Long?>(null) }
    var openHabits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var checkedOff by remember { mutableStateOf<String?>(null) }
    val dialogue by produceState<com.saiyanhabits.domain.MentorDialogue?>(null) { value = app.dialogue.all()[Mentor.PICCOLO] }
    val total = minutes * 60f

    fun finish(seconds: Long) {
        stage = Stage.DONE
        scope.launch {
            best = app.gameRepository.bests.first()[MiniGame.MEDITATION]
            result = if (seconds >= Breathing.MIN_COUNTED_SECONDS) app.gameRepository.record(MiniGame.MEDITATION, seconds, pattern.name) else null
            openHabits = app.habitRepository.openToday()
        }
    }

    // Keep the screen on while breathing.
    val view = LocalView.current
    DisposableEffect(stage) {
        view.keepScreenOn = stage == Stage.BREATHING
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(stage) {
        if (stage != Stage.BREATHING) return@LaunchedEffect
        elapsed = 0f
        val start = withFrameNanos { it }
        var lastPhase = Breathing.momentAt(pattern, 0f).phase
        if (vibrate) pulse(context)
        while (stage == Stage.BREATHING) {
            val now = withFrameNanos { it }
            elapsed = (now - start) / 1_000_000_000f
            val phase = Breathing.momentAt(pattern, elapsed).phase
            if (phase != lastPhase) {
                lastPhase = phase
                if (vibrate) pulse(context)
            }
            if (elapsed >= total) finish(total.toLong())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07121F), Color(0xFF0E2A1A)))),
    ) {
        IconButton(onClick = {
            if (stage == Stage.BREATHING) finish(elapsed.toLong()) else onBack()
        }, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = if (stage == Stage.BREATHING) "End early" else "Back", tint = Color.White)
        }
        when (stage) {
            Stage.SETUP -> Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(Mentor.PICCOLO, Modifier.size(72.dp))
                    Text(
                        dialogue?.game?.firstOrNull() ?: "Sit. Breathe.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
                TabLabel("Rhythm")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreathPattern.entries.forEach { p ->
                        ChoiceTile(pattern == p, { pattern = p }, p.title, p.caption, Modifier.weight(1f))
                    }
                }
                TabLabel("Length")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lengths.forEach { m ->
                        ChoiceTile(minutes == m, { minutes = m }, "$m", if (m == 1) "minute" else "minutes", Modifier.weight(1f))
                    }
                }
                DbzPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Vibrate on each breath", style = MaterialTheme.typography.titleMedium)
                            Text("So you can keep your eyes closed.", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                        }
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                    }
                }
                DbzPrimaryButton(onClick = { stage = Stage.BREATHING }, modifier = Modifier.fillMaxWidth()) { Text("Begin") }
                Spacer(Modifier.height(16.dp))
            }

            Stage.BREATHING -> {
                val moment = Breathing.momentAt(pattern, elapsed)
                Column(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2 * (0.42f + 0.55f * moment.fullness)
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color(0xCC8CFF6A), Color(0x448CFF6A), Color.Transparent),
                                    center, r,
                                ),
                                r,
                            )
                            drawCircle(Color(0x668CFF6A), r * 0.62f, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        }
                        MentorPortrait(Mentor.PICCOLO, Modifier.size(110.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    OutlinedText(moment.phase.label.uppercase(), MaterialTheme.typography.headlineMedium, outlineWidth = 4.dp)
                    Text("${moment.phaseSecondsLeft}", color = Color(0xFF8CFF6A), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    val left = (total - elapsed).coerceAtLeast(0f).toInt()
                    Text("${left / 60}:${(left % 60).toString().padStart(2, '0')} left", color = SaiyanColors.TextDim)
                    Text(
                        dialogue?.game?.let { lines -> lines.getOrNull(1 + (elapsed / 45f).toInt() % (lines.size - 1).coerceAtLeast(1)) } ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            }

            Stage.DONE -> Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val seconds = elapsed.coerceAtMost(total).toLong()
                GameResultCard(
                    game = MiniGame.MEDITATION,
                    headline = if (seconds >= total.toLong()) "Well breathed" else "Ended early",
                    score = seconds,
                    best = best,
                    result = result,
                    onAgain = { stage = Stage.SETUP },
                    onDone = onBack,
                    extra = {
                        Text(
                            if (seconds < Breathing.MIN_COUNTED_SECONDS) "Sessions under a minute aren't counted."
                            else dialogue?.gameDone?.randomOrNull() ?: "",
                            color = Color.White,
                        )
                    },
                )
                if (checkedOff != null) {
                    Text("Checked off “$checkedOff”.", color = SaiyanColors.Bean, style = MaterialTheme.typography.titleSmall)
                } else if (openHabits.isNotEmpty() && seconds >= Breathing.MIN_COUNTED_SECONDS) {
                    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Blue) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TabLabel("Check off a habit?")
                            Text("If this session was one of today's habits, tick it off here.", color = SaiyanColors.TextDim)
                            openHabits.forEach { habit ->
                                DbzSecondaryButton(onClick = {
                                    scope.launch {
                                        if (app.habitRepository.complete(habit, LocalDate.now(), Gravity.NORMAL) > 0) {
                                            app.reminders.onHabitCompleted(habit.id)
                                            checkedOff = habit.name
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(habit.name) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** One soft pulse. */
private fun pulse(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(60, 80.coerceAtMost(255)))
}
