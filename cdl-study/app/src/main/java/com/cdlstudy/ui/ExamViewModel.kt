package com.cdlstudy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.domain.ExamItem
import com.cdlstudy.domain.ExamMode
import com.cdlstudy.domain.ExamResult
import com.cdlstudy.domain.ExamStandards
import com.cdlstudy.domain.Exams
import com.cdlstudy.domain.Track
import kotlinx.coroutines.launch

enum class ExamStage { PICK, RUN, DONE }

/**
 * One practice exam. No answer is shown until the exam is finished, as the
 * study plan requires.
 */
class ExamViewModel(private val repo: StudyRepository) : ViewModel() {

    var stage by mutableStateOf(ExamStage.PICK)
        private set
    var mode by mutableStateOf(ExamMode.PRACTICE)
        private set
    var track by mutableStateOf(Track.GENERAL_KNOWLEDGE)
        private set
    var length by mutableStateOf(ExamStandards.defaultLength(Track.GENERAL_KNOWLEDGE))
        private set
    var items by mutableStateOf<List<ExamItem>>(emptyList())
        private set
    var answers by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var at by mutableStateOf(0)
        private set
    var result by mutableStateOf<ExamResult?>(null)
        private set
    var cardsAdded by mutableStateOf(0)
        private set

    /** When the attempt started, so the time can be recorded. */
    private var startedAt: Long = 0L

    /** How long the finished attempt took. Null until it is finished. */
    var seconds by mutableStateOf<Int?>(null)
        private set

    fun poolSize(track: Track): Int = repo.content.examPool(track).size

    fun pick(track: Track) {
        this.track = track
        length = fullLength(track)
    }

    /** Switching to test-day conditions puts the length back to full. */
    fun chooseMode(value: ExamMode) {
        mode = value
        if (!value.lengthIsYours) length = fullLength(track)
    }

    fun chooseLength(value: Int) {
        if (!mode.lengthIsYours) return
        length = value.coerceIn(1, maxOf(1, poolSize(track)))
    }

    private fun fullLength(track: Track): Int =
        minOf(ExamStandards.defaultLength(track), poolSize(track))

    /**
     * True when test-day conditions cannot be honoured, because the pool is
     * shorter than a full exam. The app says so rather than calling a short
     * exam a real one.
     */
    val poolIsShort: Boolean
        get() = poolSize(track) < ExamStandards.defaultLength(track)

    fun start() {
        viewModelScope.launch {
            val weak = if (mode.weighted) repo.weakFactIdsNow() else emptySet()
            if (!mode.lengthIsYours) length = fullLength(track)
            items = Exams.build(repo.content.examPool(track), length, weak)
            answers = emptyMap()
            at = 0
            result = null
            cardsAdded = 0
            seconds = null
            startedAt = System.currentTimeMillis()
            if (items.isNotEmpty()) stage = ExamStage.RUN
        }
    }

    fun answer(itemId: String, choice: Int) {
        answers = answers + (itemId to choice)
    }

    fun next() {
        if (at < items.lastIndex) at++ else finish()
    }

    fun back() {
        if (at > 0) at--
    }

    fun finish() {
        val graded = Exams.grade(track, items, answers)
        val took = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0)
        result = graded
        seconds = took
        stage = ExamStage.DONE
        viewModelScope.launch {
            cardsAdded = repo.saveExam(
                result = graded,
                items = items,
                answers = answers,
                realConditions = mode == ExamMode.TEST_DAY && !poolIsShort,
                seconds = took,
            )
        }
    }

    fun restart() {
        stage = ExamStage.PICK
        items = emptyList()
        answers = emptyMap()
        result = null
    }
}
