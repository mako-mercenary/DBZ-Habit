package com.saiyanhabits.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.data.AchievementState
import com.saiyanhabits.domain.Achievement
import com.saiyanhabits.domain.AchievementGroup
import com.saiyanhabits.domain.AchievementRules
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.components.drawStar
import com.saiyanhabits.ui.theme.SaiyanColors

@Composable
fun AchievementsScreen(state: AchievementState?, onBack: () -> Unit) {
    SubScreen(title = "Achievements", onBack = onBack) {
        if (state == null) return@SubScreen
        TitlePanel(state)
        AchievementGroup.entries.forEach { group ->
            Spacer(Modifier.height(4.dp))
            TabLabel(group.title)
            Achievement.entries.filter { it.group == group }.forEach { achievement ->
                AchievementRow(achievement, state)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TitlePanel(state: AchievementState) {
    val count = state.unlocked.size
    val next = AchievementRules.nextTitle(count)
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TabLabel("Your title")
            OutlinedText(
                state.title.uppercase(),
                MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp),
                fill = GoldLettering,
                outlineWidth = 5.dp,
                maxLines = 2,
            )
            Text("$count of ${Achievement.entries.size} achievements", color = Color.White, style = MaterialTheme.typography.titleSmall)
            if (next != null) {
                Text(
                    "${next.first - count} more to become ${next.second}.",
                    color = Color.White.copy(alpha = 0.9f),
                )
            } else {
                Text("Every achievement in the game. There is no one stronger.", color = Color.White)
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, state: AchievementState) {
    val unlocked = achievement in state.unlocked
    val progress = achievement.progress(state.stats).coerceAtMost(achievement.target)
    DbzPanel(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${achievement.title}. ${achievement.description}. " +
                    if (unlocked) "Unlocked." else "$progress of ${achievement.target}."
            },
        style = if (unlocked) PanelStyle.Blue else PanelStyle.Dim,
        corner = 14.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Medal(unlocked, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) SaiyanColors.Gold else SaiyanColors.TextHigh,
                )
                Text(achievement.description, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
                if (!unlocked && achievement.target > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MeterBar(achievement.fraction(state.stats), SaiyanColors.Orange, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("%,d / %,d".format(progress, achievement.target), style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                    }
                }
            }
        }
    }
}

/** A gold medal with a star when earned, a dark empty one when not. */
@Composable
fun Medal(unlocked: Boolean, modifier: Modifier = Modifier, ring: Dp = 3.dp) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        if (unlocked) {
            drawCircle(Brush.verticalGradient(listOf(SaiyanColors.TabGoldTop, SaiyanColors.FrameGoldDark)), r)
            drawCircle(SaiyanColors.FrameGoldLight, r - ring.toPx(), style = Stroke(1.5.dp.toPx()))
            drawCircle(SaiyanColors.FrameDark, r, style = Stroke(1.5.dp.toPx()))
            drawStar(center, r * 0.55f, Color(0xFFE5260E))
        } else {
            drawCircle(Color(0x55000000), r)
            drawCircle(SaiyanColors.TextDim.copy(alpha = 0.5f), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawStar(center + Offset.Zero, r * 0.45f, SaiyanColors.TextDim.copy(alpha = 0.25f))
        }
    }
}
