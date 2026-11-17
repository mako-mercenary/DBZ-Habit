package com.saiyanhabits.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.ui.theme.SaiyanColors

/** How a day looks in the calendar and the recap. */
private data class DotLook(val fill: Brush?, val border: Color?, val text: Color)

private fun lookFor(outcome: DayOutcome?): DotLook = when (outcome) {
    DayOutcome.PERFECT -> DotLook(Brush.verticalGradient(listOf(SaiyanColors.TabGoldTop, SaiyanColors.TabGoldBottom)), SaiyanColors.FrameGoldDark, SaiyanColors.TabInk)
    DayOutcome.BEAN_COVERED -> DotLook(Brush.verticalGradient(listOf(Color(0xFF9BEA86), Color(0xFF3E9A33))), Color(0xFF1F5A18), Color(0xFF0E2A0A))
    DayOutcome.MISSED, DayOutcome.BROKEN -> DotLook(Brush.verticalGradient(listOf(SaiyanColors.RibbonRedTop, SaiyanColors.RibbonRedBottom)), Color(0xFF5A0805), Color.White)
    DayOutcome.REST -> DotLook(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f))), SaiyanColors.TextDim.copy(alpha = 0.55f), SaiyanColors.TextDim)
    null -> DotLook(null, null, SaiyanColors.TextDim.copy(alpha = 0.55f))
}

/**
 * A day as a coloured circle: gold for perfect, green for saved by a bean,
 * red for missed, faint for a rest day, bare for no record. [label] goes in
 * the middle.
 */
@Composable
fun DayDot(
    outcome: DayOutcome?,
    label: String,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val look = lookFor(outcome)
    var m = modifier.clip(CircleShape)
    if (look.fill != null) m = m.background(look.fill)
    when {
        selected -> m = m.border(3.dp, SaiyanColors.Orange, CircleShape)
        isToday -> m = m.border(2.dp, Color.White, CircleShape)
        look.border != null -> m = m.border(1.dp, look.border, CircleShape)
    }
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = look.text)
    }
}

/** What the day colours mean. */
@Composable
fun DayLegend(modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LegendItem(DayOutcome.PERFECT, "Perfect")
        LegendItem(DayOutcome.REST, "Rest")
        LegendItem(DayOutcome.BEAN_COVERED, "Bean")
        LegendItem(DayOutcome.BROKEN, "Missed")
    }
}

@Composable
private fun LegendItem(outcome: DayOutcome, text: String, size: Dp = 14.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DayDot(outcome, "", Modifier.size(size))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
    }
}

/** A plain-words name for a finished day's outcome. */
fun DayOutcome.describe(): String = when (this) {
    DayOutcome.PERFECT -> "Perfect day"
    DayOutcome.REST -> "Rest day"
    DayOutcome.BEAN_COVERED -> "Missed, saved by a healing bean"
    DayOutcome.MISSED -> "Missed, a bean can still save it"
    DayOutcome.BROKEN -> "Missed"
}
