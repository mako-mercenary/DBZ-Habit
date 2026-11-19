package com.saiyanhabits.ui.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.GameConfig
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.CallResult
import com.saiyanhabits.data.MentorCallEntity
import com.saiyanhabits.data.ReadyCall
import com.saiyanhabits.domain.CallKind
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.Conversation
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.EmptyPanel
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.components.conversationLength
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today's call with [mentor]: played a line at a time, replies picked as it goes. */
@Composable
fun CallScreen(mentor: Mentor, playerForm: Int, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf<ReadyCall?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Int>() }
    var shown by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<CallResult?>(null) }

    LaunchedEffect(mentor) {
        ready = app.callRepository.todaysCall(mentor)
        loaded = true
        Notifications.cancelCall(app)
    }

    SubScreen(title = "Call: ${mentor.displayName}", onBack = onBack) {
        val call = ready
        when {
            !loaded -> Unit
            call == null -> {
                EmptyPanel(
                    title = "Already talked today",
                    body = "${mentor.displayName} has nothing more to say until tomorrow. Past calls are in the call log.",
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(mentor, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        TabLabel(call.call.kind.label)
                        Text(call.stats.fill(call.call.title), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        if (call.ringing) {
                            Text("Picked up while ringing", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.Gold)
                        }
                    }
                }
                Conversation(
                    script = call.call.script,
                    stats = call.stats,
                    picked = picked,
                    shown = shown,
                    onPick = { i ->
                        picked += i
                        shown++
                    },
                    onNext = { shown++ },
                    onFinished = if (result == null) {
                        {
                            scope.launch {
                                result = app.callRepository.finish(call, picked.toList())
                                sounds?.play(SoundKey.CHECK_OFF, 0.6f)
                            }
                        }
                    } else null,
                    playerForm = playerForm,
                )
                result?.let { r ->
                    DbzPanel(modifier = Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Call ended", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                "+${r.bondXp} bond with ${mentor.displayName}" +
                                    (if (r.pickedUpQuickly) " (quick pickup bonus)" else "") +
                                    (r.bondLevelUp?.let { " · Bond level $it!" } ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                                color = SaiyanColors.Gold,
                            )
                        }
                    }
                    DbzPrimaryButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}

private val LogTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

/** Every call so far, to read again. */
@Composable
fun CallLogScreen(playerForm: Int, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val log by app.callRepository.log.collectAsStateWithLifecycle(initialValue = emptyList())
    var open by remember { mutableStateOf<MentorCallEntity?>(null) }
    val reading = open
    if (reading != null) {
        ReplayCall(reading, playerForm, onBack = { open = null })
        return
    }
    SubScreen(title = "Call log", onBack = onBack) {
        if (log.isEmpty()) {
            EmptyPanel(title = "No calls yet", body = "Call a mentor from their page in Bonds. One call each, every day.")
        }
        log.forEach { entry ->
            var title by remember(entry.id) { mutableStateOf<String?>(null) }
            var kind by remember(entry.id) { mutableStateOf<CallKind?>(null) }
            LaunchedEffect(entry.id) {
                val call = app.callRepository.book(entry.mentor)?.call(entry.callId)
                title = call?.title
                kind = call?.kind
            }
            DbzPanel(
                onClick = { open = entry },
                watermark = { MentorWatermark(entry.mentor) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(entry.mentor, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.mentor.displayName, style = MaterialTheme.typography.titleSmall, color = entry.mentor.color)
                        Text(
                            title?.replace(Regex("\\{[a-z_]+\\}"), "a habit") ?: "…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Text(
                            listOfNotNull(kind?.label, Instant.ofEpochMilli(entry.answeredAtMillis).atZone(ZoneId.systemDefault()).format(LogTime))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = SaiyanColors.TextDim,
                        )
                    }
                }
            }
        }
    }
}

/** A past call, shown in full with the replies you picked. Numbers are today's. */
@Composable
private fun ReplayCall(entry: MentorCallEntity, playerForm: Int, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val app = LocalContext.current.applicationContext as SaiyanApp
    var ready by remember { mutableStateOf<ReadyCall?>(null) }
    LaunchedEffect(entry.id) { ready = app.callRepository.replay(entry) }
    val picked = remember(entry.id) { entry.choices.split(",").mapNotNull { it.toIntOrNull() } }
    SubScreen(title = entry.mentor.displayName, onBack = onBack) {
        val call = ready ?: return@SubScreen
        Text(call.stats.fill(call.call.title), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Conversation(
            script = call.call.script,
            stats = call.stats,
            picked = picked,
            shown = conversationLength(call.call.script, call.stats, picked),
            onPick = {},
            onNext = {},
            onFinished = null,
            playerForm = playerForm,
        )
    }
}

/** The home screen's notice that a mentor is ringing; tap to pick up. */
@Composable
fun RingingCard(mentor: Mentor, onAnswer: () -> Unit) {
    DbzPanel(onClick = onAnswer, style = PanelStyle.Orange, watermark = { MentorWatermark(mentor) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MentorPortrait(mentor, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TabLabel("Incoming call")
                Text("${mentor.displayName} is calling. Tap to pick up.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(
                    "Pick up within ${GameConfig.CALL_PICKUP_WINDOW_MINUTES} minutes for +${GameConfig.CALL_QUICK_PICKUP_BONUS_XP} extra bond XP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.Gold,
                )
            }
        }
    }
}
