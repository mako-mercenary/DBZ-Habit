package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdlstudy.domain.Review

/**
 * The cards, reviewed here.
 *
 * Nothing leaves the app. A card you miss comes back inside ten minutes, one
 * you know goes away for days, and with a test date set nothing is ever booked
 * for after the test.
 */
@Composable
fun CardsScreen(onBack: (() -> Unit)?) {
    val repo = repository()
    val dates = testDateSettings()
    val vm: ReviewViewModel = viewModel(
        factory = viewModelFactory { initializer { ReviewViewModel(repo) { dates.date } } },
    )
    val total by repo.cardCount.collectAsStateWithLifecycle(0)
    val fresh by repo.newCardCount.collectAsStateWithLifecycle(0)

    Scaffold(topBar = { Bar("Cards", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.loading) {
                item { Text("Looking at the deck...", style = MaterialTheme.typography.bodyMedium) }
                return@LazyColumn
            }

            if (total == 0) {
                item { Empty() }
                return@LazyColumn
            }

            if (vm.finished) {
                item { Done(vm, total, fresh, dates) }
                return@LazyColumn
            }

            val card = vm.card ?: return@LazyColumn
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline("${vm.left} to go")
                    Text(
                        dates.daysLeft()?.let { days ->
                            if (days >= 0) "$days days to the test" else "test date passed"
                        } ?: "no test date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            card.question,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!vm.showingAnswer) {
                item { NextButton("Show the answer", onClick = vm::reveal) }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                card.answer,
                                style = MaterialTheme.typography.titleMedium,
                                color = Gold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            card.factId?.let {
                                Text(
                                    "${card.subsectionId} · fact $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                item { Grades(vm) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::resetCard) { Text("Start this card over") }
                    }
                }
            }

            item {
                Text(
                    if (card.reps == 0) {
                        "You have not seen this one before."
                    } else {
                        "Seen ${card.reps} time(s)" +
                            if (card.lapses > 0) ", forgotten ${card.lapses}" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The four buttons, each saying when the card would come back. */
@Composable
private fun Grades(vm: ReviewViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Review.Grade.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { grade ->
                    val miss = grade == Review.Grade.AGAIN
                    Button(
                        onClick = { vm.answer(grade) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = if (miss) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(grade.label, fontWeight = FontWeight.Bold)
                            Text(
                                vm.preview[grade].orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Done(vm: ReviewViewModel, total: Int, fresh: Int, dates: TestDateSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("Clear")
        Text("Nothing due", style = MaterialTheme.typography.headlineMedium)
        if (vm.answered > 0) {
            Text(
                "${vm.right} of ${vm.answered} right this sitting.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "$total cards in the deck" + if (fresh > 0) ", $fresh never seen" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        dates.daysLeft()?.let { days ->
            if (days >= 0) {
                Text(
                    "With $days days to the test, nothing is booked for after it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedButton(onClick = vm::start, modifier = Modifier.fillMaxWidth()) {
            Text("Look again")
        }
    }
}

@Composable
private fun Empty() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Empty")
        Text("No cards yet", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Cards make themselves. Record a recall and every fact you missed becomes " +
                "a card, along with every must-memorize fact whether you missed it or not. " +
                "An exam and the drills add them too.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
