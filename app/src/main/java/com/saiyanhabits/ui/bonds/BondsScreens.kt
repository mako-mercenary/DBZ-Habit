package com.saiyanhabits.ui.bonds

import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.ScreenTitle
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.DbzPanel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.Bond
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.BondScene
import com.saiyanhabits.domain.HabitCategory
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.Technique
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.ScriptLines
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color

/** What kind of habit a mentor trains, in words. */
internal val Mentor.trains: String
    get() = HabitCategory.entries.firstOrNull { it.mentor == this }?.label ?: "Your hardest habit"

/** The Bonds tab: every mentor and how close you are. */
@Composable
fun BondsScreen(state: BondsUiState?, onOpenMentor: (Mentor) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item(key = "title") {
            Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                ScreenTitle("Bonds")
                Text(
                    "Checking off a habit builds the bond with the mentor who trains it. " +
                        "New bond levels unlock scenes and techniques.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SaiyanColors.TextDim,
                )
            }
        }
        if (state == null) return@LazyColumn
        items(state.bonds, key = { it.mentor.name }) { bond ->
            BondCard(bond, onClick = { onOpenMentor(bond.mentor) })
        }
    }
}

@Composable
private fun BondCard(bond: Bond, onClick: () -> Unit) {
    val mentor = bond.mentor
    DbzPanel(
        watermark = { MentorWatermark(mentor) },
        style = PanelStyle.Blue,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            MentorPortrait(mentor, Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mentor.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("Level ${bond.level}", style = MaterialTheme.typography.labelLarge, color = mentor.color)
                }
                Text(mentor.trains, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                Spacer(Modifier.height(8.dp))
                BondProgress(bond)
            }
        }
    }
}

@Composable
private fun BondProgress(bond: Bond) {
    MeterBar(BondRules.progressInLevel(bond.xp), bond.mentor.color)
    Spacer(Modifier.height(4.dp))
    val toNext = BondRules.xpToNextLevel(bond.xp)
    Text(
        if (toNext == null) "Highest bond level" else "$toNext XP to level ${bond.level + 1}",
        style = MaterialTheme.typography.bodySmall,
        color = SaiyanColors.TextDim,
    )
}

