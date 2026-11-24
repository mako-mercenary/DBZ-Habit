package com.cdlstudy.domain

/**
 * Your study record, written out as JSON so it survives the phone.
 *
 * Recalls, the mark you gave every fact, the cards, the exam attempts and
 * answers, and the progress of each part. The content is not in here: the app
 * carries that, and the manual stays yours. This file is only your work.
 *
 * The format is written by hand rather than by a library, so the same code
 * runs in the app and in a plain unit test, and so a person can read the file.
 */
object Backup {

    /**
     * 1: the original.
     * 2: an exam attempt records whether it ran under test-day conditions, and
     *    how long it took.
     *
     * A version 1 file still reads: its attempts come back as practice
     * attempts with no time, which is what they were. A file from a newer
     * version is refused rather than read in part.
     */
    const val FORMAT = 2

    data class Record(
        val formatVersion: Int = FORMAT,
        val appVersion: String = "",
        val savedAt: Long = 0,
        val recalls: List<Recall> = emptyList(),
        val cards: List<Card> = emptyList(),
        val attempts: List<Attempt> = emptyList(),
        val progress: List<Progress> = emptyList(),
    ) {
        val counts: String
            get() = "${recalls.size} recalls, ${cards.size} cards, " +
                "${attempts.size} exams, ${progress.size} parts"
    }

    data class Recall(
        val subsectionId: String,
        val gradedAt: Long,
        val blurt: String,
        val overall: Int,
        val critical: Int,
        val factCount: Int,
        val criticalCount: Int,
        /** Fact id to HIT, PARTIAL or MISS. */
        val marks: Map<String, String>,
    )

    data class Card(
        val subsectionId: String,
        val factId: String?,
        val question: String,
        val answer: String,
        val createdAt: Long,
        val exportedAt: Long?,
        val source: String,
    )

    data class Attempt(
        val track: String,
        val finishedAt: Long,
        val total: Int,
        val correct: Int,
        val percent: Int,
        val answers: List<Answer>,
        /** Full length, no weighting toward your weak facts. Added in format 2. */
        val realConditions: Boolean = false,
        /** Seconds taken. Null for a format 1 attempt, which did not record it. */
        val seconds: Int? = null,
    )

    data class Answer(
        val itemId: String,
        val chosen: Int,
        val wasCorrect: Boolean,
        val factId: String?,
    )

    data class Progress(
        val subsectionId: String,
        val status: String,
        val bestOverall: Int,
        val bestCritical: Int,
        val timesRecalled: Int,
        val lastStudiedAt: Long?,
    )

    // ---------- writing ----------

