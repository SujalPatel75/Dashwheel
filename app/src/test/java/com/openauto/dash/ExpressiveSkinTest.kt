package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveSkinTest {

    // --- The Home it was drawn around ---

    @Test
    fun `the wide Home fits the grid without overlaps`() {
        val wide = ExpressiveLayout.wide
        assertNotNull(wide)
        assertTrue(fitsGrid(wide!!))
    }

    @Test
    fun `every widget of the wide Home stays readable`() {
        for (item in ExpressiveLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>()) {
            val (minW, minH) = item.kind.minSize()
            assertTrue("${item.kind} too narrow", item.w >= maxOf(minW, TemplateScreen.MIN_COLS))
            assertTrue("${item.kind} too low", item.h >= maxOf(minH, 2))
        }
    }

    @Test
    fun `the wide Home holds the same widgets as the placed one`() {
        val wideKinds = ExpressiveLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }.toSet()
        assertEquals(ExpressiveLayout.home.kinds.toSet(), wideKinds)
    }

    // --- Colours from the album art ---

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `a red cover gives a red hue`() {
        val pixels = IntArray(100) { if (it < 70) argb(220, 30, 40) else argb(20, 20, 20) }
        val hue = dominantHue(pixels)
        assertNotNull(hue)
        assertTrue(hue!![0] < 15f || hue[0] > 345f)
    }

    @Test
    fun `the most present colour wins`() {
        val pixels = IntArray(100) { if (it < 30) argb(230, 40, 40) else argb(30, 90, 230) }
        val hue = dominantHue(pixels)!!
        assertTrue("blue expected, got ${hue[0]}", hue[0] in 200f..240f)
    }

    @Test
    fun `a grey or black and white cover keeps the lime`() {
        assertNull(dominantHue(IntArray(100) { argb(128, 128, 128) }))
        assertNull(dominantHue(IntArray(100) { if (it % 2 == 0) argb(0, 0, 0) else argb(255, 255, 255) }))
        assertNull(dominantHue(IntArray(0)))
    }

    @Test
    fun `the card's words read on the card's colour, whatever the hue`() {
        for (light in listOf(true, false)) {
            var h = 0f
            while (h < 360f) {
                val tone = artTone(h, 0.9f, light)
                assertTrue("hue $h light $light", contrastRatio(tone.ink, tone.card) >= 4.5f)
                h += 15f
            }
        }
    }
}
