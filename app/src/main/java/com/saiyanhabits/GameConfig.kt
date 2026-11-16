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

    /** Hour of the morning (0-23) when the app checks for a missed day and offers a bean. */
    const val MORNING_CHECK_HOUR = 8

    // ---- Animation timing (milliseconds) ----

    /** Length of the full-screen power-up when a new form unlocks. */
    const val TRANSFORMATION_MILLIS = 1900

    /** How long the power level takes to count up after a check-off. */
    const val POWER_COUNT_UP_MILLIS = 900

    /** How long the floating "+points" label stays on screen. */
    const val POWER_GAIN_LABEL_MILLIS = 1200
}
