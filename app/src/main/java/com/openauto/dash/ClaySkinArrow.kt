package com.openauto.dash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Clay skin, the puffy 3D turn arrow (the design's three.js arrow, drawn
 * flat): a rounded arrow outline, extruded toward the lower right in a few
 * darker copies for its side, its face a light-to-deep purple, a catch of
 * light inside its top-left edges and a shade inside its bottom-right ones,
 * a glossy spot, and its soft contact shadow on the card. It leans toward the
 * turn. Three clay balls float around it. Everything is built once per size
 * ([ClayArrowArt]); a frame only moves it ([drawTurn]).
 */

/** Corner radius per vertex of the arrows below, in the same 100 unit box. */
private const val TIP = 6f

/**
 * The arrow for a left turn in a 100 x 100 box (y down), as x, y, corner
 * radius triples: up the stem, round the outer corner, out along the arm,
 * the head pointing left.
 */
private val LEFT_ARROW = floatArrayOf(
    54f, 96f, TIP,
    84f, 96f, TIP,
    84f, 26f, 24f,
    42f, 26f, 3f,
    42f, 5f, TIP,
    4f, 40f, 7f,
    42f, 75f, TIP,
    42f, 54f, 3f,
    54f, 54f, 10f
)

/** Straight on: a stem and a broad head. */
private val AHEAD_ARROW = floatArrayOf(
    35f, 96f, TIP,
    65f, 96f, TIP,
    65f, 47f, 3f,
    92f, 47f, TIP,
    50f, 4f, 8f,
    8f, 47f, TIP,
    35f, 47f, 3f
)

/** Where the glossy spot sits on each arrow, in its box. */
private fun glossOf(side: TurnSide): Offset = when (side) {
    TurnSide.LEFT -> Offset(70f, 38f)
    TurnSide.RIGHT -> Offset(30f, 38f)
    TurnSide.AHEAD -> Offset(44f, 34f)
}

/**
 * A closed outline through [points] (x, y, radius triples in a 100 box),
 * each corner rounded, scaled by [k] and moved by [ox], [oy]; mirrored left
 * to right when [mirror].
 */
private fun roundedOutline(points: FloatArray, k: Float, ox: Float, oy: Float, mirror: Boolean): Path {
    val n = points.size / 3
    fun x(i: Int): Float { val v = points[(i % n) * 3]; return (if (mirror) 100f - v else v) * k + ox }
    fun y(i: Int): Float = points[(i % n) * 3 + 1] * k + oy
    fun r(i: Int): Float = points[(i % n) * 3 + 2] * k
    val path = Path()
    for (i in 0 until n) {
        val vx = x(i)
        val vy = y(i)
        val px = x(i + n - 1)
        val py = y(i + n - 1)
        val nx = x(i + 1)
        val ny = y(i + 1)
        val lp = hypot(px - vx, py - vy).coerceAtLeast(0.001f)
        val ln = hypot(nx - vx, ny - vy).coerceAtLeast(0.001f)
        val rr = min(r(i), min(lp, ln) / 2f)
        val ax = vx + (px - vx) / lp * rr
        val ay = vy + (py - vy) / lp * rr
        val bx = vx + (nx - vx) / ln * rr
        val by = vy + (ny - vy) / ln * rr
        if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
        path.quadraticTo(vx, vy, bx, by)
    }
    path.close()
    return path
}

/** How many copies make the arrow's side. */
private const val SIDE_STEPS = 8

/**
 * The arrow's drawing for a square box [s] px wide, built once: its outline,
 * the side's shades, the face's gradient, the edge lights and the contact
 * shadow. [plain] (effects off) keeps the extruded shape in flat colours.
 */
