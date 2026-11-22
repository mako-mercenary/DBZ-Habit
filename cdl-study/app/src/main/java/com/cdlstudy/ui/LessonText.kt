package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdlstudy.domain.FocusText
import com.cdlstudy.domain.ReadBlock

/** How the lesson is drawn. The reader sets all of it. */
data class ReadStyle(
    val focus: Boolean,
    val strength: FocusText.Strength,
    /** 1.0 is the normal size. */
    val scale: Float,
    val wideSpacing: Boolean,
)

/**
 * Puts the first letters of each word in bold, the bionic-style effect.
 *
 * Nothing returns early here. A remember behind an early return changes what
 * the screen holds when focus is switched on and off, and that is a fault
 * waiting for the moment somebody uses the switch.
 */
@Composable
fun focusAnnotated(text: String, style: ReadStyle): AnnotatedString =
    remember(text, style.focus, style.strength) {
        val ranges =
            if (style.focus) FocusText.boldRanges(text, style.strength) else emptyList()
        buildAnnotatedString {
            append(text)
            ranges.forEach { span ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
            }
        }
    }

@Composable
fun Lesson(blocks: List<ReadBlock>, style: ReadStyle, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((if (style.wideSpacing) 16 else 10).dp),
    ) {
        blocks.forEach { block -> LessonBlock(block, style) }
    }
}

@Composable
private fun LessonBlock(block: ReadBlock, style: ReadStyle) {
    val body = (17 * style.scale).sp
    val lineHeight = (if (style.wideSpacing) 1.75f else 1.45f) * 17 * style.scale
    when (block) {
        is ReadBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (19 * style.scale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        is ReadBlock.Paragraph -> FocusParagraph(block.text, style, body, lineHeight.sp)

        is ReadBlock.Bullets -> Column(
            verticalArrangement = Arrangement.spacedBy((if (style.wideSpacing) 12 else 8).dp)
        ) {
            block.items.forEach { item ->
                Row {
                    Text("•", fontSize = body, modifier = Modifier.width((16 * style.scale).dp))
                    FocusParagraph(item, style, body, lineHeight.sp)
                }
            }
        }

        is ReadBlock.Note -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FocusParagraph(
                block.text,
                style,
                body,
                lineHeight.sp,
                Modifier.padding(12.dp),
            )
        }

        is ReadBlock.Numbers -> Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.rows.forEach { row ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            row.value,
                            fontWeight = FontWeight.Bold,
                            fontSize = body,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width((84 * style.scale).dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        FocusParagraph(row.what, style, body, lineHeight.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusParagraph(
    text: String,
    style: ReadStyle,
    size: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = focusAnnotated(text, style),
        fontSize = size,
        lineHeight = lineHeight,
        modifier = modifier,
    )
}
