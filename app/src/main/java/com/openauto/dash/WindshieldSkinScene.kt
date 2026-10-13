package com.openauto.dash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Windshield skin, the scene behind everything: a road drawn in perspective
 * at dusk under an indigo sky, a thin warm glow and a dark skyline along the
 * horizon, street lights with soft halos on both sides, lane edges and a
 * dashed centre line, and the driver's car from behind in the chase spot,
 * tail lamps glowing red on the asphalt. While a route is guided the road
 * bends toward the side of the next turn (more sharply as it nears) and
 * glowing AR chevrons lie on the lane, pointing into the turn. By day the
 * same road under a pale sky on grey asphalt.
 *
 * The road is drawn, not filmed: the unit has no front camera. Everything
 * still is recorded once into a layer, rebuilt only when the turn's side or
 * distance bucket, the car or the look changes. Motion: the chevrons flow
 * toward the turn three times when a new turn arrives, then hold still; the
 * centre dashes drift slowly while parked and stand still while driving.
 */

/** The drawn car's height over its width (a 100 x 96 box). */
internal const val WS_CAR_ASPECT_INV = 0.96f

/** One run of the chevrons toward the turn, and how many runs a new turn gets. */
private const val FLOW_RUN_MS = 900
private const val FLOW_RUNS = 3

/** The centre dashes' drift while parked: one dash period in this many ms. */
private const val DASH_LOOP_MS = 2_800

/** How far ahead the next turn is, coarsely: what the road's bend follows. */
internal enum class WsTurnNear { NONE, FAR, MID, NEAR }

/** What the road's shape depends on: guiding or not, the turn's side (-1 left, 1 right, 0 ahead) and how near it is. */
@Immutable
internal data class WsRoadShape(val guiding: Boolean, val side: Int, val near: WsTurnNear)

private val NO_ROUTE = WsRoadShape(false, 0, WsTurnNear.NONE)

/** The distance to the next manoeuvre in metres, or null when the navigation gives none we can read. */
internal fun turnMetres(nav: NavState): Double? {
    val (value, unit) = nav.distanceParts
    if (unit.isEmpty()) return null
    val v = value.replace(',', '.').toDoubleOrNull() ?: return null
    return when (unit) {
        "m" -> v
        "km" -> v * 1000.0
        "mi" -> v * 1609.344
        "ft" -> v * 0.3048
        "yd" -> v * 0.9144
        else -> null
    }
}

/** The road's shape for [nav]: straight with no route, bending toward the turn's side, sharper as it nears. */
internal fun roadShapeOf(nav: NavState): WsRoadShape {
    if (!nav.active) return NO_ROUTE
    val side = nav.turnSide().sign
    val metres = turnMetres(nav)
    val near = when {
        metres == null -> WsTurnNear.MID
        metres <= 200.0 -> WsTurnNear.NEAR
        metres <= 700.0 -> WsTurnNear.MID
        else -> WsTurnNear.FAR
    }
    return WsRoadShape(true, side, if (side == 0) WsTurnNear.NONE else near)
}

/**
 * The page: the road scene filling the screen behind the pages and the band.
 * The route, the car and the motion are followed as [State]s read only while
 * drawing, so the dashboard around it never recomposes for them.
 */
@Composable
internal fun windshieldBackground(): Modifier {
    val light = DashColors.Light
    val effects = DashColors.Effects
    val lit = DashColors.Accent
    val nav = NavDirections.state.collectAsState()
    val road = remember(nav) { derivedStateOf { roadShapeOf(nav.value) } }
    // A new manoeuvre is a new instruction; the distance counting down is not.
    val turn = remember(nav) { derivedStateOf { nav.value.takeIf { it.active }?.let { "${it.turnSide()}|${it.instruction}" } } }
    val look = MyCarLook.shown.collectAsState()
    val flow = rememberChevronFlow(turn, effects)
    val dashes = rememberLoop(DASH_LOOP_MS)
    return remember(light, effects, lit, road, look, flow, dashes) { wsScene(light, effects, lit, road, look, flow, dashes) }
}

/**
 * 0 while the chevrons hold still; while a new turn's arrows flow, the runs
 * done so far (0 up to [FLOW_RUNS]), stepped at 30 fps (15 when reduced).
 * With effects off they never move.
 */
