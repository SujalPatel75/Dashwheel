package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindshieldSkinTest {

    // --- The look's Home ---

    @Test
    fun `the wide Home fits the wide grid without overlaps`() {
        ScreenShape.vertical = false
        val wide = WindshieldLayout.wide!!
        assertTrue(fitsGrid(wide))
    }

    @Test
    fun `every widget of the wide Home stays readable`() {
        for (w in WindshieldLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>()) {
            val (minW, minH) = w.kind.minSize()
            assertTrue("${w.kind} too narrow", w.w >= maxOf(3, minW))
            assertTrue("${w.kind} too low", w.h >= maxOf(2, minH))
        }
    }

    @Test
    fun `the wide Home only holds widgets its placed Home has, so it is used with or without an adapter`() {
        val kinds = WindshieldLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        assertTrue(WindshieldLayout.home.kinds.containsAll(kinds))
        val screen = TemplateScreen(cellAspect = 1.17, obdPaired = false, canbox = false, tyres = false)
        assertTrue(TemplatePlacer.kindsFor(WindshieldLayout.home, screen).containsAll(kinds))
    }

    // --- The road's bend ---

    @Test
    fun `no route keeps the road straight`() {
        assertEquals(WsRoadShape(false, 0, WsTurnNear.NONE), roadShapeOf(NavState()))
    }

    @Test
    fun `the road bends toward the turn, more sharply as it nears`() {
        assertEquals(WsRoadShape(true, -1, WsTurnNear.NEAR), roadShapeOf(NavState(active = true, instruction = "Turn left onto Rue Alsace-Lorraine", distance = "150 m")))
        assertEquals(WsRoadShape(true, 1, WsTurnNear.MID), roadShapeOf(NavState(active = true, instruction = "Tournez à droite", distance = "450 m")))
        assertEquals(WsRoadShape(true, 1, WsTurnNear.FAR), roadShapeOf(NavState(active = true, instruction = "Turn right", distance = "1,2 km")))
    }

    @Test
    fun `straight on guides without a bend`() {
        assertEquals(WsRoadShape(true, 0, WsTurnNear.NONE), roadShapeOf(NavState(active = true, instruction = "Continue on A6", distance = "3 km")))
    }

    @Test
    fun `distances are read in metres whatever their unit`() {
        assertEquals(1200.0, turnMetres(NavState(distance = "1,2 km"))!!, 0.01)
        assertEquals(300.0, turnMetres(NavState(distance = "300 m"))!!, 0.01)
        assertEquals(804.672, turnMetres(NavState(distance = "0.5 mi"))!!, 0.01)
        assertNull(turnMetres(NavState(distance = "")))
    }
}
