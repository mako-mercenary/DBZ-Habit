package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdlstudy.domain.Mark
import com.cdlstudy.domain.NumberDrill

/**
 * Numbers, one at a time, typed from memory.
 *
 * The keyboard is the numeric one, because every answer here is a number.
 */
@Composable
fun NumberDrillScreen(onBack: () -> Unit, onCards: () -> Unit) {
    val repo = repository()
    val vm: NumberDrillViewModel = viewModel(
        factory = viewModelFactory { initializer { NumberDrillViewModel(repo) } },
    )

    Scaffold(topBar = { Bar("Numbers", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.items.isEmpty()) {
                item { Text("No numbers to drill yet.", style = MaterialTheme.typography.bodyMedium) }
                return@LazyColumn
            }
            if (vm.finished) {
                item { Finished(vm, onCards) }
                return@LazyColumn
            }

            val item = vm.item ?: return@LazyColumn
            item {
                Overline("${vm.at + 1} of ${vm.items.size}")
                Text(
                    if (item.blanks == 1) "One number" else "${item.blanks} numbers",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        item.prompt,
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = vm.typed,
                    onValueChange = vm::type,
                    enabled = vm.mark == null,
                    label = { Text(if (item.blanks == 1) "The number" else "The numbers, in order") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                if (vm.mark == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NextButton("Check", enabled = vm.typed.isNotBlank(), onClick = vm::answer)
                        OutlinedButton(onClick = vm::reveal) { Text("I don't know") }
                    }
                } else {
                    Verdict(vm, item)
                }
            }
            item {
                Text(
                    "A fraction counts either way: 4/32 or 4 32.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Verdict(vm: NumberDrillViewModel, item: NumberDrill.Item) {
    val mark = vm.mark ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (mark == Mark.HIT) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (mark) {
                        Mark.HIT -> "Right"
                        Mark.PARTIAL -> "Half of it"
                        Mark.MISS -> "No"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(item.answer, style = MaterialTheme.typography.bodyMedium)
                item.page?.let {
                    Text("Manual page $it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        NextButton(
            if (vm.at + 1 >= vm.items.size) "See the score" else "Next number",
            onClick = vm::next,
        )
    }
}

@Composable
private fun Finished(vm: NumberDrillViewModel, onCards: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("Done")
        Text("${vm.hits} of ${vm.items.size} exactly right", style = MaterialTheme.typography.headlineSmall)
        GoldProgress(vm.percent)
        Text(
            "Scored with a half mark for one number out of two. " +
                "A number you missed is queued as a card.",
            style = MaterialTheme.typography.bodyMedium,
        )
        vm.cardsAdded?.let { added ->
            Text(
                if (added == 0) {
                    "No new cards: either you had them all, or they are in the queue already."
                } else {
                    "$added new card(s) in the deck."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NextButton("Another round", onClick = vm::start)
        OutlinedButton(onClick = onCards, modifier = Modifier.fillMaxWidth()) { Text("Go to the cards") }
    }
}
