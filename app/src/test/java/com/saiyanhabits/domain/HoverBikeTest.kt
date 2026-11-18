package com.saiyanhabits.domain

import com.saiyanhabits.domain.games.HoverBike
import com.saiyanhabits.domain.games.HoverBikeState
import com.saiyanhabits.domain.games.RoadItem
import com.saiyanhabits.domain.games.RoadThing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HoverBikeTest {

    private val calm = Random(1)

    @Test
    fun `the bike speeds up over time and never passes the top speed`() {
        var state = HoverBikeState(nextSpawnIn = 1e9f)
        repeat(10) { state = HoverBike.step(state, 1f, 0f, calm) }
        assertTrue(state.speed > HoverBike.START_SPEED)
        repeat(500) { state = HoverBike.step(state, 1f, 0f, calm) }
        assertEquals(HoverBike.MAX_SPEED, state.speed)
        assertTrue(state.distance > 0f)
    }

    @Test
    fun `steering moves the bike but not off the road`() {
        var state = HoverBikeState(nextSpawnIn = 1e9f)
        state = HoverBike.step(state, 0.1f, 1f, calm)
        assertTrue(state.bikeX > 0.5f)
        repeat(100) { state = HoverBike.step(state, 0.1f, -1f, calm) }
        assertEquals(HoverBike.BIKE_WIDTH / 2, state.bikeX, 0.0001f)
    }

    @Test
    fun `a hit costs a heart and a moment of safety, the third ends the run`() {
        val car = HoverBikeState(items = listOf(RoadItem(0, RoadThing.CAR, 0.5f, 1f)), nextSpawnIn = 1e9f)
        val hit = HoverBike.step(car, 0.05f, 0f, calm)
        assertEquals(HoverBike.HEARTS - 1, hit.hearts)
        assertFalse(hit.crashed)
        assertTrue(hit.invulnerable > 0f)
        assertTrue(hit.items.isEmpty())
        // While flashing, another hit does nothing.
        val again = HoverBike.step(hit.copy(items = listOf(RoadItem(1, RoadThing.BARRIER, 0.5f, 1f))), 0.05f, 0f, calm)
        assertEquals(hit.hearts, again.hearts)
        // On the last heart, a hit ends it.
        val last = HoverBikeState(hearts = 1, items = listOf(RoadItem(0, RoadThing.TRUCK, 0.5f, 1f)), nextSpawnIn = 1e9f)
        assertTrue(HoverBike.step(last, 0.05f, 0f, calm).crashed)
    }

    @Test
    fun `dragon balls are collected and count towards the score`() {
        val ball = HoverBikeState(items = listOf(RoadItem(0, RoadThing.DRAGON_BALL, 0.5f, 1f)), nextSpawnIn = 1e9f)
        val after = HoverBike.step(ball, 0.05f, 0f, calm)
        assertEquals(1, after.balls)
        assertTrue(after.items.isEmpty())
        assertEquals(after.distance.toLong() * HoverBike.POINTS_PER_METRE + HoverBike.POINTS_PER_BALL, after.score)
    }

    @Test
    fun `dragon balls sometimes come in trails down one lane`() {
        var state = HoverBikeState()
        val random = Random(11)
        var trail = false
        repeat(4000) {
            state = HoverBike.step(state.copy(hearts = 99, invulnerable = 99f), 1 / 30f, 0f, random)
            val balls = state.items.filter { it.thing == RoadThing.DRAGON_BALL }
            if (balls.groupBy { it.x }.values.any { it.size >= 3 }) trail = true
        }
        assertTrue(trail)
    }

    @Test
    fun `a car in another lane is passed safely`() {
        var state = HoverBikeState(bikeX = 0.2f, items = listOf(RoadItem(0, RoadThing.CAR, 0.8f, 10f)), nextSpawnIn = 1e9f)
        repeat(40) { state = HoverBike.step(state, 0.05f, 0f, calm) }
        assertFalse(state.crashed)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `a boost makes the bike faster and smashes through traffic`() {
        val boost = HoverBikeState(items = listOf(RoadItem(0, RoadThing.BOOST, 0.5f, 1f)), nextSpawnIn = 1e9f)
        val boosted = HoverBike.step(boost, 0.05f, 0f, calm)
        assertTrue(boosted.boosting)
        val withCar = boosted.copy(items = listOf(RoadItem(1, RoadThing.CAR, 0.5f, 1f)))
        val after = HoverBike.step(withCar, 0.05f, 0f, calm)
        assertFalse(after.crashed)
        assertTrue(after.items.isEmpty())
    }

    @Test
    fun `traffic gets denser further along and a crashed run stays put`() {
        val near = (1..200).map { HoverBike.gapAt(0f, Random(it)) }.average()
        val far = (1..200).map { HoverBike.gapAt(3000f, Random(it)) }.average()
        assertTrue(far < near)
        val crashed = HoverBikeState(crashed = true, distance = 50f)
        assertEquals(crashed, HoverBike.step(crashed, 1f, 1f, calm))
    }

    @Test
    fun `a long simulated run spawns things and the distance adds up`() {
        var state = HoverBikeState()
        val random = Random(42)
        var steps = 0
        // Dodge by steering away from anything close ahead in our lane.
        while (!state.crashed && steps < 3000) {
            val threat = state.items.filter { it.thing.isObstacle && it.ahead in 0f..15f }.minByOrNull { it.ahead }
            val steer = when {
                threat == null -> 0f
                threat.x > state.bikeX -> -1f
                else -> 1f
            }
            state = HoverBike.step(state, 1 / 30f, steer, random)
            steps++
        }
        assertTrue(state.distance > 100f)
        assertTrue(state.nextId > 3)
    }
}
