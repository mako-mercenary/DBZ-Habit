package com.cdlstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdlstudy.manual.ManualStore

/**
 * Imports your copy of the manual, so you can read it in the app.
 */
@Composable
fun ManualScreen(onBack: () -> Unit) {
    val store = manualStore()
    val repo = repository()
    var pages by remember { mutableIntStateOf(store.pageCount) }
    var message by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = store.import(uri)) {
            is ManualStore.ImportResult.Ok -> {
                pages = result.pageCount
                message = "Imported. ${result.pageCount} pages."
            }
            is ManualStore.ImportResult.Failed -> {
                message = result.reason
            }
        }
    }

    val needed = repo.content.lastPdfPage

    Scaffold(topBar = { Bar("Your manual", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (pages > 0) "Your manual is imported" else "No manual yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (pages > 0) {
                                "$pages pages, kept in this app only."
                            } else {
                                "The app holds no manual text. Import your own copy once, " +
                                    "and the app shows you the right pages for each subsection."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Download the handbook from the Utah Driver License Division at " +
                                "dld.utah.gov, then import the PDF here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            if (pages in 1 until needed) {
                item {
                    WarningCard(
                        "This PDF has $pages pages, and the study plan needs at least $needed. " +
                            "It may be a different edition. Check that the pages the app shows " +
                            "match the subsection titles."
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pick.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (pages > 0) "Import another copy" else "Import the manual PDF") }
                    if (pages > 0) {
                        OutlinedButton(
                            onClick = {
                                store.remove()
                                pages = 0
                                message = "Removed."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Remove it from the app") }
                    }
                }
            }
            item {
                Text(
                    "Why it works this way: the handbook is AAMVA material, and permission " +
                        "to reproduce it is given to state driver licence agencies only. " +
                        "So the app carries no manual text, and reads the copy you import.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
