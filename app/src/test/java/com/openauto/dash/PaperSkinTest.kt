package com.openauto.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PaperSkinTest {

    // --- Home ---

    @Test
    fun `the wide Home fits the grid`() {
        assertTrue(fitsGrid(PaperLayout.wide!!))
    }

    @Test
    fun `every widget of the wide Home is at least three columns by two rows and on the Home list`() {
        val home = PaperLayout.home.kinds.toSet()
        PaperLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().forEach {
            assertTrue("${it.kind} too small", it.w >= 3 && it.h >= 2 && it.w >= it.kind.minSize().first && it.h >= it.kind.minSize().second)
            assertTrue("${it.kind} not on Home", it.kind in home)
        }
    }

    @Test
    fun `the next turn takes the full height on the left`() {
        val nav = PaperLayout.wide!!.filterIsInstance<DashboardItem.BuiltinWidget>().first { it.kind == BuiltinKind.NAVIGATION }
        assertEquals(0, nav.x)
        assertEquals(GRID_ROWS, nav.h)
    }

    // --- Textures ---

    @Test
    fun `a dither level inks that many of the sixteen dots`() {
        for (level in 0..16) assertEquals(level, bayerInked(level).count { it })
    }

    @Test
    fun `each darker level keeps the dots of the lighter one`() {
        for (level in 1..16) {
            val lighter = bayerInked(level - 1)
            val darker = bayerInked(level)
            lighter.forEachIndexed { i, on -> if (on) assertTrue(darker[i]) }
        }
    }

    @Test
    fun `hatching repeats without a seam`() {
        val p = 5
        val t = hatchInked(p, 2)
        // Each row is the row above shifted by one: the lines run on across tile edges.
        for (y in 1 until p) for (x in 0 until p) assertEquals(t[(y - 1) * p + (x + 1) % p], t[y * p + x])
        assertEquals(2 * p, t.count { it })
    }

    // --- The junction ---

    @Test
    fun `the navigation's own manoeuvre wins over the words`() {
        assertEquals(PaperJunction.ROUNDABOUT, paperJunction("ROUNDABOUT", "Turn left"))
        assertEquals(PaperJunction.UTURN, paperJunction("UTURN", ""))
        assertEquals(PaperJunction.ARRIVE, paperJunction("ARRIVE", ""))
        assertEquals(PaperJunction.AHEAD, paperJunction("STRAIGHT", "Keep right"))
    }

    @Test
    fun `without a manoeuvre the words give the side`() {
        assertEquals(PaperJunction.LEFT, paperJunction(null, "Turn left onto Rue Alsace-Lorraine"))
        assertEquals(PaperJunction.RIGHT, paperJunction(null, "Tournez à droite sur Rue de Paris"))
        assertEquals(PaperJunction.AHEAD, paperJunction(null, "Continue on A6"))
    }

    @Test
    fun `a roundabout's exit is only drawn when the words name a side`() {
        assertEquals(TurnSide.RIGHT, roundaboutExit("At the roundabout, turn right"))
        assertNull(roundaboutExit("At the roundabout, take the 2nd exit"))
    }

    @Test
    fun `the street's name follows its connector`() {
        assertEquals("Rue Alsace-Lorraine", streetOf("Turn left onto Rue Alsace-Lorraine"))
        assertEquals("Rue de Paris", streetOf("Tournez à droite sur Rue de Paris"))
        assertEquals("A6", streetOf("Continue on A6"))
        assertEquals("Hauptstraße", streetOf("Links abbiegen auf die Hauptstraße"))
    }

    @Test
    fun `words that name no street give no label`() {
        assertNull(streetOf("Turn left"))
        assertNull(streetOf("Keep on the left"))
        assertNull(streetOf(""))
    }

    // --- When the turn refreshes ---

    @Test
    fun `distances read in metres whatever the unit`() {
        assertEquals(300.0, distanceMetres("300 m")!!, 0.01)
        assertEquals(1200.0, distanceMetres("1,2 km")!!, 0.01)
        assertEquals(1609.344, distanceMetres("1 mi")!!, 0.01)
        assertNull(distanceMetres("soon"))
    }

    @Test
    fun `the distance refreshes only when it crosses a step`() {
        assertEquals(distanceBucket("280 m"), distanceBucket("250 m"))
        assertNotEquals(distanceBucket("310 m"), distanceBucket("290 m"))
        assertNotEquals(distanceBucket("120 m"), distanceBucket("90 m"))
        assertEquals(-1, distanceBucket(""))
        assertTrue(distanceBucket("12 km") > distanceBucket("3 km"))
    }

    // --- Rain ---

    private val utc = TimeZone.getTimeZone("UTC")
    private val h = 3_600_000L
    private val t0 = 1_760_000_000_000L - 1_760_000_000_000L % h  // an hour's start

    @Test
    fun `a dry forecast has no wet hour`() {
        assertArrayEquals(BooleanArray(6), wetHours(t0 + 10 * 60_000L, null, null, zone = utc))
    }

    @Test
    fun `the hours that overlap the spell are wet`() {
        val now = t0 + 7 * 60_000L
        val from = t0 + h + 40 * 60_000L   // in the second hour
        val until = t0 + 3 * h + 30 * 60_000L // into the fourth
        assertArrayEquals(booleanArrayOf(false, true, true, true, false, false), wetHours(now, from, until, zone = utc))
    }

    @Test
    fun `rain already falling wets the hour in progress`() {
        val now = t0 + 20 * 60_000L
        assertArrayEquals(booleanArrayOf(true, true, false, false, false, false), wetHours(now, t0, t0 + 2 * h, zone = utc))
    }

    @Test
    fun `an hour starts on the hour in the car's zone`() {
        val paris = TimeZone.getTimeZone("Asia/Kolkata") // half-hour offset
        val start = hourStart(t0 + 50 * 60_000L, paris)
        assertEquals(0L, (start + paris.getOffset(start)) % h)
    }

    // --- The arrival row ---

    @Test
    fun `the arrival time comes first, the rest in the navigation's order`() {
        assertEquals("21:24 · 17 min · 11 km", paperEta(listOf("17 min", "11 km", "21:24")))
    }
}
