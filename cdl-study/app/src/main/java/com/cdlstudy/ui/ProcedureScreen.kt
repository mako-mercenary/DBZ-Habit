package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdlstudy.domain.Procedure
import com.cdlstudy.domain.Step

/** The list of procedures you can be asked to perform in order. */
@Composable
fun ProcedureListScreen(onBack: () -> Unit, onProcedure: (String) -> Unit) {
    val repo = repository()
    val procedures = repo.content.procedures

    Scaffold(topBar = { Bar("In order", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Overline("Step order")
                Text("Put it back in order", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "These are scored on the order, not on whether you know the parts. " +
                        "The steps come up shuffled and you tap them in order. There is no " +
                        "partial credit, because the examiner does not give you nine tenths " +
                        "of the air brake check.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(procedures, key = { it.id }) { procedure ->
                Card(onClick = { onProcedure(procedure.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(procedure.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${procedure.length} steps",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold,
                        )
                        Text(
                            procedure.why,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (procedures.isEmpty()) {
                item { Text("No procedures are written yet.", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun ProcedureScreen(procedureId: String, onBack: () -> Unit, onStudy: (String) -> Unit) {
    val repo = repository()
    val procedure: Procedure? = repo.content.procedure(procedureId)
    if (procedure == null) {
        Scaffold(topBar = { Bar("In order", onBack) }) { pad ->
            Text("That procedure is not loaded.", Modifier.padding(pad).padding(16.dp))
        }
        return
    }
    val vm: ProcedureViewModel = viewModel(
        key = "procedure-$procedureId",
        factory = viewModelFactory { initializer { ProcedureViewModel(repo, procedure) } },
    )
    val result = vm.result

    Scaffold(topBar = { Bar(procedure.title, onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(procedure.why, style = MaterialTheme.typography.bodyMedium)
            }

            if (result != null) {
                item { Score(vm, procedure, onStudy) }
                itemsIndexed(result.correct) { index, step ->
                    val yours = result.given.getOrNull(index)
                    val right = index !in result.wrongPositions
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (right) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ),
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${index + 1}. ${step.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (right) FontWeight.Normal else FontWeight.Bold,
                            )
                            if (!right) {
                                Text(
                                    "you put: " + (yours?.text ?: "nothing"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Overline("Your order: ${vm.placed.size} of ${procedure.length}")
            }
            itemsIndexed(vm.placed) { index, step ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        "${index + 1}. ${step.text}",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (vm.placed.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::undo) { Text("Take back the last one") }
                        OutlinedButton(onClick = vm::start) { Text("Start again") }
                    }
                }
            }
            if (vm.pool.isNotEmpty()) {
                item {
                    Text(
                        "Tap them in the order you would do them",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(vm.pool, key = { it.text }) { step ->
                    StepChoice(step, onClick = { vm.place(step) })
                }
            } else {
                item { NextButton("Check my order", onClick = vm::check) }
            }
        }
    }
}

@Composable
private fun StepChoice(step: Step, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(step.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Score(vm: ProcedureViewModel, procedure: Procedure, onStudy: (String) -> Unit) {
    val result = vm.result ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (result.passed) "Correct, all ${result.total} in order" else "Not yet",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!result.passed) {
            Text(
                "${result.inPlace} of ${result.total} in the right place. " +
                    "That is a fail: the order has to be right all the way through.",
                style = MaterialTheme.typography.bodyMedium,
            )
            vm.cardsAdded?.let { added ->
                if (added > 0) {
                    Text(
                        "$added card(s) queued for the steps you moved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        NextButton("Try it again", onClick = vm::start)
        procedure.subsectionId?.let { id ->
            OutlinedButton(onClick = { onStudy(id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Read $id again")
            }
        }
    }
}