@Composable
private fun rememberChevronFlow(turn: State<String?>, effects: DashEffects): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(turn, effects) {
        phase.floatValue = 0f
        if (effects == DashEffects.NONE) return@LaunchedEffect
        val step = if (effects == DashEffects.REDUCED) 66L else 33L
        snapshotFlow { turn.value }.distinctUntilChanged().collectLatest { key ->
            phase.floatValue = 0f
            if (key == null) return@collectLatest
            val start = withFrameMillis { it }
            val total = FLOW_RUN_MS.toLong() * FLOW_RUNS
            while (true) {
                val t = withFrameMillis { it } - start
                if (t >= total) break
                phase.floatValue = t.toFloat() / FLOW_RUN_MS
                delay(step)
            }
            phase.floatValue = 0f
        }
    }
    return phase
}

/**
 * The perspective of the scene on a [w] x [h] page: depth z is 1 at the
 * car's wheels, larger ahead; a point at depth z stands span / z under the
 * horizon, and the road there is [hw0] / z wide each side of its middle,
 * which leaves the straight line toward the turn's side past depth [zt].
 */
private class WsRoadGeom(val w: Float, val h: Float, density: Float, shape: WsRoadShape) {
    val vertical = h > w
    val carW = min(w, h) * 0.34f
    val carBottom = h - (WS_BAR.value + 10f) * density
    val span = h * (if (vertical) 0.46f else 0.56f)
    val hy = carBottom - span
    val cx = w / 2f
    val hw0 = carW * 0.86f

    private val side = shape.side.toFloat()
    val zt = when (shape.near) {
        WsTurnNear.NEAR -> 3.2f
        WsTurnNear.MID -> 6f
        WsTurnNear.FAR -> 11f
        WsTurnNear.NONE -> Float.MAX_VALUE
    }
    private val amp = when (shape.near) {
        WsTurnNear.NEAR -> 0.4f
        WsTurnNear.MID -> 0.3f
        WsTurnNear.FAR -> 0.18f
        WsTurnNear.NONE -> 0f
    }

    /** The bend's strength: at depth [Z_REF] the road's middle stands [amp] of the width aside. */
    private val k = if (amp > 0f) amp * min(w, h * 1.8f) * Z_REF / ((Z_REF - zt) * (Z_REF - zt)) else 0f

    fun y(z: Float): Float = hy + span / z
    fun half(z: Float): Float = hw0 / z
    fun mid(z: Float): Float = if (z <= zt) cx else cx + side * k * (z - zt) * (z - zt) / z
    fun x(z: Float, u: Float): Float = mid(z) + u * half(z)

    /** How near [z] stands, 1 at the foot of the page and 0 at the horizon. */
    fun nearness(z: Float): Float = ((y(z) - hy) / span).coerceIn(0f, 1f)

    /** A strip of the road between the lateral positions [u0] and [u1] (-1 the left edge, 1 the right) from [z0] to [z1]. */
    fun strip(u0: Float, u1: Float, z0: Float = Z_MIN, z1: Float = Z_MAX, steps: Int = 56): Path = Path().apply {
        for (i in 0..steps) {
            val z = depthAt(z0, z1, i, steps)
            if (i == 0) moveTo(x(z, u0), y(z)) else lineTo(x(z, u0), y(z))
        }
        for (i in steps downTo 0) {
            val z = depthAt(z0, z1, i, steps)
            lineTo(x(z, u1), y(z))
        }
        close()
    }

    companion object {
        const val Z_MIN = 0.72f
        const val Z_MAX = 60f
        const val Z_REF = 36f

        /** Depths spaced evenly in perspective, so the near road gets as many points as the far. */
        fun depthAt(z0: Float, z1: Float, i: Int, steps: Int): Float = z0 * (z1 / z0).pow(i.toFloat() / steps)
    }
}

