package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaySkinTest {

    private val wide = ClayLayout.wide

    @Test
    fun `the wide Home fits the 12 by 7 grid without overlaps`() {
        assertNotNull(wide)
        assertTrue(fitsGrid(wide!!))
    }

    @Test
    fun `every widget of the wide Home gets at least three columns and two rows`() {
        wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().forEach {
            assertTrue("${it.kind} is ${it.w} wide", it.w >= 3)
            assertTrue("${it.kind} is ${it.h} tall", it.h >= 2)
            assertTrue(it.w >= it.minW() && it.h >= it.minH())
        }
    }

    @Test
    fun `the wide Home fills the whole grid`() {
        assertEquals(12 * 7, wide!!.sumOf { it.w * it.h })
    }

    @Test
    fun `the wide Home shows the widgets of the list, the next turn first`() {
        val kinds = wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }.toSet()
        assertEquals(ClayLayout.home.kinds.toSet(), kinds)
        assertEquals(BuiltinKind.NAVIGATION, ClayLayout.home.kinds.first())
    }

    @Test
    fun `every pastel has a day and a night version`() {
        ClayHue.entries.forEach { hue ->
            assertNotNull(clayTint(hue, light = true))
            assertNotNull(clayTint(hue, light = false))
        }
    }

    @Test
    fun `the card light turns at the top-right and bottom-left corners, however wide the card`() {
        for ((w, h) in listOf(640f to 258f, 300f to 300f, 330f to 600f)) {
            val e = clayLightEnd(w, h)
            val len2 = e.x * e.x + e.y * e.y
            fun t(x: Float, y: Float) = (x * e.x + y * e.y) / len2
            assertEquals(0.5f, t(w, 0f), 0.001f)
            assertEquals(0.5f, t(0f, h), 0.001f)
            assertEquals(1f, t(w, h), 0.001f)
        }
    }

    @Test
    fun `a digit template keeps the readout's shape`() {
        assertEquals("888 m", clayTemplate("300 m"))
        assertEquals("-8°", clayTemplate("-4°"))
    }
}
