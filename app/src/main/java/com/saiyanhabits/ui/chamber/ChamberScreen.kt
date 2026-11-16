package com.saiyanhabits.ui.chamber

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.GameConfig
import com.saiyanhabits.chamber.ChamberFollowUp
import com.saiyanhabits.chamber.ChamberOutcome
import com.saiyanhabits.chamber.ChamberSession
import com.saiyanhabits.chamber.ChamberState
import com.saiyanhabits.chamber.ChamberTarget
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.delay

/** The Time Chamber tab: set up a focus session, watch it count down, read what it logged. */
@Composable
fun ChamberScreen(viewModel: ChamberViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "Time chamber",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        when (val current = state) {
            ChamberState.Idle -> targets?.let { Setup(it, onStart = viewModel::start) }
            is ChamberState.Running -> Running(current.session, onGiveUp = viewModel::giveUp)
            is ChamberState.Finished -> Finished(
                session = current.session,
                outcome = current.outcome,
                onBombAmount = viewModel::logBombAmount,
                onMilestoneFinished = viewModel::answerMilestoneFinished,
                onDone = viewModel::dismissResult,
            )
        }
    }
}

@Composable
private fun Setup(targets: ChamberTargets, onStart: (Int, ChamberTarget) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(GameConfig.FOCUS_SESSION_MINUTES.first()) }
    // Remembered by type and id, and looked up again each time, so a target that
    // has since been finished or deleted drops out of the selection.
    var chosenKey by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = targets.all.firstOrNull { it.key == chosenKey }

    val context = LocalContext.current
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Text(
        "A focus session spent on one thing. When the timer ends it logs your progress there, " +
            "worth ${GameConfig.FOCUS_POINT_MULTIPLIER}x the usual points.",
        style = MaterialTheme.typography.bodyMedium,
        color = SaiyanColors.TextDim,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    SectionLabel("Length", Modifier.padding(start = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameConfig.FOCUS_SESSION_MINUTES.forEach { option ->
            ChoiceTile(
                selected = minutes == option,
                onClick = { minutes = option },
                title = "$option min",
                caption = if (option == GameConfig.FOCUS_SESSION_MINUTES.first()) "One round" else "Deep work",
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionLabel("Train on", Modifier.padding(start = 4.dp, top = 6.dp))
    if (targets.isEmpty) {
        Text(
            "Nothing to train on yet. Add a habit for today, a saga or a spirit bomb first.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    TargetGroup("Today's habits", targets.habits, chosen, onChoose = { chosenKey = it.key })
    TargetGroup("Saga milestones", targets.milestones, chosen, onChoose = { chosenKey = it.key })
    TargetGroup("Spirit bombs", targets.bombs, chosen, onChoose = { chosenKey = it.key })

    Button(
        onClick = {
            val target = chosen ?: return@Button
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.areEnabled(context)) {
                // The countdown still runs if this is refused; it just has no notification.
                askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            onStart(minutes, target)
        },
        enabled = chosen != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(52.dp),
    ) { Text(if (chosen == null) "Pick what to train on" else "Enter the chamber · $minutes min") }
}

private val ChamberTarget.key: String get() = "${type.name}:$id"

@Composable
private fun TargetGroup(
    title: String,
    targets: List<ChamberTarget>,
    chosen: ChamberTarget?,
    onChoose: (ChamberTarget) -> Unit,
) {
    if (targets.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = SaiyanColors.TextDim,
        modifier = Modifier.padding(start = 4.dp),
    )
    targets.forEach { target ->
        val selected = target == chosen
        Surface(
            onClick = { onChoose(target) },
            shape = RoundedCornerShape(14.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else SaiyanColors.Panel,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SaiyanColors.Orange else SaiyanColors.Outline),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
        ) {
            Text(
                target.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Running(session: ChamberSession, onGiveUp: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val total = (session.endsAtMillis - session.startedAtMillis).coerceAtLeast(1)
    val left = (session.endsAtMillis - now).coerceIn(0, total)
    val secondsLeft = (left + 999) / 1000
    var confirmingGiveUp by rememberSaveable { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 14.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(SaiyanColors.PanelRaised, -90f, 360f, useCenter = false, topLeft = inset, size = arc, style = Stroke(stroke))
            drawArc(
                color = SaiyanColors.Blue,
                startAngle = -90f,
                sweepAngle = 360f * left / total,
                useCenter = false,
                topLeft = inset,
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                fontSize = 60.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = SaiyanColors.TextHigh,
            )
            Text("of ${session.minutes} minutes", style = MaterialTheme.typography.labelLarge, color = SaiyanColors.TextDim)
        }
    }
    Text(
        "Training on",
        style = MaterialTheme.typography.labelLarge,
        color = SaiyanColors.TextDim,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        session.target.label,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "The countdown keeps going with the screen off or the app closed. You'll get a notification when it ends.",
        style = MaterialTheme.typography.bodySmall,
        color = SaiyanColors.TextDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { confirmingGiveUp = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) { Text("Give up", color = SaiyanColors.Danger) }

    if (confirmingGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmingGiveUp = false },
            title = { Text("Leave the chamber early?") },
            text = { Text("The session ends now and nothing is logged.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingGiveUp = false
                    onGiveUp()
                }) { Text("Give up", color = SaiyanColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmingGiveUp = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Finished(
    session: ChamberSession,
    outcome: ChamberOutcome,
    onBombAmount: (Int) -> Unit,
    onMilestoneFinished: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Orange.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(20.dp)) {
            Text("Session complete", style = MaterialTheme.typography.headlineSmall, color = SaiyanColors.Orange)
            Text(
                "${session.minutes} minutes on ${session.target.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
            )
            Spacer(Modifier.height(4.dp))
            outcome.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }

            when (val followUp = outcome.followUp) {
                is ChamberFollowUp.BombAmount -> {
                    var amountText by rememberSaveable { mutableStateOf("") }
                    Spacer(Modifier.height(4.dp))
                    Text("How many ${followUp.unit} did you get through?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { text -> amountText = text.filter { it.isDigit() }.take(6) },
                            label = { Text(followUp.unit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { amountText.toIntOrNull()?.let(onBombAmount) },
                            enabled = (amountText.toIntOrNull() ?: 0) > 0,
                            modifier = Modifier.height(52.dp),
                        ) { Text("Log") }
                    }
                }
                is ChamberFollowUp.MilestoneFinished -> {
                    Spacer(Modifier.height(4.dp))
                    Text("Is \"${followUp.name}\" finished?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMilestoneFinished(false) }, modifier = Modifier.weight(1f)) {
                            Text("Not yet")
                        }
                        Button(onClick = { onMilestoneFinished(true) }, modifier = Modifier.weight(1f)) {
                            Text("Yes, hit")
                        }
                    }
                }
                null -> Unit
            }
        }
    }
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) { Text("Done") }
}
