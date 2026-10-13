package com.openauto.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainSkinTest {

    // --- Home ---

    @Test
    fun `the wide Home fits the grid with nothing overlapping`() {
        assertTrue(fitsGrid(TerrainLayout.wide!!))
    }

    @Test
    fun `every widget of the wide Home keeps its readable size`() {
        for (item in TerrainLayout.wide!!) {
            assertTrue("$item too narrow", item.w >= item.minW())
            assertTrue("$item too low", item.h >= item.minH())
        }
    }

    @Test
    fun `the wide Home holds the widgets of the Home list, the car first`() {
        val kinds = TerrainLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        assertEquals(TerrainLayout.home.kinds.toSet(), kinds.toSet())
        assertEquals(BuiltinKind.RANGE, TerrainLayout.home.kinds.first())
    }

    // --- The forecast bars ---

    private val hour = 3_600_000L

    @Test
    fun `dry hours stay dry`() {
        assertArrayEquals(BooleanArray(6), terrainWetHours(10 * hour + 5 * 60_000L, null, null))
    }

    @Test
    fun `a spell starting later lights its hours only`() {
        val now = 21 * hour + 10 * 60_000L
        val wet = terrainWetHours(now, 22 * hour, 24 * hour)
        assertArrayEquals(booleanArrayOf(false, true, true, false, false, false), wet)
    }

    @Test
    fun `rain already falling lights the hour in progress`() {
        val now = 21 * hour + 40 * 60_000L
        val wet = terrainWetHours(now, 21 * hour, 22 * hour)
        assertArrayEquals(booleanArrayOf(true, false, false, false, false, false), wet)
    }

    // --- The GPS line ---

    @Test
    fun `headings fall in their compass sector`() {
        assertEquals(0, terrainCompass(0f))
        assertEquals(0, terrainCompass(359f))
        assertEquals(0, terrainCompass(-10f))
        assertEquals(1, terrainCompass(45f))
        assertEquals(2, terrainCompass(90f))
        assertEquals(4, terrainCompass(180f))
        assertEquals(7, terrainCompass(315f))
        assertEquals(7, terrainCompass(337f))
    }

    // --- The landscape ---

    @Test
    fun `the pad sits under the car of the wide Home, and high on an upright screen`() {
        val (wx, wy) = terrainPadAt(1280, 720)
        assertTrue(wx in 0.3f..0.5f && wy in 0.45f..0.65f)
        val (ux, uy) = terrainPadAt(720, 1280)
        assertEquals(0.5f, ux)
        assertTrue(uy < 0.5f)
    }

    @Test
    fun `the pad lands where the camera says`() {
        val cam = TerrainCam(500f, 400f, 1343f)
        val out = FloatArray(3)
        assertTrue(cam.project(0f, 0f, cam.z0, out))
        assertEquals(500f, out[0], 0.01f)
        assertEquals(400f, out[1], 0.01f)
    }
}