/** One mentor: bond level, techniques and scenes. */
@Composable
fun MentorScreen(
    mentor: Mentor,
    state: BondsUiState,
    onOpenScene: (level: Int) -> Unit,
    onUseKaioken: () -> Unit,
    onUseDayOff: (habitId: Long) -> Unit,
    onTrain: () -> Unit,
    onCall: () -> Unit,
    onCallLog: () -> Unit,
    onBack: () -> Unit,
) {
    val bond = state.bond(mentor)
    var confirming by rememberSaveable { mutableStateOf<Technique?>(null) }

    SubScreen(title = mentor.displayName, onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MentorPortrait(mentor, Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Bond level ${bond.level}", style = MaterialTheme.typography.headlineSmall, color = mentor.color)
                Text(mentor.trains, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
                Spacer(Modifier.height(8.dp))
                BondProgress(bond)
            }
        }
        Text(
            when {
                mentor == Mentor.VEGETA && bond.xp == 0 ->
                    "Flag a habit as your hardest and Vegeta takes it over. Each check-off gives " +
                        "${GameConfig.BOND_XP_PER_CHECK_OFF} bond XP."
                bond.level >= GameConfig.ASSIST_MIN_BOND_LEVEL ->
                    "${mentor.displayName} joins your boss fights and adds bonus damage to every hit."
                else ->
                    "Each check-off gives ${GameConfig.BOND_XP_PER_CHECK_OFF} bond XP. " +
                        "At level ${GameConfig.ASSIST_MIN_BOND_LEVEL} ${mentor.displayName} joins your boss fights."
            },
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )

        val game = com.saiyanhabits.domain.MiniGame.of(mentor)
        DbzPrimaryButton(onClick = onTrain, modifier = Modifier.fillMaxWidth()) {
            Text("Train: ${game.title}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DbzSecondaryButton(onClick = onCall, modifier = Modifier.weight(1f)) { Text("Call ${mentor.displayName}") }
            DbzSecondaryButton(onClick = onCallLog, modifier = Modifier.weight(1f)) { Text("Call log") }
        }
        Text(
            "One call each day: +${com.saiyanhabits.GameConfig.CALL_BOND_XP} bond.",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )

        Spacer(Modifier.height(6.dp))
        SectionLabel("Techniques")
        Technique.of(mentor).forEach { technique ->
            TechniqueCard(
                technique = technique,
                unlocked = bond.level >= technique.bondLevel,
                usedToday = state.usedToday(technique),
                cooldownDaysLeft = state.cooldownDaysLeft(technique),
                canUse = technique != Technique.DAY_OFF || state.excusable.isNotEmpty(),
                onUse = { confirming = technique },
            )
        }

        Spacer(Modifier.height(6.dp))
        SectionLabel("Scenes")
        for (level in 2..BondRules.MAX_LEVEL) {
            val scene = state.dialogues[mentor]?.scene(level)
            val unlocked = scene != null && BondRules.isSceneUnlocked(level, bond.level)
            SceneRow(
                level = level,
                title = if (unlocked) scene.title else "Unlocks at bond level $level",
                unlocked = unlocked,
                onClick = { onOpenScene(level) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    when (confirming) {
        Technique.KAIOKEN -> AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Use Kaio-ken?") },
            text = { Text(Technique.KAIOKEN.description) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onUseKaioken()
                }) { Text("Use it today") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Save it") } },
        )
        Technique.DAY_OFF -> AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Excuse which habit?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("It stops counting for today. Ready again after ${Technique.DAY_OFF.cooldownDays} days.")
                    state.excusable.forEach { habit ->
                        DbzPanel(
                            style = PanelStyle.Blue,
                            onClick = {
                                confirming = null
                                onUseDayOff(habit.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                habit.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
        else -> Unit
    }
}

@Composable
private fun TechniqueCard(
    technique: Technique,
    unlocked: Boolean,
    usedToday: Boolean,
    cooldownDaysLeft: Int,
    canUse: Boolean,
    onUse: () -> Unit,
) {
    val tint = technique.mentor.color
    DbzPanel(
        style = if (unlocked) PanelStyle.Blue else PanelStyle.Dim,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    technique.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) SaiyanColors.TextHigh else SaiyanColors.TextDim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (unlocked) "Unlocked" else "Bond level ${technique.bondLevel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) tint else SaiyanColors.TextDim,
                )
            }
            Text(technique.description, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
            if (unlocked && technique.isActivated) {
                Spacer(Modifier.height(8.dp))
                when {
                    usedToday && technique == Technique.KAIOKEN ->
                        Text("Active for the rest of today.", style = MaterialTheme.typography.labelLarge, color = tint)
                    cooldownDaysLeft > 0 -> Text(
                        if (cooldownDaysLeft == 1) "Ready again tomorrow." else "Ready again in $cooldownDaysLeft days.",
                        style = MaterialTheme.typography.labelLarge,
                        color = SaiyanColors.TextDim,
                    )
                    !canUse -> Text(
                        "Nothing left to excuse today.",
                        style = MaterialTheme.typography.labelLarge,
                        color = SaiyanColors.TextDim,
                    )
                    else -> DbzPrimaryButton(onClick = onUse) { Text("Use") }
                }
            }
        }
    }
}

@Composable
private fun SceneRow(level: Int, title: String, unlocked: Boolean, onClick: () -> Unit) {
    DbzPanel(
        style = if (unlocked) PanelStyle.Blue else PanelStyle.Dim,
        onClick = onClick,
        enabled = unlocked,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "$level",
                style = MaterialTheme.typography.titleMedium,
                color = SaiyanColors.TextDim,
                modifier = Modifier.width(32.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (unlocked) SaiyanColors.TextHigh else SaiyanColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A bond scene, read top to bottom like a script. */
@Composable
fun SceneScreen(mentor: Mentor, scene: BondScene, playerForm: Int, onBack: () -> Unit) {
    SubScreen(title = scene.title, onBack = onBack) {
        Text(
            "${mentor.displayName} · Bond level ${scene.level}",
            style = MaterialTheme.typography.labelLarge,
            color = mentor.color,
        )
        ScriptLines(scene.lines, playerForm)
        Spacer(Modifier.height(16.dp))
    }
}
