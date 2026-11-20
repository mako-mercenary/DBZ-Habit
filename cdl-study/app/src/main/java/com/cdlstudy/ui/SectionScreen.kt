package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SectionScreen(
    sectionNumber: Int,
    onSubsection: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repo = repository()
    val section = repo.content.section(sectionNumber)
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())

    Scaffold(topBar = { Bar(section?.let { "Section ${it.number}" } ?: "Section", onBack) }) { pad ->
        if (section == null) {
            Text("That section is not loaded.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${section.track.label} | ${section.readingPages} pages to read",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (section.placeholder) {
                item { WarningCard("SAMPLE CONTENT. Do not study these facts.") }
            }
            items(section.subsections) { sub ->
                val row = progress.firstOrNull { it.subsectionId == sub.id }
                Card(onClick = { onSubsection(sub.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${sub.id} ${sub.title}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append("${sub.facts.size} facts")
                                append(" | ${sub.criticalFacts.size} must memorize")
                                sub.manualPages?.let { append(" | pages $it") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            when (row?.status) {
                                null, "new" -> "Not started"
                                "reading" -> "Read, no recall yet"
                                "recalled" -> "Best: ${row.bestCritical}% on must-memorize facts"
                                else -> "Mastered: ${row.bestCritical}% on must-memorize facts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
