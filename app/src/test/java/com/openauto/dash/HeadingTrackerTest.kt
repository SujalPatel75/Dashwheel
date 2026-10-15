package com.openauto.dash

import com.openauto.dash.HeadingTracker.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which way the car points: the GPS bearing on the road, the last metres while it creeps, the other way in reverse. */
class HeadingTrackerTest {

    // About a metre north, and a metre east at this latitude, in degrees.
    private val north1m = 1 / 111_195.0
    private val east1m = 1 / (111_195.0 * kotlin.math.cos(Math.toRadians(48.85)))
    private val base = Fix(48.85, 2.35, null, 0f, 5f)

    private fun at(northM: Double, eastM: Double, speedKmh: Float, bearing: Float? = null, accuracy: Float = 5f) =
        Fix(base.lat + northM * north1m, base.lon + eastM * east1m, bearing, speedKmh, accuracy)

    @Test
    fun onTheRoadTheGpsBearingCounts_inReverseTheOtherWay() {
        val t = HeadingTracker()
        assertEquals(90f, t.next(null, at(0.0, 0.0, 30f, 90f), reversing = false)!!, 0.01f)
        assertEquals(270f, t.next(90f, at(0.0, -10.0, 6f, 90f), reversing = true)!!, 0.01f)
    }

    @Test
    fun aSlowTurnIntoAParkingSpaceIsSeen() {
        val t = HeadingTracker()
        // Driving east, then creeping north into the space at 2 km/h: the GPS bearing isn't believed there.
        var h = t.next(null, at(0.0, 0.0, 20f, 90f), reversing = false)
        assertEquals(90f, h!!, 0.01f)
        h = t.next(h, at(1.0, 1.0, 2f, 200f), reversing = false)
        assertEquals("a metre or two is not yet a way", 90f, h!!, 0.01f)
        h = t.next(h, at(5.0, 1.0, 2f, 200f), reversing = false)
        // 5 m north, 1 m east of where the heading was last taken: nearly north.
        assertEquals(11.3f, h!!, 0.5f)
    }

    @Test
    fun aCarReversedIntoItsSpacePointsOut() {
        val t = HeadingTracker()
        var h = t.next(null, at(0.0, 0.0, 20f, 90f), reversing = false)
        // Backing 5 m south into the space: the car points north, out of it.
        h = t.next(h, at(-5.0, 0.0, 2f), reversing = true)
        assertEquals(0f, h!!, 0.5f)
    }

    @Test
    fun aParkedCarsWanderingGpsChangesNothing() {
        val t = HeadingTracker()
        val h = t.next(null, at(0.0, 0.0, 20f, 90f), reversing = false)
        // Stopped (under the creep speed), or a vague fix: the heading stays, however far the fix strays.
        assertEquals(h, t.next(h, at(8.0, 0.0, 0.5f), reversing = false))
        assertEquals(h, t.next(h, at(8.0, 0.0, 2f, accuracy = 30f), reversing = false))
    }

    @Test
    fun withNoHeadingYetACreepStartsFromItsFirstFix() {
        val t = HeadingTracker()
        assertNull(t.next(null, at(0.0, 0.0, 2f), reversing = false))
        assertEquals(180f, t.next(null, at(-6.0, 0.0, 2f), reversing = false)!!, 0.5f)
    }

    @Test
    fun theSecondScreenGetsTheFixsAge() {
        // Both on the elapsed-realtime clock: 350 ms between the fix and now.
        assertEquals(350, ClusterFeed.fixAgeMs(1_000_000_000L, 1_350_000_000L))
        // A fix with no time of its own, one from the future, one too old to trust: no age.
        assertNull(ClusterFeed.fixAgeMs(0L, 1_350_000_000L))
        assertNull(ClusterFeed.fixAgeMs(2_000_000_000L, 1_350_000_000L))
        assertNull(ClusterFeed.fixAgeMs(1_000_000_000L, 9_000_000_000L))
    }
}
