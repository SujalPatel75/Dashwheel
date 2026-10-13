package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Kinetic: its Home, the hero's two lines, its width and the ticker's facts. */
class KineticSkinTest {

    private val full = TemplateScreen.of(1280f, 576f, obdPaired = true, driverOnRight = false, mapsDocked = false, dockApps = emptyList())

    @Test
    fun theWideHomeFitsTheGrid_readably() {
        val wide = KineticLayout.wide!!
        assertTrue(fitsGrid(wide))
        wide.forEach { assertTrue("$it too small", it.w >= it.minW() && it.h >= it.minH() && it.w >= 3 && it.h >= 2) }
        // Every cell taken: the hero, the speed, the suggestion and the car strip fill the page.
        assertEquals(GRID_COLS * GRID_ROWS, wide.sumOf { it.w * it.h })
    }

    @Test
    fun aWideScreenGetsTheMockupsArrangement_anUprightOneItsWidgetsPlaced() {
        assertEquals(KineticLayout.wide, skinHome(KineticLayout, full))
        ScreenShape.vertical = true
        try {
            val upright = TemplateScreen.of(720f, 1120f, obdPaired = true, driverOnRight = false, mapsDocked = false, dockApps = emptyList())
            val page = skinHome(KineticLayout, upright)
            assertTrue(fitsGrid(page))
            assertTrue(page.filterIsInstance<DashboardItem.BuiltinWidget>().any { it.kind == BuiltinKind.NAVIGATION })
        } finally {
            ScreenShape.vertical = false
        }
    }

    @Test
    fun theWideKindsAreAllInTheHomeList() {
        val kinds = KineticLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        assertTrue(KineticLayout.home.kinds.containsAll(kinds))
        assertEquals(BuiltinKind.NAVIGATION, KineticLayout.home.kinds.first())
    }

    @Test
    fun theSentenceSplitsWhereTheStreetStarts() {
        assertEquals("Turn left onto" to "Rue Alsace-Lorraine", knSplitInstruction("Turn left onto Rue Alsace-Lorraine"))
        assertEquals("Tournez à droite sur" to "Avenue Jean Jaurès", knSplitInstruction("Tournez à droite sur Avenue Jean Jaurès"))
        assertEquals("Continue on" to "A7 toward Lyon", knSplitInstruction("Continue on A7 toward Lyon"))
        assertEquals("Biegen Sie links ab auf" to "Hauptstraße", knSplitInstruction("Biegen Sie links ab auf Hauptstraße"))
        assertEquals("Поверните налево на" to "улицу Ленина", knSplitInstruction("Поверните налево на улицу Ленина"))
    }

    @Test
    fun aSentenceWithoutAStreetStandsWhole() {
        assertNull(knSplitInstruction("Turn right"))
        assertNull(knSplitInstruction("Keep left at the fork"))
        // A short word that is not leading into a name: "a la izquierda" is the turn itself.
        assertNull(knSplitInstruction("Gira a la izquierda"))
        assertNull(knSplitInstruction(""))
    }

    @Test
    fun theHeroWidensAsTheTurnComesCloser() {
        assertEquals(KN_SQUEEZE_FAR, knSqueezeFor(5_000), 0.001f)
        assertEquals(KN_SQUEEZE_NEAR, knSqueezeFor(20), 0.001f)
        assertTrue(knSqueezeFor(800) < knSqueezeFor(300))
        assertTrue(knSqueezeFor(300) < knSqueezeFor(100))
        val unknown = knSqueezeFor(null)
        assertTrue(unknown > KN_SQUEEZE_FAR && unknown < KN_SQUEEZE_NEAR)
    }

    @Test
    fun theEtaSaysItsArrivalAsSuch() {
        val nav = NavState(active = true, eta = "17 min · 11 km · 21:24")
        assertEquals("Arrival 21:24 · 17 min · 11 km", knEtaLine(nav, "Arrival %s"))
        assertEquals("12 min", knEtaLine(NavState(active = true, eta = "12 min"), "Arrival %s"))
    }

    @Test
    fun parisSeesTheSunRiseBeforeItSets_atSensibleHours() {
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val day = Calendar.getInstance(paris).apply { set(2026, Calendar.OCTOBER, 13, 12, 0, 0) }.timeInMillis
        val times = sunTimes(day, 48.85, 2.35, paris)
        assertNotNull(times)
        val (rise, set) = times!!
        assertTrue(rise < set)
        val riseHour = Calendar.getInstance(paris).apply { timeInMillis = rise }.get(Calendar.HOUR_OF_DAY)
        val setHour = Calendar.getInstance(paris).apply { timeInMillis = set }.get(Calendar.HOUR_OF_DAY)
        assertTrue("rise at $riseHour", riseHour in 7..8)
        assertTrue("set at $setHour", setHour in 18..19)
    }

    @Test
    fun thePolarNightHasNoSunTimes() {
        val utc = TimeZone.getTimeZone("UTC")
        val midwinter = Calendar.getInstance(utc).apply { set(2026, Calendar.DECEMBER, 21, 12, 0, 0) }.timeInMillis
        assertNull(sunTimes(midwinter, 78.2, 15.6, utc))
    }
}
