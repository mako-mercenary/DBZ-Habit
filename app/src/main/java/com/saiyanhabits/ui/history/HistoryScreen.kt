package com.saiyanhabits.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.R
import com.saiyanhabits.domain.HistoryRules
import com.saiyanhabits.domain.HistoryStats
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.GoldLettering
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val DayTitle = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val ChartDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

@Composable
fun HistoryScreen(
    state: HistoryUiState?,
    /** The overall streak as the home screen shows it, today included. */
    currentStreak: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onRange: (ChartRange) -> Unit,
    onOpenRecap: () -> Unit,
    onBack: () -> Unit,
) {
    SubScreen(title = "History", onBack = onBack) {
        if (state == null) return@SubScreen
        StatsPanel(HistoryRules.stats(state.days, currentStreak), currentStreak)
        CalendarPanel(state, onPrevMonth, onNextMonth, onSelectDay)
        state.selected?.let { DayPanel(it) }
        PowerChartPanel(state, onRange)
        if (state.days.isNotEmpty()) {
            DbzSecondaryButton(onClick = onOpenRecap, modifier = Modifier.fillMaxWidth()) {
                Text("Last week's recap")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatsPanel(stats: HistoryStats, currentStreak: Int) {
    DbzPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TabLabel("Your record")
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat("${stats.bestStreak}", "Best streak", Modifier.weight(1f).fillMaxHeight())
                Stat("$currentStreak", "Current streak", Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat("${stats.perfectDays}", "Perfect days", Modifier.weight(1f).fillMaxHeight())
                Stat(stats.completionPercent?.let { "$it%" } ?: "-", "Habits done", Modifier.weight(1f).fillMaxHeight())
            }
            Text(
                if (stats.daysTracked == 1) "1 finished day on record." else "${stats.daysTracked} finished days on record.",
                style = MaterialTheme.typography.bodySmall,
                color = SaiyanColors.TextDim,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        OutlinedText(
            value,
            MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 40.sp),
            fill = GoldLettering,
            outlineWidth = 4.dp,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
    }
}

@Composable
private fun CalendarPanel(
    state: HistoryUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
) {
    val todayDay = state.today.toEpochDay()
    val firstTracked = state.days.firstOrNull()?.epochDay
    DbzPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevMonth, enabled = state.canGoBack) {
                    Icon(
                        painterResource(R.drawable.ic_back),
                        contentDescription = "Previous month",
                        tint = if (state.canGoBack) Color.White else Color.White.copy(alpha = 0.25f),
                    )
                }
                OutlinedText(
                    state.month.atDay(1).format(MonthTitle).uppercase(),
                    MaterialTheme.typography.titleLarge,
                    outlineWidth = 4.dp,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = onNextMonth, enabled = state.canGoForward) {
                    Icon(
                        painterResource(R.drawable.ic_back),
                        contentDescription = "Next month",
                        tint = if (state.canGoForward) Color.White else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.graphicsLayer { scaleX = -1f },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                java.time.DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = SaiyanColors.TextDim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HistoryRules.monthCells(state.month.year, state.month.monthValue).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                            if (day != null) {
                                val record = state.byDay[day]
                                // Days can be looked at from the first one on record up to today.
                                val open = day <= todayDay && (record != null || day == todayDay ||
                                    (firstTracked != null && day >= firstTracked))
                                DayDot(
                                    outcome = record?.outcome,
                                    label = LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                                    isToday = day == todayDay,
                                    selected = state.selected?.epochDay == day,
                                    onClick = if (open) ({ onSelectDay(day) }) else null,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                    }
                }
            }
            DayLegend(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun DayPanel(detail: com.saiyanhabits.data.DayDetail) {
    val date = LocalDate.ofEpochDay(detail.epochDay)
    val isToday = date == LocalDate.now()
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Selected) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TabLabel(if (isToday) "Today" else date.format(DayTitle))
            val record = detail.record
            Text(
                when {
                    record != null -> record.outcome.describe()
                    isToday -> "In progress"
                    else -> "No record for this day"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (record != null && record.scheduled > 0) {
                Text("${record.completed} of ${record.scheduled} scheduled habits done", color = SaiyanColors.TextDim)
            }
            Text(
                if (detail.points > 0) "+${"%,d".format(detail.points)} power" else "No power earned",
                color = if (detail.points > 0) SaiyanColors.Gold else SaiyanColors.TextDim,
                style = MaterialTheme.typography.titleSmall,
            )
            detail.checkOffs.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = SaiyanColors.Bean, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(name)
                }
            }
        }
    }
}

@Composable
private fun PowerChartPanel(state: HistoryUiState, onRange: (ChartRange) -> Unit) {
    val series = state.series
    val gained = state.gained
    DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Green) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabLabel("Power level")
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedText(
                    "%,d".format(series.lastOrNull() ?: 0L),
                    MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 36.sp),
                    fill = GoldLettering,
                    outlineWidth = 4.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "+${"%,d".format(gained.coerceAtLeast(0))} " + when (state.range) {
                        ChartRange.ALL -> "since you started"
                        else -> "in ${state.range.days} days"
                    },
                    color = SaiyanColors.ScouterLine,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            PowerChart(series, Modifier.fillMaxWidth().height(150.dp))
            Row {
                Text(state.seriesStart.format(ChartDate), style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextHigh.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Text("Today", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextHigh.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { range ->
                    ChoiceTile(
                        selected = state.range == range,
                        onClick = { onRange(range) },
                        title = range.title,
                        caption = range.caption,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A scouter-green line of the power level over the chart's days. */
@Composable
private fun PowerChart(series: List<Long>, modifier: Modifier = Modifier) {
    val description = if (series.isEmpty()) "No power yet" else "Power level from ${series.first()} to ${series.last()}"
    Canvas(modifier.semantics { contentDescription = description }) {
        val grid = SaiyanColors.ScouterLine.copy(alpha = 0.16f)
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        for (i in 0..6) {
            val x = size.width * i / 6f
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        if (series.isEmpty()) return@Canvas
        val low = series.min()
        val high = series.max()
        val span = (high - low).coerceAtLeast(1L).toFloat()
        val pad = 6.dp.toPx()
        fun point(i: Int): Offset {
            val x = if (series.size == 1) size.width else size.width * i / (series.size - 1f)
            val y = pad + (size.height - 2 * pad) * (1f - (series[i] - low) / span)
            return Offset(x, y)
        }
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until series.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(series.size - 1).x, size.height)
            lineTo(point(0).x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(SaiyanColors.ScouterLine.copy(alpha = 0.38f), Color.Transparent)))
        drawPath(line, Color(0xFF06300F), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, SaiyanColors.ScouterLine, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val end = point(series.size - 1)
        drawCircle(Color.White, 5.dp.toPx(), end)
        drawCircle(SaiyanColors.ScouterLine, 3.5.dp.toPx(), end)
    }
}
