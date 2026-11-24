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
import com.cdlstudy.domain.Diagram
import com.cdlstudy.domain.Diagrams

/** The diagrams, for the parts of the material that do not go into sentences. */
@Composable
fun DiagramsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { Bar("Diagrams", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Overline("Pictures")
                Text("Four things text does badly", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Drawn for this app. The manual's own figures are not in here, and " +
                        "neither is the manual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(Diagrams.all.size, key = { Diagrams.all[it].id }) { index ->
                DiagramCard(Diagrams.all[index])
            }
        }
    }
}

@Composable
fun DiagramCard(diagram: Diagram, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(diagram.title, style = MaterialTheme.typography.titleMedium)
            DiagramView(diagram)
            Text(
                diagram.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
