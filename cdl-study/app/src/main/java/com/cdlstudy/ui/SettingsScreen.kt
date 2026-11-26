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
    onReminder: () -> Unit,
    onAi: () -> Unit,
    onTestDay: () -> Unit,
    onTestDate: () -> Unit,
) {
    val store = manualStore()
    val settings = readingSettings()
    val reminder = reminderSettings()
    val ai = aiSettings()
    val testDay = testDaySettings()
    val testDate = testDateSettings()
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
                "Test date",
                testDate.date?.let { "$it · ${testDate.label()}, and no card is booked past it" }
                    ?: "Not set · cards are scheduled for long-term memory",
                onTestDate,
            )
        }
        item {
            Entry(
                "Test day",
                "${com.cdlstudy.domain.TestDay.done(testDay.ticked)} of " +
                    "${com.cdlstudy.domain.TestDay.tickable.size} ready · documents, fees " +
                    "and the rules, from dld.utah.gov",
                onTestDay,
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
                "Daily reminder",
                if (reminder.enabled) "On, at ${reminder.label}" else "Off",
                onReminder,
            )
        }
        item {
            Entry(
                "AI grading",
                if (ai.ready) {
                    "On, with your key · ${com.cdlstudy.ai.AiSettings.Models.label(ai.model)}"
                } else {
                    "Off. Needs your own API key"
                },
                onAi,
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
                if (ai.ready) {
                    "No account, no analytics. Your record stays on this phone. AI grading " +
                        "is the one thing that uses the network, and only when you press it."
                } else {
                    "Offline. No account, no network, no analytics. Your record stays on " +
                        "this phone."
                },
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
