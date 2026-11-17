package com.saiyanhabits.ui.quests

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.MilestoneDraft
import com.saiyanhabits.data.MilestoneEntity
import com.saiyanhabits.data.SagaWithMilestones
import com.saiyanhabits.data.StrikeResult
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.QuestStatus
import com.saiyanhabits.domain.SagaRules
import com.saiyanhabits.ui.components.BossFigure
import com.saiyanhabits.ui.components.BottomActionButton
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color

private const val MAX_NAME_LENGTH = 60

/** Saves a list of milestone drafts across rotation and process death. */
private val DraftListSaver = listSaver<MutableList<MilestoneDraft>, Any>(
    save = { drafts -> drafts.flatMap { listOf(it.name, it.weight) } },
    restore = { flat ->
        flat.chunked(2).map { MilestoneDraft(it[0] as String, it[1] as Int) }.toMutableStateList()
    },
)

/** Name a saga, pick its boss and list its milestones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SagaEditScreen(
    onCreate: (name: String, boss: Boss, milestones: List<MilestoneDraft>) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var boss by rememberSaveable { mutableStateOf(Boss.RADITZ) }
    val drafts = rememberSaveable(saver = DraftListSaver) { mutableListOf<MilestoneDraft>().toMutableStateList() }
    val totalHp = drafts.sumOf { it.weight }

    SubScreen(
        title = "New saga",
        onBack = onBack,
        bottomBar = {
            BottomActionButton(
                text = if (drafts.isEmpty()) "Add a milestone to begin" else "Start the fight · HP $totalHp",
                enabled = name.isNotBlank() && drafts.isNotEmpty(),
                onClick = { onCreate(name, boss, drafts.toList()) },
            )
        },
    ) {
        SectionLabel("Goal")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            placeholder = { Text("e.g. Finish the garden shed") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))
        SectionLabel("Boss")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            Boss.entries.forEach { option ->
                val selected = boss == option
                Surface(
                    onClick = { boss = option },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) option.color.copy(alpha = 0.16f) else SaiyanColors.Panel,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) option.color else SaiyanColors.Outline),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        BossFigure(option, Modifier.size(56.dp))
                        Text(option.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionLabel("Milestones")
        Text(
            "Each milestone is a hit. Its damage is how big a step it is; the boss's HP is all of them added up.",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )
        drafts.forEachIndexed { index, draft ->
            MilestoneRow(
                name = draft.name,
                detail = "${draft.weight} damage",
                trailing = { TextButton(onClick = { drafts.removeAt(index) }) { Text("Remove", color = SaiyanColors.TextDim) } },
            )
        }
        MilestoneInput(onAdd = { drafts.add(it) })
        Spacer(Modifier.height(16.dp))
    }
}

/** A text field, a damage picker and an Add button for one new milestone. */
@Composable
private fun MilestoneInput(onAdd: (MilestoneDraft) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableIntStateOf(1) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                placeholder = { Text("Next milestone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Damage", style = MaterialTheme.typography.labelMedium, color = SaiyanColors.TextDim)
                for (option in 1..GameConfig.MAX_MILESTONE_WEIGHT) {
                    FilterChip(
                        selected = weight == option,
                        onClick = { weight = option },
                        label = { Text("$option") },
                    )
                }
            }
            Button(
                onClick = {
                    onAdd(MilestoneDraft(name.trim(), weight))
                    name = ""
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Add milestone") }
        }
    }
}

@Composable
private fun MilestoneRow(
    name: String,
    detail: String,
    done: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done) SaiyanColors.TextDim else SaiyanColors.TextHigh,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
            }
            trailing()
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val border = BorderStroke(1.dp, SaiyanColors.Outline)
    val color = if (done) SaiyanColors.Panel.copy(alpha = 0.5f) else SaiyanColors.Panel
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, border = border, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** The mentors fighting alongside, and what they add to each hit. */
@Composable
private fun AssistRow(perks: Perks) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Gold.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            perks.assistMentors.forEach { MentorPortrait(it, Modifier.size(32.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Assists: " + perks.assistMentors.joinToString(", ") { it.displayName },
                    style = MaterialTheme.typography.labelLarge,
                    color = SaiyanColors.Gold,
                )
                Text(
                    "+${perks.assistDamage} bonus damage on every hit. It adds points, not speed: " +
                        "the boss still falls when the last milestone lands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
            }
        }
    }
}

