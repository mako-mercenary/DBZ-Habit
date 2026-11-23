package com.cdlstudy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdlstudy.ai.AiSettings
import com.cdlstudy.ai.Claude
import com.cdlstudy.data.SavedRecall
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.domain.AiGrading
import com.cdlstudy.domain.BlurtMatcher
import com.cdlstudy.domain.FocusText
import com.cdlstudy.domain.Fact
import com.cdlstudy.domain.Mark
import com.cdlstudy.domain.Subsection
import kotlinx.coroutines.launch

/** The four steps of one subsection. */
enum class Stage { READ, BLURT, GRADE, RESULT }

/** Where the second opinion is up to. */
sealed interface AiState {
    /** Not asked. */
    data object Idle : AiState
    data object Asking : AiState
    data class Done(val opinion: AiGrading.Opinion) : AiState
    data class Failed(val message: String) : AiState
}

/**
 * Holds one turn of the study loop.
 *
 * The facts stay out of the screen until the recall is sent. That is the whole
 * point of the method: no hint before you try.
 */
class StudyViewModel(
    private val repo: StudyRepository,
    private val settings: ReadingSettings,
    private val ai: AiSettings,
    val subsectionId: String,
) : ViewModel() {

    val subsection: Subsection? = repo.content.subsection(subsectionId)
    val sectionTitle: String = repo.content.sectionOf(subsectionId)
        ?.let { "Section ${it.number} - ${it.title}" } ?: ""

    var stage by mutableStateOf(Stage.READ)
        private set
    var blurt by mutableStateOf("")
        private set
    var marks by mutableStateOf<Map<String, Mark>>(emptyMap())
        private set
    var suggestions by mutableStateOf<Map<String, BlurtMatcher.Suggestion>>(emptyMap())
        private set
    var saved by mutableStateOf<SavedRecall?>(null)
        private set

    var aiState by mutableStateOf<AiState>(AiState.Idle)
        private set

    /** Whether there is a key, so the button is worth showing. */
    val aiReady: Boolean get() = ai.ready

    val aiModel: String get() = AiSettings.Models.label(ai.model)

    /** 1x fits the page to the screen. 2x needs a sideways scroll. */
    var zoom by mutableStateOf(1f)
        private set

    /** How the lesson is drawn. One shared setting, so a change shows at once. */
    val readStyle: ReadStyle get() = settings.style

    /** The pages of your own book instead of the lesson in the app. */
    val showManualPages: Boolean get() = settings.showManualPages

    var readMarked by mutableStateOf(false)
        private set

    fun chooseZoom(value: Float) {
        zoom = value
    }

    fun toggleFocus() = settings.toggleFocus()

    fun chooseStrength(strength: FocusText.Strength) = settings.chooseStrength(strength)

    fun bigger() = settings.bigger()

    fun smaller() = settings.smaller()

    fun toggleSpacing() = settings.toggleSpacing()

    fun toggleManualPages() = settings.toggleManualPages()

    /**
     * For a subsection whose fact checklist is not written yet. You can still
     * read it in the app and record that you read it.
     */
    fun markReadOnly() {
        viewModelScope.launch {
            repo.markRead(subsectionId)
            readMarked = true
        }
    }

    fun startBlurt() {
        viewModelScope.launch { repo.markRead(subsectionId) }
        stage = Stage.BLURT
    }

    fun editBlurt(text: String) {
        blurt = text
    }

    /** Sends the recall. Only now does the app show the facts. */
    fun submitBlurt() {
        val facts = subsection?.facts ?: return
        suggestions = BlurtMatcher.suggest(facts, blurt)
        // The suggestion is the starting mark. You change any mark you disagree with.
        marks = facts.associate { fact ->
            fact.id to (suggestions[fact.id]?.mark ?: Mark.MISS)
        }
        stage = Stage.GRADE
    }

    fun setMark(factId: String, mark: Mark) {
        marks = marks + (factId to mark)
    }

    /**
     * Asks for a second opinion. Your marks do not change until you say so:
     * the model advises, you still grade.
     */
    fun askAi() {
        val sub = subsection ?: return
        if (aiState is AiState.Asking) return
        aiState = AiState.Asking
        viewModelScope.launch {
            aiState = try {
                AiState.Done(Claude.grade(ai, sub, blurt))
            } catch (e: Claude.Failed) {
                AiState.Failed(e.message ?: "That did not work.")
            }
        }
    }

    /** Takes every mark the model gave, over the ones you have. */
    fun takeAiMarks() {
        val opinion = (aiState as? AiState.Done)?.opinion ?: return
        marks = marks + opinion.marks.associate { it.factId to it.mark }
    }

    fun aiMarkOf(factId: String): Mark? =
        (aiState as? AiState.Done)?.opinion?.markOf(factId)

    fun aiWhyOf(factId: String): String? =
        (aiState as? AiState.Done)?.opinion?.whyOf(factId)

    fun save() {
        val sub = subsection ?: return
        viewModelScope.launch {
            saved = repo.saveRecall(sub, blurt, marks)
            stage = Stage.RESULT
        }
    }

    fun again() {
        blurt = ""
        marks = emptyMap()
        suggestions = emptyMap()
        saved = null
        aiState = AiState.Idle
        stage = Stage.READ
    }

    fun factsToRelearn(): List<Fact> =
        subsection?.let { com.cdlstudy.domain.Grading.factsToRelearn(it, marks) } ?: emptyList()
}
