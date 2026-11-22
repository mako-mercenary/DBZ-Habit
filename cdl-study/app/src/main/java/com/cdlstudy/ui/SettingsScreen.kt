package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onReading: () -> Unit,
    onManual: () -> Unit,
    onPlan: () -> Unit,
    onBackup: () -> Unit,
) {
    val store = manualStore()
    val settings = readingSettings()
    val repo = repository()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Overline("Settings")
            Text("Set it up", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Entry(
                "Reading",
                if (settings.style.focus) {
                    "Focus text on, ${settings.style.strength.label.lowercase()} · " +
                        "${Math.round(settings.style.scale * 100)}% size"
                } else {
                    "Focus text off · ${Math.round(settings.style.scale * 100)}% size"
                },
                onReading,
            )
        }
        item {
            Entry(
                "Your manual",
                if (store.isImported) "${store.pageCount} pages imported" else "Not imported",
                onManual,
            )
        }
        item {
            Entry(
                "What you must study",
                "The eight sections Utah requires, and what is left out",
                onPlan,
            )
        }
        item {
            Entry(
                "Back up your record",
                "Save your recalls, cards and scores to a file, or put one back",
                onBackup,
            )
        }
        item {
            Entry(
                "Content",
                "${repo.content.sections.sumOf { it.factCount }} facts · " +
                    "${repo.content.sections.sumOf { it.withLesson }} lessons written",
                null,
            )
        }
        item {
            Text(
                "Offline. No account, no network, no analytics. Your record stays on " +
                    "this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Entry(title: String, detail: String, onClick: (() -> Unit)?) {
    val card: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { card(Modifier.fillMaxWidth()) }
    } else {
        Card(Modifier.fillMaxWidth()) { card(Modifier.fillMaxWidth()) }
    }
}
