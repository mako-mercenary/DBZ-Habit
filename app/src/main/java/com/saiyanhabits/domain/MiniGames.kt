package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** The mini games, one per mentor. Stored by name: do not rename. */
enum class MiniGame(
    val mentor: Mentor,
    val title: String,
    val blurb: String,
    /** What a play's score is, e.g. "Best distance". */
    val scoreLabel: String,
    /** Unit shown after a score. */
    val scoreUnit: String,
    val higherIsBetter: Boolean = true,
) {
    HOVER_BIKE(Mentor.BULMA, "Hover-bike run", "Tilt to weave through West City traffic and grab capsules.", "Best distance", "m"),
    MEDITATION(Mentor.PICCOLO, "Meditation", "Guided breathing with Piccolo. Eyes closed is fine.", "Longest session", "min"),
    FISHING(Mentor.GOKU, "Fishing", "Cast, wait for the bite, reel it in. Goku is very excited.", "Biggest catch", "kg"),
    CHESS(Mentor.ROSHI, "Chess", "A game against Roshi, from napping to serious.", "Wins", ""),
    RHYTHM(Mentor.VEGETA, "Gravity-room sparring", "Hit the beats as the gravity climbs.", "Best score", ""),
    ;

    companion object {
        fun of(mentor: Mentor): MiniGame = entries.first { it.mentor == mentor }
    }
}

/** Mini game rules. Pure functions, no Android. */
object MiniGameRules {

    /** Bond XP a finished play gives, when [alreadyToday] has already been earned from games with that mentor today. */
    fun bondXp(alreadyToday: Int): Int =
        GameConfig.MINIGAME_BOND_XP_PER_PLAY.coerceAtMost(GameConfig.MINIGAME_DAILY_BOND_XP_CAP - alreadyToday).coerceAtLeast(0)
}
