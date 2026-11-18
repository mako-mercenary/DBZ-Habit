package com.saiyanhabits.domain.games

/** One part of a breath. */
enum class BreathPhase(val label: String) {
    IN("Breathe in"),
    HOLD_IN("Hold"),
    OUT("Breathe out"),
    HOLD_OUT("Hold"),
}

/** A breathing rhythm: how many seconds each phase lasts (0 skips it). */
enum class BreathPattern(val title: String, val caption: String, val seconds: List<Pair<BreathPhase, Int>>) {
    CALM("Calm", "in 4, out 6", listOf(BreathPhase.IN to 4, BreathPhase.OUT to 6)),
    BOX("Box", "4 in, hold, out, hold", listOf(BreathPhase.IN to 4, BreathPhase.HOLD_IN to 4, BreathPhase.OUT to 4, BreathPhase.HOLD_OUT to 4)),
    RELAX("4-7-8", "for winding down", listOf(BreathPhase.IN to 4, BreathPhase.HOLD_IN to 7, BreathPhase.OUT to 8)),
    ;

    val cycleSeconds: Int get() = seconds.sumOf { it.second }
}

/** Where a session is at a moment. */
data class BreathMoment(
    val phase: BreathPhase,
    /** Progress through the phase, 0 to 1. */
    val phaseProgress: Float,
    /** Seconds left in the phase, rounded up. */
    val phaseSecondsLeft: Int,
    /** How full the lungs are, 0 (empty) to 1 (full): what the aura shows. */
    val fullness: Float,
    /** Whole breaths finished. */
    val breaths: Int,
)

/** Breathing session rules. Pure functions, no Android. */
object Breathing {

    fun momentAt(pattern: BreathPattern, elapsedSeconds: Float): BreathMoment {
        val cycle = pattern.cycleSeconds.toFloat()
        val t = elapsedSeconds.coerceAtLeast(0f)
        val breaths = (t / cycle).toInt()
        var inCycle = t - breaths * cycle
        for ((phase, secs) in pattern.seconds) {
            if (inCycle < secs) {
                val progress = inCycle / secs
                val fullness = when (phase) {
                    BreathPhase.IN -> progress
                    BreathPhase.HOLD_IN -> 1f
                    BreathPhase.OUT -> 1f - progress
                    BreathPhase.HOLD_OUT -> 0f
                }
                return BreathMoment(phase, progress, kotlin.math.ceil(secs - inCycle).toInt(), smooth(fullness), breaths)
            }
            inCycle -= secs
        }
        // Exactly on the end of a cycle.
        return BreathMoment(pattern.seconds.first().first, 0f, pattern.seconds.first().second, 0f, breaths + 1)
    }

    /** Eases the aura so it swells and settles gently rather than at a constant rate. */
    private fun smooth(x: Float): Float = x * x * (3 - 2 * x)

    /** A session ending this early isn't counted. */
    const val MIN_COUNTED_SECONDS = 60
}
