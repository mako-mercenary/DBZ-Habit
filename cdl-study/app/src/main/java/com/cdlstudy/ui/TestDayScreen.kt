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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.TestDay

/**
 * The things Utah asks of you that are not in the manual.
 *
 * Every line came off the Driver License Division's own pages, with the page
 * named. The screen says when they were read and tells you to check them again
 * before you go, because a fee or an office list in an app is not the fee or
 * the office list at the counter.
 */
@Composable
fun TestDayScreen(onBack: () -> Unit) {
    val settings = testDaySettings()
    val ticked = settings.ticked

    Scaffold(topBar = { Bar("Test day", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline("Utah")
                Text("What to take, and what it costs", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                WarningCard(
                    "Read off ${TestDay.SITE} on ${TestDay.CHECKED_ON}. Fees, office lists and " +
                        "rules change. Check the pages again before you drive to an office: " +
                        "this app is a reminder of what to look up, not the authority."
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${TestDay.done(ticked)} of ${TestDay.tickable.size} ready",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    GoldProgress(TestDay.percent(ticked))
                    if (ticked.isNotEmpty()) {
                        TextButton(onClick = settings::clear) { Text("Clear the ticks") }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        TestDay.NOTICE,
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TestDay.groups.forEach { group ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium, color = Gold)
                        Text(
                            group.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group.items.size, key = { group.items[it].id }) { index ->
                    val item = group.items[index]
                    val on = item.id in ticked
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (item.tickable) settings.toggle(item.id) },
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (item.tickable) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { settings.toggle(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Gold),
                                )
                            }
                            Column(
                                Modifier.padding(top = if (item.tickable) 12.dp else 0.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (on) TextDecoration.LineThrough else null,
                                    color = if (on) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (item.source.isNotEmpty()) {
                                    Text(
                                        TestDay.SITE + item.source,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
