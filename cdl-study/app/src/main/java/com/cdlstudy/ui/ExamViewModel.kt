package com.cdlstudy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.domain.ExamItem
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

    fun poolSize(track: Track): Int = repo.content.examPool(track).size

    fun pick(track: Track) {
        this.track = track
        length = minOf(ExamStandards.defaultLength(track), poolSize(track))
    }

    fun chooseLength(value: Int) {
        length = value.coerceIn(1, maxOf(1, poolSize(track)))
    }

    fun start() {
        viewModelScope.launch {
            val weak = repo.weakFactIdsNow()
            items = Exams.build(repo.content.examPool(track), length, weak)
            answers = emptyMap()
            at = 0
            result = null
            cardsAdded = 0
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
        result = graded
        stage = ExamStage.DONE
        viewModelScope.launch {
            cardsAdded = repo.saveExam(graded, items, answers)
        }
    }

    fun restart() {
        stage = ExamStage.PICK
        items = emptyList()
        answers = emptyMap()
        result = null
    }
}