/** The scene's own inks at night and by day. */
private class WsSceneInk(light: Boolean) {
    val sky = if (light) listOf(Color(0xFF8FB8DF), Color(0xFFBCD5EC), Color(0xFFE6EFF6))
    else listOf(Color(0xFF04050D), Color(0xFF0B0E2A), Color(0xFF2A1846), Color(0xFF8A3B45))
    val skyStops = if (light) floatArrayOf(0f, 0.6f, 1f) else floatArrayOf(0f, 0.55f, 0.86f, 1f)
    val ground = if (light) listOf(Color(0xFFD3D8DC), Color(0xFFBAC1C7), Color(0xFFA4ACB3))
    else listOf(Color(0xFF2A1424), Color(0xFF110C18), Color(0xFF07070C))
    val haze = if (light) Color(0xB3FFFFFF) else Color(0x998A3B45)
    val skyline = if (light) Color(0x669AA6B2) else Color(0xFF0B0912)
    val windows = if (light) Color.Transparent else Color(0xFFFFC98A)
    val asphaltFar = if (light) Color(0xFF8A929A) else Color(0xFF15121D)
    val asphaltNear = if (light) Color(0xFF5E666F) else Color(0xFF0A0B10)
    val edge = if (light) Color(0xFFFFFFFF) else Color(0xFFBFC8D8)
    val edgeAlpha = if (light) 0.95f else 0.55f
    val dash = if (light) Color(0xFFFFFFFF) else Color(0xFFD7DDE8)
    val post = if (light) Color(0xFF59606A) else Color(0xFF1B1B25)
    val lampCore = if (light) Color(0xFF8C939C) else Color(0xFFFFF1DA)
    val lampGlow = Color(0xFFFFC882)
}

