package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.Review
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The day you sit the test.
 *
 * It is the one setting that changes the whole deck: with a date set, no card
 * is ever booked for after it, and as the day closes in the intervals shorten
 * until everything comes back daily.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDateScreen(onBack: () -> Unit) {
    val settings = testDateSettings()
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val pretty = DateTimeFormatter.ofPattern("d MMMM yyyy")

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = settings.date
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        settings.choose(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    picking = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(topBar = { Bar("Test date", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline(settings.label(today))
                Text(
                    settings.date?.format(pretty) ?: "No test date",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                Text(
                    "You are not learning this for life. You are learning it for one exam on " +
                        "one day. With a date set, no card is ever booked for after it: the " +
                        "longest gap allowed is half the time you have left, so everything " +
                        "comes round at least once more with time to fix it. In the last " +
                        "days, everything comes back daily.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settings.date == null) "Pick the day" else "Change the day")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14L to "2 weeks", 30L to "1 month", 60L to "2 months").forEach { (days, label) ->
                        FilterChip(
                            selected = settings.date == today.plusDays(days),
                            onClick = { settings.choose(today.plusDays(days)) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (settings.date != null) {
                item {
                    TextButton(onClick = { settings.choose(null) }) {
                        Text("Clear the date")
                    }
                }
                item { WhatItDoes(settings, today) }
            } else {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            "With no date, the cards are scheduled for long-term memory and " +
                                "intervals run into months. Nothing is wrong with that, but " +
                                "it is not aimed at your test.",
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** The cap, shown as what it would do to a card you know well. */
@Composable
private fun WhatItDoes(settings: TestDateSettings, today: LocalDate) {
    val days = settings.daysLeft(today) ?: return
    if (days < 0) {
        WarningCard("That day has passed. Nothing is capped any more. Set a new date or clear it.")
        return
    }
    val longest = Review.cap(
        minutes = 365L * 24 * 60,
        now = System.currentTimeMillis(),
        testDate = settings.date,
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Right now", style = MaterialTheme.typography.titleSmall)
            Text(
                "$days days left, so the longest any card can be put off is " +
                    "${Review.gap(longest)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
