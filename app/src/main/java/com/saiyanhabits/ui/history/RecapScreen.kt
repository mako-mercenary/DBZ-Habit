package com.saiyanhabits.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.domain.RecapRules
import com.saiyanhabits.domain.WeekRecap
import com.saiyanhabits.domain.Weeks
import com.saiyanhabits.ui.components.ArtImage
import com.saiyanhabits.ui.components.BottomActionButton
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.OrbWatermark
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RangeDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** The week's range, like "Nov 9 - Nov 15". */
private fun weekRange(weekStart: Long): String =
    "${LocalDate.ofEpochDay(weekStart).format(RangeDay)} - ${LocalDate.ofEpochDay(weekStart + Weeks.DAYS - 1).format(RangeDay)}"

@Composable
fun RecapScreen(recap: WeekRecap?, onDone: () -> Unit) {
    SubScreen(
        title = "Weekly recap",
        onBack = onDone,
        bottomBar = { BottomActionButton("On to this week", onDone) },
    ) {
        if (recap == null) return@SubScreen
        HeadlinePanel(recap)
        DaysPanel(recap)
        PowerPanel(recap)
        OrbPanel(recap)
        TrainingPanel(recap)
        if (recap.hadBattles) BattlesPanel(recap)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeadlinePanel(recap: WeekRecap) {
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TabLabel(weekRange(recap.weekStart))
            OutlinedText(
                RecapRules.headline(recap).uppercase(),
                MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
                fill = GoldLettering,
                outlineWidth = 5.dp,
            )
            Text(RecapRules.subline(recap), style = MaterialTheme.typography.bodyLarge, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun DaysPanel(recap: WeekRecap) {
    DbzPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TabLabel("The days")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recap.days.forEachIndexed { i, outcome ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        DayDot(
                            outcome = outcome,
                            label = LocalDate.ofEpochDay(recap.weekStart + i).dayOfMonth.toString(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Text(
                            LocalDate.ofEpochDay(recap.weekStart + i).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = SaiyanColors.TextDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                buildString {
                    append(if (recap.perfectDays == 1) "1 perfect day" else "${recap.perfectDays} perfect days")
                    recap.completionPercent?.let { append(" · $it% of habits done") }
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (recap.streakAtEnd > 0) "Streak at the end of the week: ${recap.streakAtEnd} days" else "No streak running at the end of the week",
                color = SaiyanColors.TextDim,
            )
        }
    }
}

@Composable
private fun PowerPanel(recap: WeekRecap) {
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Green) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TabLabel("Power gained")
            OutlinedText(
                "+" + "%,d".format(recap.powerGained),
                MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
                fill = GoldLettering,
                outlineWidth = 5.dp,
            )
            val diff = recap.powerGained - recap.rivalPower
            Text(
                when {
                    !recap.hasRival -> "No rival yet: the week before had no power to beat."
                    recap.beatRival -> "Your rival, the week before, earned ${"%,d".format(recap.rivalPower)}. Beaten by ${"%,d".format(diff)}."
                    diff == 0 -> "Your rival, the week before, earned ${"%,d".format(recap.rivalPower)}. A dead heat."
                    else -> "Your rival, the week before, earned ${"%,d".format(recap.rivalPower)}. ${"%,d".format(-diff)} short."
                },
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun OrbPanel(recap: WeekRecap) {
    DbzPanel(Modifier.fillMaxWidth(), watermark = { OrbWatermark() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(
                key = ArtKey.APP_ICON,
                contentDescription = null,
                alpha = if (recap.perfectWeek) 1f else 0.35f,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TabLabel("Wish orb")
                Text(
                    if (recap.perfectWeek) "A perfect week earned an orb." else "No orb this time. A perfect week earns one.",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("You hold ${recap.wishOrbs} of ${GameConfig.WISH_ORBS_NEEDED}.", color = SaiyanColors.TextDim)
            }
        }
    }
}

@Composable
private fun TrainingPanel(recap: WeekRecap) {
    val top = recap.topMentor
    val watermark: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
        top?.let { (mentor, _) -> { MentorWatermark(mentor) } }
    DbzPanel(Modifier.fillMaxWidth(), watermark = watermark) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabLabel("Training")
            recap.topHabit?.let {
                Text("Most trained: ${it.name}", style = MaterialTheme.typography.titleSmall)
                Text(if (it.checkOffs == 1) "Checked off once" else "Checked off ${it.checkOffs} times", color = SaiyanColors.TextDim)
            } ?: Text("No habits checked off.", color = SaiyanColors.TextDim)
            if (top != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(top.first, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Trained you most: ${top.first.displayName}", style = MaterialTheme.typography.titleSmall)
                        Text("+${top.second} bond XP", color = SaiyanColors.Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BattlesPanel(recap: WeekRecap) {
    DbzPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TabLabel("Battles")
            if (recap.milestonesHit > 0) Text(plural(recap.milestonesHit, "saga milestone hit", "saga milestones hit"))
            if (recap.bossesDefeated > 0) Text(plural(recap.bossesDefeated, "boss defeated", "bosses defeated"), color = SaiyanColors.Gold)
            if (recap.bombsThrown > 0) Text(plural(recap.bombsThrown, "spirit bomb thrown", "spirit bombs thrown"))
        }
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
