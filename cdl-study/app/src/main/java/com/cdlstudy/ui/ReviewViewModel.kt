package com.cdlstudy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdlstudy.data.CardEntity
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.data.state
import com.cdlstudy.domain.Review
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One sitting with the cards.
 *
 * The queue is taken once when the session starts, so the list under you does
 * not move while you work. A card you answer Again comes back inside ten
 * minutes, which in practice means later in this same sitting, so it is put to
 * the back of the queue rather than dropped.
 */
class ReviewViewModel(
    private val repo: StudyRepository,
    private val testDate: () -> LocalDate?,
) : ViewModel() {

    var queue by mutableStateOf<List<CardEntity>>(emptyList())
        private set
    var showingAnswer by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set

    /** How many you have answered in this sitting, and how they went. */
    var answered by mutableStateOf(0)
        private set
    var right by mutableStateOf(0)
        private set

    /** What the buttons say they will do to the card on screen. */
    var preview by mutableStateOf<Map<Review.Grade, String>>(emptyMap())
        private set

    val card: CardEntity? get() = queue.firstOrNull()
    val left: Int get() = queue.size
    val finished: Boolean get() = !loading && queue.isEmpty()

    init {
        start()
    }

    fun start() {
        loading = true
        viewModelScope.launch {
            queue = repo.dueCardsNow()
            answered = 0
            right = 0
            showingAnswer = false
            loading = false
            refreshPreview()
        }
    }

    fun reveal() {
        showingAnswer = true
    }

    fun answer(grade: Review.Grade) {
        val current = card ?: return
        if (!showingAnswer) return
        viewModelScope.launch {
            repo.answerCard(current, grade, testDate())
            answered += 1
            if (grade != Review.Grade.AGAIN) right += 1
            // A missed card goes to the back of this sitting rather than away,
            // because ten minutes from now is usually still this sitting.
            queue = if (grade == Review.Grade.AGAIN) {
                queue.drop(1) + current
            } else {
                queue.drop(1)
            }
            showingAnswer = false
            refreshPreview()
        }
    }

    /** Starts this card over, for one you have hopelessly wrong. */
    fun resetCard() {
        val current = card ?: return
        viewModelScope.launch {
            repo.resetCard(current.id)
            queue = queue.drop(1) + current.copy(
                stage = "NEW",
                intervalMinutes = 0,
                ease = 2.5,
                reps = 0,
                lapses = 0,
                dueAt = 0,
            )
            showingAnswer = false
            refreshPreview()
        }
    }

    private fun refreshPreview() {
        val current = card
        preview = if (current == null) {
            emptyMap()
        } else {
            Review.preview(current.state(), System.currentTimeMillis(), testDate())
        }
    }
}
