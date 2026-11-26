package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.domain.Handoff
import com.cdlstudy.domain.Track
import java.time.LocalDate

@Composable
fun ProgressScreen(onBack: (() -> Unit)?) {
    val repo = repository()
    val clipboard = LocalClipboardManager.current
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val weak by repo.weakFacts().collectAsStateWithLifecycle(emptyList())
    val due by repo.dueCardCount().collectAsStateWithLifecycle(0)
    val deck by repo.cardCount.collectAsStateWithLifecycle(0)
    var copied by remember { mutableStateOf(false) }

    val weakLines = weak.mapNotNull { row ->
        repo.content.fact(row.factId)?.let {
            Handoff.WeakLine(row.factId, it.text, row.weight)
        }
    }
    val subsectionLines = progress.sortedBy { it.subsectionId }.mapNotNull { row ->
        repo.content.subsection(row.subsectionId)?.let { sub ->
            Handoff.SubsectionLine(sub.id, sub.title, row.status, row.bestCritical, row.timesRecalled)
        }
    }

    Scaffold(topBar = { Bar("Progress", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = {
                    val text = Handoff.build(
                        isoDate = LocalDate.now().toString(),
                        subsections = subsectionLines,
                        exams = emptyList(),
                        weak = weakLines,
                        cardsMade = deck,
                        cardsDue = due,
                    )
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                }) { Text("Copy my progress summary") }
            }
            if (copied) {
                item {
                    Text(
                        "Copied. Paste it into any chat to carry your progress.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { Text("Weak facts", style = MaterialTheme.typography.titleMedium) }
            if (weakLines.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. A fact appears here when you miss it in a recall or an exam.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(weakLines) { line ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Missed weight ${line.weight}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            item { Text("Subsections", style = MaterialTheme.typography.titleMedium) }
            items(subsectionLines) { line ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${line.subsectionId} ${line.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${line.status} | best ${line.bestCritical}% on must-memorize facts | " +
                                "${line.timesRecalled} recalls",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            item { Text("Exam bests", style = MaterialTheme.typography.titleMedium) }
            items(Track.entries) { track ->
                val best by repo.bestPercent(track).collectAsStateWithLifecycle(null)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(track.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            best?.let { "Best $it%" } ?: "No attempt yet",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
