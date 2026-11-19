package com.saiyanhabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** The JSON reader, and the dialogue files that ship in the app. */
class DialogueTest {

    // ---- MiniJson ----

    @Test
    fun `reads objects, arrays, text, numbers, booleans and null`() {
        val parsed = MiniJson.parse("""{"a": [1, 2.5, -3], "b": "hi", "c": true, "d": false, "e": null, "f": {}}""")
        assertEquals(
            mapOf("a" to listOf(1L, 2.5, -3L), "b" to "hi", "c" to true, "d" to false, "e" to null, "f" to emptyMap<String, Any?>()),
            parsed,
        )
    }

    @Test
    fun `reads escapes inside text`() {
        assertEquals("say \"hi\"\nnew line \\ é", MiniJson.parse(""""say \"hi\"\nnew line \\ é""""))
    }

    @Test
    fun `keeps apostrophes and unicode as they are`() {
        assertEquals("Don't… stop", MiniJson.parse("\"Don't… stop\""))
    }

    @Test
    fun `reports the line of a mistake`() {
        val broken = "{\n  \"a\": 1,\n  \"b\" 2\n}"
        try {
            MiniJson.parse(broken)
            fail("Expected a parse error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("line 3"))
        }
    }

    @Test
    fun `rejects unfinished and trailing text`() {
        for (bad in listOf("", "{", "[1, 2", "\"open", "{} extra", "{\"a\":}", "[1,]")) {
            try {
                MiniJson.parse(bad)
                fail("Expected \"$bad\" to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- DialogueParser ----

    private val small = """
        {
          "mentor": "GOKU",
          "reactions": {
            "missed_day": ["a"], "comeback": ["b"], "streak_milestone": ["c {streak}"],
            "boss_defeated": ["d {boss}"], "idle": ["e", "f"]
          },
          "scenes": [
            {"level": 3, "title": "Later", "lines": [{"speaker": "YOU", "text": "Hi"}]},
            {"level": 2, "title": "First", "lines": [{"speaker": "GOKU", "text": "Hey!"}, {"speaker": "NARRATOR", "text": "Wind."}]}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a mentor's file`() {
        val dialogue = DialogueParser.parse(small)
        assertEquals(Mentor.GOKU, dialogue.mentor)
        assertEquals(listOf("e", "f"), dialogue.reactions[ReactionTrigger.IDLE])
        assertEquals(listOf(2, 3), dialogue.scenes.map { it.level })
        assertEquals("First", dialogue.scene(2)?.title)
        assertNull(dialogue.scene(9))
    }

    @Test
    fun `tells mentor lines from the player's and the narrator's`() {
        val lines = DialogueParser.parse(small).scene(2)!!.lines
        assertEquals(Mentor.GOKU, lines[0].mentor)
        assertNull(lines[1].mentor)
        assertNull(SceneLine("YOU", "Hi").mentor)
    }

    @Test
    fun `says what is wrong with a malformed file`() {
        val missingTrigger = small.replace("\"comeback\": [\"b\"],", "")
        try {
            DialogueParser.parse(missingTrigger)
            fail("Expected an error about the missing trigger")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("comeback"))
        }
        try {
            DialogueParser.parse(small.replace("GOKU\",", "KRILLIN\","))
            fail("Expected an error about the unknown mentor")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("KRILLIN"))
        }
    }

    // ---- The files that ship ----

    /** Unit tests run from the app module's folder. */
    private val dialogueDir = File("src/main/assets/dialogue")

    private fun shipped(mentor: Mentor): MentorDialogue {
        val file = File(dialogueDir, "${mentor.name.lowercase()}.json")
        assertTrue("Missing ${file.path}", file.isFile)
        val dialogue = DialogueParser.parse(file.readText())
        assertEquals("${file.name} is for the wrong mentor", mentor, dialogue.mentor)
        return dialogue
    }

    @Test
    fun `every mentor has at least 8 lines for every trigger`() {
        for (mentor in Mentor.entries) {
            val dialogue = shipped(mentor)
            for (trigger in ReactionTrigger.entries) {
                val lines = dialogue.reactions[trigger].orEmpty()
                assertTrue("$mentor has ${lines.size} ${trigger.key} lines", lines.size >= 8)
                assertTrue("$mentor has a blank ${trigger.key} line", lines.none { it.isBlank() })
                assertEquals("$mentor repeats a ${trigger.key} line", lines.size, lines.toSet().size)
            }
        }
    }

    @Test
    fun `every mentor has at least 8 evening nudges with no placeholders`() {
        for (mentor in Mentor.entries) {
            val lines = shipped(mentor).evening
            assertTrue("$mentor has ${lines.size} evening lines", lines.size >= 8)
            assertTrue("$mentor has a blank evening line", lines.none { it.isBlank() })
            assertEquals("$mentor repeats an evening line", lines.size, lines.toSet().size)
            assertTrue("$mentor has a placeholder in an evening line", lines.none { '{' in it || '}' in it })
        }
    }

    @Test
    fun `a file without evening lines still loads`() {
        val file = File(dialogueDir, "goku.json").readText()
        val start = file.indexOf("\"evening\"")
        val end = file.indexOf("],", start) + 2
        val without = DialogueParser.parse(file.removeRange(start, end))
        assertTrue(without.evening.isEmpty())
        assertTrue(without.reactions.values.all { it.isNotEmpty() })
    }

    @Test
    fun `every mentor has a full scene for each bond level from 2 to 10`() {
        for (mentor in Mentor.entries) {
            val dialogue = shipped(mentor)
            assertEquals("$mentor scene levels", (2..BondRules.MAX_LEVEL).toList(), dialogue.scenes.map { it.level })
            for (scene in dialogue.scenes) {
                assertTrue("$mentor level ${scene.level} has no title", scene.title.isNotBlank())
                // Played with the first reply each time: long enough to be a real conversation.
                val played = ScriptRunner.play(scene.script, List(10) { 0 }, ScriptStats()).count { it !is ScriptStep.Ask }
                assertTrue("$mentor level ${scene.level} plays $played lines", played in 12..30)
                assertTrue("$mentor level ${scene.level} has no reply to pick", scene.script.any { it is ScriptNode.Choice })
                assertTrue("$mentor level ${scene.level} has a blank line", scene.lines.none { it.text.isBlank() })
            }
        }
    }

    @Test
    fun `scene speakers are mentors, the player or the narrator, and the scene's mentor speaks`() {
        for (mentor in Mentor.entries) {
            for (scene in shipped(mentor).scenes) {
                val speakers = scene.lines.map { it.speaker }.toSet()
                assertTrue(
                    "$mentor level ${scene.level} has speakers $speakers",
                    (speakers - Mentor.entries.map { it.name }.toSet() - setOf("YOU", "NARRATOR")).isEmpty(),
                )
                assertTrue("$mentor never speaks in the level ${scene.level} scene", mentor.name in speakers)
            }
        }
    }

    @Test
    fun `lines use only the placeholders the app fills in`() {
        val placeholder = Regex("\\{[^}]*\\}")
        for (mentor in Mentor.entries) {
            val dialogue = shipped(mentor)
            for ((trigger, lines) in dialogue.reactions) {
                val allowed = when (trigger) {
                    ReactionTrigger.STREAK_MILESTONE -> setOf("{streak}")
                    ReactionTrigger.BOSS_DEFEATED -> setOf("{boss}")
                    else -> emptySet()
                }
                for (line in lines) {
                    val used = placeholder.findAll(line).map { it.value }.toSet()
                    assertTrue("$mentor ${trigger.key}: \"$line\" uses $used", (used - allowed).isEmpty())
                }
            }
            val inScenes = dialogue.scenes.flatMap { it.lines }.filter { placeholder.containsMatchIn(it.text) }
            assertFalse("$mentor has a placeholder in a scene", inScenes.isNotEmpty())
        }
    }

    @Test
    fun `every streak line names the streak`() {
        for (mentor in Mentor.entries) {
            val lines = shipped(mentor).reactions[ReactionTrigger.STREAK_MILESTONE].orEmpty()
            assertTrue("$mentor has a streak line without {streak}", lines.all { "{streak}" in it })
        }
    }
}
