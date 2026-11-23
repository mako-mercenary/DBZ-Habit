package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.cdlstudy.CdlApp
import com.cdlstudy.ai.AiSettings
import com.cdlstudy.data.StudyRepository

@Composable
fun repository(): StudyRepository =
    (LocalContext.current.applicationContext as CdlApp).repository

@Composable
fun readingSettings(): ReadingSettings =
    (LocalContext.current.applicationContext as CdlApp).reading

@Composable
fun reminderSettings(): ReminderSettings =
    (LocalContext.current.applicationContext as CdlApp).reminder

@Composable
fun aiSettings(): AiSettings =
    (LocalContext.current.applicationContext as CdlApp).ai

@Composable
fun contentErrors(): List<String> =
    (LocalContext.current.applicationContext as CdlApp).contentErrors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("Back") }
            }
        },
    )
}

/** A warning the app must not hide, such as sample content or a bad file. */
@Composable
fun WarningCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatRow(vararg pairs: Pair<String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        pairs.forEach { (label, value) ->
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Text in a fixed-width font, for a TSV block you copy. */
@Composable
fun MonoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}