internal class ClayArrowArt(val side: TurnSide, val s: Float, purple: Color, light: Boolean, val plain: Boolean) {
    private val k = s / 100f * 0.8f
    private val origin = s * 0.07f
    val outline: Path = roundedOutline(if (side == TurnSide.AHEAD) AHEAD_ARROW else LEFT_ARROW, k, origin, origin, side == TurnSide.RIGHT)
    val pivot = Offset(origin + 50f * k, origin + 50f * k)
    val lean = when (side) {
        TurnSide.LEFT -> -8f
        TurnSide.RIGHT -> 8f
        TurnSide.AHEAD -> 0f
    }
    val depth = Offset(s * 0.05f, s * 0.062f)
    val sides: Array<Color> = Array(SIDE_STEPS) { i ->
        // i = 0 is the farthest copy: the deepest shade.
        val f = i / (SIDE_STEPS - 1f)
        val deep = lerp(purple, Color(0xFF1E1060), if (light) 0.55f else 0.62f)
        val near = lerp(purple, Color(0xFF1E1060), if (light) 0.25f else 0.32f)
        lerp(deep, near, f)
    }
    val face: Brush = if (plain) Brush.linearGradient(listOf(purple, purple)) else Brush.linearGradient(
        0f to lerp(purple, Color.White, if (light) 0.5f else 0.38f),
        0.55f to purple,
        1f to lerp(purple, Color.Black, 0.08f),
        start = Offset(origin, origin), end = Offset(origin + 100f * k, origin + 100f * k)
    )
    val hiWide = Stroke(s * 0.05f, join = StrokeJoin.Round)
    val hiThin = Stroke(s * 0.018f, join = StrokeJoin.Round)
    val loWide = Stroke(s * 0.06f, join = StrokeJoin.Round)
    val hiWideOff = s * 0.02f
    val hiThinOff = s * 0.011f
    val loOff = s * 0.024f
    val hiInk = Color.White.copy(alpha = if (light) 0.42f else 0.3f)
    val hiInkThin = Color.White.copy(alpha = if (light) 0.75f else 0.5f)
    val loInk = Color(0xFF1E1060).copy(alpha = 0.28f)
    private val g = glossOf(side)
    val glossC = Offset(g.x * k + origin, g.y * k + origin)
    val glossR = s * 0.09f
    val gloss = Brush.radialGradient(listOf(Color.White.copy(alpha = if (light) 0.7f else 0.45f), Color.Transparent), glossC, glossR)
    val groundC = Offset(pivot.x + s * 0.03f, origin + 100f * k + s * 0.05f)
    val groundR = s * 0.36f
    val ground = Brush.radialGradient(
        listOf((if (light) Color(0xFF7A60BE) else Color.Black).copy(alpha = if (light) 0.32f else 0.55f), Color.Transparent),
        groundC, groundR
    )
}

/**
 * Draws [art] in a box at [topLeft]: its contact shadow (smaller as it
 * lifts), then the arrow [lift] px up and [wobble] degrees off its lean.
 */
internal fun DrawScope.drawTurn(art: ClayArrowArt, topLeft: Offset, lift: Float, wobble: Float) {
    translate(topLeft.x, topLeft.y) {
        if (!art.plain) {
            val shrink = (1f - lift / art.s * 2.2f).coerceIn(0.6f, 1f)
            scale(shrink, shrink * 0.16f, art.groundC) { drawCircle(art.ground, art.groundR, art.groundC) }
        }
        translate(0f, -lift) {
            rotate(art.lean + wobble, art.pivot) {
                for (i in 0 until SIDE_STEPS) {
                    val f = (SIDE_STEPS - i) / SIDE_STEPS.toFloat()
                    translate(art.depth.x * f, art.depth.y * f) { drawPath(art.outline, art.sides[i]) }
                }
                drawPath(art.outline, art.face)
                if (!art.plain) {
                    clipPath(art.outline) {
                        translate(-art.loOff, -art.loOff) { drawPath(art.outline, art.loInk, style = art.loWide) }
                        translate(art.hiWideOff, art.hiWideOff) { drawPath(art.outline, art.hiInk, style = art.hiWide) }
                        translate(art.hiThinOff, art.hiThinOff) { drawPath(art.outline, art.hiInkThin, style = art.hiThin) }
                        drawCircle(art.gloss, art.glossR, art.glossC)
                    }
                }
            }
        }
    }
}

/** The arrow for [side] filling [box] (the theme stamp's, drawn once). */
internal fun DrawScope.drawClayArrow(side: TurnSide, box: Rect, purple: Color, light: Boolean, effects: Boolean) {
    val s = min(box.width, box.height)
    drawTurn(ClayArrowArt(side, s, purple, light, !effects), box.topLeft, 0f, 0f)
}

/**
 * A clay ball: a sphere lit from the top left, darker at its rim, its soft
 * shadow under it. Built for its radius once; drawn centred on the origin.
 */
internal class ClayBall(color: Color, val r: Float, light: Boolean, val plain: Boolean) {
    val base = color
    val body: Brush = Brush.radialGradient(
        0f to lerp(color, Color.White, 0.55f), 0.45f to color, 1f to lerp(color, Color.Black, if (light) 0.18f else 0.3f),
        center = Offset(-r * 0.35f, -r * 0.4f), radius = r * 1.5f
    )
    val shadowC = Offset(r * 0.35f, r * 0.55f)
    val shadow: Brush = Brush.radialGradient(
        listOf(Color.Black.copy(alpha = if (light) 0.16f else 0.35f), Color.Transparent), shadowC, r * 1.25f
    )
}

/** Draws [ball] centred at [c], [lift] px up and grown by [grow]. */
internal fun DrawScope.drawBall(ball: ClayBall, c: Offset, lift: Float, grow: Float) {
    translate(c.x, c.y) {
        if (!ball.plain) drawCircle(ball.shadow, ball.r * 1.25f, ball.shadowC)
        translate(0f, -lift) {
            scale(grow, grow, Offset.Zero) {
                if (ball.plain) drawCircle(ball.base, ball.r, Offset.Zero) else drawCircle(ball.body, ball.r, Offset.Zero)
            }
        }
    }
}

/** A gentle wave for the floating things: [p] 0..1 around the loop, [phase] in turns. */
internal fun clayWave(p: Float, phase: Float): Float = sin(2f * PI.toFloat() * (p + phase))
