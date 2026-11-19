package com.saiyanhabits.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.GameConfig
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.JournalEntryEntity
import com.saiyanhabits.data.JournalSaveResult
import com.saiyanhabits.domain.JournalPrompt
import com.saiyanhabits.domain.JournalRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EntryDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())

/** What the journal screen is showing. */
private sealed interface JournalMode {
    data object List : JournalMode
    data class Write(val prompt: JournalPrompt?, val entryId: Long?) : JournalMode
    data class Read(val entryId: Long) : JournalMode
}

/** Bulma's journal: today's prompt, writing, and every entry so far. */
@Composable
fun JournalScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val journal = app.journalRepository
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    val entries by journal.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    var mode by remember { mutableStateOf<JournalMode>(JournalMode.List) }
    var prompt by remember { mutableStateOf<JournalPrompt?>(null) }
    var swapsLeft by remember { mutableIntStateOf(0) }
    var bulmaSays by remember { mutableStateOf<String?>(null) }
    var lastSave by remember { mutableStateOf<JournalSaveResult?>(null) }
    var streak by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        prompt = journal.todaysPrompt()
        swapsLeft = journal.swapsLeft()
    }
    LaunchedEffect(entries) { streak = journal.streak() }

    val todayDay = LocalDate.now().toEpochDay()
    val doneToday = entries.any { it.epochDay == todayDay && it.bondXp > 0 }

    when (val m = mode) {
        is JournalMode.Write -> {
            val existing = m.entryId?.let { id -> entries.firstOrNull { it.id == id } }
            WriteEntry(
                prompt = m.prompt,
                existing = existing,
                onCancel = { mode = JournalMode.List },
                onSave = { text ->
                    scope.launch {
                        val result = journal.save(m.prompt, text, m.entryId)
                        lastSave = result
                        bulmaSays = result.line
                        if (result.reward.any) sounds?.play(SoundKey.CHECK_OFF)
                        mode = JournalMode.List
                    }
                },
            )
            return
        }
        is JournalMode.Read -> {
            val entry = entries.firstOrNull { it.id == m.entryId }
            if (entry == null) {
                mode = JournalMode.List
                return
            }
            ReadEntry(
                entry = entry,
                onBack = { mode = JournalMode.List },
                onEdit = { mode = JournalMode.Write(entry.promptId.takeIf { it > 0 }?.let { JournalPrompt(it, "", entry.prompt) }, entry.id) },
                onDelete = {
                    scope.launch { journal.delete(entry.id) }
                    mode = JournalMode.List
                },
            )
            return
        }
        JournalMode.List -> Unit
    }

    var query by rememberSaveable { mutableStateOf("") }
    SubScreen(title = "Journal", onBack = onBack) {
        // Bulma, with today's prompt.
        DbzPanel(
            modifier = Modifier.fillMaxWidth(),
            style = PanelStyle.Orange,
            watermark = { MentorWatermark(Mentor.BULMA) },
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MentorPortrait(Mentor.BULMA, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        TabLabel("Today's prompt")
                        Text("from Bulma", style = MaterialTheme.typography.bodySmall, color = Mentor.BULMA.color)
                    }
                }
                Text(prompt?.text ?: "…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    if (doneToday) {
                        "Done for today: +${GameConfig.JOURNAL_BOND_XP} bond with Bulma, +${GameConfig.JOURNAL_POWER} power. " +
                            "You can keep writing."
                    } else {
                        "Write at least ${GameConfig.JOURNAL_MIN_WORDS} words for +${GameConfig.JOURNAL_BOND_XP} bond with Bulma " +
                            "and +${GameConfig.JOURNAL_POWER} power."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doneToday) SaiyanColors.Gold else SaiyanColors.TextDim,
                )
                if (streak > 1) {
                    Text("$streak days in a row", style = MaterialTheme.typography.titleSmall, color = SaiyanColors.Gold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DbzSecondaryButton(
                        onClick = {
                            scope.launch {
                                val swap = journal.swapPrompt()
                                prompt = swap.prompt
                                swapsLeft = journal.swapsLeft()
                                bulmaSays = swap.line
                                lastSave = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (swapsLeft > 0) "Different one" else "No swaps left") }
                    DbzPrimaryButton(
                        onClick = { mode = JournalMode.Write(prompt, null) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Write") }
                }
                TextButton(onClick = { mode = JournalMode.Write(null, null) }) {
                    Text("Or write freely, no prompt", color = SaiyanColors.TextDim)
                }
            }
        }

        // What Bulma said last.
        bulmaSays?.takeIf { it.isNotBlank() }?.let { line ->
            DbzPanel(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    MentorPortrait(Mentor.BULMA, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("BULMA", style = MaterialTheme.typography.labelLarge, color = Mentor.BULMA.color)
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        lastSave?.takeIf { it.reward.any }?.let { save ->
                            Text(
                                "+${save.reward.bondXp} bond with Bulma · +${save.reward.power} power" +
                                    (save.bondLevelUp?.let { " · Bond level $it!" } ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                                color = SaiyanColors.Gold,
                            )
                        }
                    }
                }
            }
        }

        // Every entry so far.
        if (entries.isNotEmpty()) {
            SectionLabel("Your entries (${entries.size})")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search your entries") },
                modifier = Modifier.fillMaxWidth(),
            )
            val shown = if (query.isBlank()) entries else entries.filter {
                it.text.contains(query, ignoreCase = true) || it.prompt.contains(query, ignoreCase = true)
            }
            if (shown.isEmpty()) {
                Text("Nothing matches \"$query\".", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
            }
            shown.forEach { entry -> EntryCard(entry, onClick = { mode = JournalMode.Read(entry.id) }) }
        } else {
            Text(
                "Your entries will collect here. They stay on your phone, and they're included when you export a backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
            )
        }
        Spacer(Modifier.padding(4.dp))
    }
}

