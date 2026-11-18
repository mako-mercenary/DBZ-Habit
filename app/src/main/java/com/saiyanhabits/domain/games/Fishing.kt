package com.saiyanhabits.domain.games

import kotlin.random.Random

/** How rare a fish is. */
enum class Rarity(val label: String) { COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"), LEGENDARY("Legendary") }

/**
 * A kind of fish: its weight range in kg, how hard it fights (0 to 1), and
 * how often it bites. [farWeight] is extra weight in the odds for a long cast.
 */
enum class Fish(
    val title: String,
    val rarity: Rarity,
    val minKg: Float,
    val maxKg: Float,
    val fight: Float,
    val weight: Float,
    val farWeight: Float,
) {
    MINNOW("Minnow", Rarity.COMMON, 0.05f, 0.2f, 0.1f, 30f, 0f),
    TROUT("River trout", Rarity.COMMON, 0.4f, 2.5f, 0.3f, 26f, 4f),
    BASS("Striped bass", Rarity.UNCOMMON, 1f, 5f, 0.45f, 14f, 6f),
    CATFISH("Whiskered catfish", Rarity.UNCOMMON, 2f, 12f, 0.55f, 10f, 8f),
    CARP("Golden carp", Rarity.RARE, 3f, 15f, 0.6f, 4f, 6f),
    PIKE("Paozu pike", Rarity.RARE, 5f, 25f, 0.75f, 3f, 8f),
    GIANT("Giant river beast", Rarity.LEGENDARY, 200f, 900f, 0.95f, 0.6f, 2.4f),
}

enum class FishingPhase { IDLE, CHARGING, WAITING, BITE, REELING, CAUGHT, ESCAPED, SPOOKED, SNAPPED }

/** One moment of a fishing trip. */
data class FishingState(
    val phase: FishingPhase = FishingPhase.IDLE,
    /** Cast strength while charging and after: 0 to 1. */
    val castPower: Float = 0f,
    /** Seconds until a bite while waiting, or left to strike once bitten. */
    val timer: Float = 0f,
    val fish: Fish? = null,
    val kg: Float = 0f,
    /** How far the fish still is, 1 at the bobber, 0 in the net. */
    val distance: Float = 1f,
    /** Line tension, 0 slack to 1 snapping. */
    val tension: Float = 0.3f,
    /** Seconds the line has been slack. */
    val slack: Float = 0f,
    /** Seconds left in the fish's current run; it pulls hard while above 0. */
    val run: Float = 0f,
)

/**
 * Fishing as plain rules: hold to charge a cast and let go, wait for the bite,
 * tap to strike in time, then hold to reel while keeping the line between
 * slack and snapping. Pure Kotlin, so it can be tested.
 */
object Fishing {
    const val CHARGE_RATE = 0.8f
    const val MIN_WAIT = 2f
    const val MAX_WAIT = 7f
    /** Seconds to strike once the bobber dips. */
    const val STRIKE_WINDOW = 1.1f
    const val SLACK_LIMIT = 2.5f

    fun startCharging(state: FishingState): FishingState =
        if (state.phase in listOf(FishingPhase.IDLE, FishingPhase.CAUGHT, FishingPhase.ESCAPED, FishingPhase.SPOOKED, FishingPhase.SNAPPED)) {
            FishingState(phase = FishingPhase.CHARGING)
        } else state

    /** Lets go of a charging cast: the line goes out and the wait begins. */
    fun cast(state: FishingState, random: Random): FishingState {
        if (state.phase != FishingPhase.CHARGING) return state
        val power = state.castPower.coerceIn(0.1f, 1f)
        return state.copy(phase = FishingPhase.WAITING, castPower = power, timer = MIN_WAIT + random.nextFloat() * (MAX_WAIT - MIN_WAIT))
    }

    /** A tap: strikes a bite in time, or spooks the fish if nothing has bitten yet. */
    fun strike(state: FishingState, random: Random): FishingState = when (state.phase) {
        FishingPhase.BITE -> {
            val fish = pick(state.castPower, random)
            state.copy(
                phase = FishingPhase.REELING,
                fish = fish,
                kg = fish.minKg + random.nextFloat() * (fish.maxKg - fish.minKg),
                distance = 1f,
                tension = 0.35f,
                slack = 0f,
                run = 0f,
            )
        }
        FishingPhase.WAITING -> state.copy(phase = FishingPhase.SPOOKED)
        else -> state
    }

    /** Moves time on by [dt] seconds; [reeling] is whether the reel is held. */
    fun step(state: FishingState, dt: Float, reeling: Boolean, random: Random): FishingState = when (state.phase) {
        FishingPhase.CHARGING -> {
            // The power bar sweeps up and down so timing the release matters.
            val cycle = (state.timer + dt * CHARGE_RATE) % 2f
            state.copy(timer = cycle, castPower = if (cycle <= 1f) cycle else 2f - cycle)
        }
        FishingPhase.WAITING -> {
            val left = state.timer - dt
            if (left <= 0f) state.copy(phase = FishingPhase.BITE, timer = STRIKE_WINDOW) else state.copy(timer = left)
        }
        FishingPhase.BITE -> {
            val left = state.timer - dt
            if (left <= 0f) state.copy(phase = FishingPhase.ESCAPED) else state.copy(timer = left)
        }
        FishingPhase.REELING -> reel(state, dt, reeling, random)
        else -> state
    }

    private fun reel(state: FishingState, dt: Float, reeling: Boolean, random: Random): FishingState {
        val fight = state.fish?.fight ?: 0.3f
        var run = (state.run - dt).coerceAtLeast(0f)
        // Now and then the fish makes a run; stronger fish run more often.
        if (run == 0f && random.nextFloat() < dt * (0.4f + fight)) run = 0.6f + random.nextFloat() * fight * 1.4f
        val running = run > 0f
        var tension = state.tension
        var distance = state.distance
        if (reeling) {
            tension += dt * (0.35f + if (running) 0.55f + fight * 0.6f else 0f)
            distance -= dt * if (running) 0.03f else 0.16f * (1.15f - fight * 0.6f)
        } else {
            tension -= dt * 0.45f
            if (running) distance += dt * 0.05f * (0.5f + fight)
        }
        tension = tension.coerceIn(0f, 1f)
        distance = distance.coerceIn(0f, 1f)
        val slack = if (tension < 0.08f) state.slack + dt else 0f
        val phase = when {
            tension >= 1f -> FishingPhase.SNAPPED
            slack >= SLACK_LIMIT -> FishingPhase.ESCAPED
            distance <= 0f -> FishingPhase.CAUGHT
            else -> FishingPhase.REELING
        }
        return state.copy(phase = phase, tension = tension, distance = distance, slack = slack, run = run)
    }

    /** Which fish takes the bait: a long cast favours the bigger ones. */
    fun pick(castPower: Float, random: Random): Fish {
        val weights = Fish.entries.map { it.weight + it.farWeight * castPower }
        var roll = random.nextFloat() * weights.sum()
        Fish.entries.forEachIndexed { i, fish ->
            roll -= weights[i]
            if (roll <= 0f) return fish
        }
        return Fish.entries.last()
    }
}