private fun wsScene(
    light: Boolean,
    effects: DashEffects,
    lit: Color,
    road: State<WsRoadShape>,
    look: State<CarLook?>,
    flow: State<Float>,
    dashes: State<Float>
): Modifier = Modifier.drawWithCache {
    // Read here, so the cache is rebuilt when the road's shape or the car changes, and only then.
    val shape = road.value
    val view = look.value?.back
    val g = WsRoadGeom(size.width, size.height, density, shape)
    val w = g.w
    val h = g.h
    val hy = g.hy
    val glowK = effects.scale * if (light) 0.35f else 1f
    val ink = WsSceneInk(light)
    val plain = effects == DashEffects.NONE

    // --- The still scene, recorded once ---
    val sky = Brush.verticalGradient(*ink.skyStops.zip(ink.sky).toTypedArray(), startY = 0f, endY = hy)
    val ground = Brush.verticalGradient(0f to ink.ground[0], 0.06f to ink.ground[1], 1f to ink.ground[2], startY = hy, endY = h)
    val hazeC = Offset(g.cx, hy)
    val hazeRx = w * 0.75f
    val hazeRy = g.span * 0.16f
    val haze = Brush.radialGradient(listOf(ink.haze, Color.Transparent), hazeC, hazeRx)

    // A skyline along the horizon and, at night, its lit windows and the stars; placed by a fixed seed, so it never jumps.
    val rnd = WsLcg(7)
    val skyline = Path()
    val windows = ArrayList<Offset>()
    run {
        var x = -8f * density
        while (x < w) {
            val bw = (10f + rnd.next() * 34f) * density
            val far = 1f - (kotlin.math.abs(x + bw / 2f - g.cx) / (w / 2f)).coerceIn(0f, 1f) * 0.4f
            val bh = (4f + rnd.next() * rnd.next() * 34f) * density * far
            skyline.addRect(Rect(x, hy - bh, x + bw, hy + 1f))
            if (!light) {
                var wy = hy - bh + 3f * density
                while (wy < hy - 2f * density) {
                    if (rnd.next() < 0.35f) windows += Offset(x + bw * (0.2f + rnd.next() * 0.6f), wy)
                    wy += 4f * density
                }
            }
            x += bw + rnd.next() * 6f * density
        }
    }
    val stars = if (!light) List(46) { Offset(rnd.next() * w, rnd.next() * rnd.next() * hy * 0.8f) } else emptyList()
    val cityGlows = if (!light && !plain) List(6) { i ->
        val c = Offset(w * (0.08f + i * 0.17f + rnd.next() * 0.06f), hy - 2f * density)
        val r = (50f + rnd.next() * 90f) * density
        Triple(c, r, Brush.radialGradient(listOf(Color(0xFFFF9A5A).copy(alpha = 0.16f * glowK), Color.Transparent), c, r))
    } else emptyList()

    val roadPath = g.strip(-1f, 1f)
    val roadFill = Brush.verticalGradient(
        0f to ink.asphaltFar.copy(alpha = 0f), 0.05f to ink.asphaltFar, 1f to ink.asphaltNear, startY = hy, endY = h
    )
    val edges = Path().apply {
        addPath(g.strip(-0.97f, -0.9f))
        addPath(g.strip(0.9f, 0.97f))
    }
    val edgeInk = Brush.verticalGradient(
        0f to ink.edge.copy(alpha = 0f), 0.08f to ink.edge.copy(alpha = ink.edgeAlpha * 0.35f), 1f to ink.edge.copy(alpha = ink.edgeAlpha),
        startY = hy, endY = h
    )
    // The mockup's faint cyan light down the lane's middle.
    val laneGlow = if (!plain && !light) g.strip(-0.38f, 0.38f) to Brush.verticalGradient(
        0f to lit.copy(alpha = 0f), 0.2f to lit.copy(alpha = 0.035f * glowK), 1f to lit.copy(alpha = 0.07f * glowK), startY = hy, endY = h
    ) else null

    // Street lights both sides, far ones first so the near ones stand in front.
    val posts = ArrayList<FloatArray>()
    var zp = 1.12f
    while (zp < 34f) {
        for (u in floatArrayOf(-1.9f, 1.9f)) {
            val bx = g.x(zp, u)
            val by = g.y(zp)
            val top = by - g.span * 0.8f / zp
            posts += floatArrayOf(bx, by, top, zp)
        }
        zp += 1.7f
    }
    posts.reverse()
    val postW = g.carW * 0.032f
    val lampGlows = if (!light && glowK > 0f) posts.map { p ->
        val r = max(6f * density, g.carW * 0.26f / p[3])
        Brush.radialGradient(listOf(ink.lampGlow.copy(alpha = 0.55f * glowK), ink.lampGlow.copy(alpha = 0.12f * glowK), Color.Transparent), Offset(p[0], p[2]), r) to r
    } else null

    val scenery = obtainGraphicsLayer()
    scenery.record {
        drawRect(sky, size = Size(w, hy))
        if (stars.isNotEmpty()) drawPoints(stars, PointMode.Points, Color.White.copy(alpha = 0.45f), 1.6f * density, StrokeCap.Round)
        drawRect(ground, Offset(0f, hy), Size(w, h - hy))
        if (!plain) scale(1f, hazeRy / hazeRx, hazeC) { drawCircle(haze, hazeRx, hazeC) }
        for ((c, r, b) in cityGlows) scale(1f, 0.35f, c) { drawCircle(b, r, c) }
        drawPath(skyline, ink.skyline)
        if (windows.isNotEmpty()) drawPoints(windows, PointMode.Points, ink.windows.copy(alpha = 0.55f), 1.4f * density, StrokeCap.Square)
        if (!light) drawLine(Color(0xFFC76A5E).copy(alpha = 0.55f), Offset(0f, hy + 0.5f), Offset(w, hy + 0.5f), density)
        drawPath(roadPath, roadFill)
        if (laneGlow != null) drawPath(laneGlow.first, laneGlow.second)
        drawPath(edges, edgeInk)
        posts.forEachIndexed { i, p ->
            val z = p[3]
            val near = g.nearness(z)
            drawLine(ink.post, Offset(p[0], p[1]), Offset(p[0], p[2]), max(density, postW / z))
            // The lamp's arm reaching over the road, and its head.
            val arm = g.carW * 0.12f / z * (if (p[0] < g.mid(z)) 1f else -1f)
            drawLine(ink.post, Offset(p[0], p[2]), Offset(p[0] + arm, p[2]), max(density * 0.8f, postW * 0.6f / z))
            lampGlows?.get(i)?.let { (b, r) -> drawCircle(b, r, Offset(p[0] + arm, p[2])) }
            drawCircle(ink.lampCore.copy(alpha = 0.5f + 0.5f * near), max(1.1f * density, g.carW * 0.02f / z), Offset(p[0] + arm, p[2]))
        }
    }

    // --- The car, its shadow and its lamps, also recorded once ---
    val carRect: Rect
    val lamps: List<Offset>
    if (view != null) {
        var cw = g.carW
        var ch = cw / view.aspect
        if (ch > g.carW * 1.2f) {
            ch = g.carW * 1.2f
            cw = ch * view.aspect
        }
        carRect = Rect(g.cx - cw / 2f, g.carBottom - ch, g.cx + cw / 2f, g.carBottom)
        lamps = view.lights.ifEmpty { listOf(Offset(0.14f, 0.42f), Offset(0.86f, 0.42f)) }.map { carRect.at(it) }
    } else {
        val cw = g.carW
        carRect = Rect(g.cx - cw / 2f, g.carBottom - cw * WS_CAR_ASPECT_INV, g.cx + cw / 2f, g.carBottom)
        lamps = WsDrawnCar.lamps.map { carRect.at(it) }
    }
    val red = Color(0xFFFF1A2A)
    val roadRedC = Offset(g.cx, g.carBottom - carRect.height * 0.08f)
    val roadRedR = carRect.width * 1.0f
    val roadRed = if (glowK > 0f) Brush.radialGradient(listOf(red.copy(alpha = 0.24f * glowK), Color.Transparent), roadRedC, roadRedR) else null
    val shadowC = Offset(g.cx, g.carBottom - carRect.height * 0.01f)
    val shadowRx = carRect.width * 0.6f
    val shadow = Brush.radialGradient(0f to Color.Black.copy(alpha = if (light) 0.4f else 0.85f), 0.75f to Color.Transparent, center = shadowC, radius = shadowRx)
    val lampR = carRect.width * 0.2f
    val lampGlow = if (glowK > 0f) lamps.map { Brush.radialGradient(listOf(red.copy(alpha = 0.6f * glowK), red.copy(alpha = 0.15f * glowK), Color.Transparent), it, lampR) } else emptyList()
    val ink2 = lit
    val car = obtainGraphicsLayer()
    car.record {
        if (roadRed != null) scale(1f, 0.32f, roadRedC) { drawCircle(roadRed, roadRedR, roadRedC) }
        scale(1f, 0.13f, shadowC) { drawCircle(shadow, shadowRx, shadowC) }
        if (view != null) drawCarView(view, CarLookStyle.PHOTO, carRect, ink2, Color(0xFFFFB547))
        else drawWsDrawnCar(carRect, light, 0f)
        lampGlow.forEachIndexed { i, b -> drawCircle(b, lampR, lamps[i]) }
    }

    // --- What moves: the dashes and the chevrons, drawn each frame without allocating ---
    val dashGap = 0.62f
    val dashLen = 0.26f
    val dashW = g.carW * 0.045f
    val chevStart = 2.45f
    val chevGap = 0.78f
    val chevEnd = if (shape.side != 0) min(g.zt + 4.5f, 18f) else 9.5f
    val chevCount = ((chevEnd - chevStart) / chevGap).toInt() + 1
    val chevInk = if (light) lit else Color(0xFF6FE0FF)
    val chevW = g.carW * 0.05f
    val chevWing = 0.5f
    val chevDepth = 0.34f
    onDrawBehind {
        drawLayer(scenery)

        // Centre dashes: toward the arrows only while guiding, drifting toward the car while parked.
        val dashTo = if (shape.guiding) chevStart else 26f
        val phase = dashes.value
        var k = 0
        while (true) {
            val z0 = WsRoadGeom.Z_MIN + (k - phase) * dashGap
            k++
            if (z0 > dashTo) break
            val z1 = z0 + dashLen
            if (z1 <= WsRoadGeom.Z_MIN) continue
            val a = max(z0, WsRoadGeom.Z_MIN)
            val b = min(z1, dashTo)
            if (b <= a) continue
            val zm = (a + b) / 2f
            val near = g.nearness(zm)
            drawLine(
                ink.dash.copy(alpha = (if (light) 0.95f else 0.75f) * sqrt(near)),
                Offset(g.mid(a), g.y(a)), Offset(g.mid(b), g.y(b)), dashW / zm
            )
        }

        // The AR chevrons on the lane, pointing into the turn.
        if (shape.guiding) {
            val f = flow.value
            val moving = f > 0f
            val shift = if (moving) (f % 1f) * chevGap else 0f
            for (i in 0 until chevCount) {
                val z = chevStart + i * chevGap + shift
                if (z > chevEnd + chevGap) break
                val tipZ = z + chevDepth / 2f
                val backZ = z - chevDepth / 2f
                // Fading in from behind the car while flowing, and out into the distance.
                val fadeIn = if (moving && i == 0) (f % 1f) else 1f
                val fadeOut = ((chevEnd + chevGap - z) / (chevGap * 2.5f)).coerceIn(0f, 1f)
                val alpha = (if (light) 1f else 0.92f) * fadeIn * fadeOut
                if (alpha <= 0.02f) continue
                val tip = Offset(g.mid(tipZ), g.y(tipZ))
                val left = Offset(g.x(backZ, -chevWing), g.y(backZ))
                val right = Offset(g.x(backZ, chevWing), g.y(backZ))
                val sw = chevW / z
                if (glowK > 0f) {
                    val glow = chevInk.copy(alpha = 0.22f * glowK * alpha)
                    drawLine(glow, left, tip, sw * 3.4f, StrokeCap.Round)
                    drawLine(glow, right, tip, sw * 3.4f, StrokeCap.Round)
                }
                val core = chevInk.copy(alpha = alpha)
                drawLine(core, left, tip, sw, StrokeCap.Round)
                drawLine(core, right, tip, sw, StrokeCap.Round)
            }
        }

        drawLayer(car)
    }
}

