package com.saiyanhabits.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.saiyanhabits.data.HistoryRepository
import com.saiyanhabits.ui.theme.SaiyanColors

/** Writes or edits the line about one day. Saving it blank removes it. */
@Composable
fun NoteDialog(title: String, initial: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(HistoryRepository.NOTE_MAX_LENGTH) },
                placeholder = { Text("How did today go?") },
                supportingText = { Text("${text.length} / ${HistoryRepository.NOTE_MAX_LENGTH}", color = SaiyanColors.TextDim) },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (!initial.isNullOrBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Remove", color = SaiyanColors.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
