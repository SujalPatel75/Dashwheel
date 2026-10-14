package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrutalSkinTest {

    @Test
    fun `the wide Home fits the grid without overlaps`() {
        val wide = BrutalLayout.wide!!
        assertTrue(fitsGrid(wide))
    }

    @Test
    fun `the wide Home fills the whole grid`() {
        val cells = BrutalLayout.wide!!.sumOf { it.w * it.h }
        assertEquals(12 * 7, cells)
    }

    @Test
    fun `every widget keeps its minimum size`() {
        BrutalLayout.wide!!.forEach {
            assertTrue("$it narrower than its minimum", it.w >= it.minW())
            assertTrue("$it lower than its minimum", it.h >= it.minH())
        }
    }

    @Test
    fun `the wide Home holds the widgets of the Home list, the next turn first`() {
        val kinds = BrutalLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        assertEquals(BrutalLayout.home.kinds.toSet(), kinds.toSet())
        assertEquals(BuiltinKind.NAVIGATION, BrutalLayout.home.kinds.first())
        assertEquals(false, BrutalLayout.home.dock)
    }

    @Test
    fun `the palettes are the Brutal skin's, light and dark`() {
        assertEquals(DashSkin.BRUTAL, BrutalPalette.Skin)
        assertEquals(DashSkin.BRUTAL, BrutalLightPalette.Skin)
        assertTrue(BrutalLightPalette.Light)
        assertEquals(false, BrutalPalette.Light)
    }
}
