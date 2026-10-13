package com.openauto.dash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Spatial skin: the city behind the windows. A night city seen from above at
 * a slant, as the mockup's three.js scene has it: rows of extruded blocks
 * (lit from the upper left, a few windows lit), fog swallowing the far
 * streets, and a route glowing cyan to violet along the avenues to a beam of
 * light. By day the same city in pale blue-greys.
 *
 * It is a backdrop, not the map: one made-up city and one made-up route,
 * always the same (seeded), never the driver's position or road. The still
 * picture is drawn once per size and look into a bitmap, off the main
 * thread; the only motion is a short light running along the route now and
 * then while parked, a few dozen short lines over the bitmap.
 */

// The plan, in the mockup's units: x to the right, z away from the camera, y up.

/** Blocks per side, their pitch and their footprint. */
private const val CITY_N = 36
private const val CITY_PITCH = 10f
private const val CITY_BLOCK = 7.8f

/** The camera's height over the streets. */
private const val EYE_H = 95f

/** Fog: none nearer than [FOG_NEAR], the page colour from [FOG_FAR] on (view depth). */
private const val FOG_NEAR = 60f
private const val FOG_FAR = 260f

/** The route as ground (x, z) pairs along the avenues, from under the camera to the destination. */
private val ROUTE = floatArrayOf(-20f, -150f, -20f, -20f, 20f, -20f, 20f, 30f, 60f, 30f, 60f, 130f)

/** The route's height over the ground and half its width. */
private const val ROUTE_Y = 0.6f
private const val ROUTE_HALF = 1.1f

/** Route samples are this far apart along it (plan units). */
private const val ROUTE_STEP = 2f

/** The light running along the route: how many samples its tail spans. */
internal const val PULSE_TAIL = 18

/** The car's dot on the route, and the beam over the destination. */
private const val PUCK_X = -20f
private const val PUCK_Z = -36f
private const val BEAM_H = 40f
private const val BEAM_R = 2.2f

private class SpatialInk(
    val page: Color,
    val ground: Color,
    val street: Color,
    val top: Color,
    val lit: Color,
    val front: Color,
    val shade: Color,
    val edge: Color,
    val warmWindow: Color?,
    val coolWindow: Color?,
    val lamp: Color?,
    val routeA: Color,
    val routeB: Color,
    val core: Color,
    val beam: Color,
    val puck: Color
)

private val NightInk = SpatialInk(
    page = Color(0xFF0A0D16), ground = Color(0xFF0C1020), street = Color(0xFF111729),
    top = Color(0xFF3B4570), lit = Color(0xFF353E64), front = Color(0xFF2A3253), shade = Color(0xFF1C2239),
    edge = Color(0xFF55618F),
    warmWindow = Color(0xFFFFD49A), coolWindow = Color(0xFF9FD8FF), lamp = Color(0xFFFFB86B),
    routeA = Color(0xFF33BFFF), routeB = Color(0xFFB380FF), core = Color(0xFFEAF8FF),
    beam = Color(0xFF8F6BFF), puck = Color.White
)

private val DayInk = SpatialInk(
    page = Color(0xFFE8EDF5), ground = Color(0xFFDCE3EE), street = Color(0xFFE4E9F2),
    top = Color(0xFFF8FAFD), lit = Color(0xFFE2E8F1), front = Color(0xFFD0D8E6), shade = Color(0xFFBCC6D8),
    edge = Color(0xFFFFFFFF),
    warmWindow = null, coolWindow = null, lamp = null,
    routeA = Color(0xFF0A8FC4), routeB = Color(0xFF6A4BE0), core = Color(0xFFFFFFFF),
    beam = Color(0xFF6A4BE0), puck = Color(0xFF0D1220)
)

/** The page colour of the city, for whatever is drawn before it is ready or around it. */
internal fun spatialCityPage(light: Boolean): Color = if (light) DayInk.page else NightInk.page

/** A stable 0..1 for a block of the plan and a [salt]. */
private fun cityHash(a: Int, b: Int, salt: Int): Float {
    var h = a * 73_856_093 xor b * 19_349_663 xor salt * 83_492_791
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    h = h xor (h ushr 15)
    return (h and 0xFFFFFF) / 16_777_216f
}

/**
 * The city seen in a [width] x [height] px box. [glow] scales the route's
 * glow, the beam and the lit windows (1 full, 0.5 reduced, 0 with effects
 * off, which also flattens the city so the windows over it read in the sun).
 * Built on any thread; [raster] is the still picture, [drawPulse] the light
 * along the route.
 */
