package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdlstudy.domain.AiGrading
import com.cdlstudy.domain.Fact
import com.cdlstudy.domain.FocusText
import com.cdlstudy.domain.Mark

@Composable
fun StudyScreen(
    subsectionId: String,
    onDone: () -> Unit,
    onCards: () -> Unit,
    onManual: () -> Unit,
) {
    val repo = repository()
    val settings = readingSettings()
    val ai = aiSettings()
    val vm: StudyViewModel = viewModel(
        key = "study-$subsectionId",
        factory = viewModelFactory {
            initializer { StudyViewModel(repo, settings, ai, subsectionId) }
        },
    )
    val sub = vm.subsection

    Scaffold(topBar = { Bar(sub?.let { "${it.id} ${it.title}" } ?: "Study", onDone) }) { pad ->
        if (sub == null) {
            Text("That subsection is not loaded.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (vm.stage) {
                Stage.READ -> ReadStep(vm, onManual, onDone)
                Stage.BLURT -> BlurtStep(vm)
                Stage.GRADE -> GradeStep(vm)
                Stage.RESULT -> ResultStep(vm, onDone, onCards)
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadStep(vm: StudyViewModel, onManual: () -> Unit, onDone: () -> Unit) {
    val sub = vm.subsection!!
    val store = manualStore()
    val lesson = sub.lesson
    val style = vm.readStyle

    ReaderControls(vm)

    if (vm.showManualPages) {
        val span = sub.pdfPages
        when {
            span == null -> WarningCard("This subsection has no manual pages in the content file.")
            !store.isImported -> {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No manual imported", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The pages of the book come from your own copy. Import it, or read " +
                                "the lesson in the app instead.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Button(onClick = onManual) { Text("Import the manual") }
            }
            else -> ManualPages(
                span = span,
                zoom = vm.zoom,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    } else if (lesson.isEmpty()) {
        WarningCard(
            "No lesson and no facts are written for ${sub.id} yet. " +
                "Switch on the manual pages to read it in your own copy of the book."
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${sub.id} ${sub.title}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        buildString {
                            append("${com.cdlstudy.domain.Reading.minutes(lesson)} min read")
                            sub.manualPages?.let { append(" | manual $it") }
                            if (!sub.hasLesson) append(" | fact list")
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { Lesson(lesson, style) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (sub.canRecall) {
        Text(
            "${sub.facts.size} facts, ${sub.criticalFacts.size} of them must-memorize. " +
                "The next screen hides the lesson.",
            style = MaterialTheme.typography.labelSmall,
        )
        Button(onClick = vm::startBlurt, modifier = Modifier.fillMaxWidth()) {
            Text("Close the book and start my recall")
        }
    } else {
        Text(
            "The fact checklist for this subsection is not written yet, so a recall " +
                "cannot be graded here. You can still read it and mark it read.",
            style = MaterialTheme.typography.labelSmall,
        )
        Button(
            onClick = vm::markReadOnly,
            enabled = !vm.readMarked,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.readMarked) "Marked as read" else "Mark this as read") }
    }
}

/** Focus text, size, spacing, and the switch to the pages of the book. */
@Composable
private fun ReaderControls(vm: StudyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = vm.readStyle.focus,
                onClick = { vm.toggleFocus() },
                label = { Text("Focus") },
            )
            if (vm.readStyle.focus) {
                FocusText.Strength.entries.forEach { option ->
                    FilterChip(
                        selected = vm.readStyle.strength == option,
                        onClick = { vm.chooseStrength(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { vm.smaller() }) { Text("A-") }
            OutlinedButton(onClick = { vm.bigger() }) { Text("A+") }
            FilterChip(
                selected = vm.readStyle.wideSpacing,
                onClick = { vm.toggleSpacing() },
                label = { Text("Airy") },
            )
            FilterChip(
                selected = vm.showManualPages,
                onClick = { vm.toggleManualPages() },
                label = { Text("Book pages") },
            )
            if (vm.showManualPages) {
                FilterChip(
                    selected = vm.zoom > 1f,
                    onClick = { vm.chooseZoom(if (vm.zoom > 1f) 1f else 2f) },
                    label = { Text(if (vm.zoom > 1f) "2x" else "Fit") },
                )
            }
        }
    }
}

@Composable
private fun BlurtStep(vm: StudyViewModel) {
    Text("Step 2 of 4: recall", style = MaterialTheme.typography.labelLarge)
    Text(
        "Write everything you remember. Do not make it tidy. " +
            "Numbers, procedures and rules first.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = vm.blurt,
        onValueChange = vm::editBlurt,
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        label = { Text("Your recall") },
    )
    Button(
        onClick = vm::submitBlurt,
        enabled = vm.blurt.isNotBlank(),
    ) { Text("Send my recall and show the facts") }
}

@Composable
private fun GradeStep(vm: StudyViewModel) {
    val sub = vm.subsection!!
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Step 3 of 4: correct", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Mark each fact against your own words. The app suggests a mark from " +
                        "your keywords, but a keyword in a wrong sentence is still wrong. " +
                        "You decide every mark.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("What you wrote", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(vm.blurt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { AiCard(vm) }
        items(sub.facts, key = { it.id }) { fact ->
            FactRow(vm, fact)
        }
        item {
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save my score and make the cards")
            }
        }
    }
}

/**
 * The second opinion. It is the only thing in the app that uses the network,
 * and it does nothing until you press it.
 */
@Composable
private fun AiCard(vm: StudyViewModel) {
    if (!vm.aiReady) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val state = vm.aiState) {
                is AiState.Idle -> {
                    Text(
                        "A keyword cannot tell you that a number was backwards. " +
                            "${vm.aiModel} can read what you wrote and say.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = vm::askAi, modifier = Modifier.fillMaxWidth()) {
                        Text("Ask ${vm.aiModel} to check my recall")
                    }
                }

                is AiState.Asking -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.height(18.dp))
                    Text("Reading your words...", style = MaterialTheme.typography.bodyMedium)
                }

                is AiState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = vm::askAi, modifier = Modifier.fillMaxWidth()) {
                        Text("Try again")
                    }
                }

                is AiState.Done -> AiOpinion(vm, state.opinion)
            }
        }
    }
}