    fun write(record: Record): String = buildString {
        append("{\n")
        append("  \"formatVersion\": ${record.formatVersion},\n")
        append("  \"appVersion\": ${quote(record.appVersion)},\n")
        append("  \"savedAt\": ${record.savedAt},\n")
        append("  \"recalls\": [\n")
        record.recalls.forEachIndexed { index, recall ->
            append("    {")
            append("\"subsectionId\": ${quote(recall.subsectionId)}, ")
            append("\"gradedAt\": ${recall.gradedAt}, ")
            append("\"overall\": ${recall.overall}, ")
            append("\"critical\": ${recall.critical}, ")
            append("\"factCount\": ${recall.factCount}, ")
            append("\"criticalCount\": ${recall.criticalCount}, ")
            append("\"blurt\": ${quote(recall.blurt)}, ")
            append("\"marks\": {")
            append(recall.marks.entries.joinToString(", ") { "${quote(it.key)}: ${quote(it.value)}" })
            append("}}")
            append(if (index == record.recalls.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"cards\": [\n")
        record.cards.forEachIndexed { index, card ->
            append("    {")
            append("\"subsectionId\": ${quote(card.subsectionId)}, ")
            append("\"factId\": ${quoteOrNull(card.factId)}, ")
            append("\"question\": ${quote(card.question)}, ")
            append("\"answer\": ${quote(card.answer)}, ")
            append("\"createdAt\": ${card.createdAt}, ")
            append("\"exportedAt\": ${card.exportedAt ?: "null"}, ")
            append("\"source\": ${quote(card.source)}}")
            append(if (index == record.cards.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"attempts\": [\n")
        record.attempts.forEachIndexed { index, attempt ->
            append("    {")
            append("\"track\": ${quote(attempt.track)}, ")
            append("\"finishedAt\": ${attempt.finishedAt}, ")
            append("\"total\": ${attempt.total}, ")
            append("\"correct\": ${attempt.correct}, ")
            append("\"percent\": ${attempt.percent}, ")
            append("\"realConditions\": ${attempt.realConditions}, ")
            attempt.seconds?.let { append("\"seconds\": $it, ") }
            append("\"answers\": [")
            append(
                attempt.answers.joinToString(", ") { answer ->
                    "{\"itemId\": ${quote(answer.itemId)}, \"chosen\": ${answer.chosen}, " +
                        "\"wasCorrect\": ${answer.wasCorrect}, \"factId\": ${quoteOrNull(answer.factId)}}"
                }
            )
            append("]}")
            append(if (index == record.attempts.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"progress\": [\n")
        record.progress.forEachIndexed { index, row ->
            append("    {")
            append("\"subsectionId\": ${quote(row.subsectionId)}, ")
            append("\"status\": ${quote(row.status)}, ")
            append("\"bestOverall\": ${row.bestOverall}, ")
            append("\"bestCritical\": ${row.bestCritical}, ")
            append("\"timesRecalled\": ${row.timesRecalled}, ")
            append("\"lastStudiedAt\": ${row.lastStudiedAt ?: "null"}}")
            append(if (index == record.progress.lastIndex) "\n" else ",\n")
        }
        append("  ]\n")
        append("}\n")
    }

    // ---------- reading ----------

    class BadBackup(message: String) : Exception(message)

    fun read(text: String): Record {
        val root = runCatching { MiniJson.parse(text) as? Map<*, *> }.getOrNull()
            ?: throw BadBackup("This file is not a study record.")
        val version = root.int("formatVersion")
            ?: throw BadBackup("This file has no format version, so it is not a study record.")
        if (version > FORMAT) {
            throw BadBackup(
                "This record was written by a newer version of the app (format $version). " +
                    "Update the app, then import it."
            )
        }
        return Record(
            formatVersion = version,
            appVersion = root.str("appVersion").orEmpty(),
            savedAt = root.long("savedAt") ?: 0,
            recalls = root.list("recalls").map { raw ->
                val map = raw.asMap("a recall")
                Recall(
                    subsectionId = map.str("subsectionId") ?: throw BadBackup("A recall has no subsection."),
                    gradedAt = map.long("gradedAt") ?: 0,
                    blurt = map.str("blurt").orEmpty(),
                    overall = map.int("overall") ?: 0,
                    critical = map.int("critical") ?: 0,
                    factCount = map.int("factCount") ?: 0,
                    criticalCount = map.int("criticalCount") ?: 0,
                    marks = (map["marks"] as? Map<*, *>)?.entries?.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        val value = entry.value as? String ?: return@mapNotNull null
                        key to value
                    }?.toMap().orEmpty(),
                )
            },
            cards = root.list("cards").map { raw ->
                val map = raw.asMap("a card")
                Card(
                    subsectionId = map.str("subsectionId").orEmpty(),
                    factId = map.str("factId"),
                    question = map.str("question") ?: throw BadBackup("A card has no question."),
                    answer = map.str("answer").orEmpty(),
                    createdAt = map.long("createdAt") ?: 0,
                    exportedAt = map.long("exportedAt"),
                    source = map.str("source") ?: "recall",
                )
            },
            attempts = root.list("attempts").map { raw ->
                val map = raw.asMap("an exam attempt")
                Attempt(
                    track = map.str("track") ?: throw BadBackup("An exam attempt has no track."),
                    finishedAt = map.long("finishedAt") ?: 0,
                    total = map.int("total") ?: 0,
                    correct = map.int("correct") ?: 0,
                    percent = map.int("percent") ?: 0,
                    // Absent in a format 1 file, and false is the truth there.
                    realConditions = map["realConditions"] as? Boolean ?: false,
                    seconds = map.int("seconds"),
                    answers = map.list("answers").map { rawAnswer ->
                        val answer = rawAnswer.asMap("an exam answer")
                        Answer(
                            itemId = answer.str("itemId").orEmpty(),
                            chosen = answer.int("chosen") ?: -1,
                            wasCorrect = answer["wasCorrect"] as? Boolean ?: false,
                            factId = answer.str("factId"),
                        )
                    },
                )
            },
            progress = root.list("progress").map { raw ->
                val map = raw.asMap("a progress row")
                Progress(
                    subsectionId = map.str("subsectionId") ?: throw BadBackup("A progress row has no subsection."),
                    status = map.str("status") ?: "new",
                    bestOverall = map.int("bestOverall") ?: 0,
                    bestCritical = map.int("bestCritical") ?: 0,
                    timesRecalled = map.int("timesRecalled") ?: 0,
                    lastStudiedAt = map.long("lastStudiedAt"),
                )
            },
        )
    }

    /** A file name with the date, such as gateway-record-2026-11-22.json */
    fun fileName(isoDate: String): String = "gateway-record-$isoDate.json"

    // ---------- helpers ----------

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        text.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        return out.append("\"").toString()
    }

    private fun quoteOrNull(text: String?): String = if (text == null) "null" else quote(text)

    private fun Any?.asMap(what: String): Map<*, *> =
        this as? Map<*, *> ?: throw BadBackup("Expected $what in the record.")

    private fun Map<*, *>.str(key: String): String? = this[key] as? String

    private fun Map<*, *>.int(key: String): Int? = when (val v = this[key]) {
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Int -> v
        else -> null
    }

    private fun Map<*, *>.long(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Double -> v.toLong()
        is Int -> v.toLong()
        else -> null
    }

    private fun Map<*, *>.list(key: String): List<Any?> = (this[key] as? List<*>) ?: emptyList()
}
