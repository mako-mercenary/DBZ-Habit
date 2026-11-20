package com.cdlstudy.data

import com.cdlstudy.domain.Card
import com.cdlstudy.domain.Content
import com.cdlstudy.domain.ExamItem
import com.cdlstudy.domain.ExamResult
import com.cdlstudy.domain.Grading
import com.cdlstudy.domain.Mark
import com.cdlstudy.domain.RecallScore
import com.cdlstudy.domain.Subsection
import com.cdlstudy.domain.Track
import com.cdlstudy.domain.Tsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Everything the screens need. The content is read-only; the record is yours. */
class StudyRepository(
    private val db: AppDatabase,
    val content: Content,
) {

    val pendingCards: Flow<List<CardEntity>> get() = db.cards().pending()
    val pendingCardCount: Flow<Int> get() = db.cards().pendingCount()
    val progress: Flow<List<ProgressEntity>> get() = db.progress().all()

    fun recallsFor(subsectionId: String): Flow<List<RecallEntity>> =
        db.recalls().forSubsection(subsectionId)

    fun attemptsFor(track: Track): Flow<List<ExamAttemptEntity>> =
        db.exams().forTrack(track.id)

    fun bestPercent(track: Track): Flow<Int?> = db.exams().bestPercent(track.id)

    /**
     * The weak facts, from recall marks and from missed exam questions together.
     * A fact that fails in both places gets the sum of both weights.
     */
    fun weakFacts(limit: Int = 40): Flow<List<WeakFact>> =
        combine(db.recalls().weakFacts(limit), db.exams().missedFacts(limit)) { recall, exam ->
            (recall + exam)
                .groupBy { it.factId }
                .map { (factId, rows) -> WeakFact(factId, rows.sumOf { it.weight }) }
                .sortedByDescending { it.weight }
                .take(limit)
        }

    /** Saves a graded recall, then queues the cards it earned. */
    suspend fun saveRecall(
        subsection: Subsection,
        blurt: String,
        marks: Map<String, Mark>,
        now: Long = System.currentTimeMillis(),
    ): SavedRecall {
        val score = Grading.score(subsection.facts, marks)
        val recallId = db.recalls().insert(
            RecallEntity(
                subsectionId = subsection.id,
                gradedAt = now,
                blurt = blurt,
                overall = score.overall,
                critical = score.critical,
                factCount = score.factCount,
                criticalCount = score.criticalCount,
            )
        )
        db.recalls().insertMarks(
            subsection.facts.map { fact ->
                FactMarkEntity(recallId, fact.id, Grading.markOf(marks, fact.id).name)
            }
        )
        val added = queueCards(subsection.id, Grading.cardsToAdd(subsection, marks), "recall", now)
        val previous = db.progress().get(subsection.id)
        db.progress().upsert(
            ProgressEntity(
                subsectionId = subsection.id,
                status = if (score.mastered) "mastered" else "recalled",
                bestOverall = maxOf(previous?.bestOverall ?: 0, score.overall),
                bestCritical = maxOf(previous?.bestCritical ?: 0, score.critical),
                timesRecalled = (previous?.timesRecalled ?: 0) + 1,
                lastStudiedAt = now,
            )
        )
        return SavedRecall(score, added)
    }

    suspend fun markRead(subsectionId: String, now: Long = System.currentTimeMillis()) {
        val previous = db.progress().get(subsectionId)
        if (previous != null && previous.status != "new") return
        db.progress().upsert(
            ProgressEntity(
                subsectionId = subsectionId,
                status = "reading",
                bestOverall = previous?.bestOverall ?: 0,
                bestCritical = previous?.bestCritical ?: 0,
                timesRecalled = previous?.timesRecalled ?: 0,
                lastStudiedAt = now,
            )
        )
    }

    suspend fun saveExam(
        result: ExamResult,
        items: List<ExamItem>,
        answers: Map<String, Int>,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val attemptId = db.exams().insert(
            ExamAttemptEntity(
                track = result.track.id,
                finishedAt = now,
                total = result.total,
                correct = result.correct,
                percent = result.percent,
            )
        )
        db.exams().insertAnswers(
            items.map { item ->
                ExamAnswerEntity(
                    attemptId = attemptId,
                    itemId = item.id,
                    chosen = answers[item.id] ?: -1,
                    wasCorrect = answers[item.id] == item.answerIndex,
                    factId = item.factId,
                )
            }
        )
        val cards = com.cdlstudy.domain.Exams.cardsForMissed(content, result)
        return queueCards(subsectionOfTrack(result.track), cards, "exam", now)
    }

    suspend fun weakFactIdsNow(): Set<String> = db.exams().allMissedFactIds().toSet()

    /** Adds cards, and leaves out a question that is in the queue already. */
    private suspend fun queueCards(
        subsectionId: String,
        cards: List<Card>,
        source: String,
        now: Long,
    ): Int {
        if (cards.isEmpty()) return 0
        val known = db.cards().allQuestions().map { Grading.normalizeQuestion(it) }.toSet()
        val fresh = cards.filterNot { Grading.normalizeQuestion(it.question) in known }
        if (fresh.isEmpty()) return 0
        db.cards().insert(
            fresh.map {
                CardEntity(
                    subsectionId = subsectionId,
                    factId = it.factId,
                    question = Tsv.clean(it.question),
                    answer = Tsv.clean(it.answer),
                    createdAt = now,
                    source = source,
                )
            }
        )
        return fresh.size
    }

    private fun subsectionOfTrack(track: Track): String =
        content.sections.firstOrNull { it.track == track }?.let { "exam-${it.number}" } ?: "exam"

    /** The TSV text for every card that is not exported yet. */
    suspend fun pendingTsv(): String = Tsv.of(
        db.cards().pendingNow().map { Card(it.question, it.answer, it.factId) }
    )

    suspend fun markPendingExported(now: Long = System.currentTimeMillis()) {
        val ids = db.cards().pendingNow().map { it.id }
        if (ids.isNotEmpty()) db.cards().markExported(ids, now)
    }

    /** How far you are through the plan, for the home screen. */
    val planProgress: Flow<PlanProgress>
        get() = progress.map { rows ->
            val total = content.sections.sumOf { it.subsections.size }
            PlanProgress(
                subsectionsTotal = total,
                subsectionsRecalled = rows.count { it.status == "recalled" || it.status == "mastered" },
                subsectionsMastered = rows.count { it.status == "mastered" },
            )
        }
}

data class SavedRecall(val score: RecallScore, val cardsAdded: Int)

data class PlanProgress(
    val subsectionsTotal: Int,
    val subsectionsRecalled: Int,
    val subsectionsMastered: Int,
)
