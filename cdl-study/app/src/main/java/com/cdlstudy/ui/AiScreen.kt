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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cdlstudy.ai.AiSettings

/**
 * The one screen that turns on the network.
 *
 * It says plainly what is sent, because the rest of the app sends nothing at
 * all and that should not change quietly.
 */
@Composable
fun AiScreen(onBack: () -> Unit) {
    val settings = aiSettings()
    var typed by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    Scaffold(topBar = { Bar("AI grading", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline(if (settings.ready) "On" else "Off")
                Text(
                    "A second opinion on your recall",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                Text(
                    "The app marks your recall by keyword. A keyword cannot tell you that " +
                        "you had a number backwards. With your own API key, the correct " +
                        "step gets a button that sends your words to Claude and asks for a " +
                        "mark on each fact, and for anything you said that is wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("What leaves the phone", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Only two things, and only when you press the button: the words " +
                                "you typed from memory, and this app's own fact list for that " +
                                "subsection. Your manual is not sent. The app does not hold " +
                                "it, so it cannot send it. Your name is not sent. Your scores " +
                                "are not sent.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Each request is billed to your own API account. Nothing else in " +
                                "the app uses the network.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Your API key", style = MaterialTheme.typography.titleMedium)
                        if (settings.ready) {
                            Text(
                                "A key is saved, ending ${settings.keyTail}. It is kept in " +
                                    "this app's private storage. No other app can read it.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(
                                onClick = { settings.forgetKey() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Forget the key and turn this off")
                            }
                        } else {
                            Text(
                                "Make a key at console.anthropic.com, then paste it here.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text(if (settings.ready) "Replace the key" else "Paste the key") },
                            singleLine = true,
                            visualTransformation = if (show) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { show = !show }) {
                                Text(if (show) "Hide" else "Show")
                            }
                            OutlinedButton(
                                onClick = {
                                    settings.chooseKey(typed)
                                    typed = ""
                                    show = false
                                },
                                enabled = typed.isNotBlank(),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Which model grades", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiSettings.Models.ALL.forEach { id ->
                                FilterChip(
                                    selected = settings.model == id,
                                    onClick = { settings.chooseModel(id) },
                                    label = { Text(AiSettings.Models.label(id)) },
                                )
                            }
                        }
                        Text(
                            AiSettings.Models.note(settings.model),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    "The model advises. It does not grade you: your own marks are what the " +
                        "app saves, and what makes your cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
