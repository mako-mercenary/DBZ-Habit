package com.cdlstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The small gold line above a headline: where you are. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = OverlineStyle, modifier = modifier)
}

/** STEP 1, STEP 2: the outlined gold pill beside a section name. */
@Composable
fun StepChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Transparent)
            .padding(0.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, GoldDim),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text.uppercase(),
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

/** A row heading: name, step chip, a line under it, and an action on the right. */
@Composable
fun RowHeader(
    title: String,
    step: String? = null,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (step != null) {
                Box(Modifier.padding(start = 10.dp)) { StepChip(step) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                subtitle.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (action != null && onAction != null) {
                Text(
                    action,
                    color = Gold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp),
                )
            }
        }
    }
}

/** The big gold button that always points at the next thing to do. */
@Composable
fun NextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = GoldInk,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The progress bar with the percentage written across it. */
@Composable
fun GoldProgress(percent: Int, modifier: Modifier = Modifier) {
    val safe = percent.coerceIn(0, 100)
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(safe / 100f)
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0xFF6B4F12), Gold))),
        )
        Text(
            "$safe%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A tile in a row: a number held in the art, a title and one line of detail.
 * The gold rule under the art shows how far through it you are.
 */
@Composable
fun Tile(
    tag: String,
    number: String,
    title: String,
    meta: String,
    progress: Float = 0f,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(156.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF232A33), Color(0xFF12161A)),
                    )
                ),
        ) {
            Text(
                tag.uppercase(),
                style = OverlineStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            )
            Text(
                number,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = Gold.copy(alpha = 0.22f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp),
            )
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Gold)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text(badge, color = GoldInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Gold),
            )
        }
        Column(Modifier.padding(11.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
