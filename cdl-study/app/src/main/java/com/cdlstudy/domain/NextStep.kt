package com.cdlstudy.domain

/**
 * What to do next. The big button on the Today screen shows this, so it must
 * always point at one thing, and the right one.
 *
 * The order is the study plan's order: read and recall a section's parts, then
 * take that section's exam, then move to the next section. Nothing is skipped,
 * and an exam is not offered before its reading is done.
 */
sealed interface NextStep {

    /** Read and recall this part. */
    data class Study(
        val subsectionId: String,
        val title: String,
        val sectionNumber: Int,
        val firstTime: Boolean,
    ) : NextStep

    /** Every part of this track is recalled. Take the exam. */
    data class TakeExam(val track: Track, val bestPercent: Int?) : NextStep

    /** Cards you have learned have come due again. */
    data class ReviewCards(val count: Int) : NextStep

    /** Nothing is written to study yet. */
    data object NothingWritten : NextStep

    /** Everything written is recalled and every exam is at mastery. */
    data object AllDone : NextStep
}

object NextStepRules {

    /** The status strings the record uses for a part that is done. */
    private val RECALLED = setOf("recalled", "mastered")

    /**
     * @param statusOf the saved status of a part, or null when it is untouched.
     * @param bestOf the best exam score of a track, or null when never taken.
     */
    fun next(
        content: Content,
        statusOf: (String) -> String?,
        bestOf: (Track) -> Int?,
        dueCards: Int = 0,
    ): NextStep {
        // Cards come first, before any new reading.
        //
        // A card that has come due is time-sensitive in a way a subsection is
        // not: the whole point of the interval is that you are answering it at
        // the edge of forgetting. Reading a new part can wait an hour. A card
        // due today cannot wait a week.
        //
        // Only cards you have LEARNED count here. A card you have never seen
        // is due the moment it is made, and if those counted you would never
        // get past the first subsection.
        if (dueCards > 0) return NextStep.ReviewCards(dueCards)

        val sections = content.sections.sortedBy { it.number }
        if (sections.all { it.readyToRecall == 0 }) return NextStep.NothingWritten

        for (section in sections) {
            val parts = section.subsections.filter { it.canRecall }
            val unread = parts.firstOrNull { statusOf(it.id) !in RECALLED }
            if (unread != null) {
                return NextStep.Study(
                    subsectionId = unread.id,
                    title = unread.title,
                    sectionNumber = section.number,
                    firstTime = statusOf(unread.id) == null,
                )
            }
        }

        for (track in Track.entries) {
            val pool = content.examPool(track)
            if (pool.isEmpty()) continue
            val sectionsOfTrack = content.sections.filter { it.track == track }
            if (sectionsOfTrack.all { it.readyToRecall == 0 }) continue
            val best = bestOf(track)
            if (best == null || best < ExamStandards.MASTERY_PERCENT) {
                return NextStep.TakeExam(track, best)
            }
        }

        return NextStep.AllDone
    }

    /** The words on the button. */
    fun label(step: NextStep): String = when (step) {
        is NextStep.Study ->
            if (step.firstTime) "Next: ${step.subsectionId} ${step.title}"
            else "Again: ${step.subsectionId} ${step.title}"
        is NextStep.TakeExam ->
            if (step.bestPercent == null) "Next: ${step.track.label} exam"
            else "Beat ${step.bestPercent}%: ${step.track.label} exam"
        is NextStep.ReviewCards ->
            if (step.count == 1) "Next: 1 card due" else "Next: ${step.count} cards due"
        NextStep.NothingWritten -> "Nothing written to study yet"
        NextStep.AllDone -> "Everything is at mastery"
    }
}
