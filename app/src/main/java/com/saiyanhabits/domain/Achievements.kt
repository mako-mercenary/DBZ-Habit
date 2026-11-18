package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** Everything the achievements look at, all of it counted over all time. */
data class AchievementStats(
    val checkOffs: Int = 0,
    val perfectDays: Int = 0,
    val bestStreak: Int = 0,
    val perfectWeeks: Int = 0,
    val beanSaves: Int = 0,
    val powerLevel: Long = GameConfig.STARTING_POWER_LEVEL.toLong(),
    val highestForm: Int = 0,
    val bossesDefeated: List<Boss> = emptyList(),
    val milestonesHit: Int = 0,
    val bombsThrown: Int = 0,
    val chamberSessions: Int = 0,
    val bondLevels: Map<Mentor, Int> = emptyMap(),
    val chaptersRead: Int = 0,
    val wishesGranted: Int = 0,
    val notesWritten: Int = 0,
    /** The longest any single habit's own streak has reached right now. */
    val bestHabitStreak: Int = 0,
    /** Longest hover-bike run, in metres. */
    val hoverBikeBest: Long = 0,
    /** Minutes meditated in all. */
    val meditationMinutes: Long = 0,
    /** Kinds of fish caught, by name. */
    val fishCaught: Set<String> = emptySet(),
    /** Levels Roshi has been beaten on, by name. */
    val chessWinsAt: Set<String> = emptySet(),
    /** Rhythm levels a song has been finished on, by name. */
    val rhythmLevelsDone: Set<String> = emptySet(),
)

/** What an achievement is about, for grouping on the screen. */
enum class AchievementGroup(val title: String) {
    TRAINING("Training"),
    STREAKS("Streaks"),
    POWER("Power"),
    BATTLES("Battles"),
    BONDS("Bonds"),
    GAMES("Mini games"),
    LEGEND("Legend"),
}

/**
 * An achievement: a goal with a [target] that [progress] counts towards.
 * Stored by name in nothing but the "seen" list, so a rename only re-announces it.
 */
