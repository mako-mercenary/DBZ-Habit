package com.saiyanhabits.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saiyanhabits.domain.SceneLine
import com.saiyanhabits.domain.ScriptNode
import com.saiyanhabits.domain.ScriptRunner
import com.saiyanhabits.domain.ScriptStats
import com.saiyanhabits.domain.ScriptStep

/**
 * A branching conversation, revealed one line at a time. [picked] are the
 * replies chosen so far and [shown] how many lines are on screen; the caller
 * keeps both, so the conversation survives recomposition. [onFinished] is
 * offered once every line has been shown.
 */
@Composable
fun ColumnScope.Conversation(
    script: List<ScriptNode>,
    stats: ScriptStats,
    picked: List<Int>,
    shown: Int,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onFinished: (() -> Unit)?,
    finishLabel: String = "Hang up",
    playerForm: Int = 0,
) {
    val steps = ScriptRunner.play(script, picked, stats)
    val lines = steps.mapNotNull { step ->
        when (step) {
            is ScriptStep.Say -> SceneLine(step.speaker, step.text)
            is ScriptStep.Picked -> SceneLine("YOU", step.text)
            is ScriptStep.Ask -> null
        }
    }
    val visible = shown.coerceIn(0, lines.size)
    ScriptLines(lines.take(visible), playerForm)
    val ask = steps.lastOrNull() as? ScriptStep.Ask
    when {
        visible < lines.size -> DbzSecondaryButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
        ask != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ask.options.forEachIndexed { i, option ->
                DbzPrimaryButton(onClick = { onPick(i) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
            }
        }
        onFinished != null -> DbzPrimaryButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(finishLabel) }
    }
}

/** How many lines a conversation shows in full, for jumping straight to the end of one already finished. */
fun conversationLength(script: List<ScriptNode>, stats: ScriptStats, picked: List<Int>): Int =
    ScriptRunner.play(script, picked, stats).count { it !is ScriptStep.Ask }
