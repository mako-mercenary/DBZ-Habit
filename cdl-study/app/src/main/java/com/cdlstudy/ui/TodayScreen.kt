package com.cdlstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cdlstudy.data.PlanProgress
import com.cdlstudy.data.ProgressEntity
import com.cdlstudy.domain.ExamStandards
import com.cdlstudy.domain.NumberDrill
import com.cdlstudy.domain.NextStep
import com.cdlstudy.domain.NextStepRules
import com.cdlstudy.domain.Section
import com.cdlstudy.domain.Streak
import com.cdlstudy.domain.Track

/**
 * The first screen: where you are, the one thing to do next, then the modules.
 */
@Composable
fun TodayScreen(
    onSection: (Int) -> Unit,
    onStudy: (String) -> Unit,
    onExams: () -> Unit,
    onCards: () -> Unit,
    onProgress: () -> Unit,
    onManual: () -> Unit,
    onDrills: () -> Unit,
) {
    val repo = repository()
    val store = manualStore()
    val errors = contentErrors()
    val plan by repo.planProgress.collectAsStateWithLifecycle(PlanProgress(0, 0, 0))
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val dueReviews by repo.dueReviewCount().collectAsStateWithLifecycle(0)
    val dueCards by repo.dueCardCount().collectAsStateWithLifecycle(0)
    val deckSize by repo.cardCount.collectAsStateWithLifecycle(0)
    val streak by repo.streak.collectAsStateWithLifecycle(Streak.Result(0, 0, false, emptyList()))
    val bests = Track.entries.associateWith {
        repo.bestPercent(it).collectAsStateWithLifecycle(null).value
    }
    // The best from an attempt that was not weighted in your favour. It is the
    // only score that means what it says, so the tile shows it when it exists.
    val realBests = Track.entries.associateWith {
        repo.bestRealPercent(it).collectAsStateWithLifecycle(null).value
    }

    val statusOf: (String) -> String? = { id -> progress.firstOrNull { it.subsectionId == id }?.status }
    val step = NextStepRules.next(repo.content, statusOf, { bests[it] }, dueReviews)
    val percent =
        if (plan.subsectionsTotal == 0) 0
        else Math.round(100f * plan.subsectionsRecalled / plan.subsectionsTotal)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Overline("Utah · Class A")
            Spacer(Modifier.height(8.dp))
            Text(
                "Let's get you road ready.",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(18.dp))
            GoldProgress(percent)
            Text(
                "${plan.subsectionsRecalled} of ${plan.subsectionsTotal} parts recalled" +
                    if (plan.subsectionsMastered > 0) " · ${plan.subsectionsMastered} mastered" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
            NextButton(
                text = NextStepRules.label(step),
                enabled = step !is NextStep.NothingWritten && step !is NextStep.AllDone,
            ) {
                when (step) {
                    is NextStep.Study -> onStudy(step.subsectionId)
                    is NextStep.TakeExam -> onExams()
                    is NextStep.ReviewCards -> onCards()
                    else -> Unit
                }
            }
            Text(
                "This always points at the next thing to do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp, end = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
        }

        if (!store.isImported) {
            item {
                WarningCard(
                    "Import your copy of the handbook to read the real pages. " +
                        "The lessons work without it."
                )
                Spacer(Modifier.height(20.dp))
            }
        }
        errors.forEach { error ->
            item {
                WarningCard("Content file problem - $error")
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            RowHeader(
                title = "Reading",
                step = "Step 1",
                subtitle = "The eight sections Utah requires",
                action = "See all ${repo.content.sections.size}",
                onAction = { repo.content.sections.firstOrNull()?.let { onSection(it.number) } },
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(repo.content.sections) { section ->
                    SectionTile(section, progress, step, onSection)
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            RowHeader(
                title = "Testing",
                step = "Step 2",
                subtitle = "Six exams, each graded on its own",
                action = "See all 6",
                onAction = onExams,
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Track.entries.toList()) { track ->
                    val pool = repo.content.examPool(track).size
                    val best = bests[track]
                    val real = realBests[track]
                    Tile(
                        tag = track.label,
                        number = if (pool == 0) "—" else "$pool",
                        title = track.label,
                        meta = when {
                            pool == 0 -> "no questions yet"
                            best == null -> "$pool ready · not taken"
                            real != null -> "test-day $real% · practice $best%"
                            else -> "practice $best% · no test-day run yet"
                        },
                        progress = (best ?: 0) / 100f,
                        badge = if (step is NextStep.TakeExam && step.track == track) "Start" else null,
                        onClick = onExams,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            RowHeader(
                title = "Cards",
                step = "Step 3",
                subtitle = "Reviewed here, scheduled to your test date",
                action = "Review",
                onAction = onCards,
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Tile(
                        tag = "Due now",
                        number = "$dueCards",
                        title = "Cards due",
                        meta = if (deckSize == 0) {
                            "none made yet"
                        } else if (dueCards == 0) {
                            "$deckSize in the deck, none due"
                        } else {
                            "of $deckSize in the deck"
                        },
                        badge = if (dueCards > 0) "Review" else null,
                        onClick = onCards,
                    )
                }
                item {
                    Tile(
                        tag = "Record",
                        number = "${plan.subsectionsRecalled}",
                        title = "Your progress",
                        meta = "scores and weak facts",
                        onClick = onProgress,
                    )
                }
                item {
                    Tile(
                        tag = "Drills",
                        number = "${NumberDrill.pool(repo.content).size}",
                        title = "Numbers and order",
                        meta = "short, hard, one at a time",
                        onClick = onDrills,
                    )
                }
                item {
                    Tile(
                        tag = "Manual",
                        number = if (store.isImported) "✓" else "—",
                        title = "Your manual",
                        meta = if (store.isImported) "${store.pageCount} pages imported" else "not imported",
                        onClick = onManual,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTile(
    section: Section,
    progress: List<ProgressEntity>,
    step: NextStep,
    onSection: (Int) -> Unit,
) {
    val parts = section.subsections.filter { it.canRecall }.map { it.id }.toSet()
    val done = progress.count {
        it.subsectionId in parts && (it.status == "recalled" || it.status == "mastered")
    }
    val isNext = step is NextStep.Study && step.sectionNumber == section.number
    Tile(
        tag = "Section ${section.number}",
        number = "${section.number}",
        title = section.title,
        meta = when {
            parts.isEmpty() -> "${section.subsections.size} parts · not written"
            else -> "$done of ${parts.size} parts recalled"
        },
        progress = if (parts.isEmpty()) 0f else done.toFloat() / parts.size,
        badge = if (isNext) "Continue" else null,
        onClick = { onSection(section.number) },
    )
}
