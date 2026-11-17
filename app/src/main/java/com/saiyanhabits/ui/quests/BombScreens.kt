package com.saiyanhabits.ui.quests

import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.DbzPrimaryButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.BombLogEntity
import com.saiyanhabits.data.SpiritBombEntity
import com.saiyanhabits.domain.QuestStatus
import com.saiyanhabits.domain.SpiritBombRules
import com.saiyanhabits.ui.components.BottomActionButton
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SpiritBombOrb
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.launch

private const val MAX_NAME_LENGTH = 60
private const val MAX_UNIT_LENGTH = 20
private const val MAX_AMOUNT_DIGITS = 6
private val UnitPresets = listOf("minutes", "pages", "sessions")

private fun String.digitsOnly(): String = filter { it.isDigit() }.take(MAX_AMOUNT_DIGITS)

/** Name a spirit bomb and say how much it takes to fill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BombEditScreen(
    onCreate: (name: String, unit: String, target: Int, size: Int) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(UnitPresets.first()) }
    var size by rememberSaveable { mutableIntStateOf(2) }
    val target = targetText.toIntOrNull() ?: 0

    SubScreen(
        title = "New spirit bomb",
        onBack = onBack,
        bottomBar = {
            BottomActionButton(
                text = "Start gathering energy",
                enabled = name.isNotBlank() && target > 0 && unit.isNotBlank(),
                onClick = { onCreate(name, unit, target, size) },
            )
        },
    ) {
        SectionLabel("Task")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            placeholder = { Text("e.g. Read the big history book") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))
        SectionLabel("How much to fill it")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it.digitsOnly() },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it.take(MAX_UNIT_LENGTH) },
                label = { Text("Counted in") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitPresets.forEach { preset ->
                FilterChip(selected = unit == preset, onClick = { unit = preset }, label = { Text(preset) })
            }
        }
        Text(
            "Time chamber sessions log themselves to bombs counted in minutes or sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )

        Spacer(Modifier.height(6.dp))
        SectionLabel("Size")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in SpiritBombRules.MIN_SIZE..SpiritBombRules.MAX_SIZE) {
                ChoiceTile(
                    selected = size == option,
                    onClick = { size = option },
                    title = SpiritBombRules.sizeName(option),
                    caption = "${SpiritBombRules.sizePoints(option)} points",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "The points are paid out bit by bit as it fills, plus " +
                "${GameConfig.SPIRIT_BOMB_THROW_BONUS_PERCENT}% more when you throw it.",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** A spirit bomb: the growing ball, logging progress, and throwing it once full. */
@Composable
fun BombDetailScreen(
    bomb: SpiritBombEntity,
    lastLog: BombLogEntity?,
    onLog: (Int) -> Unit,
    onUndoLastLog: () -> Unit,
    onThrow: (onThrown: (bonus: Int) -> Unit) -> Unit,
    onAbandon: () -> Unit,
    onBack: () -> Unit,
) {
    val active = bomb.status == QuestStatus.ACTIVE
    val fraction = SpiritBombRules.fraction(bomb.progress, bomb.target)
    val ready = active && SpiritBombRules.isReadyToThrow(bomb.progress, bomb.target)

    var amountText by rememberSaveable { mutableStateOf("") }
    var confirmingAbandon by rememberSaveable { mutableStateOf(false) }
    var thrownBonus by rememberSaveable { mutableStateOf<Int?>(null) }

    // 0 while the bomb sits there, runs to 1 as it flies off the top of the screen.
    val flight = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    SubScreen(
        title = bomb.name,
        onBack = onBack,
        actions = {
            if (active) {
                TextButton(onClick = { confirmingAbandon = true }) { Text("Abandon", color = SaiyanColors.Danger) }
            }
        },
        bottomBar = {
            if (ready && thrownBonus == null) {
                BottomActionButton(
                    text = "Throw it",
                    enabled = !flight.isRunning,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            flight.animateTo(1f, tween(GameConfig.BOMB_THROW_MILLIS, easing = FastOutLinearInEasing))
                            onThrow { thrownBonus = it }
                        }
                    },
                )
            }
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                // A tap skips the rest of the throw.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = flight.isRunning,
                ) { scope.launch { flight.snapTo(1f) } },
        ) {
            SpiritBombOrb(
                fraction = if (active) fraction else 1f,
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        val t = flight.value
                        translationY = -size.height * 3f * t * t
                        val scale = 1f - 0.7f * t
                        scaleX = scale
                        scaleY = scale
                        alpha = if (active || t > 0f) 1f - t * t else 0.35f
                    },
            )
        }
        Text(
            "${bomb.progress} / ${bomb.target} ${bomb.unit}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        MeterBar(fraction, SaiyanColors.Blue)
        Text(
            when (bomb.status) {
                QuestStatus.DONE -> "Thrown on ${formatDay(bomb.finishedAtEpochDay)}"
                QuestStatus.ABANDONED -> "Abandoned on ${formatDay(bomb.finishedAtEpochDay)}"
                QuestStatus.ACTIVE -> if (ready) {
                    "Fully charged. Throw it to finish the task."
                } else {
                    "${(fraction * 100).toInt()}% charged · ${SpiritBombRules.sizeName(bomb.size)} · " +
                        "${SpiritBombRules.sizePoints(bomb.size)} points"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) SaiyanColors.Orange else SaiyanColors.TextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        if (active && !ready) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("Log progress")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.digitsOnly() },
                    label = { Text(bomb.unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                DbzPrimaryButton(
                    onClick = {
                        amountText.toIntOrNull()?.let(onLog)
                        amountText = ""
                    },
                    enabled = (amountText.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.height(52.dp),
                ) { Text("Log") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 25).forEach { quick ->
                    DbzSecondaryButton(onClick = { onLog(quick) }, modifier = Modifier.weight(1f)) { Text("+$quick") }
                }
            }
        }
        if (active && lastLog != null && !flight.isRunning && thrownBonus == null) {
            TextButton(onClick = onUndoLastLog, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Undo last log (${lastLog.amount} ${bomb.unit})", color = SaiyanColors.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    thrownBonus?.let { bonus ->
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Spirit bomb thrown") },
            text = { Text("\"${bomb.name}\" is finished. +$bonus power for the throw.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Done") } },
        )
    }

    if (confirmingAbandon) {
        AlertDialog(
            onDismissRequest = { confirmingAbandon = false },
            title = { Text("Abandon this spirit bomb?") },
            text = { Text("\"${bomb.name}\" leaves your list. Points from progress already logged stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingAbandon = false
                    onAbandon()
                }) { Text("Abandon", color = SaiyanColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmingAbandon = false }) { Text("Keep it") } },
        )
    }
}