@Composable
private fun EntryCard(entry: JournalEntryEntity, onClick: () -> Unit) {
    DbzPanel(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.ofEpochDay(entry.epochDay).format(EntryDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text("${entry.words} words", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
            }
            if (entry.prompt.isNotBlank()) {
                Text(entry.prompt, style = MaterialTheme.typography.bodySmall, color = Mentor.BULMA.color, maxLines = 2)
            }
            Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextHigh, maxLines = 3)
        }
    }
}

/** Writing or editing one entry. */
@Composable
private fun WriteEntry(
    prompt: JournalPrompt?,
    existing: JournalEntryEntity?,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var text by rememberSaveable { mutableStateOf(existing?.text ?: "") }
    val words = JournalRules.wordCount(text)
    val enough = words >= GameConfig.JOURNAL_MIN_WORDS
    SubScreen(
        title = if (existing == null) "New entry" else "Edit entry",
        onBack = onCancel,
        actions = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Save", color = if (text.isNotBlank()) SaiyanColors.Gold else SaiyanColors.TextDim)
            }
        },
    ) {
        if (prompt != null && prompt.text.isNotBlank()) {
            DbzPanel(modifier = Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    MentorPortrait(Mentor.BULMA, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(prompt.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Start anywhere. Bulma isn't grading your spelling.") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
        )
        Text(
            if (enough) "$words words · enough to count" else "$words / ${GameConfig.JOURNAL_MIN_WORDS} words",
            style = MaterialTheme.typography.titleSmall,
            color = if (enough) SaiyanColors.Gold else SaiyanColors.TextDim,
        )
        DbzPrimaryButton(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Save entry")
        }
    }
}

/** One entry, in full. */
@Composable
private fun ReadEntry(entry: JournalEntryEntity, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    if (entry.bondXp > 0) "It can't be undone, and the bond and power it earned go with it." else "It can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = SaiyanColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    SubScreen(
        title = LocalDate.ofEpochDay(entry.epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
        onBack = onBack,
        actions = {
            TextButton(onClick = onEdit) { Text("Edit", color = SaiyanColors.Gold) }
        },
    ) {
        if (entry.prompt.isNotBlank()) {
            DbzPanel(modifier = Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                Text(entry.prompt, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
        Text(entry.text, style = MaterialTheme.typography.bodyLarge, color = SaiyanColors.TextHigh)
        Text(
            "${entry.words} words" + if (entry.bondXp > 0) " · earned +${entry.bondXp} bond with Bulma" else "",
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )
        TextButton(onClick = { confirmDelete = true }) { Text("Delete entry", color = SaiyanColors.Danger) }
    }
}

/** The home screen's way into the journal: today's prompt, and whether today's entry is done. */
@Composable
fun JournalHomeCard(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val entries by app.journalRepository.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    var prompt by remember { mutableStateOf<JournalPrompt?>(null) }
    LaunchedEffect(Unit) { prompt = app.journalRepository.todaysPrompt() }
    val todayDay = LocalDate.now().toEpochDay()
    val done = entries.any { it.epochDay == todayDay && it.bondXp > 0 }
    DbzPanel(
        onClick = onClick,
        style = if (done) PanelStyle.Blue else PanelStyle.Orange,
        watermark = { MentorWatermark(Mentor.BULMA) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MentorPortrait(Mentor.BULMA, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TabLabel(if (done) "Journal · done today" else "Journal")
                Text(
                    if (done) "Bulma's filed it. Tap to write more or read back." else prompt?.text ?: "Bulma has a question for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 3,
                )
            }
        }
    }
}