/** A small fixed-seed random source, so the skyline and the stars stand where they stood. */
private class WsLcg(seed: Int) {
    private var s = seed.toLong()
    fun next(): Float {
        s = (s * 1103515245L + 12345L) and 0x7FFFFFFFL
        return (s % 10_000L).toFloat() / 10_000f
    }
}

// --- The drawn car, from behind -----------------------------------------------------------

/**
 * A big MPV from behind, for when the driver has not imported their own
 * car's back view: a tall rounded body in aluminium grey, a wide rear window
 * under a roof spoiler with a third brake light, tail lamps wrapping round
 * the window's lower corners, the plate, a dark bumper and the tyres under
 * it. Built once in a 100 x 96 box and scaled.
 */
private object WsDrawnCar {
    val body = Path().apply {
        moveTo(7f, 88f)
        lineTo(5.2f, 64f)
        cubicTo(4.6f, 52f, 6.2f, 38f, 10f, 26f)
        cubicTo(13f, 16f, 20f, 10.5f, 30f, 9.5f)
        lineTo(70f, 9.5f)
        cubicTo(80f, 10.5f, 87f, 16f, 90f, 26f)
        cubicTo(93.8f, 38f, 95.4f, 52f, 94.8f, 64f)
        lineTo(93f, 88f)
        close()
    }
    val mirrors = Path().apply {
        moveTo(6f, 39.5f); lineTo(1f, 41f); cubicTo(0.2f, 43.5f, 0.6f, 46f, 2.2f, 46.8f); lineTo(6.2f, 46.2f); close()
        moveTo(94f, 39.5f); lineTo(99f, 41f); cubicTo(99.8f, 43.5f, 99.4f, 46f, 97.8f, 46.8f); lineTo(93.8f, 46.2f); close()
    }
    val glass = Path().apply {
        moveTo(15.5f, 49f)
        lineTo(13.4f, 33f)
        cubicTo(14.4f, 22.5f, 20f, 16f, 29.5f, 15.2f)
        lineTo(70.5f, 15.2f)
        cubicTo(80f, 16f, 85.6f, 22.5f, 86.6f, 33f)
        lineTo(84.5f, 49f)
        cubicTo(70f, 50.5f, 30f, 50.5f, 15.5f, 49f)
        close()
    }
    val glare = Path().apply {
        moveTo(31f, 16.6f); lineTo(43f, 16.6f); lineTo(35f, 48.6f); lineTo(23f, 48.3f); close()
    }
    val lampsPath = Path().apply {
        moveTo(5f, 47.5f); cubicTo(10f, 48.2f, 16f, 49.6f, 21.5f, 51.8f); lineTo(20.6f, 58.5f); cubicTo(15f, 59.2f, 9.5f, 60.2f, 5.4f, 61.2f); close()
        moveTo(95f, 47.5f); cubicTo(90f, 48.2f, 84f, 49.6f, 78.5f, 51.8f); lineTo(79.4f, 58.5f); cubicTo(85f, 59.2f, 90.5f, 60.2f, 94.6f, 61.2f); close()
    }
    val bumper = Path().apply {
        moveTo(5.6f, 68f); lineTo(94.4f, 68f); lineTo(93.4f, 86f)
        cubicTo(93.2f, 88f, 92f, 89f, 90f, 89f); lineTo(10f, 89f)
        cubicTo(8f, 89f, 6.8f, 88f, 6.6f, 86f); close()
    }
    val shoulder = Path().apply {
        moveTo(8f, 51f)
        cubicTo(30f, 53f, 70f, 53f, 92f, 51f)
    }

