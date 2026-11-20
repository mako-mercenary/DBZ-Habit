package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.StudyPlan

/**
 * What the plan covers, and what it leaves out. A driver should know that the
 * sections missing from the app are missing on purpose.
 */
@Composable
fun PlanScreen(onBack: () -> Unit) {
    val repo = repository()

    Scaffold(topBar = { Bar("What you must study", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Class A with air brakes, no endorsements. Three knowledge tests and " +
                        "a skills test in three parts.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item { Text("In the plan", style = MaterialTheme.typography.titleMedium) }
            items(StudyPlan.required) { section ->
                val loaded = repo.content.section(section.number)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Section ${section.number} - ${section.title}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(section.why, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (loaded == null) {
                                "Not loaded in the app yet."
                            } else {
                                "${loaded.subsections.size} subsections | " +
                                    "${loaded.readingPages} pages | " +
                                    "${loaded.readyToRecall} with a fact checklist"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            item { Text("Left out on purpose", style = MaterialTheme.typography.titleMedium) }
            items(StudyPlan.excluded) { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Section ${section.number} - ${section.title}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(section.why, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                WarningCard(
                    "Check this against the state. The plan comes from the 2025 Utah " +
                        "Commercial Driver License Handbook and the Utah Driver License " +
                        "Division testing page, read in November 2026. Requirements change, " +
                        "and the Division is the authority, not this app."
                )
            }
        }
    }
}
