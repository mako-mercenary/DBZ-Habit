package com.saiyanhabits.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.DayDetail
import com.saiyanhabits.data.HistoryRepository
import com.saiyanhabits.domain.HistoryRules
import com.saiyanhabits.domain.PastDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** How far back the power chart looks. */
enum class ChartRange(val days: Int?, val title: String, val caption: String) {
    MONTH(30, "30", "days"),
    QUARTER(90, "90", "days"),
    ALL(null, "All", "time"),
}

data class HistoryUiState(
    val today: LocalDate,
    /** Finished days, oldest first. */
    val days: List<PastDay>,
    val month: YearMonth,
    val firstMonth: YearMonth,
    val range: ChartRange,
    /** Power level at the end of each day of the chart, oldest first. */
    val series: List<Long>,
    val seriesStart: LocalDate,
    /** Power earned over the chart's days. */
    val gained: Long,
    val selected: DayDetail?,
    /** Days with a note written. */
    val notedDays: Set<Long> = emptySet(),
) {
    val byDay: Map<Long, PastDay> = days.associateBy { it.epochDay }
    val canGoBack: Boolean get() = month > firstMonth
    val canGoForward: Boolean get() = month < YearMonth.from(today)
}

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val range = MutableStateFlow(ChartRange.MONTH)
    private val selectedDay = MutableStateFlow<Long?>(null)

    /** The day picked in the calendar, reloaded whenever the data changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val selected = combine(selectedDay, history.dailyPoints, history.pastDays, history.notes) { day, _, _, _ -> day }
        .mapLatest { day -> day?.let { history.dayDetail(it) } }

    /** Null until the first database read comes back. */
    val uiState: StateFlow<HistoryUiState?> = combine(
        history.pastDays,
        history.dailyPoints,
        combine(month, range, ::Pair),
        selected,
        history.notes,
    ) { days, points, (shownMonth, chartRange), detail, notes ->
        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val firstDay = listOfNotNull(days.firstOrNull()?.epochDay, points.keys.minOrNull()).minOrNull() ?: todayDay
        val from = chartRange.days?.let { todayDay - it + 1 } ?: firstDay
        HistoryUiState(
            today = today,
            days = days,
            month = shownMonth,
            firstMonth = minOf(YearMonth.from(LocalDate.ofEpochDay(firstDay)), YearMonth.from(today)),
            range = chartRange,
            series = HistoryRules.powerSeries(points, from, todayDay),
            seriesStart = LocalDate.ofEpochDay(from),
            gained = points.filterKeys { it in from..todayDay }.values.sumOf { it.toLong() },
            selected = detail,
            notedDays = notes.keys,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showMonth(next: YearMonth) {
        month.value = next
    }

    fun setRange(next: ChartRange) {
        range.value = next
    }

    /** Picks a day in the calendar; picking it again closes it. */
    fun select(epochDay: Long) {
        selectedDay.value = if (selectedDay.value == epochDay) null else epochDay
    }

    fun saveNote(epochDay: Long, text: String) {
        viewModelScope.launch { history.saveNote(epochDay, text) }
    }

    /** Goes back to this month with nothing picked, for the next visit. */
    fun reset() {
        month.value = YearMonth.now()
        selectedDay.value = null
    }
}
