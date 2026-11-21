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
                        "${section.track.label} | ${section.readingPages} pages | " +
                            "${section.readyToRecall} of ${section.parts.size} " +
                            "parts have a fact checklist",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (section.placeholder) {
                item { WarningCard("SAMPLE CONTENT. Do not study these facts.") }
            }
            items(section.subsections) { sub ->
                val row = progress.firstOrNull { it.subsectionId == sub.id }

                // A heading with nothing of its own is a label over its parts,
                // not something to open. The manual gives it no text, and that
                // is what a heading is for.
                if (section.isHeading(sub) && !sub.hasLesson && !sub.canRecall) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            "${sub.id} ${sub.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Heading. The parts below hold the reading.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    return@items
                }

                Card(
                    onClick = { onSubsection(sub.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // A part sits under its heading, so the shape of the
                        // manual is visible in the list.
                        .padding(start = (if (sub.id.count { it == '.' } > 1) 12 else 0).dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${sub.id} ${sub.title}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                when {
                                    // A subsection you can recall: the checklist is the point.
                                    sub.canRecall -> {
                                        append("${sub.facts.size} facts")
                                        append(" | ${sub.criticalFacts.size} must memorize")
                                    }
                                    // A heading or an overview: reading only, and no
                                    // "0 facts", which reads like something is broken.
                                    sub.hasLesson -> append("Reading only, no recall")
                                    else -> append("Not written yet")
                                }
                                sub.manualPages?.let { append(" | pages $it") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            when {
                                !sub.canRecall && !sub.hasLesson ->
                                    "Read it in your own manual with the book pages switch"
                                !sub.canRecall -> "Read when you reach it"
                                row == null || row.status == "new" -> "Not started"
                                row.status == "reading" -> "Read, no recall yet"
                                row.status == "recalled" ->
                                    "Best: ${row.bestCritical}% on must-memorize facts"
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
