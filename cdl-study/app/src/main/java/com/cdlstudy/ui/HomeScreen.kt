package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.data.PlanProgress
import com.cdlstudy.data.ProgressEntity
import com.cdlstudy.domain.Section

@Composable
fun HomeScreen(
    onSection: (Int) -> Unit,
    onCards: () -> Unit,
    onExams: () -> Unit,
    onProgress: () -> Unit,
    onManual: () -> Unit,
    onPlan: () -> Unit,
) {
    val repo = repository()
    val store = manualStore()
    val errors = contentErrors()
    val plan by repo.planProgress.collectAsStateWithLifecycle(
        PlanProgress(0, 0, 0)
    )
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val pendingCards by repo.pendingCardCount.collectAsStateWithLifecycle(0)

    Scaffold(topBar = { Bar("Utah Class A CDL") }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (repo.content.sections.isEmpty()) {
                item {
                    WarningCard(
                        "There is no study content yet. Add a section file to assets/content."
                    )
                }
            } else if (repo.content.isPlaceholderOnly) {
                item {
                    WarningCard(
                        "SAMPLE CONTENT. These facts are an example of the format only. " +
                            "Do not study them. Replace them with content made from the manual."
                    )
                }
            }
            errors.forEach { error ->
                item { WarningCard("Content file problem - $error") }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Your plan", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        StatRow(
                            "subsections" to "${plan.subsectionsRecalled}/${plan.subsectionsTotal}",
                            "mastered" to "${plan.subsectionsMastered}",
                            "cards to export" to "$pendingCards",
                        )
                        Spacer(Modifier.height(12.dp))
                        if (plan.subsectionsTotal > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    plan.subsectionsRecalled.toFloat() / plan.subsectionsTotal
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (!store.isImported) {
                item {
                    Card(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Import your manual", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Then you read each subsection in the app, at the right pages. " +
                                    "The app carries no manual text of its own.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCards) { Text("Cards ($pendingCards)") }
                    OutlinedButton(onClick = onExams) { Text("Exams") }
                    OutlinedButton(onClick = onProgress) { Text("Progress") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPlan) { Text("What you must study") }
                    OutlinedButton(onClick = onManual) { Text("Your manual") }
                }
            }

            item {
                Column {
                    Text(
                        "The eight required sections",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Class A with air brakes, no endorsements.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            items(repo.content.sections) { section ->
                SectionRow(section, progress) { onSection(section.number) }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: Section,
    progress: List<ProgressEntity>,
    onClick: () -> Unit,
) {
    val ids = section.subsections.map { it.id }.toSet()
    val done = progress.count { it.subsectionId in ids && it.status != "new" && it.status != "reading" }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Section ${section.number} - ${section.title}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${section.track.label} | $done of ${section.subsections.size} subsections recalled",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (section.factCount == 0) {
                    "${section.readingPages} pages to read | no fact checklist yet"
                } else {
                    "${section.factCount} facts | ${section.cardCount} cards | " +
                        "${section.examCount} questions"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
