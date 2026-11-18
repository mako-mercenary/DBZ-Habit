package com.saiyanhabits

/**
 * Every tuning number in the game lives here. Change a value, rebuild, done.
 *
 * Points already earned are stored with each check-off, so changing a number
 * only affects check-offs made after the change.
 */
object GameConfig {

    // ---- Power level ----

    /** Power level of a brand new fighter. */
    const val STARTING_POWER_LEVEL = 5

    /** Points for one check-off = this x difficulty x gravity multiplier. */
    const val BASE_POINTS_PER_COMPLETION = 10

    // ---- Habit difficulty ----

    const val MIN_DIFFICULTY = 1
    const val MAX_DIFFICULTY = 3
    const val DEFAULT_DIFFICULTY = 1

    // ---- Flexible habits ----

    /** Most times a week a weekly-target habit can ask for (every day is a habit on all 7 days). */
    const val MAX_WEEKLY_TARGET = 6
    const val DEFAULT_WEEKLY_TARGET = 3

    /** Most a counted habit can ask for in a day, e.g. 8 glasses of water. */
    const val MAX_DAILY_TARGET = 50
    const val DEFAULT_DAILY_TARGET = 8

    // ---- Gravity training (point multipliers) ----

    const val GRAVITY_NORMAL_MULTIPLIER = 1
    const val GRAVITY_10X_MULTIPLIER = 2
    const val GRAVITY_100X_MULTIPLIER = 3

    // ---- Streaks and transformations ----

    /**
     * Overall streak (days in a row with every scheduled habit done) needed to
     * unlock form 1, form 2, form 3. Once unlocked, a form is kept for good.
     */
    val FORM_STREAK_THRESHOLDS = listOf(7, 30, 100)

    // ---- Healing beans ----

    /** One bean is earned each time the overall streak reaches a multiple of this. */
    const val BEAN_STREAK_INTERVAL = 7

    /** Most beans that can be held at once. */
    const val MAX_HEALING_BEANS = 3

    /** Default time of the evening nudge, in minutes after midnight (21:00). */
    const val EVENING_NUDGE_DEFAULT_MINUTES = 21 * 60

    /** Hour of the morning (0-23) when the app checks for a missed day and offers a bean. */
    const val MORNING_CHECK_HOUR = 8

    // ---- Sagas (boss fights) ----

    /** A milestone's weight is the damage it deals. Weights run from 1 to this. */
    const val MAX_MILESTONE_WEIGHT = 5

    /** Points for finishing a milestone = this x its weight. */
    const val MILESTONE_POINTS_PER_WEIGHT = 10

    /** Power bonus for defeating a boss = base + per-HP x the boss's total HP. */
    const val SAGA_VICTORY_BONUS_BASE = 100
    const val SAGA_VICTORY_BONUS_PER_HP = 20

    // ---- Spirit bombs ----

    /**
     * Total points a spirit bomb pays out as it fills, by size: small, medium,
     * large. Each bit of progress pays its share.
     */
    val SPIRIT_BOMB_SIZE_POINTS = listOf(100, 250, 500)

    /** Extra bonus for throwing a full bomb, as a percentage of its size points. */
    const val SPIRIT_BOMB_THROW_BONUS_PERCENT = 50

    // ---- Time chamber (focus timer) ----

    /** Session lengths on offer, in minutes. */
    val FOCUS_SESSION_MINUTES = listOf(25, 50)

    /** Progress logged by a finished session is worth this many times its normal points. */
    const val FOCUS_POINT_MULTIPLIER = 1.5

    // ---- Mentor bonds ----

    /** Bond XP a mentor gains each time one of their habits is checked off. */
    const val BOND_XP_PER_CHECK_OFF = 10

    /**
     * Total bond XP needed to reach each bond level, starting with level 1.
     * The number of entries is the highest level.
     */
    val BOND_LEVEL_XP = listOf(0, 30, 80, 150, 250, 400, 600, 850, 1150, 1500)

    /** Bond level from which a mentor joins boss fights as an assist. */
    const val ASSIST_MIN_BOND_LEVEL = 5

    /** Bonus damage each assisting mentor adds to a milestone hit. */
    const val ASSIST_DAMAGE_PER_MENTOR = 1

