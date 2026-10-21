package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Crimson look's wiring: its palettes, its Home and its place on the Look page. */
class CrimsonSkinTest {

    @Test
    fun bothVersionsUseTheCrimsonSkin() {
        assertEquals(DashSkin.CRIMSON, paletteFor(DashThemeMode.CRIMSON, light = false).Skin)
        assertEquals(DashSkin.CRIMSON, paletteFor(DashThemeMode.CRIMSON, light = true).Skin)
    }

    @Test
    fun homeIsTheMockupsFourTilesAndFitsTheGrid() {
        val layout = skinLayout(DashThemeMode.CRIMSON)
        assertNotNull(layout)
        val wide = layout!!.wide!!
        assertTrue(fitsGrid(wide))
        val kinds = wide.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }.toSet()
        assertEquals(
            setOf(BuiltinKind.SPEED_HUD, BuiltinKind.TELEMETRY, BuiltinKind.NAVIGATION, BuiltinKind.MEDIA),
            kinds
        )
    }

    @Test
    fun lookIsListedWithTheDials() {
        assertEquals(LookFamily.DIALS, lookFamily(DashThemeMode.CRIMSON))
    }
}
