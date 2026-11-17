package com.saiyanhabits.ui.story

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.StoryRepository
import com.saiyanhabits.domain.Forms
import com.saiyanhabits.domain.StoryChapter
import com.saiyanhabits.domain.StoryRequirement
import com.saiyanhabits.domain.StoryRules
import com.saiyanhabits.ui.components.ScriptLines
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A chapter and where the player stands with it. */
data class ChapterStatus(val chapter: StoryChapter, val unlocked: Boolean, val read: Boolean) {
    val isNew: Boolean get() = unlocked && !read

    /** What it takes to unlock, in words. */
    val requirement: String
        get() = when (val needs = StoryRules.requirement(chapter.number)) {
            is StoryRequirement.PowerLevel -> "Unlocks at power level ${"%,d".format(needs.level)}"
            is StoryRequirement.Form -> "Unlocks at ${Forms.name(needs.form)}"
        }
}

class StoryViewModel(story: StoryRepository, private val habits: HabitRepository) : ViewModel() {

    /** Every chapter in order. Null until the first read comes back. */
    val chapters: StateFlow<List<ChapterStatus>?> = combine(
        flow { emit(story.chapters()) },
        habits.powerLevel,
        habits.playerState,
    ) { all, power, player ->
        all.map { chapter ->
            ChapterStatus(
                chapter = chapter,
                unlocked = StoryRules.isUnlocked(chapter.number, power, player.highestForm),
                read = StoryRules.isRead(chapter.number, player.chaptersRead),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markRead(chapter: Int) {
        viewModelScope.launch { habits.markChapterRead(chapter) }
    }
}

/** The chapter list. */
@Composable
fun StoryScreen(chapters: List<ChapterStatus>?, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    SubScreen(title = "Story", onBack = onBack) {
        Text(
            "Chapters unlock as your power level grows. The last one waits for your final form.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
        )
        chapters.orEmpty().forEach { status ->
            Surface(
                onClick = { onOpen(status.chapter.number) },
                enabled = status.unlocked,
                shape = RoundedCornerShape(16.dp),
                color = if (status.unlocked) SaiyanColors.Panel else SaiyanColors.Panel.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (status.isNew) SaiyanColors.Orange else SaiyanColors.Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        "${status.chapter.number}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (status.unlocked) SaiyanColors.Orange else SaiyanColors.TextDim,
                        modifier = Modifier.width(40.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (status.unlocked) status.chapter.title else "Chapter ${status.chapter.number}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (status.unlocked) SaiyanColors.TextHigh else SaiyanColors.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!status.unlocked) {
                            Text(status.requirement, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                        }
                    }
                    if (status.isNew) {
                        Text("NEW", style = MaterialTheme.typography.labelMedium, color = SaiyanColors.Orange)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One chapter, read top to bottom. */
@Composable
fun ChapterScreen(chapter: StoryChapter, playerForm: Int, onBack: () -> Unit) {
    SubScreen(title = chapter.title, onBack = onBack) {
        Text("Chapter ${chapter.number}", style = MaterialTheme.typography.labelLarge, color = SaiyanColors.Orange)
        ScriptLines(chapter.lines, playerForm)
        Spacer(Modifier.height(16.dp))
    }
}
