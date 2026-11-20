package com.cdlstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.domain.Card as StudyCard
import com.cdlstudy.domain.Tsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The Anki export. The block is exactly the format the study plan asks for:
 * question, tab, answer, one card per line, no header row.
 */
@Composable
fun CardsScreen(onBack: () -> Unit) {
    val repo = repository()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pending by repo.pendingCards.collectAsStateWithLifecycle(emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    val tsv = remember(pending) {
        Tsv.of(pending.map { StudyCard(it.question, it.answer, it.factId) })
    }
    val fileName = remember(pending) { Tsv.fileName("cards", LocalDate.now().toString()) }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(tsv.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            message = if (written) "File saved. Import it in Anki as Basic cards." else "The file could not be saved."
        }
    }

    Scaffold(topBar = { Bar("Cards to export", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${pending.size} cards are waiting. Anki keeps your review schedule, " +
                        "so this app only makes the cards.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            if (pending.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clipboard.setText(AnnotatedString(tsv))
                            message = "Copied. Paste it into Anki with File, then Import."
                        }) { Text("Copy TSV") }
                        OutlinedButton(onClick = { saveFile.launch(fileName) }) {
                            Text("Save .tsv file")
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        scope.launch {
                            repo.markPendingExported()
                            message = "Marked as exported."
                        }
                    }) { Text("Mark these as exported") }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        MonoText(tsv, Modifier.padding(12.dp))
                    }
                }
            } else {
                item {
                    Text(
                        "No cards are waiting. Cards appear here after a recall or an exam.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