/** The boss fight: the boss, its HP, and the milestones left to land. Also shows a finished saga. */
@Composable
fun SagaDetailScreen(
    saga: SagaWithMilestones,
    perks: Perks,
    onStrike: (milestoneId: Long, onHit: (StrikeResult) -> Unit) -> Unit,
    onAddMilestone: (MilestoneDraft) -> Unit,
    onRemoveMilestone: (Long) -> Unit,
    onAbandon: () -> Unit,
    onBack: () -> Unit,
) {
    val boss = saga.saga.boss
    val active = saga.saga.status == QuestStatus.ACTIVE
    val states = saga.milestones.map { it.toState() }
    val totalHp = SagaRules.totalHp(states)
    val remainingHp = SagaRules.remainingHp(states)
    val open = saga.milestones.filterNot { it.isDone }
    val landed = saga.milestones.filter { it.isDone }

    var striking by remember { mutableStateOf<MilestoneEntity?>(null) }
    var confirmingAbandon by rememberSaveable { mutableStateOf(false) }
    var addingMilestone by rememberSaveable { mutableStateOf(false) }

    // The hit animation: 1 at rest, runs 0 -> 1 after each strike.
    val hit = remember { Animatable(1f) }
    var lastHit by remember { mutableStateOf<StrikeResult?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(lastHit) {
        if (lastHit == null) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        hit.snapTo(0f)
        hit.animateTo(1f, tween(GameConfig.BOSS_HIT_MILLIS, easing = LinearEasing))
    }

    SubScreen(
        title = saga.saga.name,
        onBack = onBack,
        actions = {
            if (active) {
                TextButton(onClick = { confirmingAbandon = true }) { Text("Abandon", color = SaiyanColors.Danger) }
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            BossFigure(boss, Modifier.size(200.dp), hit = hit.value, defeated = !active)
            val shown = lastHit
            if (shown != null && hit.value < 1f) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 32.dp)
                        .graphicsLayer {
                            translationY = 60.dp.toPx() * (1f - hit.value)
                            alpha = ((1f - hit.value) / 0.3f).coerceIn(0f, 1f)
                        },
                ) {
                    Text("-${shown.damage}", style = MaterialTheme.typography.displaySmall, color = SaiyanColors.Danger)
                    if (shown.assistDamage > 0) {
                        Text(
                            "+${shown.assistDamage} assist",
                            style = MaterialTheme.typography.titleMedium,
                            color = SaiyanColors.Gold,
                        )
                    }
                }
            }
        }
        Text(
            boss.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = if (active) boss.color else SaiyanColors.TextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        when (saga.saga.status) {
            QuestStatus.ACTIVE -> {
                MeterBar(if (totalHp == 0) 0f else remainingHp.toFloat() / totalHp, boss.color)
                Text(
                    "HP $remainingHp / $totalHp",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            QuestStatus.DONE -> Text(
                "Defeated on ${formatDay(saga.saga.finishedAtEpochDay)} · +${saga.saga.victoryBonus} victory bonus",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.Orange,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            QuestStatus.ABANDONED -> Text(
                "Abandoned on ${formatDay(saga.saga.finishedAtEpochDay)}",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (active && perks.assistMentors.isNotEmpty()) {
            AssistRow(perks)
        }

        if (active) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("Milestones to land")
            open.forEach { milestone ->
                val charged = milestone.focusSessions > 0
                val points = SagaRules.milestonePoints(milestone.weight, charged, perks)
                val assist = if (perks.assistDamage > 0) " +${perks.assistDamage} assist" else ""
                MilestoneRow(
                    name = milestone.name,
                    detail = "${milestone.weight} damage$assist · +$points" +
                        if (charged) " (charged in the time chamber)" else "",
                    onClick = { striking = milestone },
                    trailing = {
                        if (open.size > 1) {
                            TextButton(onClick = { onRemoveMilestone(milestone.id) }) {
                                Text("Remove", color = SaiyanColors.TextDim)
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        Button(onClick = { striking = milestone }) { Text("Hit") }
                        Spacer(Modifier.width(6.dp))
                    },
                )
            }
            if (addingMilestone) {
                MilestoneInput(onAdd = {
                    onAddMilestone(it)
                    addingMilestone = false
                })
            } else {
                OutlinedButton(onClick = { addingMilestone = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add a milestone")
                }
            }
        }

        if (landed.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("Hits landed")
            landed.forEach { milestone ->
                MilestoneRow(
                    name = milestone.name,
                    detail = "${milestone.damageDealt} damage · +${milestone.points} · ${formatDay(milestone.completedAtEpochDay)}",
                    done = true,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    striking?.let { milestone ->
        val finishing = remainingHp - SagaRules.damageFor(milestone.weight) <= 0
        AlertDialog(
            onDismissRequest = { striking = null },
            title = { Text(if (finishing) "Land the final blow?" else "Land this hit?") },
            text = {
                Text(
                    "\"${milestone.name}\" is finished: ${milestone.weight} damage to ${boss.displayName}." +
                        if (finishing) " This wins the saga." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    striking = null
                    onStrike(milestone.id) { lastHit = it }
                }) { Text("Hit") }
            },
            dismissButton = { TextButton(onClick = { striking = null }) { Text("Not yet") } },
        )
    }

    if (confirmingAbandon) {
        AlertDialog(
            onDismissRequest = { confirmingAbandon = false },
            title = { Text("Abandon this saga?") },
            text = { Text("\"${saga.saga.name}\" leaves your active bosses. Points from hits already landed stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingAbandon = false
                    onAbandon()
                }) { Text("Abandon", color = SaiyanColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmingAbandon = false }) { Text("Keep fighting") } },
        )
    }
}
