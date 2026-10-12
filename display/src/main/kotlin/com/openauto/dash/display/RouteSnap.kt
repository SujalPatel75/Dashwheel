package com.openauto.dash.display

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The car held on the route while it follows it: a GPS fix wanders 5 to 20 m
 * off the road, and between fixes the car went straight on at its last
 * heading, off the road on every bend. Matched to the route, the car is drawn
 * on the route's line with the road's own heading, and goes on along the
 * line between fixes. Off the route (a wrong turn, a detour) it is drawn
 * where the GPS says, as without a route.
 *
 * Positions are world pixels at one zoom ([route] = x0, y0, x1, y1…), [mpp]
 * the metres per pixel there; headings are radians from north, clockwise.
 */
internal class RouteSnap(private val route: DoubleArray) {
    /** A place on the route: segment [seg] (from point seg to seg+1), [t] of the way along it. */
    data class Spot(val seg: Int, val t: Double, val x: Double, val y: Double, val heading: Double)

    private val n = route.size / 2
    /** The segment the car was last matched to: the next match prefers it and those after it. */
    private var last = -1

    /**
     * The route's spot for a fix at ([x], [y]) going [heading] (null when
     * unknown), or null when the car is not on it: farther than [MAX_OFF_M],
     * or going another way (the other carriageway, a road crossing under it).
     */
    fun match(x: Double, y: Double, heading: Double?, mpp: Double): Spot? {
        if (n < 2) return null
        var best: Spot? = null
        var bestCost = Double.MAX_VALUE
        for (s in 0 until n - 1) {
            val ax = route[2 * s]; val ay = route[2 * s + 1]
            val bx = route[2 * s + 2]; val by = route[2 * s + 3]
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            if (len2 == 0.0) continue
            val t = (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + dx * t; val py = ay + dy * t
            val off = hypot(x - px, y - py) * mpp
            if (off > MAX_OFF_M) continue
            val segHeading = atan2(dx, -dy)
            if (heading != null && abs(angle(segHeading - heading)) > MAX_TURN_RAD) continue
            // Going back along the route is what a car following it doesn't do: a loop's
            // earlier passage, or the way out of a roundabout, costs more than staying on.
            val back = if (last >= 0 && s < last - 1) BACK_COST_M else 0.0
            val cost = off + back
            if (cost < bestCost) {
                bestCost = cost
                best = Spot(s, t, px, py, segHeading)
            }
        }
        last = best?.seg ?: last
        return best
    }

    /** The spot [metres] further along the route from [from]; the route's end at most. */
    fun along(from: Spot, metres: Double, mpp: Double): Spot {
        if (metres <= 0.0) return from
        var left = metres / mpp
        var s = from.seg
        var t = from.t
        while (s < n - 1) {
            val ax = route[2 * s]; val ay = route[2 * s + 1]
            val dx = route[2 * s + 2] - ax; val dy = route[2 * s + 3] - ay
            val len = hypot(dx, dy)
            val rest = len * (1 - t)
            if (len > 0.0 && left <= rest) {
                val nt = t + left / len
                return Spot(s, nt, ax + dx * nt, ay + dy * nt, atan2(dx, -dy))
            }
            left -= rest
            s++
            t = 0.0
        }
        val i = n - 1
        return Spot(n - 2, 1.0, route[2 * i], route[2 * i + 1], from.heading)
    }

    companion object {
        /** A fix this far from the route at most is still on it: a GPS's error, a wide road. */
        const val MAX_OFF_M = 30.0
        /** Going this far from the route's way, the car is on another road. */
        val MAX_TURN_RAD = Math.toRadians(60.0)
        /** What matching a segment behind the last one costs, in metres of distance. */
        const val BACK_COST_M = 25.0

        private fun angle(a: Double): Double = atan2(sin(a), cos(a))
    }
}
