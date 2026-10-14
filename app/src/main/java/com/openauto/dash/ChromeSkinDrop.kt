package com.openauto.dash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Chrome skin, the liquid-metal drop: a circle bent by a few slow sines,
 * filled like polished chrome reflecting a room (a bright sky above a dark
 * horizon, light bouncing up from the floor), with the design's coloured
 * reflections along its edge (pink, ice blue, lime, amber), a highlight, a
 * spark and a soft shadow under it. While parked it ripples slowly; while
 * music plays it moves more and swells gently once a second (the app has no
 * beat or level to follow, so it never pretends to). While the car moves it
 * holds still where it is. One Path rebuilt in place per frame, every
 * gradient made once per size: nothing is allocated while it moves.
 */

/** Points around the drop's outline, joined by smooth curves. */
private const val DROP_POINTS = 64

/** One loop of the drop's motion: every wave turns a whole number of times in it, so it never jumps. */
private const val DROP_LOOP_MS = 60_000

/** How far the drop reaches past its radius at most, as a share of it (the waves added up). */
internal const val DROP_REACH = 1.2f

private val DropCos = FloatArray(DROP_POINTS) { cos(2.0 * PI * it / DROP_POINTS).toFloat() }
private val DropSin = FloatArray(DROP_POINTS) { sin(2.0 * PI * it / DROP_POINTS).toFloat() }
private val DropTheta = FloatArray(DROP_POINTS) { (2.0 * PI * it / DROP_POINTS).toFloat() }

/**
 * The drop at its loop's [phase] (0..1), its outline radius at [theta]
 * relative to its own radius. [energy] (0..1, the music) adds faster waves
 * and the swell.
 */
internal fun dropRadius(theta: Float, phase: Float, energy: Float): Float {
    val a = (2.0 * PI * phase).toFloat()
    // Slow ripples: 3, 2 and 4 turns a minute.
    var d = 0.050f * sin(3f * theta + 3f * a) +
        0.035f * sin(2f * theta - 2f * a + 1.3f) +
        0.022f * sin(5f * theta + 4f * a + 0.7f)
    if (energy > 0f) {
        d += energy * (0.040f * sin(4f * theta - 9f * a + 0.4f) + 0.022f * sin(7f * theta + 13f * a))
        // A gentle swell once a second.
        d += energy * 0.018f * (0.5f - 0.5f * cos(60f * a))
    }
    return 1f + d
}

/**
 * Draws the drop around [c], [r] its resting radius. Everything that does
 * not move is made once ([DropPainter] keeps it); the outline is rebuilt in
 * place on each [draw].
 */