internal class SpatialCityScene(val width: Int, val height: Int, val light: Boolean, val glow: Float) {
    private val w = width.toFloat().coerceAtLeast(1f)
    private val h = height.toFloat().coerceAtLeast(1f)
    private val ink = if (light) DayInk else NightInk
    private val plain = glow <= 0f

    // The camera: eye, forward, right and up, and the focal length in px.
    private val focal: Float
    private val ex: Float
    private val ey: Float
    private val ez: Float
    private val fx: Float
    private val fy: Float
    private val fz: Float
    private val rx: Float
    private val rz: Float
    private val ux: Float
    private val uy: Float
    private val uz: Float

    // The projection's output, so projecting allocates nothing.
    private var px = 0f
    private var py = 0f
    private var pz = 0f

    // The blocks in view, farthest first.
    private val bx0: FloatArray
    private val bx1: FloatArray
    private val bz0: FloatArray
    private val bz1: FloatArray
    private val bh: FloatArray
    private val bDepth: FloatArray
    private val bSeed: IntArray
    private val blocks: Int

    // The route's samples on screen: position, width, how far along (0..1), and whether a block hides it.
    private val sx: FloatArray
    private val sy: FloatArray
    private val sw: FloatArray
    private val sAlong: FloatArray
    private val sSeen: BooleanArray

    /** Route samples, for the pulse's head. */
    val samples: Int

    private var bitmap: ImageBitmap? = null

    init {
        val wide = w >= h
        // The mockup's 40 degree view across a wide screen; an upright one keeps its width's view and looks down more steeply.
        focal = if (wide) (h / 2f) / tan(rad(20f)) else (w / 2f) / tan(rad(33f))
        val vHalf = atan((h / 2f) / focal)
        val pitch = max(rad(29f), vHalf + rad(8f))
        val tx = if (wide) 8f else 20f
        val tz = if (wide) 20f else 10f
        val dist = EYE_H / tan(pitch)
        ex = tx - 0.13f * dist
        ey = EYE_H
        ez = tz - dist
        var dx = tx - ex
        var dy = -EYE_H
        var dz = tz - ez
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        dx /= len; dy /= len; dz /= len
        fx = dx; fy = dy; fz = dz
        val rl = sqrt(fz * fz + fx * fx)
        rx = fz / rl
        rz = -fx / rl
        ux = fy * rz
        uy = fz * rx - fx * rz
        uz = -fy * rx

        // The blocks: the mockup's seeded heights, every 4th column and 5th row left for the avenues and streets.
        val cap = CITY_N * CITY_N
        val x0 = FloatArray(cap)
        val x1 = FloatArray(cap)
        val z0 = FloatArray(cap)
        val z1 = FloatArray(cap)
        val hh = FloatArray(cap)
        val depth = FloatArray(cap)
        val seed = IntArray(cap)
        var seedRnd = 7L
        fun rnd(): Float {
            seedRnd = (seedRnd * 16807L) % 2147483647L
            return seedRnd / 2147483647f
        }
        var k = 0
        for (i in 0 until CITY_N) for (j in 0 until CITY_N) {
            if (i % 4 == 0 || j % 5 == 0) continue
            val height = 3f + rnd().pow(2.2f) * 28f
            val cx = (i - CITY_N / 2) * CITY_PITCH
            val cz = (CITY_N / 2 - j) * CITY_PITCH
            val half = CITY_BLOCK / 2f
            if (!inView(cx - half, cx + half, cz - half, cz + half, height)) continue
            x0[k] = cx - half; x1[k] = cx + half; z0[k] = cz - half; z1[k] = cz + half; hh[k] = height
            project(cx, height / 2f, cz)
            depth[k] = pz
            seed[k] = i * 131 + j
            k++
        }
        // Farthest first, for the painter.
        val order = (0 until k).sortedByDescending { depth[it] }
        blocks = k
        bx0 = FloatArray(k) { x0[order[it]] }
        bx1 = FloatArray(k) { x1[order[it]] }
        bz0 = FloatArray(k) { z0[order[it]] }
        bz1 = FloatArray(k) { z1[order[it]] }
        bh = FloatArray(k) { hh[order[it]] }
        bDepth = FloatArray(k) { depth[order[it]] }
        bSeed = IntArray(k) { seed[order[it]] }

        // The route's samples.
        var total = 0f
        for (s in 0 until ROUTE.size / 2 - 1) total += segLength(s)
        val counts = IntArray(ROUTE.size / 2 - 1) { ceil(segLength(it) / ROUTE_STEP).toInt().coerceAtLeast(1) }
        val n = counts.sum() + 1
        samples = n
        sx = FloatArray(n); sy = FloatArray(n); sw = FloatArray(n); sAlong = FloatArray(n); sSeen = BooleanArray(n)
        var at = 0
        var along = 0f
        for (s in counts.indices) {
            val ax = ROUTE[s * 2]; val az = ROUTE[s * 2 + 1]
            val bx = ROUTE[s * 2 + 2]; val bz = ROUTE[s * 2 + 3]
            val l = segLength(s)
            val last = if (s == counts.lastIndex) counts[s] else counts[s] - 1
            for (q in 0..last) {
                val t = q / counts[s].toFloat()
                val x = ax + (bx - ax) * t
                val z = az + (bz - az) * t
                project(x, ROUTE_Y, z)
                sx[at] = px; sy[at] = py
                sw[at] = if (pz > 1f) focal * ROUTE_HALF * 2f / pz else 0f
                sAlong[at] = (along + l * t) / total
                sSeen[at] = pz > 1f && !hidden(x, ROUTE_Y, z)
                at++
            }
            along += l
        }
    }

