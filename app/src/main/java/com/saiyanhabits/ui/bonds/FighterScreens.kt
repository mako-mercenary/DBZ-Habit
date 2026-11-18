package com.saiyanhabits.ui.bonds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.FighterState
import com.saiyanhabits.domain.Forms
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.Stat
import com.saiyanhabits.domain.stats
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.PlayerPortrait
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import com.saiyanhabits.ui.theme.formAura
import java.util.Locale

private fun Long.grouped(): String = String.format(Locale.US, "%,d", this)
private fun Int.grouped(): String = String.format(Locale.US, "%,d", this)

/** A gold coin with a Z, then the amount. */
@Composable
fun ZeniAmount(amount: Long, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "${amount.grouped()} zeni" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (big) 26.dp else 20.dp)
                .background(Brush.verticalGradient(listOf(SaiyanColors.TabGoldTop, SaiyanColors.FrameGoldDark)), CircleShape)
                .border(1.5.dp, SaiyanColors.FrameDark, CircleShape),
        ) {
            Text(
                "Z",
                color = SaiyanColors.TabInk,
                fontWeight = FontWeight.Black,
                fontSize = if (big) 14.sp else 11.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            amount.grouped(),
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
    }
}

/** Your fighter at the top of the Team tab: level, HP, ki and zeni. Opens the status screen. */
@Composable
fun FighterCard(state: FighterState, onClick: () -> Unit) {
    val sheet = state.sheet
    DbzPanel(onClick = onClick, style = PanelStyle.Orange, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerPortrait(state.highestForm, Modifier.size(72.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText(
                        "LV ${sheet.level}",
                        MaterialTheme.typography.headlineSmall,
                        fill = GoldLettering,
                        outlineWidth = 4.dp,
                        modifier = Modifier.weight(1f),
                    )
                    ZeniAmount(state.zeni)
                }
                Text("HP ${sheet.maxHp.grouped()}   ·   Ki ${sheet.maxKi.grouped()}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                MeterBar(sheet.levelProgress, SaiyanColors.Gold)
                Text(
                    sheet.nextLevelPower?.let { "${(it - sheet.powerLevel).grouped()} power to level ${sheet.level + 1}" }
                        ?: "Highest level",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The full sheet: level, HP, ki, every stat and where it comes from, transformations, zeni. */
@Composable
fun StatusScreen(state: FighterState?, onBack: () -> Unit) {
    SubScreen(title = "Status", onBack = onBack) {
        if (state == null) return@SubScreen
        val sheet = state.sheet
        DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerPortrait(state.highestForm, Modifier.size(84.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        OutlinedText("LEVEL ${sheet.level}", MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp), fill = GoldLettering, outlineWidth = 5.dp)
                        Text("Power level ${sheet.powerLevel.grouped()}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
                MeterBar(sheet.levelProgress, SaiyanColors.Gold)
                Text(
                    sheet.nextLevelPower?.let {
                        "${(it - sheet.powerLevel).grouped()} more power to level ${sheet.level + 1}. Power comes only from training."
                    } ?: "The highest level there is.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        DbzPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabLabel("Vitals")
                Vital("HP", sheet.maxHp, GameConfig.HP_CAP, SaiyanColors.Bean)
                Vital("Ki", sheet.maxKi, GameConfig.KI_CAP, SaiyanColors.Blue)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zeni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    ZeniAmount(state.zeni, big = true)
                }
            }
        }
        DbzPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabLabel("Stats")
                Stat.entries.forEach { stat -> StatRow(stat, sheet.stats.getValue(stat).base, sheet.stats.getValue(stat).fromBonds) }
                Text(
                    "Stats grow with your level, and each mentor's bond raises the stats they train.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
            }
        }
        DbzPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TabLabel("Transformations")
                if (state.highestForm == 0) {
                    Text("None yet. A 7-day streak unlocks your first form.", color = SaiyanColors.TextDim)
                } else {
                    (1..state.highestForm).forEach { form ->
                        Text(Forms.name(form), style = MaterialTheme.typography.titleMedium, color = formAura(form))
                    }
                    Text("In battle, transforming boosts every stat while it drains ki.", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Vital(label: String, value: Int, cap: Int, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${value.grouped()} / ${cap.grouped()}", style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        MeterBar(value.toFloat() / cap, color)
    }
}

@Composable
private fun StatRow(stat: Stat, base: Int, fromBonds: Int) {
    val total = (base + fromBonds).coerceAtMost(GameConfig.STAT_CAP)
    val mentors = Mentor.entries.filter { stat in it.stats }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedText("$total", MaterialTheme.typography.titleLarge, fill = GoldLettering, outlineWidth = 3.dp)
        }
        Spacer(Modifier.height(4.dp))
        MeterBar(total.toFloat() / GameConfig.STAT_CAP, mentors.firstOrNull()?.color ?: SaiyanColors.Orange)
        Text(
            "${stat.what}. $base from level" +
                if (fromBonds > 0) " + $fromBonds from ${mentors.joinToString(" and ") { it.displayName }}" else
                    " · ${mentors.joinToString(" and ") { it.displayName }}'s bond raises it",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )
    }
}
