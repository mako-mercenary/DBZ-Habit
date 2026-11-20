package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinesTest {

    @Test
    fun `items are trimmed and blanks dropped`() {
        assertEquals(listOf("Stretch", "Shower"), RoutineRules.cleanItems(listOf("  Stretch ", "", "   ", "Shower")))
        assertEquals(emptyList<String>(), RoutineRules.cleanItems(listOf(" ")))
    }

    @Test
    fun `a blank name falls back`() {
        assertEquals("Morning", RoutineRules.cleanName("  Morning "))
        assertEquals("Routine", RoutineRules.cleanName("   "))
    }

    @Test
    fun `only items checked off on their day earn power`() {
        assertEquals(GameConfig.ROUTINE_ITEM_POWER, RoutineRules.powerFor(100, 100))
        assertEquals(0, RoutineRules.powerFor(101, 100))
        assertEquals(0, RoutineRules.powerFor(99, 100))
        assertEquals(5, GameConfig.ROUTINE_ITEM_POWER)
    }

    @Test
    fun `routines can be planned from today up to the limit`() {
        assertTrue(RoutineRules.canPlan(100, 100))
        assertTrue(RoutineRules.canPlan(100L + GameConfig.ROUTINE_PLAN_DAYS_AHEAD, 100))
        assertFalse(RoutineRules.canPlan(99, 100))
        assertFalse(RoutineRules.canPlan(101L + GameConfig.ROUTINE_PLAN_DAYS_AHEAD, 100))
    }

    @Test
    fun `progress counts done items`() {
        assertEquals(2 to 3, RoutineRules.progress(listOf(true, false, true)))
        assertEquals(0 to 0, RoutineRules.progress(emptyList()))
    }
}
