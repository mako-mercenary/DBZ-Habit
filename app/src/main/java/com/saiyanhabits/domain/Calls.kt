package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import kotlin.random.Random

/** What a call is about. */
enum class CallKind(val key: String, val label: String) {
    WISDOM("wisdom", "Advice"),
    ABOUT("about", "About them"),
    SUBJECT("subject", "Fun fact"),
    CHECK_IN("checkin", "Check-in"),
    GOSSIP("gossip", "Gossip"),
}

/** One call a mentor can make. */
data class MentorCall(val id: String, val kind: CallKind, val title: String, val script: List<ScriptNode>)

/** A mentor's calls (assets/dialogue/calls_<mentor>.json). */
data class CallBook(
    val mentor: Mentor,
    val calls: List<MentorCall>,
    /** The notification when they ring. */
    val ring: List<String>,
) {
    fun call(id: String): MentorCall? = calls.firstOrNull { it.id == id }
}

/** Call rules. Pure functions, no Android. */
object CallRules {

    /**
     * Today's call from a mentor: one not heard yet, or, once all have been
     * heard, any except the [last] one. Check-ins are kept for when there are
     * numbers to talk about: never on the [daysUsing] first two days.
     */
    fun pickCall(calls: List<MentorCall>, heard: Set<String>, random: Random, last: String? = null, daysUsing: Int = 99): MentorCall? {
        val usable = calls.filter { daysUsing >= 2 || it.kind != CallKind.CHECK_IN }.ifEmpty { calls }
        if (usable.isEmpty()) return null
        val fresh = usable.filter { it.id !in heard }
        val pool = fresh.ifEmpty { usable.filter { it.id != last }.ifEmpty { usable } }
        return pool[random.nextInt(pool.size)]
    }

    /** Bond XP for answering, with the bonus for picking up soon after the phone rang. */
    fun bondXp(pickedUpQuickly: Boolean): Int =
        GameConfig.CALL_BOND_XP + if (pickedUpQuickly) GameConfig.CALL_QUICK_PICKUP_BONUS_XP else 0

    /** True when [answeredAtMillis] is within the pickup window after [rangAtMillis]. */
    fun isQuick(rangAtMillis: Long?, answeredAtMillis: Long): Boolean {
        if (rangAtMillis == null || answeredAtMillis < rangAtMillis) return false
        return answeredAtMillis - rangAtMillis <= GameConfig.CALL_PICKUP_WINDOW_MINUTES * 60_000L
    }

    /** Who rings today: a mentor you haven't talked to yet today, or nobody if you've talked to them all. */
    fun whoRings(answeredToday: Set<Mentor>, random: Random): Mentor? {
        val left = Mentor.entries.filter { it !in answeredToday }
        return if (left.isEmpty()) null else left[random.nextInt(left.size)]
    }

    /** The minute of the day the phone rings, inside the ring hours. */
    fun ringMinute(random: Random): Int {
        val (from, to) = GameConfig.CALL_RING_HOURS
        return from * 60 + random.nextInt((to - from) * 60)
    }
}

/** Reads a mentor's calls file. Throws IllegalArgumentException if it is malformed. */
object CallParser {
    fun parse(json: String): CallBook {
        val root = MiniJson.parse(json).asObject("the file")
        val name = root["mentor"].asString("\"mentor\"")
        val mentor = Mentor.entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException("Unknown mentor \"$name\"")
        val calls = root["calls"].asArray("\"calls\"").mapIndexed { i, c ->
            val o = c.asObject("call $i")
            val id = o["id"].asString("call $i id")
            val kindKey = o["kind"].asString("kind of $id")
            MentorCall(
                id = id,
                kind = CallKind.entries.firstOrNull { it.key == kindKey } ?: throw IllegalArgumentException("$id has unknown kind \"$kindKey\""),
                title = o["title"].asString("title of $id"),
                script = ScriptParser.nodes(o["lines"], "$id lines"),
            )
        }
        require(calls.map { it.id }.toSet().size == calls.size) { "Two calls share an id" }
        return CallBook(mentor, calls, root["ring"].asArray("\"ring\"").map { it.asString("a ring line") })
    }
}
