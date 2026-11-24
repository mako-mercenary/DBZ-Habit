package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.Diagrams
import com.cdlstudy.domain.InspectionWalk
import com.cdlstudy.domain.NumberDrill

/**
 * The three drills that the main recall loop cannot do.
 *
 * The loop reads a subsection and grades a blurt. It cannot make you produce a
 * number cold, it cannot test an order, and it cannot walk you round a vehicle.
 */
@Composable
fun DrillsScreen(
    onBack: () -> Unit,
    onNumbers: () -> Unit,
    onProcedures: () -> Unit,
    onWalk: () -> Unit,
    onDiagrams: () -> Unit,
) {
    val repo = repository()
    val numbers = NumberDrill.pool(repo.content).size
    val procedures = repo.content.procedures
    val areas = InspectionWalk.areas(repo.content).size

    Scaffold(topBar = { Bar("Drills", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline("Drills")
                Text("Short, hard, one thing at a time", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Entry(
                    "Numbers",
                    "$numbers facts with a number in them. The number is blanked out and " +
                        "you type it. Five minutes at a time.",
                    onNumbers,
                )
            }
            item {
                Entry(
                    "In order",
                    "${procedures.size} procedures, ${procedures.sumOf { it.length }} steps. " +
                        "The air brake check, the seven-step inspection, coupling and " +
                        "uncoupling. Shuffled, and you put them back.",
                    onProcedures,
                )
            }
            item {
                Entry(
                    "The walk-around",
                    "$areas vehicle areas in the order you walk them, with what you must " +
                        "name in each.",
                    onWalk,
                )
            }
            item {
                Entry(
                    "Diagrams",
                    "${Diagrams.all.size} drawings for the parts that do not go into " +
                        "sentences: the air brake chain, the fifth wheel, the glad hands and " +
                        "the walk-around route.",
                    onDiagrams,
                )
            }
            item {
                Text(
                    "A drill that catches you queues the card. Anki still keeps the " +
                        "schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Entry(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
