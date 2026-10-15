package com.openauto.dash

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Which way the car points, from its GPS fixes: what the second screen's map
 * turns by, what the assistant calls "ahead". Kept while the car stands.
 *
 * The GPS bearing is only believed above [MOVING_KMH]; below it, it wanders.
 * That left the slow last metres into a parking space unseen: a car that
 * turned into its space at walking pace showed on the map still pointing the
 * way it came, its building off to the side instead of ahead. So while the
 * car creeps, the way it went over the last few metres counts instead
 * ([CREEP_M]), from fixes good enough to tell ([CREEP_ACCURACY_M]). And the
 * GPS gives the way the car travels: in reverse, the car points the other
 * way, so a car reversed into its space used to show facing the road.
 *
 * Pure: the caller feeds it fixes (see [LocationFeed]).
 */
internal class HeadingTracker {

    /** One GPS fix, as much of it as the heading needs. */
    data class Fix(val lat: Double, val lon: Double, val bearingDeg: Float?, val speedKmh: Float, val accuracyM: Float)

    /** The fix the last heading was taken at: the creep is measured from it. */
    private var anchor: Fix? = null

    /**
     * The heading after [fix], given the one before ([prev]) and whether the
     * car is in reverse; [prev] when the fix tells nothing new.
     */
    fun next(prev: Float?, fix: Fix, reversing: Boolean): Float? {
        val bearing = fix.bearingDeg
        if (fix.speedKmh > MOVING_KMH && bearing != null) {
            anchor = fix
            return pointing(bearing, reversing)
        }
        if (fix.speedKmh < CREEP_KMH || fix.accuracyM > CREEP_ACCURACY_M) return prev
        val from = anchor ?: run { anchor = fix; return prev }
        if (distanceM(from, fix) < CREEP_M) return prev
        anchor = fix
        return pointing(bearingDeg(from, fix), reversing)
    }

    /** Forgets where the last heading was taken (a new drive). */
    fun reset() {
        anchor = null
    }

    companion object {
        /** Above this the GPS's own bearing is believed. */
        const val MOVING_KMH = 3f
        /** From this up to [MOVING_KMH] the car creeps: its way over the last metres counts. */
        const val CREEP_KMH = 1.5f
        /** How far the car has to creep before that way counts: a parked GPS wanders less. */
        const val CREEP_M = 4.0
        /** A fix less sure than this says nothing about a creep. */
        const val CREEP_ACCURACY_M = 12f

        /** Where the car points when it travels along [travelDeg]. */
        fun pointing(travelDeg: Float, reversing: Boolean): Float =
            norm(if (reversing) travelDeg + 180f else travelDeg)

        private fun norm(deg: Float): Float = ((deg % 360f) + 360f) % 360f

        private const val EARTH_M = 6_371_000.0

        /** Metres from [a] to [b]: flat over a few metres, which is all it is used for. */
        fun distanceM(a: Fix, b: Fix): Double {
            val (east, north) = offsetM(a, b)
            return hypot(east, north)
        }

        /** Degrees from north, clockwise, from [a] to [b]. */
        fun bearingDeg(a: Fix, b: Fix): Float {
            val (east, north) = offsetM(a, b)
            return norm(Math.toDegrees(atan2(east, north)).toFloat())
        }

        private fun offsetM(a: Fix, b: Fix): Pair<Double, Double> {
            val north = Math.toRadians(b.lat - a.lat) * EARTH_M
            val east = Math.toRadians(b.lon - a.lon) * EARTH_M * cos(Math.toRadians((a.lat + b.lat) / 2))
            return east to north
        }
    }
}