internal class DropPainter(
    private val c: Offset,
    private val r: Float,
    light: Boolean,
    private val plain: Boolean,
    glowK: Float,
    private val px: Float
) {
    private val outline = Path()
    private val inner = Path()
    private val xs = FloatArray(DROP_POINTS)
    private val ys = FloatArray(DROP_POINTS)

    /** The room reflected in the metal: sky, horizon, floor, bounce. */
    private val body = Brush.verticalGradient(
        *(if (light) arrayOf(
            0f to Color(0xFFE6E8EF), 0.3f to Color(0xFF9FA4B1), 0.46f to Color(0xFF4B4E59), 0.52f to Color(0xFF17181D),
            0.7f to Color(0xFF24262D), 0.86f to Color(0xFF5E626E), 1f to Color(0xFFB4B8C4)
        ) else arrayOf(
            0f to Color(0xFFF4F6FC), 0.3f to Color(0xFFB9BDC9), 0.46f to Color(0xFF5D616D), 0.52f to Color(0xFF1E1F25),
            0.7f to Color(0xFF2A2C34), 0.86f to Color(0xFF6E7280), 1f to Color(0xFFC9CCD6)
        )),
        startY = c.y - r * 1.1f, endY = c.y + r * 1.1f
    )

    /** Darker toward the edge, so the flat shape reads round. */
    private val shade = Brush.radialGradient(
        0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = if (light) 0.4f else 0.55f),
        center = Offset(c.x - r * 0.18f, c.y - r * 0.22f), radius = r * 1.3f
    )

    /** The coloured reflections along the edge: ice blue on the right, lime below, pink on the left, amber above. */
    private val glints: Brush? = if (plain) null else {
        val k = if (light) 0.7f else 0.85f
        val t = Color.Transparent
        Brush.sweepGradient(
            0f to IrisBlue.copy(alpha = k), 0.06f to IrisBlue.copy(alpha = k), 0.1f to t, 0.18f to t,
            0.21f to IrisLime.copy(alpha = k), 0.31f to IrisLime.copy(alpha = k), 0.35f to t, 0.4f to t,
            0.43f to IrisPink.copy(alpha = k), 0.55f to IrisPink.copy(alpha = k), 0.59f to t, 0.77f to t,
            0.8f to IrisAmber.copy(alpha = k), 0.88f to IrisAmber.copy(alpha = k), 0.92f to t, 0.97f to t, 1f to IrisBlue.copy(alpha = k),
            center = c
        )
    }
    private val glintStroke = Stroke(r * 0.1f)

    private val highlightC = Offset(c.x - r * 0.16f, c.y - r * 0.5f)
    private val highlight = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.95f), 0.45f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
        center = highlightC, radius = r * 0.36f
    )
    private val sparkC = Offset(c.x + r * 0.42f, c.y + r * 0.34f)
    private val spark = Brush.radialGradient(listOf(Color.White, Color(0x669AD8FF), Color.Transparent), sparkC, r * 0.11f)

    private val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.85f), Color(0x4D9AA0B0), Color.White.copy(alpha = 0.55f)),
        startY = c.y - r * 1.2f, endY = c.y + r * 1.2f
    )
    private val rimStroke = Stroke(1.5f * px)

    private val haloR = r * 1.38f
    private val halo: Brush? = if (glowK > 0f && !plain) Brush.radialGradient(
        0f to Color(0xFFB8C4FF).copy(alpha = 0.22f * glowK), 0.55f to Color(0xFFB8C4FF).copy(alpha = 0.1f * glowK), 1f to Color.Transparent,
        center = c, radius = haloR
    ) else null

    private val floorC = Offset(c.x, c.y + r * 1.14f)
    private val floorRx = r * 0.82f
    private val floor: Brush? = if (plain) null else Brush.radialGradient(
        listOf((if (light) Color(0xFF14161C) else Color.Black).copy(alpha = if (light) 0.22f else 0.7f), Color.Transparent),
        floorC, floorRx
    )

    /** Rebuilds the outline for [phase] and [energy] and draws the whole drop. */
    fun draw(scope: DrawScope, phase: Float, energy: Float) = with(scope) {
        for (i in 0 until DROP_POINTS) {
            val rr = r * dropRadius(DropTheta[i], phase, energy)
            xs[i] = c.x + rr * DropCos[i]
            ys[i] = c.y + rr * DropSin[i]
        }
        smoothClosed(outline, 1f)
        if (floor != null) scale(1f, 0.14f, floorC) { drawCircle(floor, floorRx, floorC) }
        if (halo != null) drawCircle(halo, haloR, c)
        drawPath(outline, body)
        drawPath(outline, shade)
        if (glints != null) {
            smoothClosed(inner, 0.9f)
            drawPath(inner, glints, style = glintStroke)
        }
        drawCircle(highlight, r * 0.36f, highlightC)
        drawPath(outline, rim, style = rimStroke)
        if (!plain) drawCircle(spark, r * 0.11f, sparkC)
    }

    /** The points as one smooth closed curve (Catmull-Rom as cubics), pulled toward the centre by [k]. */
    private fun smoothClosed(path: Path, k: Float) {
        path.reset()
        path.moveTo(ptX(0, k), ptY(0, k))
        for (i in 0 until DROP_POINTS) {
            val c1x = ptX(i, k) + (ptX(i + 1, k) - ptX(i - 1, k)) / 6f
            val c1y = ptY(i, k) + (ptY(i + 1, k) - ptY(i - 1, k)) / 6f
            val c2x = ptX(i + 1, k) - (ptX(i + 2, k) - ptX(i, k)) / 6f
            val c2y = ptY(i + 1, k) - (ptY(i + 2, k) - ptY(i, k)) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, ptX(i + 1, k), ptY(i + 1, k))
        }
        path.close()
    }

    private fun ptX(i: Int, k: Float): Float = c.x + (xs[(i + DROP_POINTS) % DROP_POINTS] - c.x) * k
    private fun ptY(i: Int, k: Float): Float = c.y + (ys[(i + DROP_POINTS) % DROP_POINTS] - c.y) * k
}

/** The drop drawn once, still (the theme stamp). */
internal fun DrawScope.drawChromeDrop(c: Offset, r: Float, phase: Float, energy: Float, light: Boolean, glowK: Float) {
    DropPainter(c, r, light, plain = false, glowK = glowK, px = (r / 60f).coerceAtLeast(1f)).draw(this, phase, energy)
}

/**
 * The liquid-metal drop filling [modifier]'s box, as large as fits with its
 * waves, halo and shadow. [playing] music livens it up, eased in and out
 * over a second (at once while the car moves, where it holds still).
 * Ambient motion: still with effects off, on a page out of sight and while
 * the car moves (SkinKit's [rememberLoop]); in its own layer, only drawing
 * reruns.
 */
@Composable
internal fun ChromeDrop(playing: Boolean, modifier: Modifier) {
    val light = DashColors.Light
    val effects = DashColors.Effects
    val glowK = chromeGlow
    val moving = LocalCarMoving.current
    val loop = rememberLoop(DROP_LOOP_MS)
    val energy = animateFloatAsState(
        if (playing && effects != DashEffects.NONE) 1f else 0f,
        if (moving) snap() else tween(1_200),
        label = "drop energy"
    )
    Spacer(
        modifier
            .graphicsLayer()
            .cachedDraw(light, effects, glowK, loop, energy) {
                val w = size.width
                val h = size.height
                // Room for the waves, the halo and the shadow under it.
                val r = min(w / (2f * DROP_REACH * 1.18f), h / (2f * DROP_REACH * 1.22f))
                val c = Offset(w / 2f, h / 2f - r * 0.08f)
                val painter = if (r > 4f) DropPainter(c, r, light, effects == DashEffects.NONE, glowK, density) else null
                onDrawBehind { painter?.draw(this, loop.value, energy.value) }
            }
    )
}
