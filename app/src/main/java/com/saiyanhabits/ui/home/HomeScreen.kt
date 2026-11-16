package com.saiyanhabits.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.R
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.mentorFor
import com.saiyanhabits.ui.components.EmptyPanel
import com.saiyanhabits.ui.components.ScouterReadout
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderDate = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    val state = uiState ?: return

    val haptics = LocalHapticFeedback.current
    var choosingGravityFor by remember { mutableStateOf<HabitEntity?>(null) }
    var undoing by remember { mutableStateOf<TodayHabit?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "date") {
            SectionLabel(state.date.format(HeaderDate), Modifier.padding(start = 4.dp))
        }
        item(key = "power") {
            ScouterReadout(powerLevel = state.powerLevel, gains = viewModel.gains)
        }
        item(key = "progress") {
            TodayProgress(done = state.doneCount, total = state.today.size)
        }
        when {
            state.hasNoHabits -> item(key = "empty") {
                EmptyPanel(
                    title = "Your training starts here",
                    body = "Add a habit, check it off each day, and watch your power level climb.",
                    actionLabel = "Add your first habit",
                    onAction = onAddHabit,
                )
            }
            state.today.isEmpty() -> item(key = "rest") {
                EmptyPanel(
                    title = "Rest day",
                    body = "Nothing is scheduled for today.",
                )
            }
            else -> items(state.today, key = { it.habit.id }) { entry ->
                TodayHabitRow(
                    entry = entry,
                    onClick = {
                        if (entry.isDone) undoing = entry else choosingGravityFor = entry.habit
                    },
                )
            }
        }
    }

    choosingGravityFor?.let { habit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { choosingGravityFor = null },
            sheetState = sheetState,
            containerColor = SaiyanColors.Panel,
        ) {
            GravityChooser(
                habit = habit,
                onChoose = { gravity ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.complete(habit, gravity)
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { choosingGravityFor = null }
                },
            )
        }
    }

    undoing?.let { entry ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text("Undo this check-off?") },
            text = {
                Text(
                    "\"${entry.habit.name}\" goes back to not done, and the " +
                        "${entry.completion?.points ?: 0} points it gave are removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.undo(entry.habit)
                    undoing = null
                }) { Text("Undo check-off") }
            },
            dismissButton = {
                TextButton(onClick = { undoing = null }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun TodayProgress(done: Int, total: Int) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Today's training", Modifier.weight(1f))
            if (total > 0) {
                val allDone = done == total
                Text(
                    text = if (allDone) "ALL DONE" else "$done / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (allDone) SaiyanColors.Orange else SaiyanColors.TextHigh,
                )
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                color = SaiyanColors.Orange,
                trackColor = SaiyanColors.PanelRaised,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun TodayHabitRow(entry: TodayHabit, onClick: () -> Unit) {
    val habit = entry.habit
    val mentor = mentorFor(habit.category, habit.isHardest)
    val done = entry.completion
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (done != null) SaiyanColors.Panel.copy(alpha = 0.6f) else SaiyanColors.Panel,
        border = BorderStroke(1.dp, if (done != null) SaiyanColors.Orange.copy(alpha = 0.45f) else SaiyanColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 40.dp)
                    .clip(CircleShape)
                    .background(mentor.color.copy(alpha = if (done != null) 0.45f else 1f)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done != null) SaiyanColors.TextDim else SaiyanColors.TextHigh,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (done != null) {
                        "${done.gravity.label} · +${done.points}"
                    } else {
                        val base = PowerCalculator.completionPoints(habit.difficulty, Gravity.NORMAL)
                        "${mentor.displayName} · +$base and up"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done != null) SaiyanColors.Orange else SaiyanColors.TextDim,
                )
            }
            Spacer(Modifier.width(12.dp))
            CheckMark(checked = done != null, description = if (done != null) "Done" else "Not done")
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean, description: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(SaiyanColors.Orange)
                } else {
                    Modifier.border(2.dp, SaiyanColors.TextDim, CircleShape)
                },
            ),
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun GravityChooser(habit: HabitEntity, onChoose: (Gravity) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Text("How did you train?", style = MaterialTheme.typography.headlineSmall)
        Text(
            habit.name,
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Gravity.entries.forEach { gravity ->
            val accent = when (gravity) {
                Gravity.NORMAL -> SaiyanColors.Blue
                Gravity.X10 -> SaiyanColors.Orange
                Gravity.X100 -> SaiyanColors.Danger
            }
            Surface(
                onClick = { onChoose(gravity) },
                shape = RoundedCornerShape(16.dp),
                color = SaiyanColors.PanelRaised,
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(gravity.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            gravity.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = SaiyanColors.TextDim,
                        )
                    }
                    Text(
                        "+${PowerCalculator.completionPoints(habit.difficulty, gravity)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                }
            }
        }
    }
}
