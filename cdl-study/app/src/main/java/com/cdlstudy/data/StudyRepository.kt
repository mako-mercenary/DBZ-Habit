package com.cdlstudy.data

import com.cdlstudy.domain.Backup
import com.cdlstudy.domain.Card
import com.cdlstudy.domain.Content
import com.cdlstudy.domain.ExamItem
import com.cdlstudy.domain.ExamResult
import com.cdlstudy.domain.Grading
import com.cdlstudy.domain.Mark
import com.cdlstudy.domain.RecallScore
import com.cdlstudy.domain.Streak
import com.cdlstudy.domain.Subsection
import com.cdlstudy.domain.Track
import com.cdlstudy.domain.Review
import com.cdlstudy.domain.CardText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Everything the screens need. The content is read-only; the record is yours. */
class StudyRepository(
    private val db: AppDatabase,
    val content: Content,
) {

    /** The whole record, for backup and restore. */
    val record: RecordStore = RecordStore(db)


    /** The cards waiting for you right now. */
    fun dueCards(now: Long = System.currentTimeMillis()): Flow<List<CardEntity>> =
        db.cards().due(now)

    fun dueCardCount(now: Long = System.currentTimeMillis()): Flow<Int> = db.cards().dueCount(now)

    val newCardCount: Flow<Int> get() = db.cards().newCount()

    /** Learned cards that have come due. These drive the next step. */
    fun dueReviewCount(now: Long = System.currentTimeMillis()): Flow<Int> =
        db.cards().dueReviewCount(now)
    val cardCount: Flow<Int> get() = db.cards().count()
    val progress: Flow<List<ProgressEntity>> get() = db.progress().all()

    fun recallsFor(subsectionId: String): Flow<List<RecallEntity>> =
        db.recalls().forSubsection(subsectionId)

    fun attemptsFor(track: Track): Flow<List<ExamAttemptEntity>> =
        db.exams().forTrack(track.id)

    fun bestPercent(track: Track): Flow<Int?> = db.exams().bestPercent(track.id)

    /** The best score from an attempt that was not weighted in your favour. */
    fun bestRealPercent(track: Track): Flow<Int?> = db.exams().bestRealPercent(track.id)

    fun realAttemptCount(track: Track): Flow<Int> = db.exams().realAttemptCount(track.id)

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
        realConditions: Boolean = false,
        seconds: Int? = null,
    ): Int {
        val attemptId = db.exams().insert(
            ExamAttemptEntity(
                track = result.track.id,
                finishedAt = now,
                total = result.total,
                correct = result.correct,
                percent = result.percent,
                realConditions = realConditions,
                seconds = seconds,
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

    // Plain reads for the reminder worker, which has no screen to collect a flow.
    suspend fun progressNow(): List<ProgressEntity> = db.progress().allNow()

    suspend fun bestPercentNow(track: Track): Int? = db.exams().bestPercentNow(track.id)

    suspend fun dueCardCountNow(now: Long = System.currentTimeMillis()): Int =
        db.cards().dueNow(now).size

    suspend fun dueReviewCountNow(now: Long = System.currentTimeMillis()): Int =
        db.cards().dueReviewCountNow(now)

    suspend fun dueCardsNow(now: Long = System.currentTimeMillis()): List<CardEntity> =
        db.cards().dueNow(now)

    suspend fun recallTimesNow(): List<Long> = db.recalls().allNow().map { it.gradedAt }

    /** The streak of days with at least one recall. */
    val streak: Flow<Streak.Result>
        get() = db.recalls().recent(5000).map { recalls ->
            Streak.of(recalls.map { it.gradedAt }, java.time.LocalDate.now())
        }

    /**
     * Queues a card for each fact a drill caught you on.
     *
     * A drill does not keep its own schedule. It puts the card in the deck,
     * and the deck's own schedule decides when you see it again.
     */
    suspend fun queueDrillMisses(
        factIds: Collection<String>,
        source: String,
        now: Long = System.currentTimeMillis(),
    ): Int {
        var added = 0
        factIds.distinct().forEach { factId ->
            val subsection = content.sections
                .flatMap { it.subsections }
                .firstOrNull { sub -> sub.facts.any { it.id == factId } }
                ?: return@forEach
            val fact = subsection.fact(factId) ?: return@forEach
            // Use the fact's own card when it has one. Otherwise make one from
            // the fact itself, so a drilled fact is never lost for want of a
            // hand-written question.
            val card = subsection.cards.firstOrNull { it.factId == factId }
                ?: Card(
                    question = "${subsection.id} ${subsection.title}: what is the rule?",
                    answer = fact.text,
                    factId = factId,
                )
            added += queueCards(subsection.id, listOf(card), source, now)
        }
        return added
    }

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
                    question = CardText.clean(it.question),
                    answer = CardText.clean(it.answer),
                    createdAt = now,
                    source = source,
                )
            }
        )
        return fresh.size
    }

    private fun subsectionOfTrack(track: Track): String =
        content.sections.firstOrNull { it.track == track }?.let { "exam-${it.number}" } ?: "exam"

    /** Records what you said about a card, and books the next sighting. */
    suspend fun answerCard(
        card: CardEntity,
        grade: Review.Grade,
        testDate: java.time.LocalDate?,
        now: Long = System.currentTimeMillis(),
    ): Review.State {
        val next = Review.answer(card.state(), grade, now, testDate)
        db.cards().schedule(
            id = card.id,
            stage = next.stage.name,
            intervalMinutes = next.intervalMinutes,
            ease = next.ease,
            reps = next.reps,
            lapses = next.lapses,
            dueAt = next.dueAt,
            at = now,
        )
        return next
    }

    /** Starts one card over, as if it had never been seen. */
    suspend fun resetCard(id: Long) = db.cards().reset(id)


    /** How far you are through the plan, for the home screen. */
    val planProgress: Flow<PlanProgress>
        get() = progress.map { rows ->
            // Only a subsection with a fact checklist can be recalled. A parent
            // heading never can, so counting it would put 100% out of reach.
            val total = content.sections.sumOf { it.readyToRecall }
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

/** Reads and writes the whole study record, for backup and restore. */
class RecordStore(private val db: AppDatabase) {

    /** Everything you have done, ready to write to a file. */
    suspend fun export(appVersion: String, now: Long = System.currentTimeMillis()): Backup.Record {
        val recalls = db.recalls().allNow().map { recall ->
            Backup.Recall(
                subsectionId = recall.subsectionId,
                gradedAt = recall.gradedAt,
                blurt = recall.blurt,
                overall = recall.overall,
                critical = recall.critical,
                factCount = recall.factCount,
                criticalCount = recall.criticalCount,
                marks = db.recalls().marksOf(recall.id).associate { it.factId to it.mark },
            )
        }
        val cards = db.cards().allNow().map {
            Backup.Card(
                subsectionId = it.subsectionId,
                factId = it.factId,
                question = it.question,
                answer = it.answer,
                createdAt = it.createdAt,
                exportedAt = it.exportedAt,
                source = it.source,
                stage = it.stage,
                intervalMinutes = it.intervalMinutes,
                ease = it.ease,
                reps = it.reps,
                lapses = it.lapses,
                dueAt = it.dueAt,
                lastReviewedAt = it.lastReviewedAt,
            )
        }
        val attempts = db.exams().allAttemptsNow().map { attempt ->
            Backup.Attempt(
                track = attempt.track,
                finishedAt = attempt.finishedAt,
                total = attempt.total,
                correct = attempt.correct,
                percent = attempt.percent,
                realConditions = attempt.realConditions,
                seconds = attempt.seconds,
                answers = db.exams().answersOf(attempt.id).map {
                    Backup.Answer(it.itemId, it.chosen, it.wasCorrect, it.factId)
                },
            )
        }
        val progress = db.progress().allNow().map {
            Backup.Progress(it.subsectionId, it.status, it.bestOverall, it.bestCritical, it.timesRecalled, it.lastStudiedAt)
        }
        return Backup.Record(Backup.FORMAT, appVersion, now, recalls, cards, attempts, progress)
    }

    /**
     * Puts a record back, replacing everything that is here now.
     *
     * Replace, not merge. Merging two records would double every card and every
     * recall, and there is no honest way to tell a repeat recall from a copy of
     * one. The screen warns before it does this.
     */
    suspend fun import(record: Backup.Record) {
        db.recalls().deleteAllMarks()
        db.recalls().deleteAll()
        db.cards().deleteAll()
        db.exams().deleteAllAnswers()
        db.exams().deleteAllAttempts()
        db.progress().deleteAll()

        record.recalls.forEach { recall ->
            val id = db.recalls().insert(
                RecallEntity(
                    subsectionId = recall.subsectionId,
                    gradedAt = recall.gradedAt,
                    blurt = recall.blurt,
                    overall = recall.overall,
                    critical = recall.critical,
                    factCount = recall.factCount,
                    criticalCount = recall.criticalCount,
                )
            )
            db.recalls().insertMarks(recall.marks.map { FactMarkEntity(id, it.key, it.value) })
        }
        if (record.cards.isNotEmpty()) {
            db.cards().insert(
                record.cards.map {
                    CardEntity(
                        subsectionId = it.subsectionId,
                        factId = it.factId,
                        question = it.question,
                        answer = it.answer,
                        createdAt = it.createdAt,
                        exportedAt = it.exportedAt,
                        source = it.source,
                        stage = it.stage,
                        intervalMinutes = it.intervalMinutes,
                        ease = it.ease,
                        reps = it.reps,
                        lapses = it.lapses,
                        dueAt = it.dueAt,
                        lastReviewedAt = it.lastReviewedAt,
                    )
                }
            )
        }
        record.attempts.forEach { attempt ->
            val id = db.exams().insert(
                ExamAttemptEntity(
                    track = attempt.track,
                    finishedAt = attempt.finishedAt,
                    total = attempt.total,
                    correct = attempt.correct,
                    percent = attempt.percent,
                    realConditions = attempt.realConditions,
                    seconds = attempt.seconds,
                )
            )
            db.exams().insertAnswers(
                attempt.answers.map { ExamAnswerEntity(id, it.itemId, it.chosen, it.wasCorrect, it.factId) }
            )
        }
        record.progress.forEach {
            db.progress().upsert(
                ProgressEntity(it.subsectionId, it.status, it.bestOverall, it.bestCritical, it.timesRecalled, it.lastStudiedAt)
            )
        }
    }
}
