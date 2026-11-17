package com.saiyanhabits.ui.settings

import com.saiyanhabits.ui.components.DbzSecondaryButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.BackupCheck
import com.saiyanhabits.domain.Backup
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DataUiState(
    /** True while a file is being written or read. */
    val busy: Boolean = false,
    /** An export read and checked, waiting for the user to confirm replacing everything. */
    val pendingImport: Backup? = null,
    /** The result of the last export or import, to show. */
    val message: String? = null,
)

class DataViewModel(private val app: SaiyanApp) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state

    fun export(uri: Uri) = run {
        val backup = app.backupRepository.export(uri)
        "Saved: ${summary(backup)}."
    }

    fun check(uri: Uri) {
        _state.value = DataUiState(busy = true)
        viewModelScope.launch {
            _state.value = when (val result = app.backupRepository.check(uri)) {
                is BackupCheck.Ready -> DataUiState(pendingImport = result.backup)
                is BackupCheck.Refused -> DataUiState(message = "Not imported. ${result.reason}")
            }
        }
    }

    fun confirmImport() {
        val backup = _state.value.pendingImport ?: return
        run {
            app.backupRepository.restore(backup)
            // Reminders follow the imported habits; days that ended since the export are recorded.
            app.habitRepository.getActiveHabits().forEach(app.reminders::onHabitSaved)
            app.habitRepository.syncDays()
            "Imported: ${summary(backup)}."
        }
    }

    fun cancelImport() {
        _state.value = DataUiState()
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun run(work: suspend () -> String) {
        _state.value = DataUiState(busy = true)
        viewModelScope.launch {
            val message = try {
                work()
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: e.javaClass.simpleName}. Nothing was changed."
            }
            _state.value = DataUiState(message = message)
        }
    }

    companion object {
        fun summary(backup: Backup): String = listOf(
            backup.count("habits") to "habit",
            backup.count("completions") to "check-off",
            backup.count("sagas") to "saga",
            backup.count("spirit_bombs") to "spirit bomb",
            backup.count("wishes") to "wish",
        ).joinToString(", ") { (n, what) -> "$n $what" + if (n == 1) "" else if (what.endsWith("sh")) "es" else "s" }
    }
}

/** Settings > Your data: export everything to a file, or replace everything from one. */
@Composable
fun DataSection(state: DataUiState, onExport: (Uri) -> Unit, onImport: (Uri) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    SectionLabel("Your data")
    Text(
        "Export saves everything (habits, check-offs, streaks, sagas, bonds, wishes) to one JSON file " +
            "you can keep anywhere. Import replaces everything in the app with an export. " +
            "Pictures chosen under Art aren't included.",
        style = MaterialTheme.typography.bodyMedium,
        color = SaiyanColors.TextDim,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DbzSecondaryButton(
            onClick = { exporter.launch("saiyan-habits-${LocalDate.now()}.json") },
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Export data") }
        DbzSecondaryButton(
            // Any file: phones and cloud drives label .json files inconsistently, and the
            // import checks the contents itself.
            onClick = { importer.launch(arrayOf("*/*")) },
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Import data") }
    }
    if (state.busy) Text("Working…", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
    state.message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = if (it.startsWith("Not") || it.startsWith("Something")) SaiyanColors.Danger else SaiyanColors.Bean,
        )
    }

    state.pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Replace everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This export${if (backup.exportedAt.isNotBlank()) " from ${backup.exportedAt.replace('T', ' ')}" else ""} has ${DataViewModel.summary(backup)}.")
                    Text(
                        "Everything currently in the app will be replaced by it. Export first if you might want it back.",
                        color = SaiyanColors.Danger,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Replace", color = SaiyanColors.Danger) } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
    }
}
