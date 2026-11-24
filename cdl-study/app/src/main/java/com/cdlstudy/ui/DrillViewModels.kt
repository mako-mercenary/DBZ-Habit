package com.cdlstudy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.domain.Mark
import com.cdlstudy.domain.NumberDrill
import com.cdlstudy.domain.Procedure
import com.cdlstudy.domain.Step
import com.cdlstudy.domain.StepOrder
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One round of the numbers drill. */
class NumberDrillViewModel(private val repo: StudyRepository) : ViewModel() {

    var items by mutableStateOf<List<NumberDrill.Item>>(emptyList())
        private set
    var at by mutableStateOf(0)
        private set
    var typed by mutableStateOf("")
        private set

    /** The mark for the question on screen, once you have answered it. */
    var mark by mutableStateOf<Mark?>(null)
        private set
    var marks by mutableStateOf<List<Mark>>(emptyList())
        private set
    var cardsAdded by mutableStateOf<Int?>(null)
        private set

    val item: NumberDrill.Item? get() = items.getOrNull(at)
    val finished: Boolean get() = items.isNotEmpty() && at >= items.size
    val hits: Int get() = marks.count { it == Mark.HIT }
    val percent: Int
        get() = if (marks.isEmpty()) 0 else (100 * marks.sumOf { it.weight } / marks.size).toInt()

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            val weak = runCatching { repo.weakFactIdsNow() }.getOrDefault(emptySet())
            items = NumberDrill.build(repo.content, Random, weakFactIds = weak)
            at = 0
            typed = ""
            mark = null
            marks = emptyList()
            cardsAdded = null
        }
    }

    fun type(text: String) {
        typed = text
    }

    /** Marks the answer. The number is right or it is not. */
    fun answer() {
        val current = item ?: return
        if (mark != null) return
        mark = NumberDrill.check(typed, current)
    }

    fun next() {
        val current = mark ?: return
        marks = marks + current
        at += 1
        typed = ""
        mark = null
        if (finished) queueMisses()
    }

    /** Gives up on this one: it counts as a miss, which is what it is. */
    fun reveal() {
        if (mark == null) mark = Mark.MISS
    }

    private fun queueMisses() {
        val missed = items.zip(marks)
            .filter { (_, mark) -> mark != Mark.HIT }
            .map { (item, _) -> item.factId }
        if (missed.isEmpty()) {
            cardsAdded = 0
            return
        }
        viewModelScope.launch {
            cardsAdded = repo.queueDrillMisses(missed, source = "numbers")
        }
    }
}

/** One attempt at putting a procedure back in order. */
class ProcedureViewModel(
    private val repo: StudyRepository,
    val procedure: Procedure,
) : ViewModel() {

    /** The shuffled steps still waiting to be placed. */
    var pool by mutableStateOf<List<Step>>(emptyList())
        private set

    /** The order you have built so far. */
    var placed by mutableStateOf<List<Step>>(emptyList())
        private set

    var result by mutableStateOf<StepOrder.Result?>(null)
        private set
    var cardsAdded by mutableStateOf<Int?>(null)
        private set

    val ready: Boolean get() = pool.isEmpty() && result == null

    init {
        start()
    }

    fun start() {
        pool = StepOrder.shuffle(procedure, Random)
        placed = emptyList()
        result = null
        cardsAdded = null
    }

    /** Tapping a step puts it next in your order. No dragging on a phone. */
    fun place(step: Step) {
        if (result != null) return
        pool = pool.filterNot { it.text == step.text }
        placed = placed + step
    }

    /** Takes the last one back, for the moment you see your mistake. */
    fun undo() {
        if (result != null) return
        val last = placed.lastOrNull() ?: return
        placed = placed.dropLast(1)
        pool = pool + last
    }

    fun check() {
        if (!ready) return
        val scored = StepOrder.score(placed, procedure)
        result = scored
        if (scored.passed) {
            cardsAdded = 0
            return
        }
        viewModelScope.launch {
            cardsAdded = repo.queueDrillMisses(scored.missedFactIds, source = "order")
        }
    }
}
