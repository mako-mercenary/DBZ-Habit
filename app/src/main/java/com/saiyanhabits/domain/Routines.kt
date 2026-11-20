package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** Routine rules. Pure functions, no Android. */
object RoutineRules {

    /** Item texts as typed: trimmed, blank ones dropped. */
    fun cleanItems(texts: List<String>): List<String> = texts.map { it.trim() }.filter { it.isNotEmpty() }

    /** A routine's name as typed, or [fallback] when it's blank. */
    fun cleanName(name: String, fallback: String = "Routine"): String = name.trim().ifEmpty { fallback }

    /** Power for checking an item off. Only today's items earn it, so checking off ahead or after the fact gives nothing. */
    fun powerFor(itemDay: Long, today: Long): Int = if (itemDay == today) GameConfig.ROUTINE_ITEM_POWER else 0

    /** True when [epochDay] can have a routine planned: not in the past, and not too far ahead. */
    fun canPlan(epochDay: Long, today: Long): Boolean =
        epochDay >= today && epochDay <= today + GameConfig.ROUTINE_PLAN_DAYS_AHEAD

    /** Done and total, as a pair. */
    fun progress(done: List<Boolean>): Pair<Int, Int> = done.count { it } to done.size
}
