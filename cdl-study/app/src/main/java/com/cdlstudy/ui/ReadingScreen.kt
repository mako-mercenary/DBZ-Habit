package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdlstudy.domain.FocusText
import com.cdlstudy.domain.ReadBlock

/**
 * The reading settings, with a sample that changes as you set them.
 *
 * These same controls sit at the top of every lesson. This screen exists so
 * they can be found without opening a subsection first.
 */
@Composable
fun ReadingScreen(onBack: () -> Unit) {
    val settings = readingSettings()
    val style = settings.style

    val sample = listOf(
        ReadBlock.Heading("What the governor does"),
        ReadBlock.Paragraph(
            "The governor decides when the compressor pumps. It does not make the " +
                "air. It only switches the pumping on and off, to hold the tank " +
                "pressure in a band."
        ),
        ReadBlock.Numbers(
            listOf(
                ReadBlock.Numbers.Row("125 psi", "cut-out: the governor stops the pumping"),
                ReadBlock.Numbers.Row("100 psi", "cut-in: the governor starts it again"),
            )
        ),
        ReadBlock.Note("A number never goes half bold. The number is the fact."),
    )

    Scaffold(topBar = { Bar("Reading", onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Focus text puts the first letters of each word in bold, so your " +
                        "eye lands on the start of the word. It is the bionic-style " +
                        "effect. Research is mixed on whether it makes anybody read " +
                        "faster, so set it the way it suits you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Focus text", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = style.focus,
                            onClick = { settings.toggleFocus() },
                            label = { Text(if (style.focus) "On" else "Off") },
                        )
                        FocusText.Strength.entries.forEach { option ->
                            FilterChip(
                                selected = style.focus && style.strength == option,
                                enabled = style.focus,
                                onClick = { settings.chooseStrength(option) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Size and spacing", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { settings.smaller() }) { Text("A-") }
                        OutlinedButton(onClick = { settings.bigger() }) { Text("A+") }
                        Text(
                            "${Math.round(style.scale * 100)}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        FilterChip(
                            selected = style.wideSpacing,
                            onClick = { settings.toggleSpacing() },
                            label = { Text("Airy") },
                        )
                    }
                }
            }

            item { Text("How a lesson will look", style = MaterialTheme.typography.titleSmall) }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Lesson(sample, style, Modifier.padding(16.dp))
                }
            }

            item {
                Text(
                    "Where to find this again: the same controls are at the top of " +
                        "every lesson, above the text.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
