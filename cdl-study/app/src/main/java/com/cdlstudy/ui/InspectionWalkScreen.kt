package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.domain.InspectionWalk

/**
 * The pre-trip inspection as a route, not a pool.
 *
 * The areas come in the order you walk the vehicle. An area counts as walked
 * once you have recorded a recall for it.
 */
@Composable
fun InspectionWalkScreen(onBack: () -> Unit, onArea: (String) -> Unit) {
    val repo = repository()
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val recalled = progress.filter { it.timesRecalled > 0 }.map { it.subsectionId }.toSet()
    val walk = InspectionWalk.progress(repo.content, recalled)

    Scaffold(topBar = { Bar("The walk-around", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline("Vehicle inspection")
                Text("Walk it in order", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("At every item", style = MaterialTheme.typography.titleSmall)
                        Text(
                            InspectionWalk.THE_THREE_THINGS,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Say it out loud. On the day you will be speaking, not reading.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${walk.cleared} of ${walk.total} areas walked",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    GoldProgress(walk.percent)
                }
            }
            walk.next?.let { next ->
                item {
                    NextButton("Next area: ${next.title}") { onArea(next.subsectionId) }
                }
            }
            itemsIndexed(walk.areas) { index, area ->
                val done = area.subsectionId in walk.done
                Card(onClick = { onArea(area.subsectionId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (done) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(area.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            buildString {
                                append("${area.items} items")
                                append(" · ${area.mustMemorize} must memorize")
                                if (area.combinationOnly) append(" · combination only")
                                if (done) append(" · walked")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
