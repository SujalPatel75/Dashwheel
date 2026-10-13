package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chrome's Home and its liquid drop. */
class ChromeSkinTest {

    private val wide: List<DashboardItem> get() = requireNotNull(ChromeLayout.wide)

    @Test
    fun wideHomeFitsTheGrid() {
        ScreenShape.vertical = false
        assertTrue(fitsGrid(wide))
    }

    @Test
    fun everyWidgetKeepsItsReadableMinimum() {
        ScreenShape.vertical = false
        wide.forEach {
            assertTrue("$it at least 3 columns", it.w >= 3)
            assertTrue("$it at least 2 rows", it.h >= 2)
            assertTrue("$it above its kind's minimum", it.w >= it.minW() && it.h >= it.minH())
        }
    }

    @Test
    fun wideHomeOnlyUsesTheHomeWidgets() {
        val kinds = wide.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        assertEquals(kinds.size, kinds.toSet().size)
        assertTrue(ChromeLayout.home.kinds.containsAll(kinds))
        assertEquals(
            listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE, BuiltinKind.CLOCK),
            ChromeLayout.home.kinds
        )
    }

    @Test
    fun theFootRowLinesUp() {
        ScreenShape.vertical = false
        // The arrival chip (foot of the turn tile), the music pill and the range chip all end on the last row.
        val byKind = wide.filterIsInstance<DashboardItem.BuiltinWidget>().associateBy { it.kind }
        listOf(BuiltinKind.NAVIGATION, BuiltinKind.MEDIA, BuiltinKind.RANGE).forEach {
            val t = byKind.getValue(it)
            assertEquals("$it ends on the last row", GRID_ROWS, t.y + t.h)
        }
    }

    @Test
    fun theDropStaysWithinItsReach() {
        var lo = Float.MAX_VALUE
        var hi = 0f
        for (p in 0 until 120) for (e in listOf(0f, 0.5f, 1f)) for (t in 0 until 64) {
            val r = dropRadius((t / 64f * 2f * Math.PI).toFloat(), p / 120f, e)
            lo = minOf(lo, r)
            hi = maxOf(hi, r)
        }
        assertTrue("never pinched: $lo", lo > 0.75f)
        assertTrue("never past its reach: $hi", hi <= DROP_REACH)
    }

    @Test
    fun theDropLoopsWithoutAJump() {
        for (t in 0 until 64) {
            val theta = (t / 64f * 2f * Math.PI).toFloat()
            assertEquals(dropRadius(theta, 0f, 1f), dropRadius(theta, 1f, 1f), 1e-4f)
        }
    }
}
