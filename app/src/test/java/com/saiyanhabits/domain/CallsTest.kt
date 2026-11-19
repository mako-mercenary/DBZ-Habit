package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CallsTest {

    private val line = { s: String, t: String -> ScriptNode.Line(s, t) }

    private val script = listOf(
        line("GOKU", "Hi {streak}!"),
        ScriptNode.Choice(
            listOf(
                ScriptNode.Choice.Option("A", listOf(line("GOKU", "picked A"))),
                ScriptNode.Choice.Option("B", listOf(line("GOKU", "picked B"))),
            ),
        ),
        ScriptNode.Check(
            "streak",
            listOf(
                ScriptNode.Check.Branch(7, listOf(line("GOKU", "long"))),
                ScriptNode.Check.Branch(0, listOf(line("GOKU", "short"))),
            ),
        ),
        line("GOKU", "Bye"),
    )

    @Test
    fun `a script stops at the first unanswered choice`() {
        val steps = ScriptRunner.play(script, emptyList(), ScriptStats(mapOf("streak" to 3)))
        assertEquals(listOf(ScriptStep.Say("GOKU", "Hi 3!"), ScriptStep.Ask(listOf("A", "B"))), steps)
        assertFalse(ScriptRunner.isFinished(script, emptyList(), ScriptStats()))
    }

    @Test
    fun `a picked reply plays its branch and checks follow the numbers`() {
        val short = ScriptRunner.play(script, listOf(1), ScriptStats(mapOf("streak" to 3)))
        assertEquals(
            listOf(
                ScriptStep.Say("GOKU", "Hi 3!"),
                ScriptStep.Picked("B"),
                ScriptStep.Say("GOKU", "picked B"),
                ScriptStep.Say("GOKU", "short"),
                ScriptStep.Say("GOKU", "Bye"),
            ),
            short,
        )
        val long = ScriptRunner.play(script, listOf(0), ScriptStats(mapOf("streak" to 9)))
        assertTrue(ScriptStep.Say("GOKU", "long") in long)
        assertTrue(ScriptRunner.isFinished(script, listOf(0), ScriptStats()))
    }

    @Test
    fun `placeholders fill from numbers and words, unknown ones stay`() {
        val stats = ScriptStats(mapOf("power" to 120), mapOf("top_habit" to "Reading"))
        assertEquals("120 and Reading and {nope}", stats.fill("{power} and {top_habit} and {nope}"))
        assertEquals(setOf("streak"), ScriptRunner.statsUsed(script))
    }

    @Test
    fun `calls are picked fresh until all are heard`() {
        val calls = (1..4).map { MentorCall("c$it", CallKind.WISDOM, "t", emptyList()) }
        val heard = mutableSetOf<String>()
        val random = Random(3)
        repeat(4) {
            val c = CallRules.pickCall(calls, heard, random)!!
            assertFalse(c.id in heard)
            heard += c.id
        }
        repeat(10) { assertNotEquals("c2", CallRules.pickCall(calls, heard, Random(it), last = "c2")!!.id) }
    }

    @Test
    fun `check-ins wait for a couple of days of numbers`() {
        val calls = listOf(MentorCall("w", CallKind.WISDOM, "t", emptyList()), MentorCall("c", CallKind.CHECK_IN, "t", emptyList()))
        repeat(10) { assertEquals("w", CallRules.pickCall(calls, emptySet(), Random(it), daysUsing = 1)!!.id) }
    }

    @Test
    fun `quick pickups earn the bonus`() {
        val window = GameConfig.CALL_PICKUP_WINDOW_MINUTES * 60_000L
        assertTrue(CallRules.isQuick(1_000, 1_000 + window))
        assertFalse(CallRules.isQuick(1_000, 1_000 + window + 1))
        assertFalse(CallRules.isQuick(null, 5_000))
        assertFalse(CallRules.isQuick(5_000, 1_000))
        assertEquals(GameConfig.CALL_BOND_XP + GameConfig.CALL_QUICK_PICKUP_BONUS_XP, CallRules.bondXp(true))
        assertEquals(GameConfig.CALL_BOND_XP, CallRules.bondXp(false))
    }

    @Test
    fun `nobody rings once everyone has been called`() {
        assertEquals(null, CallRules.whoRings(Mentor.entries.toSet(), Random(1)))
        val left = Mentor.entries.filter { it != Mentor.GOKU }.toSet()
        assertEquals(Mentor.GOKU, CallRules.whoRings(left, Random(1)))
        repeat(50) {
            val m = CallRules.ringMinute(Random(it))
            assertTrue(m >= GameConfig.CALL_RING_HOURS.first * 60 && m < GameConfig.CALL_RING_HOURS.second * 60)
        }
    }

    private val knownStats = setOf(
        "streak", "power", "week_checkoffs", "week_missed", "journal_streak", "bond_level", "habits", "days_using",
        "top_habit", "weak_habit",
    )
    private val speakers = Mentor.entries.map { it.name }.toSet() + setOf("YOU", "NARRATOR")

    /** Every path through a script: all combinations of replies, with each check branch forced in turn. */
    private fun everyPath(script: List<ScriptNode>): List<List<ScriptStep>> {
        val results = ArrayList<List<ScriptStep>>()
        val checks = ArrayList<ScriptNode.Check>()
        fun collect(nodes: List<ScriptNode>) {
            nodes.forEach { n ->
                when (n) {
                    is ScriptNode.Check -> { checks += n; n.branches.forEach { collect(it.then) } }
                    is ScriptNode.Choice -> n.options.forEach { collect(it.then) }
                    else -> Unit
                }
            }
        }
        collect(script)
        val statValues = checks.flatMap { c -> c.branches.map { c.stat to it.min } }.ifEmpty { listOf("none" to 0) }
        for ((stat, value) in statValues) {
            val stats = ScriptStats(knownStats.associateWith { 0 } + (stat to value), mapOf("top_habit" to "X", "weak_habit" to "Y"))
            fun explore(picked: List<Int>) {
                val steps = ScriptRunner.play(script, picked, stats)
                val ask = steps.lastOrNull() as? ScriptStep.Ask
                if (ask == null) results += steps else ask.options.indices.forEach { explore(picked + it) }
            }
            explore(emptyList())
        }
        return results
    }

    @Test
    fun `every mentor has 30 well-formed calls`() {
        for (mentor in Mentor.entries) {
            val file = File("src/main/assets/dialogue/calls_${mentor.name.lowercase()}.json")
            val book = CallParser.parse(file.readText())
            assertEquals(mentor, book.mentor)
            assertEquals("${mentor.name} call count", 30, book.calls.size)
            assertTrue(book.ring.isNotEmpty())
            for (kind in CallKind.entries) assertTrue("${mentor.name} has no ${kind.key} calls", book.calls.any { it.kind == kind })
            for (call in book.calls) {
                val unknown = ScriptRunner.statsUsed(call.script) - knownStats
                assertTrue("${call.id} uses unknown stats $unknown", unknown.isEmpty())
                ScriptRunner.allLines(call.script).forEach {
                    assertTrue("${call.id}: unknown speaker ${it.speaker}", it.speaker in speakers)
                    assertTrue("${call.id}: empty line", it.text.isNotBlank())
                }
                for (path in everyPath(call.script)) {
                    val said = path.count { it !is ScriptStep.Ask }
                    assertTrue("${call.id} has a path only $said lines long", said >= 6)
                    assertTrue("${call.id}: a path leaves a {placeholder} unfilled", path.none { it is ScriptStep.Say && Regex("\\{[a-z_]+\\}").containsMatchIn(it.text) })
                }
            }
        }
    }
}
