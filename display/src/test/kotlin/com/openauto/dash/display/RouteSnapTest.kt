package com.openauto.dash.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The car held on the route on the second screen's map. */
class RouteSnapTest {

    /** One world pixel = one metre here; y grows southwards, so north is -y. */
    private val mpp = 1.0
    private val north = 0.0
    private val east = Math.PI / 2

    // North 200 m, then east 200 m: an L-shaped route.
    private val ell = doubleArrayOf(0.0, 0.0, 0.0, -200.0, 200.0, -200.0)

    @Test
    fun aFixBesideTheRoadIsPutOnIt() {
        val spot = RouteSnap(ell).match(12.0, -100.0, north, mpp)!!
        assertEquals(0.0, spot.x, 1e-9)
        assertEquals(-100.0, spot.y, 1e-9)
        assertEquals(north, spot.heading, 1e-9)
    }

    @Test
    fun betweenFixesTheCarGoesRoundTheBend() {
        val snap = RouteSnap(ell)
        val spot = snap.match(3.0, -190.0, north, mpp)!!
        // 30 m on: 10 m up to the corner, then 20 m east along the next leg.
        val on = snap.along(spot, 30.0, mpp)
        assertEquals(20.0, on.x, 1e-6)
        assertEquals(-200.0, on.y, 1e-6)
        assertEquals(east, on.heading, 1e-6)
    }

    @Test
    fun farFromTheRouteTheCarIsWhereTheGpsSays() {
        assertNull(RouteSnap(ell).match(40.0, -100.0, north, mpp))
    }

    @Test
    fun goingTheOtherWayIsAnotherRoad() {
        // Right beside the route but heading south: the other carriageway.
        assertNull(RouteSnap(ell).match(5.0, -100.0, Math.PI, mpp))
        // Heading unknown: the nearest line will do.
        assertNotNull(RouteSnap(ell).match(5.0, -100.0, null, mpp))
    }

    @Test
    fun aLoopsEarlierPassageIsNotJumpedBackTo() {
        // North, east, south and back west across the start: the last leg passes near the first.
        val loop = doubleArrayOf(0.0, 0.0, 0.0, -100.0, 100.0, -100.0, 100.0, -20.0, -50.0, -20.0)
        val snap = RouteSnap(loop)
        assertNotNull(snap.match(100.0, -60.0, Math.PI, mpp))
        // Near both the first leg (going north) and the last (going west); heading west, the last one.
        val spot = snap.match(2.0, -22.0, -east, mpp)!!
        assertEquals(3, spot.seg)
        assertTrue(spot.y == -20.0)
    }

    @Test
    fun theRoutesEndIsAsFarAsItGoes() {
        val snap = RouteSnap(ell)
        val spot = snap.match(190.0, -200.0, east, mpp)!!
        val on = snap.along(spot, 500.0, mpp)
        assertEquals(200.0, on.x, 1e-9)
        assertEquals(-200.0, on.y, 1e-9)
    }
}
