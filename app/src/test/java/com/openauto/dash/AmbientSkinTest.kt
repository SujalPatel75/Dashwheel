package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Ambient look's Home and the light a coming turn gives. */
class AmbientSkinTest {

    @Test
    fun wideHomeFitsTheGrid() {
        val wide = AmbientLayout.wide!!
        assertTrue(fitsGrid(wide))
        wide.forEach { assertTrue("$it too small", it.w >= 3 && it.h >= 2) }
    }

    @Test
    fun homeListsItsWidgetsMostImportantFirst() {
        assertEquals(
            listOf(BuiltinKind.SPEED_HUD, BuiltinKind.NAVIGATION, BuiltinKind.MEDIA, BuiltinKind.CLOCK),
            AmbientLayout.home.kinds
        )
        val wideKinds = AmbientLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }.toSet()
        assertEquals(AmbientLayout.home.kinds.toSet(), wideKinds)
    }

    @Test
    fun distancesReadInMetres() {
        assertEquals(300f, ambientTurnMeters("300 m")!!, 0.01f)
        assertEquals(1200f, ambientTurnMeters("1,2 km")!!, 0.01f)
        assertEquals(1500f, ambientTurnMeters("1.5 km")!!, 0.01f)
        assertEquals(152.4f, ambientTurnMeters("500 ft")!!, 0.1f)
        assertEquals(321.9f, ambientTurnMeters("0.2 mi")!!, 0.1f)
        assertNull(ambientTurnMeters(""))
        assertNull(ambientTurnMeters("soon"))
    }

    @Test
    fun theLightWarmsAsTheTurnNears() {
        assertEquals(0f, ambientWarmth(null), 0f)
        assertEquals(0f, ambientWarmth(601f), 0f)
        assertEquals(1f, ambientWarmth(0f), 0f)
        var last = 0f
        for (m in 600 downTo 0 step 25) {
            val w = ambientWarmth(m.toFloat())
            assertTrue("$m m: $w", w > 0f && w >= last)
            last = w
        }
    }

    @Test
    fun theGlowRisesFromTheTurnsSide() {
        val left = ambientTurn(NavState(active = true, instruction = "Turn left onto Rue Alsace-Lorraine", distance = "300 m"))
        assertEquals(-1, left.side)
        assertTrue(left.warmth > 0f)
        val right = ambientTurn(NavState(active = true, instruction = "Tourner à droite", distance = "120 m"))
        assertEquals(1, right.side)
        assertTrue(right.warmth > left.warmth)
        // Far away, no route, or no distance: calm.
        assertEquals(0f, ambientTurn(NavState(active = true, instruction = "Turn left", distance = "2 km")).warmth, 0f)
        assertEquals(0f, ambientTurn(NavState(active = false, instruction = "Turn left", distance = "100 m")).warmth, 0f)
        assertEquals(0f, ambientTurn(NavState(active = true, instruction = "Turn left")).warmth, 0f)
        // Straight on: a softer glow, from the foot.
        val ahead = ambientTurn(NavState(active = true, instruction = "Continue on A7", distance = "100 m"))
        assertEquals(0, ahead.side)
        assertTrue(ahead.warmth in 0.01f..0.99f)
    }
}
