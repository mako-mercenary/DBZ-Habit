package com.saiyanhabits.domain

/** One line of a bond scene. [speaker] is a mentor's name in capitals, "YOU" or "NARRATOR". */
data class SceneLine(val speaker: String, val text: String) {
    /** The mentor speaking, or null for the player and the narrator. */
    val mentor: Mentor? get() = Mentor.entries.firstOrNull { it.name == speaker }
}

/** The scene unlocked at one bond level. */
data class BondScene(val level: Int, val title: String, val lines: List<SceneLine>)

/** Everything one mentor can say: home-screen one-liners per trigger, and bond scenes. */
data class MentorDialogue(
    val mentor: Mentor,
    val reactions: Map<ReactionTrigger, List<String>>,
    val scenes: List<BondScene>,
    /** Evening nudges, for a day with training still open. */
    val evening: List<String> = emptyList(),
    /** What the mentor says during their mini game. */
    val game: List<String> = emptyList(),
) {
    fun scene(level: Int): BondScene? = scenes.firstOrNull { it.level == level }
}

/**
 * Reads a mentor's dialogue file (assets/dialogue/<mentor>.json).
 * Throws IllegalArgumentException with a readable message if the file is malformed.
 */
object DialogueParser {

    fun parse(json: String): MentorDialogue {
        val root = MiniJson.parse(json).asObject("the file")
        val mentorName = root["mentor"].asString("\"mentor\"")
        val mentor = Mentor.entries.firstOrNull { it.name == mentorName }
            ?: throw IllegalArgumentException("Unknown mentor \"$mentorName\"")

        val reactionsJson = root["reactions"].asObject("\"reactions\"")
        val reactions = ReactionTrigger.entries.associateWith { trigger ->
            reactionsJson[trigger.key].asArray("reactions.${trigger.key}").map { it.asString("a ${trigger.key} line") }
        }

        val scenes = root["scenes"].asArray("\"scenes\"").map { sceneJson ->
            val scene = sceneJson.asObject("a scene")
            val level = scene["level"].asInt("scene level")
            BondScene(
                level = level,
                title = scene["title"].asString("title of the level $level scene"),
                lines = scene["lines"].asArray("lines of the level $level scene").map { lineJson ->
                    val line = lineJson.asObject("a line of the level $level scene")
                    SceneLine(
                        speaker = line["speaker"].asString("speaker in the level $level scene"),
                        text = line["text"].asString("text in the level $level scene"),
                    )
                },
            )
        }.sortedBy { it.level }

        // Optional, so a file without evening lines still loads: that mentor just never nudges.
        val evening = root["evening"]?.asArray("\"evening\"")?.map { it.asString("an evening line") }.orEmpty()

        val game = root["game"]?.asArray("\"game\"")?.map { it.asString("a game line") }.orEmpty()

        return MentorDialogue(mentor, reactions, scenes, evening, game)
    }
}

/** A story chapter: a short scene told in lines, like a bond scene. */
data class StoryChapter(val number: Int, val title: String, val lines: List<SceneLine>) {
    /** Number of words to read, for keeping chapters short. */
    val wordCount: Int get() = lines.sumOf { line -> line.text.split(Regex("\\s+")).count { it.isNotBlank() } }
}

/**
 * Reads the story file (assets/dialogue/story.json).
 * Throws IllegalArgumentException with a readable message if the file is malformed.
 */
object StoryParser {

    fun parse(json: String): List<StoryChapter> {
        val root = MiniJson.parse(json).asObject("the file")
        return root["chapters"].asArray("\"chapters\"").map { chapterJson ->
            val chapter = chapterJson.asObject("a chapter")
            val number = chapter["number"].asInt("chapter number")
            StoryChapter(
                number = number,
                title = chapter["title"].asString("title of chapter $number"),
                lines = chapter["lines"].asArray("lines of chapter $number").map { lineJson ->
                    val line = lineJson.asObject("a line of chapter $number")
                    SceneLine(
                        speaker = line["speaker"].asString("speaker in chapter $number"),
                        text = line["text"].asString("text in chapter $number"),
                    )
                },
            )
        }.sortedBy { it.number }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asObject(what: String): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw IllegalArgumentException("Expected $what to be an object")

private fun Any?.asArray(what: String): List<Any?> =
    this as? List<Any?> ?: throw IllegalArgumentException("Expected $what to be a list")

private fun Any?.asString(what: String): String =
    this as? String ?: throw IllegalArgumentException("Expected $what to be text")

private fun Any?.asInt(what: String): Int =
    (this as? Number)?.toInt() ?: throw IllegalArgumentException("Expected $what to be a number")