    /** Overall streak lengths the mentors comment on. */
    val STREAK_REACTION_MILESTONES = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365)

    // ---- Techniques (perks unlocked at bond levels 3, 6 and 9) ----

    val TECHNIQUE_BOND_LEVELS = listOf(3, 6, 9)

    /** Roshi, Goku, Piccolo and Bulma level 3: extra points on the habits that mentor trains, in percent. */
    const val TECHNIQUE_HABIT_BOOST_PERCENT = 20

    /** Vegeta level 3: extra points on the hardest habit, in percent. */
    const val TECHNIQUE_HARDEST_BOOST_PERCENT = 30

    /** Roshi level 6: extra healing bean slots. */
    const val TECHNIQUE_EXTRA_BEAN_SLOTS = 1

    /** Roshi level 9: days before a habit can be excused again. */
    const val TECHNIQUE_DAY_OFF_COOLDOWN_DAYS = 14

    /** Goku level 6: what check-off points are multiplied by on the day it is used, and days before it can be used again. */
    const val TECHNIQUE_KAIOKEN_MULTIPLIER = 2
    const val TECHNIQUE_KAIOKEN_COOLDOWN_DAYS = 7

    /** Goku level 9: what the spirit bomb throw bonus is multiplied by. */
    const val TECHNIQUE_BOMB_THROW_MULTIPLIER = 2

    /** Piccolo level 6: replaces FOCUS_POINT_MULTIPLIER. */
    const val TECHNIQUE_FOCUS_POINT_MULTIPLIER = 2.0

    /** Piccolo level 9: replaces BEAN_STREAK_INTERVAL. */
    const val TECHNIQUE_BEAN_STREAK_INTERVAL = 5

    /** Bulma level 6: extra points on saga milestones, in percent. */
    const val TECHNIQUE_MILESTONE_BOOST_PERCENT = 25

    /** Bulma level 9: extra victory bonus, in percent. */
    const val TECHNIQUE_VICTORY_BOOST_PERCENT = 50

    /** Vegeta level 6: replaces GRAVITY_100X_MULTIPLIER. */
    const val TECHNIQUE_GRAVITY_100X_MULTIPLIER = 4

    /** Vegeta level 9: what every assist's bonus damage is multiplied by. */
    const val TECHNIQUE_ASSIST_DAMAGE_MULTIPLIER = 2

    // ---- Story ----

    /**
     * Power level at which each story chapter unlocks: chapter 1, chapter 2,
     * and so on. One more chapter follows the last entry: the finale, which
     * unlocks by form instead.
     */
    val STORY_CHAPTER_POWER_LEVELS = listOf(5, 100, 300, 700, 1500, 3000, 6000)

    /** Form that unlocks the final chapter. */
    const val STORY_FINAL_CHAPTER_FORM = 3

    // ---- Wish orbs ----

    /** Orbs needed to summon the dragon. One is earned per perfect week, and no more are held than this. */
    const val WISH_ORBS_NEEDED = 7

    // ---- Weekly recap ----

    /** Perfect days that make a week a "strong week" in the Monday recap. */
    const val RECAP_STRONG_WEEK_PERFECT_DAYS = 5

    // ---- Achievements ----

    /** Titles, earned by the number of achievements unlocked, lowest first. */
    val TITLES = listOf(
        0 to "Trainee",
        3 to "Fighter",
        7 to "Warrior",
        12 to "Elite warrior",
        18 to "Champion",
        25 to "Legendary warrior",
    )

    /** The title for unlocking every achievement in the game. */
    const val TOP_TITLE = "Mightiest in the universe"

    // ---- Mini games ----

    /** Bond XP a finished mini game gives its mentor. */
    const val MINIGAME_BOND_XP_PER_PLAY = 10

    /** Most bond XP mini games can give one mentor in a day, so they never replace check-offs. */
    const val MINIGAME_DAILY_BOND_XP_CAP = 20

    // ---- Animation timing (milliseconds) ----

    /** The dragon rising when it is summoned. */
    const val DRAGON_SUMMON_MILLIS = 2000

    /** Boss flinch and damage number when a milestone lands. */
    const val BOSS_HIT_MILLIS = 900

    /** Full-screen victory when a boss reaches 0 HP. */
    const val VICTORY_MILLIS = 1900

    /** Spirit bomb leaving the screen when it is thrown. */
    const val BOMB_THROW_MILLIS = 1300

    /** Length of the full-screen power-up when a new form unlocks. */
    const val TRANSFORMATION_MILLIS = 1900

    /** How long the power level takes to count up after a check-off. */
    const val POWER_COUNT_UP_MILLIS = 900

    /** How long the floating "+points" label stays on screen. */
    const val POWER_GAIN_LABEL_MILLIS = 1200

    /** Hover-bike run: the rider sprite's width as a share of the screen width. */
    const val BIKE_SPRITE_WIDTH = 0.5f

    /** Hover-bike run: how far the rider sprite tilts at full steer, in degrees. */
    const val BIKE_SPRITE_LEAN_DEGREES = 9f

    /** Hover-bike run: the skyline's width as a share of the screen width; the extra lets it slide. */
    const val BIKE_SKYLINE_WIDTH = 1.3f

    /** Hover-bike run: how far below the horizon the skyline's foot sits, as a share of the screen height. */
    const val BIKE_SKYLINE_OVERLAP = 0.015f

    /** Hover-bike run: how far the skyline slides per metre the bike moves sideways, as a share of the screen width. */
    const val BIKE_SKYLINE_PARALLAX = 0.012f

    /**
     * Hover-bike run: how wide each roadside building picture stands, in metres, by slot
     * (dome, shop, apartments, the fourth); its height follows the picture's proportions.
     */
    val BIKE_CUTOUT_BUILDING_WIDTHS = floatArrayOf(18f, 14f, 17f, 16f)

    /** Hover-bike run: how far building pictures are turned from facing down the road towards it, in degrees. */
    const val BIKE_CUTOUT_BUILDING_ANGLE = 35f
}
