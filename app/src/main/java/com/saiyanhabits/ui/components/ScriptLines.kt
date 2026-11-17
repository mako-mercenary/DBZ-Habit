package com.saiyanhabits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.SceneLine
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color

/**
 * A scene read top to bottom like a script: mentors speak from the left with
 * their portrait, the player answers from the right, narration runs between.
 */
@Composable
fun ColumnScope.ScriptLines(lines: List<SceneLine>) {
    lines.forEach { line ->
        val speaker = line.mentor
        when {
            speaker != null -> SpeechBubble(
                name = speaker.displayName,
                text = line.text,
                nameColor = speaker.color,
                alignment = Alignment.CenterStart,
                portrait = speaker,
            )
            line.speaker == "YOU" -> SpeechBubble(
                name = "You",
                text = line.text,
                nameColor = SaiyanColors.Blue,
                alignment = Alignment.CenterEnd,
                portrait = null,
            )
            else -> Text(
                line.text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = SaiyanColors.TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun SpeechBubble(name: String, text: String, nameColor: Color, alignment: Alignment, portrait: Mentor?) {
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.Top) {
            if (portrait != null) {
                MentorPortrait(portrait, Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (portrait != null) SaiyanColors.Panel else SaiyanColors.BlueDeep.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, SaiyanColors.Outline),
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = nameColor)
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
