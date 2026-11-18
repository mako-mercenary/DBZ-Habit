package com.saiyanhabits.ui.games

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import java.util.Locale

/** A score in the game's own terms: "1,240 m", "12 min", "3.4 kg", "5 wins". */
fun MiniGame.format(score: Long): String = when (this) {
    MiniGame.HOVER_BIKE -> String.format(Locale.US, "%,d m", score)
    MiniGame.MEDITATION -> "${score / 60}:${(score % 60).toString().padStart(2, '0')}"
    MiniGame.FISHING -> String.format(Locale.US, "%.1f kg", score / 1000.0)
    MiniGame.CHESS -> if (score == 1L) "1 win" else "$score wins"
    MiniGame.RHYTHM -> String.format(Locale.US, "%,d", score)
}

/** Every mentor's game, with your best so far. */
@Composable
fun GamesScreen(bests: Map<MiniGame, Long>, onPlay: (MiniGame) -> Unit, onBack: () -> Unit) {
    SubScreen(title = "Train with your mentors", onBack = onBack) {
        Text(
            "A game with each mentor. Each one you finish gives that mentor bond XP, up to " +
                "${GameConfig.MINIGAME_DAILY_BOND_XP_CAP} a day.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
        )
        MiniGame.entries.forEach { game ->
            DbzPanel(
                onClick = { onPlay(game) },
                watermark = { MentorWatermark(game.mentor) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(game.mentor, Modifier.size(60.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("with ${game.mentor.displayName}", style = MaterialTheme.typography.bodySmall, color = game.mentor.color)
                        Text(game.blurb, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
                        bests[game]?.let {
                            Text("${game.scoreLabel}: ${game.format(it)}", style = MaterialTheme.typography.titleSmall, color = SaiyanColors.Gold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Shown when a play ends: the result, the best so far, and the bond XP it earned. */
@Composable
fun GameResultCard(
    game: MiniGame,
    headline: String,
    score: Long,
    best: Long?,
    result: PlayResult?,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabLabel(game.title)
            OutlinedText(headline.uppercase(), MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp), fill = GoldLettering, outlineWidth = 5.dp, maxLines = 2)
            Text(game.format(score), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (best != null) {
                val isBest = if (game.higherIsBetter) score >= best else score <= best
                Text(
                    if (isBest) "A new best!" else "${game.scoreLabel}: ${game.format(best)}",
                    color = if (isBest) SaiyanColors.Gold else Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            extra()
            if (result != null) {
                Text(
                    if (result.bondXp > 0) "+${result.bondXp} bond XP with ${game.mentor.displayName}"
                    else "No more bond XP from games with ${game.mentor.displayName} today.",
                    color = Color.White,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DbzSecondaryButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
                DbzPrimaryButton(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("Again") }
            }
        }
    }
}
