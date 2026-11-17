package com.saiyanhabits.ui.quests

import com.saiyanhabits.ui.components.ScreenTitle
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzSecondaryButton
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saiyanhabits.data.SagaWithMilestones
import com.saiyanhabits.data.SpiritBombEntity
import com.saiyanhabits.domain.SagaRules
import com.saiyanhabits.domain.SpiritBombRules
import com.saiyanhabits.ui.components.BossFigure
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SpiritBombOrb
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HistoryDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

internal fun formatDay(epochDay: Long?): String =
    epochDay?.let { LocalDate.ofEpochDay(it).format(HistoryDate) }.orEmpty()

/** The Sagas tab: bosses being fought, spirit bombs being charged, and what has been finished. */
@Composable
fun SagasScreen(
    overview: QuestsOverview?,
    onNewSaga: () -> Unit,
    onOpenSaga: (Long) -> Unit,
    onNewBomb: () -> Unit,
    onOpenBomb: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item(key = "title") {
            ScreenTitle(
                "Sagas",
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        if (overview == null) return@LazyColumn

        item(key = "bosses") { SectionLabel("Bosses", Modifier.padding(start = 4.dp)) }
        if (overview.activeSagas.isEmpty()) {
            item(key = "no-bosses") {
                Hint("A saga is a long goal fought as a boss. Each milestone you finish is a hit.")
            }
        }
        items(overview.activeSagas, key = { "saga-${it.saga.id}" }) { saga ->
            SagaCard(saga, onClick = { onOpenSaga(saga.saga.id) })
        }
        item(key = "new-saga") {
            DbzSecondaryButton(onClick = onNewSaga, modifier = Modifier.fillMaxWidth()) { Text("New saga") }
        }

        item(key = "bombs") { SectionLabel("Spirit bombs", Modifier.padding(start = 4.dp, top = 12.dp)) }
        if (overview.activeBombs.isEmpty()) {
            item(key = "no-bombs") {
                Hint("A spirit bomb is a long task you measure by amount: minutes, pages, sessions. Fill it, then throw it.")
            }
        }
        items(overview.activeBombs, key = { "bomb-${it.id}" }) { bomb ->
            BombCard(bomb, onClick = { onOpenBomb(bomb.id) })
        }
        item(key = "new-bomb") {
            DbzSecondaryButton(onClick = onNewBomb, modifier = Modifier.fillMaxWidth()) { Text("New spirit bomb") }
        }

        if (overview.defeatedSagas.isNotEmpty() || overview.thrownBombs.isNotEmpty()) {
            item(key = "history") { SectionLabel("History", Modifier.padding(start = 4.dp, top = 12.dp)) }
            items(overview.defeatedSagas, key = { "won-${it.saga.id}" }) { saga ->
                HistoryRow(
                    title = saga.saga.name,
                    detail = "Defeated ${saga.saga.boss.displayName} · ${formatDay(saga.saga.finishedAtEpochDay)}",
                    onClick = { onOpenSaga(saga.saga.id) },
                )
            }
            items(overview.thrownBombs, key = { "thrown-${it.id}" }) { bomb ->
                HistoryRow(
                    title = bomb.name,
                    detail = "Spirit bomb thrown · ${bomb.target} ${bomb.unit} · ${formatDay(bomb.finishedAtEpochDay)}",
                    onClick = { onOpenBomb(bomb.id) },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = SaiyanColors.TextDim,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun QuestCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    DbzPanel(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SagaCard(saga: SagaWithMilestones, onClick: () -> Unit) {
    val states = saga.milestones.map { it.toState() }
    val total = SagaRules.totalHp(states)
    val remaining = SagaRules.remainingHp(states)
    val boss = saga.saga.boss
    QuestCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            BossFigure(boss, Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    saga.saga.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${boss.displayName} · HP $remaining / $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = boss.color,
                )
                Spacer(Modifier.height(8.dp))
                MeterBar(if (total == 0) 0f else remaining.toFloat() / total, boss.color)
            }
        }
    }
}

@Composable
private fun BombCard(bomb: SpiritBombEntity, onClick: () -> Unit) {
    val fraction = SpiritBombRules.fraction(bomb.progress, bomb.target)
    QuestCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            SpiritBombOrb(fraction, Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bomb.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (fraction >= 1f) {
                        "Fully charged. Ready to throw."
                    } else {
                        "${bomb.progress} / ${bomb.target} ${bomb.unit}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.Blue,
                )
                Spacer(Modifier.height(8.dp))
                MeterBar(fraction, SaiyanColors.Blue)
            }
        }
    }
}

@Composable
private fun HistoryRow(title: String, detail: String, onClick: () -> Unit) {
    QuestCard(onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
        }
    }
}
