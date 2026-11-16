package com.saiyanhabits.domain

/** The teachers. Names are stored in the database: do not rename existing values. */
enum class Mentor(val displayName: String) {
    ROSHI("Roshi"),
    GOKU("Goku"),
    PICCOLO("Piccolo"),
    BULMA("Bulma"),
    VEGETA("Vegeta"),
}

/**
 * What kind of habit it is. Each category belongs to one mentor.
 * Names are stored in the database: do not rename existing values.
 */
enum class HabitCategory(val label: String, val mentor: Mentor) {
    DAILY_BASICS("Daily basics", Mentor.ROSHI),
    WORKOUT("Workouts", Mentor.GOKU),
    FOCUS("Focus & study", Mentor.PICCOLO),
    PROJECTS("Projects & creative", Mentor.BULMA),
}

/** The mentor who trains a habit: Vegeta takes the hardest one, whatever its category. */
fun mentorFor(category: HabitCategory, isHardest: Boolean): Mentor =
    if (isHardest) Mentor.VEGETA else category.mentor
