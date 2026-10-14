package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSkinTest {

    private val wide = LiquidLayout.wide!!

    @Test
    fun `the wide Home fits the grid without overlaps`() {
        assertTrue(fitsGrid(wide))
    }

    @Test
    fun `every widget of the wide Home keeps a readable size`() {
        for (item in wide) {
            assertTrue("$item narrower than 3 columns", item.w >= 3)
            assertTrue("$item lower than 2 rows", item.h >= 2)
            assertTrue("$item under its kind's minimum", item.w >= item.minW() && item.h >= item.minH())
        }
    }

    @Test
    fun `the wide Home holds the widgets of the Home list, the car in the middle`() {
        val kinds = wide.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }.toSet()
        assertEquals(LiquidLayout.home.kinds.toSet(), kinds)
        val car = wide.filterIsInstance<DashboardItem.BuiltinWidget>().firstOrNull { it.kind == BuiltinKind.RANGE }
        assertNotNull(car)
        assertTrue(car!!.x > 0 && car.x + car.w < GRID_COLS)
    }

    @Test
    fun `choosing Liquid brings its own Home`() {
        assertTrue(skinLayout(DashThemeMode.LIQUID) === LiquidLayout)
    }
}
