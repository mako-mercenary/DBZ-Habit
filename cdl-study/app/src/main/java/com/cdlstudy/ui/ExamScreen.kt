package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdlstudy.domain.ExamStandards
import com.cdlstudy.domain.Track

@Composable
fun ExamScreen(onBack: () -> Unit) {
    val repo = repository()
    val vm: ExamViewModel = viewModel(
        factory = viewModelFactory { initializer { ExamViewModel(repo) } },
    )

    Scaffold(topBar = { Bar("Practice exams", onBack) }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (vm.stage) {
                ExamStage.PICK -> PickExam(vm)
                ExamStage.RUN -> RunExam(vm)
                ExamStage.DONE -> ExamDone(vm, onBack)
            }
        }
    }
}

@Composable
private fun PickExam(vm: ExamViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Six exams, each graded on its own. Vehicle Inspection is never " +
                    "mixed with Basic Vehicle Control or On-Road Driving.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(Track.entries) { track ->
            val pool = vm.poolSize(track)
            Card(
                onClick = { vm.pick(track) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(track.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$pool questions available | default length ${ExamStandards.defaultLength(track)}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (ExamStandards.needsRoadPractice(track)) {
                        Text(
                            "This exam tests knowledge only. You still need practice behind the wheel.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (vm.track == track) {
                        Text(
                            if (pool == 0) {
                                "No questions are loaded for this track yet."
                            } else {
                                "Selected. Length: ${vm.length} questions."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 25, 50).forEach { n ->
                    FilterChip(
                        selected = vm.length == n,
                        onClick = { vm.chooseLength(n) },
                        label = { Text("$n") },
                    )
                }
            }
        }
        item {
            Text(
                "WARNING: the official question count and pass score come from the " +
                    "Utah Driver License Division. Check their current numbers. " +
                    "This app uses ${ExamStandards.PASS_PERCENT}% as the pass level.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        item {
            Button(
                onClick = vm::start,
                enabled = vm.poolSize(vm.track) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start the ${vm.track.label} exam") }
        }
    }
}

@Composable
private fun RunExam(vm: ExamViewModel) {
    val item = vm.items.getOrNull(vm.at) ?: return
    val chosen = vm.answers[item.id]
    LinearProgressIndicator(
        progress = { (vm.at + 1).toFloat() / vm.items.size },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Question ${vm.at + 1} of ${vm.items.size}",
        style = MaterialTheme.typography.labelLarge,
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(item.question, style = MaterialTheme.typography.titleMedium)
        }
        items(item.choices.size) { index ->
            Card(
                onClick = { vm.answer(item.id, index) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == index, onClick = { vm.answer(item.id, index) })
                    Text(item.choices[index], style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.at > 0) OutlinedButton(onClick = vm::back) { Text("Back") }
                Button(onClick = vm::next, enabled = chosen != null) {
                    Text(if (vm.at == vm.items.lastIndex) "Finish" else "Next")
                }
            }
        }
    }
}

@Composable
private fun ExamDone(vm: ExamViewModel, onBack: () -> Unit) {
    val result = vm.result ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.track.label, style = MaterialTheme.typography.titleMedium)
                    StatRow(
                        "score" to "${result.percent}%",
                        "correct" to "${result.correct}/${result.total}",
                        "new cards" to "${vm.cardsAdded}",
                    )
                    Text(
                        when {
                            result.mastered -> "Mastery level."
                            result.passed -> "Pass level. Keep going for 90%."
                            else -> "Below ${ExamStandards.PASS_PERCENT}%. Study the missed facts again."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (result.missed.isNotEmpty()) {
            item { Text("What you missed", style = MaterialTheme.typography.titleMedium) }
            items(result.missed, key = { it.id }) { item ->
                val chosen = vm.answers[item.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.question, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Your answer: " + (chosen?.let { item.choices.getOrNull(it) } ?: "none"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Correct answer: ${item.answerText}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        item.why?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        item.page?.let {
                            Text("Manual page $it", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::restart) { Text("Another exam") }
                OutlinedButton(onClick = onBack) { Text("Done") }
            }
        }
    }
}
