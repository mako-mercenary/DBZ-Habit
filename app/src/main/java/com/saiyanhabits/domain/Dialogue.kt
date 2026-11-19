package com.saiyanhabits.domain

/** One line of a bond scene. [speaker] is a mentor's name in capitals, "YOU" or "NARRATOR". */
data class SceneLine(val speaker: String, val text: String) {
    /** The mentor speaking, or null for the player and the narrator. */
    val mentor: Mentor? get() = Mentor.entries.firstOrNull { it.name == speaker }
}

/** The scene unlocked at one bond level. */
data class BondScene(val level: Int, val title: String, val script: List<ScriptNode>) {
    /** Every line in every branch. */
    val lines: List<SceneLine> get() = ScriptRunner.allLines(script).map { SceneLine(it.speaker, it.text) }
}

/** Everything one mentor can say: home-screen one-liners per trigger, and bond scenes. */
data class MentorDialogue(
    val mentor: Mentor,
    val reactions: Map<ReactionTrigger, List<String>>,
    val scenes: List<BondScene>,
    /** Evening nudges, for a day with training still open. */
    val evening: List<String> = emptyList(),
    /** What the mentor says during their mini game. */
    val game: List<String> = emptyList(),
    /** What the mentor says when their mini game ends (for a game you can lose: when you win). */
    val gameDone: List<String> = emptyList(),
    /** What the mentor says when you lose their game. */
    val gameLost: List<String> = emptyList(),
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
                script = ScriptParser.nodes(scene["lines"], "lines of the level $level scene"),
            )
        }.sortedBy { it.level }

        // Optional, so a file without evening lines still loads: that mentor just never nudges.
        val evening = root["evening"]?.asArray("\"evening\"")?.map { it.asString("an evening line") }.orEmpty()

        val game = root["game"]?.asArray("\"game\"")?.map { it.asString("a game line") }.orEmpty()

        val gameDone = root["game_done"]?.asArray("\"game_done\"")?.map { it.asString("a game_done line") }.orEmpty()

        val gameLost = root["game_lost"]?.asArray("\"game_lost\"")?.map { it.asString("a game_lost line") }.orEmpty()

        return MentorDialogue(mentor, reactions, scenes, evening, game, gameDone, gameLost)
    }
}

/** A story chapter: a short scene told in lines, like a bond scene. */
data class StoryChapter(val number: Int, val title: String, val script: List<ScriptNode>) {
    /** Every line in every branch. */
    val lines: List<SceneLine> get() = ScriptRunner.allLines(script).map { SceneLine(it.speaker, it.text) }

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
                script = ScriptParser.nodes(chapter["lines"], "lines of chapter $number"),
            )
        }.sortedBy { it.number }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObject(what: String): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw IllegalArgumentException("Expected $what to be an object")

internal fun Any?.asArray(what: String): List<Any?> =
    this as? List<Any?> ?: throw IllegalArgumentException("Expected $what to be a list")

internal fun Any?.asString(what: String): String =
    this as? String ?: throw IllegalArgumentException("Expected $what to be text")

internal fun Any?.asInt(what: String): Int =
    (this as? Number)?.toInt() ?: throw IllegalArgumentException("Expected $what to be a number")
