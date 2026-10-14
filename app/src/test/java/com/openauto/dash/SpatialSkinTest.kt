package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialSkinTest {

    @Test
    fun `the wide Home fits the grid`() {
        val wide = SpatialLayout.wide
        assertTrue(wide != null && fitsGrid(wide))
    }

    @Test
    fun `every widget of the wide Home is at least 3 by 2 and listed in the Home`() {
        val widgets = SpatialLayout.wide.orEmpty().filterIsInstance<DashboardItem.BuiltinWidget>()
        assertTrue(widgets.isNotEmpty())
        for (w in widgets) {
            assertTrue("${w.kind} too narrow", w.w >= 3)
            assertTrue("${w.kind} too low", w.h >= 2)
            assertTrue("${w.kind} not in the Home list", w.kind in SpatialLayout.home.kinds)
        }
    }

    @Test
    fun `the turn's window turns from the left, the music's from the right, the speed stays flat`() {
        val byKind = SpatialLayout.wide.orEmpty().filterIsInstance<DashboardItem.BuiltinWidget>().associateBy { it.kind }
        val nav = byKind.getValue(BuiltinKind.NAVIGATION)
        val media = byKind.getValue(BuiltinKind.MEDIA)
        val speed = byKind.getValue(BuiltinKind.SPEED_HUD)
        assertEquals(-1, spatialTiltSide(nav.x, nav.w, 12))
        assertEquals(1, spatialTiltSide(media.x, media.w, 12))
        assertEquals(0, spatialTiltSide(speed.x, speed.w, 12))
    }

    @Test
    fun `a tile across the middle of an upright grid stays flat`() {
        assertEquals(0, spatialTiltSide(0, 7, 7))
        assertEquals(0, spatialTiltSide(2, 3, 7))
        assertEquals(-1, spatialTiltSide(0, 3, 7))
        assertEquals(1, spatialTiltSide(4, 3, 7))
    }

    @Test
    fun `the trip left is read from the arrival line in any unit`() {
        assertEquals(11.0, spatialTripKm(listOf("18 min", "11 km", "21:24"))!!, 1e-6)
        assertEquals(6.4, spatialTripKm(listOf("6,4 km"))!!, 1e-6)
        assertEquals(0.8, spatialTripKm(listOf("800 m"))!!, 1e-6)
        assertEquals(16.09344, spatialTripKm(listOf("10 mi"))!!, 1e-6)
        assertNull(spatialTripKm(listOf("18 min", "21:24")))
    }

    @Test
    fun `the range covers the trip only with room to spare`() {
        assertEquals(true, spatialFuelReaches(400, 11.0))
        assertEquals(false, spatialFuelReaches(20, 11.0))
        assertEquals(false, spatialFuelReaches(110, 100.0))
        assertEquals(true, spatialFuelReaches(130, 100.0))
        assertNull(spatialFuelReaches(null, 11.0))
        assertNull(spatialFuelReaches(300, null))
        assertFalse(spatialFuelReaches(0, 0.5) ?: true)
    }
}
