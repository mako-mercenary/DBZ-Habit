package com.saiyanhabits.domain.games

import kotlin.math.abs
import kotlin.random.Random

/** Things on the road. */
enum class RoadThing(val width: Float, val length: Float) {
    CAR(0.17f, 6f),
    TRUCK(0.2f, 10f),
    ROBOT(0.11f, 3f),
    CAPSULE(0.08f, 2f),
    BOOST(0.09f, 2f),
    ;

    val isObstacle: Boolean get() = this == CAR || this == TRUCK || this == ROBOT
}

/** Something on the road, [ahead] metres in front of the bike, across the road at [x] (0 = left kerb, 1 = right). */
data class RoadItem(val id: Int, val thing: RoadThing, val x: Float, val ahead: Float)

/** One moment of a hover-bike run. */
data class HoverBikeState(
    val bikeX: Float = 0.5f,
    val distance: Float = 0f,
    val speed: Float = HoverBike.START_SPEED,
    val items: List<RoadItem> = emptyList(),
    val capsules: Int = 0,
    /** Seconds of boost left; while boosting the bike is faster and can't crash. */
    val boost: Float = 0f,
    val crashed: Boolean = false,
    /** Metres to go until the next thing appears. */
    val nextSpawnIn: Float = 30f,
    val nextId: Int = 0,
) {
    val boosting: Boolean get() = boost > 0f
}

/**
 * The hover-bike run through West City, as plain rules: the bike steers
 * across the road while traffic, robots and capsules come towards it, faster
 * the longer the run goes. Pure Kotlin, so it can be tested.
 */
object HoverBike {
    const val START_SPEED = 18f
    const val MAX_SPEED = 55f
    /** Speed gained per second of riding. */
    const val ACCELERATION = 0.45f
    const val BOOST_SECONDS = 3f
    const val BOOST_SPEED_BONUS = 20f
    /** How far across the road the bike moves per second at full steer. */
    const val STEER_RATE = 1.4f
    const val BIKE_WIDTH = 0.09f
    const val BIKE_LENGTH = 3f
    /** How far ahead things appear, in metres. */
    const val VIEW_AHEAD = 70f

    /** Moves the run on by [dt] seconds with [steer] from -1 (left) to 1 (right). */
    fun step(state: HoverBikeState, dt: Float, steer: Float, random: Random): HoverBikeState {
        if (state.crashed || dt <= 0f) return state
        val boost = (state.boost - dt).coerceAtLeast(0f)
        val baseSpeed = (state.speed + ACCELERATION * dt).coerceAtMost(MAX_SPEED)
        val moving = baseSpeed + if (state.boost > 0f) BOOST_SPEED_BONUS else 0f
        val moved = moving * dt
        val half = BIKE_WIDTH / 2
        val bikeX = (state.bikeX + steer.coerceIn(-1f, 1f) * STEER_RATE * dt).coerceIn(half, 1f - half)

        var capsules = state.capsules
        var boostLeft = boost
        var crashed = false
        val kept = mutableListOf<RoadItem>()
        for (item in state.items) {
            val moved1 = item.copy(ahead = item.ahead - moved)
            if (moved1.ahead < -moved1.thing.length) continue // passed
            if (hits(bikeX, moved1)) {
                when (moved1.thing) {
                    RoadThing.CAPSULE -> { capsules++; continue }
                    RoadThing.BOOST -> { boostLeft = BOOST_SECONDS; continue }
                    else -> if (state.boost <= 0f) crashed = true else continue // boosting smashes through
                }
            }
            kept += moved1
        }

        var next = state.nextSpawnIn - moved
        var id = state.nextId
        val distance = state.distance + moved
        while (next <= 0f) {
            kept += spawn(id++, distance, random)
            next += gapAt(distance, random)
        }
        return state.copy(
            bikeX = bikeX,
            distance = distance,
            speed = baseSpeed,
            items = kept,
            capsules = capsules,
            boost = boostLeft,
            crashed = crashed,
            nextSpawnIn = next,
            nextId = id,
        )
    }

    /** True when [item] overlaps the bike at [bikeX]. */
    fun hits(bikeX: Float, item: RoadItem): Boolean {
        val across = abs(item.x - bikeX) < (item.thing.width + BIKE_WIDTH) / 2 * 0.85f
        val along = item.ahead < BIKE_LENGTH / 2 && item.ahead > -item.thing.length
        return across && along
    }

    /** Metres to the next thing: shorter the further the run has gone. */
    fun gapAt(distance: Float, random: Random): Float {
        val base = (26f - distance / 150f).coerceAtLeast(9f)
        return base + random.nextFloat() * base * 0.6f
    }

    private fun spawn(id: Int, distance: Float, random: Random): RoadItem {
        val roll = random.nextFloat()
        val thing = when {
            roll < 0.22f -> RoadThing.CAPSULE
            roll < 0.26f -> RoadThing.BOOST
            roll < 0.40f && distance > 300f -> RoadThing.ROBOT
            roll < 0.52f && distance > 600f -> RoadThing.TRUCK
            else -> RoadThing.CAR
        }
        val half = thing.width / 2
        return RoadItem(id, thing, half + random.nextFloat() * (1f - 2 * half), VIEW_AHEAD)
    }
}
