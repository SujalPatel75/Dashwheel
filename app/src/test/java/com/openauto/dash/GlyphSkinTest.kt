package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GlyphSkinTest {

    // --- The Home it was drawn around ---

    @Test
    fun `the wide Home fits the grid with no overlap`() {
        assertFalse(ScreenShape.vertical)
        assertTrue(fitsGrid(GlyphLayout.wide!!))
    }

    @Test
    fun `every widget of the wide Home is at least its readable size`() {
        for (item in GlyphLayout.wide!!) {
            val w = item as DashboardItem.BuiltinWidget
            val (minW, minH) = w.kind.minSize()
            assertTrue("${w.kind} too narrow", w.w >= maxOf(minW, 3))
            assertTrue("${w.kind} too low", w.h >= maxOf(minH, 2))
        }
    }

    @Test
    fun `the wide Home holds the same widgets as the placed one`() {
        val wide = GlyphLayout.wide!!.map { (it as DashboardItem.BuiltinWidget).kind }.toSet()
        assertEquals(GlyphLayout.home.kinds.toSet(), wide)
    }

    // --- The dot font ---

    @Test
    fun `the font writes digits, capitals and the reading signs`() {
        assertEquals("21:07", GlyphFont.prepare("21:07"))
        assertEquals("300M", GlyphFont.prepare("300m"))
        assertEquals("14°", GlyphFont.prepare("14°"))
        assertEquals("ETE", GlyphFont.prepare("Été"))
    }

    @Test
    fun `a text the font cannot write is handed to the mono face`() {
        assertNull(GlyphFont.prepare("Привет"))
        assertNull(GlyphFont.prepare(""))
        assertFalse(GlyphFont.writes("300 м"))
    }

    @Test
    fun `columns count glyphs and the gaps between them`() {
        // Five-wide digits, a one-wide colon, one column between each.
        assertEquals(5 + 1 + 5 + 1 + 1 + 1 + 5 + 1 + 5, GlyphFont.columns("21:07"))
        assertEquals(5, GlyphFont.columns("8"))
    }

    @Test
    fun `every lit dot sits inside the text's box`() {
        val text = GlyphFont.prepare("Nightcall 2:14 / 4:18 (feat. X) 100 %")!!
        val cols = GlyphFont.columns(text)
        val cells = GlyphFont.cells(text)
        assertTrue(cells.isNotEmpty())
        for (i in cells.indices step 2) {
            assertTrue(cells[i] > 0f && cells[i] < cols)
            assertTrue(cells[i + 1] > 0f && cells[i + 1] < GlyphFont.ROWS)
        }
    }

    @Test
    fun `a long title is cut to its columns and ends in two dots`() {
        val cut = cutToColumns("NIGHTCALL FOREVER", 30)
        assertTrue(GlyphFont.columns(cut) <= 30)
        assertTrue(cut.endsWith(".."))
        assertEquals("NIGHTCALL", cutToColumns("NIGHTCALL", 100))
    }

    // --- The light strips ---

    @Test
    fun `a side strip chases only for a near turn on its side`() {
        assertEquals(TurnSide.LEFT, glyphSignalSide(true, 300, TurnSide.LEFT))
        assertEquals(TurnSide.RIGHT, glyphSignalSide(true, 50, TurnSide.RIGHT))
        assertNull(glyphSignalSide(true, 900, TurnSide.LEFT))
        assertNull(glyphSignalSide(true, 100, TurnSide.AHEAD))
        assertNull(glyphSignalSide(false, 100, TurnSide.LEFT))
        assertNull(glyphSignalSide(true, null, TurnSide.LEFT))
    }

    @Test
    fun `the chase lights half the strip and moves along it`() {
        val now = (0 until 9).map { glyphChaseLit(it, 0.3f) }
        val later = (0 until 9).map { glyphChaseLit(it, 0.6f) }
        assertTrue(now.any { it } && now.any { !it })
        assertTrue(now != later)
    }

    @Test
    fun `the fuel strip lights one segment for any fuel and all for a full tank`() {
        assertEquals(0, glyphFuelSegments(0))
        assertEquals(1, glyphFuelSegments(1))
        assertEquals(20, glyphFuelSegments(100))
        assertEquals(10, glyphFuelSegments(50))
    }

    // --- The car in dots ---

    private fun px(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `a dot for each cell the car covers, sorted along the car`() {
        val clear = 0
        val grey = px(255, 120, 120, 120)
        val dark = px(255, 20, 20, 20)
        val cloud = glyphCloudOf(intArrayOf(grey, clear, dark, grey, dark, clear), 3, 2)!!
        assertEquals(4, cloud.count)
        for (i in 1 until cloud.count) assertTrue(cloud.xy[i * 2] >= cloud.xy[(i - 1) * 2])
        assertEquals(1.5f, cloud.aspect, 0.001f)
    }

    @Test
    fun `lamp red is marked, a red car is not`() {
        val grey = px(255, 120, 120, 120)
        val red = px(255, 220, 30, 40)
        val lamp = glyphCloudOf(IntArray(40) { if (it == 0) red else grey }, 8, 5)!!
        assertEquals(1, lamp.red.count { it })
        val redCar = glyphCloudOf(IntArray(40) { red }, 8, 5)!!
        assertEquals(0, redCar.red.count { it })
    }

    @Test
    fun `nothing to sample gives no cloud`() {
        assertNull(glyphCloudOf(IntArray(4), 2, 2))
        assertNull(glyphCloudOf(IntArray(2), 2, 2))
    }

    @Test
    fun `dark parts get the big dots by day and the small ones by night`() {
        assertTrue(glyphDotLevel(0f, 1f, light = true) > glyphDotLevel(1f, 1f, light = true))
        assertTrue(glyphDotLevel(0f, 1f, light = false) < glyphDotLevel(1f, 1f, light = false))
    }

    // --- The rest ---

    @Test
    fun `the equaliser stays in its box and lies low when nothing plays`() {
        for (i in 0 until 18) for (k in 0..20) {
            val v = glyphEqLevel(i, k / 20f, true)
            assertTrue(v in 0f..1f)
        }
        assertTrue(glyphEqLevel(3, 0.4f, false) < 0.2f)
    }

    @Test
    fun `the clock gives the evening's sunset in Paris in June`() {
        val zone = TimeZone.getTimeZone("Europe/Paris")
        val noon = Calendar.getInstance(zone).apply { set(2026, Calendar.JUNE, 21, 12, 0, 0) }.timeInMillis
        val event = glyphSunEvent(noon, 48.85, 2.35, zone)
        assertNotNull(event)
        assertTrue(event!!.second)
        assertTrue("sunset at ${event.first}", event.first in (21 * 60 + 40)..(22 * 60 + 15))
        val night = Calendar.getInstance(zone).apply { set(2026, Calendar.JUNE, 21, 23, 0, 0) }.timeInMillis
        assertFalse(glyphSunEvent(night, 48.85, 2.35, zone)!!.second)
    }
}