@Composable
private fun AiOpinion(vm: StudyViewModel, opinion: AiGrading.Opinion) {
    val differs = opinion.disagreements(vm.marks)
    Text("${vm.aiModel} says", style = MaterialTheme.typography.labelLarge)
    if (opinion.note.isNotEmpty()) {
        Text(opinion.note, style = MaterialTheme.typography.bodyMedium)
    }
    if (opinion.errors.isNotEmpty()) {
        Text(
            "Wrong in what you wrote",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        opinion.errors.forEach { error ->
            Text("- " + error, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        if (differs.isEmpty()) {
            "It marked every fact the same way you did."
        } else {
            "It marks ${differs.size} fact(s) differently. Each one is noted below."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    if (differs.isNotEmpty()) {
        OutlinedButton(onClick = vm::takeAiMarks, modifier = Modifier.fillMaxWidth()) {
            Text("Take its marks")
        }
    }
    Text(
        "It is a second opinion, not the grader. Your marks are what gets saved.",
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun FactRow(vm: StudyViewModel, fact: Fact) {
    val mark = vm.marks[fact.id] ?: Mark.MISS
    val suggestion = vm.suggestions[fact.id]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fact.critical) {
                Text(
                    "MUST MEMORIZE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(fact.text, style = MaterialTheme.typography.bodyLarge)
            fact.page?.let {
                Text("Manual page $it", style = MaterialTheme.typography.labelSmall)
            }
            val aiMark = vm.aiMarkOf(fact.id)
            if (aiMark != null) {
                val agrees = aiMark == mark
                Text(
                    buildString {
                        append(if (agrees) "AI agrees: " else "AI says ")
                        append(
                            when (aiMark) {
                                Mark.HIT -> "you said it"
                                Mark.PARTIAL -> "you said part of it"
                                Mark.MISS -> "you missed it"
                            }
                        )
                        vm.aiWhyOf(fact.id)?.takeIf { it.isNotEmpty() }?.let { append(". " + it) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (agrees) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (suggestion != null && suggestion.hasKeywords) {
                val found = suggestion.found.joinToString(", ").ifBlank { "none" }
                val missing = suggestion.missing.joinToString(", ").ifBlank { "none" }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("in your words: $found | not found: $missing") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mark.entries.forEach { option ->
                    FilterChip(
                        selected = mark == option,
                        onClick = { vm.setMark(fact.id, option) },
                        label = {
                            Text(
                                when (option) {
                                    Mark.HIT -> "Said it"
                                    Mark.PARTIAL -> "Part of it"
                                    Mark.MISS -> "Missed it"
                                }
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultStep(vm: StudyViewModel, onDone: () -> Unit, onCards: () -> Unit) {
    val saved = vm.saved ?: return
    val score = saved.score
    val relearn = vm.factsToRelearn()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Step 4 of 4: result", style = MaterialTheme.typography.labelLarge)
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow(
                        "must memorize" to "${score.critical}%",
                        "all facts" to "${score.overall}%",
                        "new cards" to "${saved.cardsAdded}",
                    )
                    Text(
                        "${score.hits} said, ${score.partials} part said, ${score.misses} missed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        when {
                            score.mastered -> "Mastery level. Move to the next subsection."
                            score.critical >= 80 -> "Pass level. Read your missed facts again, then move on."
                            else -> "Below 80%. Read this subsection again before the next one."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (relearn.isNotEmpty()) {
            item { Text("Read these again", style = MaterialTheme.typography.titleMedium) }
            items(relearn, key = { it.id }) { fact ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(fact.text, style = MaterialTheme.typography.bodyMedium)
                        fact.page?.let {
                            Text("Manual page $it", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCards) { Text("Go to my cards") }
                OutlinedButton(onClick = vm::again) { Text("Do it again") }
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}