    private fun rad(deg: Float): Float = deg * PI.toFloat() / 180f

    private fun segLength(s: Int): Float {
        val dx = ROUTE[s * 2 + 2] - ROUTE[s * 2]
        val dz = ROUTE[s * 2 + 3] - ROUTE[s * 2 + 1]
        return sqrt(dx * dx + dz * dz)
    }

    /** Projects a point into [px], [py] (screen px) and [pz] (view depth). */
    private fun project(x: Float, y: Float, z: Float) {
        val dx = x - ex
        val dy = y - ey
        val dz = z - ez
        val zc = dx * fx + dy * fy + dz * fz
        val xc = dx * rx + dz * rz
        val yc = dx * ux + dy * uy + dz * uz
        val d = if (zc > 0.01f) zc else 0.01f
        px = w / 2f + focal * xc / d
        py = h / 2f - focal * yc / d
        pz = zc
    }

    /** Whether a block is in front of the camera and on screen. */
    private fun inView(x0: Float, x1: Float, z0: Float, z1: Float, height: Float): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        for (c in 0 until 8) {
            project(if (c and 1 == 0) x0 else x1, if (c and 2 == 0) 0f else height, if (c and 4 == 0) z0 else z1)
            if (pz < 4f) return false
            minX = min(minX, px); maxX = max(maxX, px); minY = min(minY, py)
        }
        return maxX > -2f && minX < w + 2f && minY < h + 2f
    }

    /** Whether a block stands between the camera and the point. */
    private fun hidden(x: Float, y: Float, z: Float): Boolean {
        val dx = x - ex
        val dy = y - ey
        val dz = z - ez
        for (b in 0 until blocks) {
            var t0 = 0f
            var t1 = 0.995f
            // x slab
            if (abs(dx) < 1e-6f) {
                if (ex < bx0[b] || ex > bx1[b]) continue
            } else {
                val a = (bx0[b] - ex) / dx
                val c = (bx1[b] - ex) / dx
                t0 = max(t0, min(a, c)); t1 = min(t1, max(a, c))
                if (t0 > t1) continue
            }
            // y slab: from the ground to the roof
            val a = (0f - ey) / dy
            val c = (bh[b] - ey) / dy
            t0 = max(t0, min(a, c)); t1 = min(t1, max(a, c))
            if (t0 > t1) continue
            // z slab
            if (abs(dz) < 1e-6f) {
                if (ez < bz0[b] || ez > bz1[b]) continue
            } else {
                val e = (bz0[b] - ez) / dz
                val f = (bz1[b] - ez) / dz
                t0 = max(t0, min(e, f)); t1 = min(t1, max(e, f))
                if (t0 > t1) continue
            }
            return true
        }
        return false
    }

    private fun fogAt(depth: Float): Float {
        val f = ((depth - FOG_NEAR) / (FOG_FAR - FOG_NEAR)).coerceIn(0f, 1f)
        return if (plain) min(1f, 0.25f + f) else f
    }

    private fun fogged(color: Color, depth: Float): Color = lerp(color, ink.page, fogAt(depth))

    private fun routeColor(along: Float): Color = lerp(ink.routeA, ink.routeB, along)

    /** The still picture, drawn on first use (call it off the main thread first). */
    fun raster(): ImageBitmap = synchronized(this) {
        bitmap ?: ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1)).also { bmp ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w, h)) { drawCity() }
            bitmap = bmp
        }
    }

    /** The picture when it is drawn already, else null. */
    fun rasterIfReady(): ImageBitmap? = bitmap

    private fun DrawScope.drawCity() {
        val path = Path()
        drawRect(ink.page)
        // The ground, fading into the fog toward the top.
        val farY = groundY(FOG_FAR)
        val nearY = groundY(FOG_NEAR)
        drawRect(
            Brush.verticalGradient(listOf(ink.page, if (plain) lerp(ink.ground, ink.page, 0.4f) else ink.ground), startY = farY, endY = max(nearY, farY + 1f))
        )
        drawStreetGlow()
        drawRoute(path)
        // The beam and the car's dot are drawn among the blocks, at their own depth.
        project(ROUTE[ROUTE.size - 2], 0f, ROUTE[ROUTE.size - 1])
        val beamDepth = pz
        project(PUCK_X, 0f, PUCK_Z)
        val puckDepth = pz
        var beamDone = false
        var puckDone = false
        for (b in 0 until blocks) {
            if (!beamDone && bDepth[b] < beamDepth) { drawBeam(path); beamDone = true }
            if (!puckDone && bDepth[b] < puckDepth) { drawPuck(); puckDone = true }
            drawBlock(path, b)
        }
        if (!beamDone) drawBeam(path)
        if (!puckDone) drawPuck()
        // A last veil of fog at the top, under the speed.
        drawRect(Brush.verticalGradient(listOf(ink.page.copy(alpha = if (light) 0.6f else 0.75f), ink.page.copy(alpha = 0f)), startY = 0f, endY = h * 0.18f), size = Size(w, h * 0.18f))
    }

    /** Screen y of the ground straight ahead at view depth [depth]. */
    private fun groundY(depth: Float): Float {
        val hl = sqrt(fx * fx + fz * fz)
        val t = (depth + ey * fy) / hl
        project(ex + fx / hl * t, 0f, ez + fz / hl * t)
        return py
    }

    /** At night a soft lamp at each crossing of the avenues and streets. */
    private fun DrawScope.drawStreetGlow() {
        val lamp = ink.lamp ?: return
        if (plain) return
        for (i in 0..CITY_N step 4) for (j in 0..CITY_N step 5) {
            val x = (i - CITY_N / 2) * CITY_PITCH
            val z = (CITY_N / 2 - j) * CITY_PITCH
            project(x, 0f, z)
            if (pz < 8f || px < -40f || px > w + 40f || py < -40f || py > h + 40f) continue
            val fade = 1f - fogAt(pz)
            if (fade <= 0.02f) continue
            val r = focal * 3.2f / pz
            scale(1f, 0.45f, Offset(px, py)) {
                drawCircle(lamp.copy(alpha = 0.10f * fade * glow), r * 2.2f, Offset(px, py))
                drawCircle(lamp.copy(alpha = 0.22f * fade * glow), r, Offset(px, py))
            }
        }
    }

    /**
     * The route on the ground: a wide soft glow and a narrower one, each one
     * filled ribbon (so its alpha never doubles where pieces meet), the line,
     * and a bright thread along its middle.
     */
    private fun DrawScope.drawRoute(path: Path) {
        val last = samples - 1
        if (last < 1) return
        val brush = Brush.linearGradient(listOf(ink.routeA, ink.routeB), Offset(sx[0], sy[0]), Offset(sx[last], sy[last]))
        if (!plain) {
            ribbon(path, 0, last, 8f)
            drawPath(path, brush, alpha = 0.05f * glow)
            ribbon(path, 0, last, 5f)
            drawPath(path, brush, alpha = 0.08f * glow)
            ribbon(path, 0, last, 2.4f)
            drawPath(path, brush, alpha = 0.26f * glow)
        }
        ribbon(path, 0, last, 1f)
        drawPath(path, brush)
        if (!plain) {
            for (i in 0 until last) {
                if (sw[i] <= 0f) continue
                val c = lerp(routeColor(sAlong[i]), ink.core, 0.6f)
                drawLine(c, Offset(sx[i], sy[i]), Offset(sx[i + 1], sy[i + 1]), (sw[i] * 0.3f).coerceAtLeast(1f), StrokeCap.Round)
            }
        }
    }

    /** The route from sample [from] to [to] as a filled band [k] times its width, into [path]. */
    private fun ribbon(path: Path, from: Int, to: Int, k: Float) {
        path.reset()
        for (i in from..to) {
            val a = max(0, i - 1)
            val b = min(samples - 1, i + 1)
            val dx = sx[b] - sx[a]
            val dy = sy[b] - sy[a]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val half = sw[i] * k / 2f
            val x = sx[i] - dy / len * half
            val y = sy[i] + dx / len * half
            if (i == from) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in to downTo from) {
            val a = max(0, i - 1)
            val b = min(samples - 1, i + 1)
            val dx = sx[b] - sx[a]
            val dy = sy[b] - sy[a]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val half = sw[i] * k / 2f
            path.lineTo(sx[i] + dy / len * half, sy[i] - dx / len * half)
        }
        path.close()
    }

    /** One block: its sides that face the camera, then its roof with a lit edge, then a few lit windows at night. */
    private fun DrawScope.drawBlock(path: Path, b: Int) {
        val x0 = bx0[b]; val x1 = bx1[b]; val z0 = bz0[b]; val z1 = bz1[b]; val top = bh[b]
        val depth = bDepth[b]
        val fog = fogAt(depth)
        if (fog >= 0.995f) return
        val flatten = if (plain) 0.35f else 0f
        fun face(c: Color): Color = lerp(lerp(c, ink.page, flatten), ink.page, fog)
        // The side toward the camera's left or right.
        if (ex < x0) quad(path, x0, z1, x0, z0, top, face(ink.lit))
        if (ex > x1) quad(path, x1, z0, x1, z1, top, face(ink.shade))
        // The near face.
        quad(path, x0, z0, x1, z0, top, face(ink.front))
        // The roof.
        path.reset()
        project(x0, top, z0); path.moveTo(px, py)
        val ax = px; val ay = py
        project(x1, top, z0); path.lineTo(px, py)
        val bxp = px; val byp = py
        project(x1, top, z1); path.lineTo(px, py)
        project(x0, top, z1); path.lineTo(px, py)
        path.close()
        drawPath(path, face(ink.top))
        if (!plain) drawLine(face(ink.edge).copy(alpha = (if (light) 0.9f else 0.55f) * (1f - fog)), Offset(ax, ay), Offset(bxp, byp), 1f)
        // Lit windows on the near face, at night.
        val warm = ink.warmWindow
        val cool = ink.coolWindow
        if (warm != null && cool != null && !plain && top > 8f && depth < FOG_FAR * 0.85f) {
            val seed = bSeed[b]
            val count = (cityHash(seed, 1, 3) * 5f).toInt() - 1
            for (q in 0 until count) {
                val u = 0.15f + 0.7f * cityHash(seed, q, 11)
                val v = 0.12f + 0.78f * cityHash(seed, q, 17)
                project(x0 + (x1 - x0) * u, top * v, z0)
                val ww = (focal * 0.7f / pz).coerceAtLeast(1f)
                val wh = (focal * 0.45f / pz).coerceAtLeast(1f)
                val c = if (cityHash(seed, q, 23) < 0.7f) warm else cool
                drawRect(c.copy(alpha = 0.8f * (1f - fog) * glow.coerceAtLeast(0.6f)), Offset(px - ww / 2f, py - wh / 2f), Size(ww, wh))
            }
        }
    }

    /** A vertical wall from ground (xa, za) to (xb, zb), [top] high. */
    private fun DrawScope.quad(path: Path, xa: Float, za: Float, xb: Float, zb: Float, top: Float, color: Color) {
        path.reset()
        project(xa, 0f, za); path.moveTo(px, py)
        project(xb, 0f, zb); path.lineTo(px, py)
        project(xb, top, zb); path.lineTo(px, py)
        project(xa, top, za); path.lineTo(px, py)
        path.close()
        drawPath(path, color)
    }

    /** The beam over the destination: a pool of violet on the ground and a column of light fading upward. */
    private fun DrawScope.drawBeam(path: Path) {
        val bx = ROUTE[ROUTE.size - 2]
        val bz = ROUTE[ROUTE.size - 1]
        project(bx, 0f, bz)
        if (pz < 4f) return
        val baseX = px
        val baseY = py
        val fade = 1f - fogAt(pz) * 0.5f
        val r = focal * BEAM_R / pz
        project(bx, BEAM_H, bz)
        val topX = px
        val topY = py
        if (!plain) {
            scale(1f, 0.4f, Offset(baseX, baseY)) {
                drawCircle(Brush.radialGradient(listOf(ink.beam.copy(alpha = 0.45f * glow * fade), Color.Transparent), Offset(baseX, baseY), r * 4f), r * 4f, Offset(baseX, baseY))
            }
            path.reset()
            path.moveTo(baseX - r, baseY); path.lineTo(baseX + r, baseY); path.lineTo(topX + r * 0.8f, topY); path.lineTo(topX - r * 0.8f, topY); path.close()
            drawPath(path, Brush.verticalGradient(listOf(ink.beam.copy(alpha = 0.5f * glow * fade), ink.beam.copy(alpha = 0f)), startY = baseY, endY = topY))
        }
        drawLine(
            Brush.verticalGradient(listOf(lerp(ink.beam, ink.core, 0.5f).copy(alpha = fade), ink.beam.copy(alpha = 0f)), startY = baseY, endY = topY),
            Offset(baseX, baseY), Offset(topX, topY), (r * 0.5f).coerceAtLeast(1.5f), StrokeCap.Round
        )
    }

    /** The dot where the route starts, with a soft halo. */
    private fun DrawScope.drawPuck() {
        project(PUCK_X, 2f, PUCK_Z)
        if (pz < 2f) return
        val r = focal * 2f / pz
        val c = Offset(px, py)
        if (!plain) drawCircle(Brush.radialGradient(listOf(ink.routeA.copy(alpha = 0.5f * glow), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
        drawCircle(ink.puck, r, c)
        if (light) drawCircle(Color.White, r * 0.45f, c)
    }

    /** The pulse's band, reused from frame to frame (drawn on the main thread only). */
    private val pulsePath = Path()

    /**
     * The light running along the route, its head at sample [head], fading
     * over [PULSE_TAIL] samples behind it in short bands that meet without
     * overlapping; left out where a block hides the route.
     */
    fun drawPulse(scope: DrawScope, head: Int) = with(scope) {
        if (head < 1) return@with
        val end = min(head, samples - 1)
        val start = max(0, head - PULSE_TAIL)
        val core = if (light) Color.White else ink.core
        var i = start
        while (i < end) {
            if (!sSeen[i] || sw[i] <= 0f) { i++; continue }
            var j = i
            while (j < end && j - i < 3 && sSeen[j + 1]) j++
            if (j == i) { i++; continue }
            val a = (1f - (head - j).toFloat() / PULSE_TAIL).coerceIn(0.1f, 1f)
            if (glow > 0f) {
                ribbon(pulsePath, i, j, 3.2f)
                drawPath(pulsePath, routeColor(sAlong[i]).copy(alpha = 0.3f * a * glow))
            }
            ribbon(pulsePath, i, j, 0.9f)
            drawPath(pulsePath, core.copy(alpha = 0.9f * a))
            i = j
        }
    }

    fun matches(width: Int, height: Int, light: Boolean, glow: Float): Boolean =
        this.width == width && this.height == height && this.light == light && this.glow == glow
}

/**
 * The scenes in use (the page's, and the same one the window frame reads for
 * its corners), so a size change does not draw the city twice.
 */
internal object SpatialCity {
    private val scenes = ArrayList<SpatialCityScene>()

    /** Where the page background lies on screen, in px, for the window frame. */
    @Volatile var page: IntRect? = null

    fun scene(width: Int, height: Int, light: Boolean, glow: Float): SpatialCityScene = synchronized(scenes) {
        val found = scenes.firstOrNull { it.matches(width, height, light, glow) }
        if (found != null) {
            scenes.remove(found)
            scenes.add(0, found)
            return found
        }
        val made = SpatialCityScene(width, height, light, glow)
        scenes.add(0, made)
        while (scenes.size > MAX_SCENES) scenes.removeAt(scenes.lastIndex)
        made
    }

    /** The scene for these values if it was made already, without making it. */
    fun existing(width: Int, height: Int, light: Boolean, glow: Float): SpatialCityScene? = synchronized(scenes) {
        scenes.firstOrNull { it.matches(width, height, light, glow) }
    }

    private const val MAX_SCENES = 2
}