enum class Achievement(
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val target: Long,
    val progress: (AchievementStats) -> Long,
) {
    FIRST_STEP("First step", "Check off a habit", AchievementGroup.TRAINING, 1, { it.checkOffs.toLong() }),
    WARMED_UP("Warmed up", "Check off 50 habits", AchievementGroup.TRAINING, 50, { it.checkOffs.toLong() }),
    HARD_WORKER("Hard worker", "Check off 250 habits", AchievementGroup.TRAINING, 250, { it.checkOffs.toLong() }),
    THOUSAND_REPS("A thousand reps", "Check off 1,000 habits", AchievementGroup.TRAINING, 1000, { it.checkOffs.toLong() }),
    FIRST_PERFECT("Flawless", "Have a perfect day", AchievementGroup.TRAINING, 1, { it.perfectDays.toLong() }),
    FIFTY_PERFECT("Fifty clean days", "Have 50 perfect days", AchievementGroup.TRAINING, 50, { it.perfectDays.toLong() }),
    TWO_HUNDRED_PERFECT("Unshakeable", "Have 200 perfect days", AchievementGroup.TRAINING, 200, { it.perfectDays.toLong() }),
    FOCUSED("Into the chamber", "Finish a time chamber session", AchievementGroup.TRAINING, 1, { it.chamberSessions.toLong() }),
    CHAMBER_REGULAR("A year in a day", "Finish 25 time chamber sessions", AchievementGroup.TRAINING, 25, { it.chamberSessions.toLong() }),
    JOURNAL("Training log", "Write notes on 10 days", AchievementGroup.TRAINING, 10, { it.notesWritten.toLong() }),

    WEEK_STREAK("Seven days strong", "Reach a 7-day streak", AchievementGroup.STREAKS, 7, { it.bestStreak.toLong() }),
    MONTH_STREAK("A month unbroken", "Reach a 30-day streak", AchievementGroup.STREAKS, 30, { it.bestStreak.toLong() }),
    HUNDRED_STREAK("Hundred-day warrior", "Reach a 100-day streak", AchievementGroup.STREAKS, 100, { it.bestStreak.toLong() }),
    YEAR_STREAK("Year of training", "Reach a 365-day streak", AchievementGroup.STREAKS, 365, { it.bestStreak.toLong() }),
    HABIT_STREAK("Creature of habit", "Keep one habit going 30 times in a row", AchievementGroup.STREAKS, 30, { it.bestHabitStreak.toLong() }),
    PERFECT_WEEK("Perfect week", "Have a perfect Monday-to-Sunday week", AchievementGroup.STREAKS, 1, { it.perfectWeeks.toLong() }),
    SAVED_BY_A_BEAN("Senzu save", "Let a healing bean save your streak", AchievementGroup.STREAKS, 1, { it.beanSaves.toLong() }),

    POWER_1000("Over a thousand", "Reach power level 1,000", AchievementGroup.POWER, 1000, { it.powerLevel }),
    SCOUTER_BREAKER("Scouter breaker", "Reach power level 9,001", AchievementGroup.POWER, 9001, { it.powerLevel }),
    POWER_25000("Off the charts", "Reach power level 25,000", AchievementGroup.POWER, 25000, { it.powerLevel }),
    FIRST_FORM("Ascended", "Unlock your first form", AchievementGroup.POWER, 1, { it.highestForm.toLong() }),
    FINAL_FORM("Final form", "Unlock your third form", AchievementGroup.POWER, 3, { it.highestForm.toLong() }),

    FIRST_BOSS("First blood", "Defeat a boss", AchievementGroup.BATTLES, 1, { it.bossesDefeated.size.toLong() }),
    FIVE_BOSSES("Saga veteran", "Defeat 5 bosses", AchievementGroup.BATTLES, 5, { it.bossesDefeated.size.toLong() }),
    EVERY_BOSS("Every last villain", "Defeat each kind of boss once", AchievementGroup.BATTLES, Boss.entries.size.toLong(), { it.bossesDefeated.distinct().size.toLong() }),
    MILESTONES_25("Chipping away", "Land 25 saga milestones", AchievementGroup.BATTLES, 25, { it.milestonesHit.toLong() }),
    FIRST_BOMB("Lend me your energy", "Throw a spirit bomb", AchievementGroup.BATTLES, 1, { it.bombsThrown.toLong() }),
    FIVE_BOMBS("Bombardier", "Throw 5 spirit bombs", AchievementGroup.BATTLES, 5, { it.bombsThrown.toLong() }),

    FIRST_ASSIST("Backup has arrived", "Reach bond level 5 with a mentor", AchievementGroup.BONDS, 1, { s -> s.bondLevels.values.count { it >= 5 }.toLong() }),
    WHOLE_TEAM("The whole team", "Reach bond level 5 with every mentor", AchievementGroup.BONDS, Mentor.entries.size.toLong(), { s -> Mentor.entries.count { (s.bondLevels[it] ?: 1) >= 5 }.toLong() }),
    MASTERED("Student becomes master", "Reach bond level 10 with a mentor", AchievementGroup.BONDS, 1, { s -> s.bondLevels.values.count { it >= 10 }.toLong() }),
    ALL_MASTERED("Master of masters", "Reach bond level 10 with every mentor", AchievementGroup.BONDS, Mentor.entries.size.toLong(), { s -> Mentor.entries.count { (s.bondLevels[it] ?: 1) >= 10 }.toLong() }),

    RUSH_HOUR("Rush hour", "Ride 1,000 m on Bulma's hover bike", AchievementGroup.GAMES, 1000, { it.hoverBikeBest }),
    INNER_PEACE("Inner peace", "Meditate with Piccolo for 60 minutes in all", AchievementGroup.GAMES, 60, { it.meditationMinutes }),
    MASTER_ANGLER("Master angler", "Catch every kind of fish with Goku", AchievementGroup.GAMES, 7, { it.fishCaught.size.toLong() }),
    THE_BIG_ONE("The one that didn't get away", "Catch the giant river beast", AchievementGroup.GAMES, 1, { if ("GIANT" in it.fishCaught) 1 else 0 }),
    CHECKMATE("Checkmate, old man", "Beat Roshi at chess on Serious", AchievementGroup.GAMES, 1, { if ("SERIOUS" in it.chessWinsAt) 1 else 0 }),
    HUNDRED_GRAVITY("A hundred times gravity", "Finish a song on Hard in the gravity room", AchievementGroup.GAMES, 1, { if ("HARD" in it.rhythmLevelsDone) 1 else 0 }),

    STORYTELLER("The whole story", "Read every story chapter", AchievementGroup.LEGEND, StoryRules.CHAPTER_COUNT.toLong(), { it.chaptersRead.toLong() }),
    FIRST_WISH("Your wish is granted", "Claim a wish from the dragon", AchievementGroup.LEGEND, 1, { it.wishesGranted.toLong() }),
    FIVE_WISHES("Dragon's regular", "Claim 5 wishes", AchievementGroup.LEGEND, 5, { it.wishesGranted.toLong() }),
    ;

    fun isUnlocked(stats: AchievementStats): Boolean = progress(stats) >= target

    /** How far along, from 0 to 1. */
    fun fraction(stats: AchievementStats): Float = (progress(stats).toFloat() / target).coerceIn(0f, 1f)
}

/** Achievement and title rules. Pure functions, no Android. */
object AchievementRules {

    /** Achievements unlocked by [stats], plus any already seen unlocked before (so an undo never takes one back). */
    fun unlocked(stats: AchievementStats, seen: Set<String>): Set<Achievement> =
        Achievement.entries.filter { it.isUnlocked(stats) || it.name in seen }.toSet()

    /** Every title with the achievements it needs, lowest first; the last needs all of them. */
    val titles: List<Pair<Int, String>>
        get() = GameConfig.TITLES.filter { it.first < Achievement.entries.size } + (Achievement.entries.size to GameConfig.TOP_TITLE)

    /** The title earned with [count] achievements unlocked. */
    fun title(count: Int): String =
        titles.lastOrNull { (needed, _) -> count >= needed }?.second ?: titles.first().second

    /** The next title and how many achievements it needs, or null at the top. */
    fun nextTitle(count: Int): Pair<Int, String>? = titles.firstOrNull { (needed, _) -> count < needed }
}
