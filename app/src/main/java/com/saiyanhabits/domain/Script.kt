package com.saiyanhabits.domain

/**
 * A conversation that can branch: used by mentor calls and cutscenes. A script
 * is a list of nodes; a [ScriptNode.Choice] lets you pick a reply, and a
 * [ScriptNode.Check] picks lines from your real numbers (streak, power...).
 */
sealed interface ScriptNode {
    /** One line. [speaker] is a mentor's name, "YOU" or "NARRATOR". */
    data class Line(val speaker: String, val text: String) : ScriptNode

    /** A reply for you to pick; each option carries what follows it. */
    data class Choice(val options: List<Option>) : ScriptNode {
        data class Option(val text: String, val then: List<ScriptNode>)
    }

    /** The first branch whose [Branch.min] the stat reaches is played. */
    data class Check(val stat: String, val branches: List<Branch>) : ScriptNode {
        data class Branch(val min: Int, val then: List<ScriptNode>)
    }
}

/** What to show next in a conversation. */
sealed interface ScriptStep {
    data class Say(val speaker: String, val text: String) : ScriptStep

    /** A reply to pick, with the option texts. */
    data class Ask(val options: List<String>) : ScriptStep

    /** A reply you already picked, shown as your line. */
    data class Picked(val text: String) : ScriptStep
}

/** Your numbers, for checks and for {placeholders} in lines. */
data class ScriptStats(val numbers: Map<String, Int> = emptyMap(), val words: Map<String, String> = emptyMap()) {
    fun number(name: String): Int = numbers[name] ?: 0

    /** [text] with every {name} replaced by its number or word; unknown names are left as they are. */
    fun fill(text: String): String = PLACEHOLDER.replace(text) { m ->
        val name = m.groupValues[1]
        numbers[name]?.toString() ?: words[name] ?: m.value
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{([a-z_]+)\\}")
    }
}

/** Plays scripts. Pure functions, no Android. */
object ScriptRunner {

    /**
     * Everything shown so far, given the replies [picked] (option indexes, in
     * order). Stops at the first choice not yet made, which is last as [ScriptStep.Ask];
     * if every choice is made, the whole conversation is returned.
     */
    fun play(nodes: List<ScriptNode>, picked: List<Int>, stats: ScriptStats): List<ScriptStep> {
        val out = ArrayList<ScriptStep>()
        var choice = 0
        fun walk(list: List<ScriptNode>): Boolean {
            for (node in list) {
                when (node) {
                    is ScriptNode.Line -> out += ScriptStep.Say(node.speaker, stats.fill(node.text))
                    is ScriptNode.Check -> {
                        val value = stats.number(node.stat)
                        val branch = node.branches.firstOrNull { value >= it.min }
                        if (branch != null && !walk(branch.then)) return false
                    }
                    is ScriptNode.Choice -> {
                        val index = picked.getOrNull(choice)
                        if (index == null || index !in node.options.indices) {
                            out += ScriptStep.Ask(node.options.map { stats.fill(it.text) })
                            return false
                        }
                        choice++
                        val option = node.options[index]
                        out += ScriptStep.Picked(stats.fill(option.text))
                        if (!walk(option.then)) return false
                    }
                }
            }
            return true
        }
        walk(nodes)
        return out
    }

    /** True when [picked] answers every choice on the way through. */
    fun isFinished(nodes: List<ScriptNode>, picked: List<Int>, stats: ScriptStats): Boolean =
        play(nodes, picked, stats).lastOrNull() !is ScriptStep.Ask

    /** Every line in every branch, for checks on the files. */
    fun allLines(nodes: List<ScriptNode>): List<ScriptNode.Line> = nodes.flatMap { node ->
        when (node) {
            is ScriptNode.Line -> listOf(node)
            is ScriptNode.Choice -> node.options.flatMap { allLines(it.then) }
            is ScriptNode.Check -> node.branches.flatMap { allLines(it.then) }
        }
    }

    /** Every reply option text, for checks on the files. */
    fun allOptions(nodes: List<ScriptNode>): List<String> = nodes.flatMap { node ->
        when (node) {
            is ScriptNode.Line -> emptyList()
            is ScriptNode.Choice -> node.options.flatMap { listOf(it.text) + allOptions(it.then) }
            is ScriptNode.Check -> node.branches.flatMap { allOptions(it.then) }
        }
    }

    /** The stats a script's checks and placeholders use. */
    fun statsUsed(nodes: List<ScriptNode>): Set<String> {
        val names = HashSet<String>()
        val placeholder = Regex("\\{([a-z_]+)\\}")
        fun walk(list: List<ScriptNode>) {
            for (node in list) when (node) {
                is ScriptNode.Line -> placeholder.findAll(node.text).forEach { names += it.groupValues[1] }
                is ScriptNode.Choice -> node.options.forEach { o ->
                    placeholder.findAll(o.text).forEach { names += it.groupValues[1] }
                    walk(o.then)
                }
                is ScriptNode.Check -> {
                    names += node.stat
                    node.branches.forEach { walk(it.then) }
                }
            }
        }
        walk(nodes)
        return names
    }
}

/** Reads script nodes from parsed JSON. Throws IllegalArgumentException if malformed. */
object ScriptParser {

    /**
     * A node is one of:
     * {"speaker": "GOKU", "text": "..."},
     * {"choice": [{"text": "reply", "then": [nodes]}, ...]}, or
     * {"check": "streak", "branches": [{"min": 7, "then": [nodes]}, ...]}.
     */
    fun nodes(json: Any?, where: String): List<ScriptNode> = json.asArray(where).mapIndexed { i, item ->
        val o = item.asObject("$where[$i]")
        when {
            o.containsKey("choice") -> ScriptNode.Choice(
                o["choice"].asArray("$where[$i].choice").mapIndexed { k, opt ->
                    val option = opt.asObject("$where[$i].choice[$k]")
                    ScriptNode.Choice.Option(
                        option["text"].asString("$where[$i].choice[$k].text"),
                        nodes(option["then"] ?: emptyList<Any?>(), "$where[$i].choice[$k].then"),
                    )
                }.also { require(it.size >= 2) { "$where[$i] is a choice with fewer than two options" } },
            )
            o.containsKey("check") -> ScriptNode.Check(
                o["check"].asString("$where[$i].check"),
                o["branches"].asArray("$where[$i].branches").mapIndexed { k, b ->
                    val branch = b.asObject("$where[$i].branches[$k]")
                    ScriptNode.Check.Branch(
                        branch["min"].asInt("$where[$i].branches[$k].min"),
                        nodes(branch["then"], "$where[$i].branches[$k].then"),
                    )
                }.sortedByDescending { it.min },
            )
            else -> ScriptNode.Line(o["speaker"].asString("$where[$i].speaker"), o["text"].asString("$where[$i].text"))
        }
    }
}
