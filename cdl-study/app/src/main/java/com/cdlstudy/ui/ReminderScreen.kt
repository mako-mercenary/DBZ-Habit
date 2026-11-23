package com.cdlstudy.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.domain.Streak
import com.cdlstudy.reminder.Reminders

@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = reminderSettings()
    val repo = repository()
    val streak by repo.streak.collectAsStateWithLifecycle(Streak.Result(0, 0, false, emptyList()))

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settings.switchOn(true)
            Reminders.schedule(context, settings.time)
        }
    }

    fun turnOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings.switchOn(true)
            Reminders.schedule(context, settings.time)
        }
    }

    Scaffold(topBar = { Bar("Daily reminder", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Remind me daily", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (settings.enabled) "On, at ${settings.label}" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { on ->
                                if (on) {
                                    turnOn()
                                } else {
                                    settings.switchOn(false)
                                    Reminders.cancel(context)
                                }
                            },
                        )
                    }
                }
            }

            item { Text("At what time", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6 * 60, 12 * 60, 17 * 60, 19 * 60, 21 * 60).forEach { minutes ->
                        FilterChip(
                            selected = settings.minuteOfDay == minutes,
                            onClick = {
                                settings.chooseTime(minutes)
                                if (settings.enabled) Reminders.schedule(context, settings.time)
                            },
                            label = { Text("%02d:%02d".format(minutes / 60, minutes % 60)) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        settings.chooseTime(settings.minuteOfDay - 30)
                        if (settings.enabled) Reminders.schedule(context, settings.time)
                    }) { Text("30 min earlier") }
                    OutlinedButton(onClick = {
                        settings.chooseTime(settings.minuteOfDay + 30)
                        if (settings.enabled) Reminders.schedule(context, settings.time)
                    }) { Text("30 min later") }
                }
            }

            item { Text("Your streak", style = MaterialTheme.typography.titleSmall) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatRow(
                            "current" to "${streak.current}",
                            "longest" to "${streak.longest}",
                            "days studied" to "${streak.days.size}",
                        )
                        Text(
                            when {
                                streak.current == 0 && streak.days.isEmpty() ->
                                    "A day counts once you finish one recall."
                                streak.studiedToday -> "Today is counted. Nothing can take it away now."
                                streak.atRisk ->
                                    "Today is not counted yet. One recall keeps the streak."
                                else -> "A day counts once you finish one recall."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Text(
                    "The reminder names the next thing to do, and says if your streak " +
                        "is at risk. It needs no network: everything is worked out on the phone.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