    /** The lamps' middles, as fractions of the box, for their glow. */
    val lamps = listOf(Offset(0.13f, 0.56f), Offset(0.87f, 0.56f))

    val paint = Brush.verticalGradient(
        0f to Color(0xFFD7DDE4), 0.25f to Color(0xFFAEB7C1), 0.5f to Color(0xFF8E98A4), 0.75f to Color(0xFF5C6570), 1f to Color(0xFF30363E),
        startY = 9f, endY = 89f
    )
    val sides = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.32f), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.32f),
        startX = 4f, endX = 96f
    )
    val glassFill = Brush.verticalGradient(listOf(Color(0xFF2B3646), Color(0xFF0A0E15)), startY = 15f, endY = 50f)
    val lampFill = Brush.verticalGradient(listOf(Color(0xFFFF5560), Color(0xFFB0101C)), startY = 47f, endY = 61f)
    val bumperFill = Brush.verticalGradient(listOf(Color(0xFF7A838E), Color(0xFF3A4048)), startY = 68f, endY = 89f)
}

/** The drawn MPV from behind in [dst]; [glow] adds its tail lamps' red halo (the stamp; the page draws its own). */
internal fun DrawScope.drawWsDrawnCar(dst: Rect, light: Boolean, glow: Float) {
    val s = dst.width / 100f
    withTransform({
        translate(dst.left, dst.top + (dst.height - 96f * s) / 2f)
        scale(s, s, Offset.Zero)
    }) {
        // Tyres under the body.
        drawRoundRect(Color(0xFF08090B), Offset(9f, 82f), Size(15f, 13.5f), CornerRadius(2.5f))
        drawRoundRect(Color(0xFF08090B), Offset(76f, 82f), Size(15f, 13.5f), CornerRadius(2.5f))
        drawPath(WsDrawnCar.mirrors, Color(0xFF59626D))
        drawPath(WsDrawnCar.body, WsDrawnCar.paint)
        drawPath(WsDrawnCar.body, WsDrawnCar.sides)
        // Roof spoiler and its third brake light.
        drawRoundRect(Color(0xFF20252D), Offset(24f, 7.2f), Size(52f, 3.8f), CornerRadius(1.9f))
        drawRoundRect(Color(0xFFFF3B47), Offset(43f, 8.4f), Size(14f, 1.3f), CornerRadius(0.6f))
        drawPath(WsDrawnCar.glass, WsDrawnCar.glassFill)
        drawPath(WsDrawnCar.glare, Color.White.copy(alpha = 0.07f))
        drawLine(Color(0xFF05070A), Offset(50f, 47.5f), Offset(37f, 41f), 0.9f, StrokeCap.Round)
        drawPath(WsDrawnCar.shoulder, Color.White.copy(alpha = if (light) 0.55f else 0.4f), style = Stroke(0.6f, cap = StrokeCap.Round))
        drawPath(WsDrawnCar.lampsPath, WsDrawnCar.lampFill)
        drawLine(Color(0xFFFFC9CC), Offset(7.2f, 51.6f), Offset(18.6f, 54.2f), 0.8f, StrokeCap.Round)
        drawLine(Color(0xFFFFC9CC), Offset(92.8f, 51.6f), Offset(81.4f, 54.2f), 0.8f, StrokeCap.Round)
        // The tailgate's handle strip and the plate.
        drawRoundRect(Color(0xFFE2E7ED), Offset(40f, 57.4f), Size(20f, 1.4f), CornerRadius(0.7f))
        drawPath(WsDrawnCar.bumper, WsDrawnCar.bumperFill)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(6.5f, 68.4f), Offset(93.5f, 68.4f), 0.5f)
        drawRoundRect(Color(0xFFE9EDF2), Offset(38.5f, 61.6f), Size(23f, 6.4f), CornerRadius(0.8f))
        drawRoundRect(Color(0xFF2A2F36), Offset(38.5f, 61.6f), Size(23f, 6.4f), CornerRadius(0.8f), style = Stroke(0.4f))
        drawRoundRect(Color(0xFF15191F), Offset(14f, 82f), Size(72f, 5.6f), CornerRadius(1.6f))
        drawRoundRect(Color(0xFFB0101C), Offset(9f, 75.5f), Size(6f, 1.6f), CornerRadius(0.8f))
        drawRoundRect(Color(0xFFB0101C), Offset(85f, 75.5f), Size(6f, 1.6f), CornerRadius(0.8f))
        if (glow > 0f) {
            for (c in WsDrawnCar.lamps) {
                val p = Offset(c.x * 100f, c.y * 96f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF1A2A).copy(alpha = 0.6f * glow), Color.Transparent), p, 18f), 18f, p)
            }
        }
    }
}
