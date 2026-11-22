package com.cdlstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cdlstudy.BuildConfig
import com.cdlstudy.domain.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Saves your study record to a file, and puts one back.
 *
 * The record lives only on this phone. Lose the phone, or uninstall the app,
 * and every recall, score and card goes with it. This screen is the answer.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val repo = repository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Backup.Record?>(null) }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val record = repo.record.export(BuildConfig.VERSION_NAME)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Backup.write(record).toByteArray(Charsets.UTF_8))
                    } ?: error("The file could not be opened for writing.")
                    "Saved: ${record.counts}."
                }.getOrElse { "The record could not be saved. ${it.message}" }
            }
        }
    }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("The file could not be opened.")
                    Backup.read(text)
                }
            }
            result.fold(
                onSuccess = { pending = it },
                onFailure = { message = it.message ?: "That file could not be read." },
            )
        }
    }

    pending?.let { record ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace your record?") },
            text = {
                Text(
                    "This file holds ${record.counts}. Putting it back REPLACES " +
                        "everything on this phone: every recall, card, exam and score. " +
                        "It cannot be undone. Save the current record first if you want it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = record
                    pending = null
                    scope.launch {
                        message = runCatching {
                            repo.record.import(chosen)
                            "Record restored: ${chosen.counts}."
                        }.getOrElse { "The record could not be restored. ${it.message}" }
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    Scaffold(topBar = { Bar("Back up your record", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Your recalls, marks, cards, exam results and progress live only on " +
                        "this phone. Save them to a file and keep it somewhere else.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message?.let {
                item { Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(14.dp)) } }
            }
            item {
                Button(
                    onClick = { save.launch(Backup.fileName(LocalDate.now().toString())) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save my record to a file") }
            }
            item {
                OutlinedButton(
                    onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Put a saved record back") }
            }
            item {
                WarningCard(
                    "Putting a record back REPLACES what is on the phone. It does not " +
                        "merge. Merging would double every card and every recall, with no " +
                        "honest way to tell a repeat from a copy."
                )
            }
            item {
                Text(
                    "The file holds your work only. The lessons come with the app, and " +
                        "your manual stays where you imported it.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
