package com.openauto.dash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.location.Location
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Terrain skin, the page: a landscape of contour lines seen from above and
 * behind, tilted away like the mockup's 3D stage, dark olive with sand and
 * ochre lines by night, sand paper with brown lines by day. It is decoration:
 * a height field made of noise seeded by the car's rough position (rounded to
 * a fifth of a degree, so it only changes after a long drive and never while
 * moving), not real elevation, and nothing says otherwise.
 *
 * It is drawn once per screen size, seed and day/night into one bitmap off
 * the main thread (perspective grid, slope shading, fog, contours by marching
 * squares, far rows first so nearer hills hide the lines behind them) and
 * kept. The only motion is a faint amber pulse running out across the
 * contour lines from the car's pad while parked: one stroke along a ring on
 * the ground, painted with the landscape itself through a filter that keeps
 * its lines alone. Still while moving and with effects off.
 */

private const val TAG = "TerrainMap"

/** The camera's pitch below the horizon, as in the mockup (62 up, 120 back). */
private const val PITCH_DEG = 27f

/** The camera's height over the pad, in the height field's units. */
private const val CAM_H = 62f

/** One contour every this many units; every fifth is a major one. */
private const val CONTOUR_STEP = 4f

/** Around the pad the land is flat (inside the first radius) and rises to its full relief at the second. */
private const val FLAT_IN = 40f
private const val FLAT_OUT = 130f

/** The pulse: one run out from the pad every [PULSE_MS], the run taking [PULSE_RUN_MS]; the rest is still. */
private const val PULSE_MS = 9_000
private const val PULSE_RUN_MS = 5_200

/** The seed's cell: a fifth of a degree, about 20 km. */
private const val SEED_PER_DEGREE = 5

/**
 * Where the car's pad is on a screen of [w] x [h], as fractions: under the
 * car of Terrain's wide Home (the large tile in the middle left, right of
 * the rail), or high in the middle of an upright screen.
 */
internal fun terrainPadAt(w: Int, h: Int): Pair<Float, Float> = if (w >= h) 0.39f to 0.57f else 0.5f to 0.34f

/**
 * The camera over the landscape: the pad (the ground's origin, [z0] ahead
 * of the camera's foot) lands on the screen at ([cx], [cy]); [f] is the focal
 * length in pixels.
 */
internal class TerrainCam(val cx: Float, val cy: Float, val f: Float) {
    private val theta = Math.toRadians(PITCH_DEG.toDouble()).toFloat()
    val sin = sin(theta)
    val cos = cos(theta)
    val z0 = CAM_H * cos / sin

    /** Projects (x, y, z) to the screen into [out] (x, y, depth); false when behind the camera. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val dy = y - CAM_H
        val d = z * cos - dy * sin
        if (d < 1f) return false
        out[0] = cx + f * x / d
        out[1] = cy - f * (dy * cos + z * sin) / d
        out[2] = d
        return true
    }

    /** How far ahead the ray through screen row [py] meets flat ground; [cap] past the horizon. */
    fun groundZ(py: Float, cap: Float): Float {
        val a = theta + atan((py - cy) / f)
        return if (a <= 0.02f) cap else min(cap, CAM_H / tan(a))
    }

    /** The depth of the flat ground [z] ahead, for spreading the grid's columns along the screen. */
    fun groundDepth(z: Float): Float = z * cos + CAM_H * sin
}

/** One drawn landscape and what it was drawn for. */
internal class TerrainArt(val key: TerrainKey, val image: ImageBitmap, val cam: TerrainCam)

internal data class TerrainKey(val w: Int, val h: Int, val seed: Int, val light: Boolean)

/** The last landscape drawn, kept for the next time the page shows (one only: a screen-size bitmap is ~3.7 MB). */
internal object TerrainMaps {
    @Volatile
    var latest: TerrainArt? = null
        private set

    fun find(key: TerrainKey): TerrainArt? = latest?.takeIf { it.key == key }

    /** Draws the landscape for [key] (slow: call off the main thread); null if it could not be. */
    fun make(key: TerrainKey, density: Float): TerrainArt? {
        find(key)?.let { return it }
        val made = runCatching { renderTerrain(key, density) }
            .onFailure { Log.w(TAG, "landscape not drawn", it) }
            .getOrNull() ?: return null
        latest = made
        return made
    }
}

/** The landscape's seed for the car's position [l]: the same within a fifth of a degree. */
internal fun terrainSeed(l: Location?): Int {
    if (l == null) return 0
    val lat = (l.latitude * SEED_PER_DEGREE).roundToInt()
    val lon = (l.longitude * SEED_PER_DEGREE).roundToInt()
    return lat * 73_856_093 xor lon * 19_349_663
}

// --- The page's modifier --------------------------------------------------------------------

/**
 * The page background: the landscape once drawn (a plain olive or sand
 * gradient until then, or while a new one is drawn for the other time of
 * day), and the pulse over it while parked.
 */
@Composable
internal fun terrainMapBackground(): Modifier {
    val light = DashColors.Light
    val effects = DashColors.Effects
    val moving = LocalCarMoving.current
    val density = LocalDensity.current.density
    val stops = DashColors.BackgroundStops
    val seed = rememberTerrainSeed(moving)
    val width = remember { mutableIntStateOf(0) }
    val height = remember { mutableIntStateOf(0) }
    val key = TerrainKey(width.intValue, height.intValue, seed, light)
    var art by remember { mutableStateOf(TerrainMaps.latest) }
    LaunchedEffect(key) {
        if (key.w <= 0 || key.h <= 0) return@LaunchedEffect
        TerrainMaps.find(key)?.let { art = it; return@LaunchedEffect }
        withContext(Dispatchers.Default) { TerrainMaps.make(key, density) }?.let { art = it }
    }
    val shown = art?.takeIf { it.key.light == light }
    val pulse = if (effects != DashEffects.NONE && !moving && shown != null) rememberTerrainPulse() else null
    val sizeModifier = remember {
        Modifier.onSizeChanged { s: IntSize ->
            width.intValue = s.width
            height.intValue = s.height
        }
    }
    return sizeModifier.then(remember(shown, light, effects, stops, pulse) { terrainDraw(shown, light, effects, stops, pulse) })
}

/** The seed of the landscape, from the car's position; it waits for the car to stand still before it changes. */
@Composable
private fun rememberTerrainSeed(moving: Boolean): Int {
    val cell by rememberLocationCell(SEED_PER_DEGREE)
    val now = terrainSeed(cell)
    val kept = remember { mutableIntStateOf(now) }
    if (!moving && kept.intValue != now) kept.intValue = now
    return kept.intValue
}

/**
 * The pulse's run, 0 to 1, and -1 between runs: a value that stays put while
 * nothing runs, so the page is not redrawn then.
 */
@Composable
private fun rememberTerrainPulse(): State<Float> {
    val loop = rememberLoop(PULSE_MS)
    return remember(loop) {
        derivedStateOf {
            val t = loop.value * PULSE_MS / PULSE_RUN_MS
            if (t >= 1f) -1f else t
        }
    }
}

private fun terrainDraw(art: TerrainArt?, light: Boolean, effects: DashEffects, stops: List<Color>, pulse: State<Float>?): Modifier =
    Modifier.drawWithCache {
        val plain = Brush.verticalGradient(stops)
        if (art == null) return@drawWithCache onDrawBehind { drawRect(plain) }
        val img = art.image
        val sx = size.width / img.width
        val sy = size.height / img.height
        val fits = abs(sx - 1f) < 0.01f && abs(sy - 1f) < 0.01f
        // The pulse: the landscape itself through a filter that keeps its lines alone, in amber.
        val ringBrush = if (pulse != null) ShaderBrush(ImageShader(img)) else null
        val lines = if (pulse != null) ColorFilter.colorMatrix(pulseMatrix(light)) else null
        val ring = Path()
        val pt = FloatArray(3)
        val steps = 72
        val cosT = FloatArray(steps + 1) { cos(it * 2.0 * PI / steps).toFloat() }
        val sinT = FloatArray(steps + 1) { sin(it * 2.0 * PI / steps).toFloat() }
        val wide = Stroke(34f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val narrow = Stroke(12f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val strength = effects.scale
        onDrawBehind {
            if (fits) drawImage(img) else scale(sx, sy, Offset.Zero) { drawImage(img) }
            val p = pulse?.value ?: -1f
            if (p < 0f || ringBrush == null) return@onDrawBehind
            // The ring on the ground, out from the pad; points behind the camera break it.
            val r = 34f + p * 430f
            val cam = art.cam
            ring.reset()
            var open = false
            for (k in 0..steps) {
                if (cam.project(r * cosT[k], 0f, cam.z0 + r * sinT[k], pt)) {
                    if (open) ring.lineTo(pt[0], pt[1]) else ring.moveTo(pt[0], pt[1])
                    open = true
                } else open = false
            }
            val a = (min(1f, p * 6f) * (1f - p) * (1f - p) * strength).coerceIn(0f, 1f)
            if (a <= 0.01f) return@onDrawBehind
            scale(sx, sy, Offset.Zero) {
                drawPath(ring, ringBrush, alpha = 0.4f * a, style = wide, colorFilter = lines)
                drawPath(ring, ringBrush, alpha = a, style = narrow, colorFilter = lines)
            }
        }
    }

/**
 * Amber where the landscape has a line, nothing on its ground: the lines are
 * lighter than the olive ground by night and darker than the sand paper by
 * day, so the alpha follows the brightness, the colour is the amber.
 */
private fun pulseMatrix(light: Boolean): ColorMatrix {
    val (r, g, b) = if (light) Triple(200f, 110f, 20f) else Triple(255f, 178f, 64f)
    val k = if (light) -6.5f else 6f
    val off = if (light) 1335f else -250f
    return ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, r,
            0f, 0f, 0f, 0f, g,
            0f, 0f, 0f, 0f, b,
            k * 0.299f, k * 0.587f, k * 0.114f, 0f, off
        )
    )
}

// --- Drawing the landscape --------------------------------------------------------------------

/** Gradient noise for the height field (classic 2D Perlin), seeded. */
private class TerrainNoise(seed: Int) {
    private val p = IntArray(512)

    init {
        val rnd = java.util.Random(seed.toLong() * 7_919L + 17L)
        val a = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        for (i in 0 until 512) p[i] = a[i and 255]
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun grad(h: Int, x: Float, y: Float): Float = when (h and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    fun noise(x: Float, y: Float): Float {
        val xf = floor(x)
        val yf = floor(y)
        val xi = xf.toInt() and 255
        val yi = yf.toInt() and 255
        val x0 = x - xf
        val y0 = y - yf
        val u = fade(x0)
        val v = fade(y0)
        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]
        val l1 = grad(aa, x0, y0) + u * (grad(ba, x0 - 1f, y0) - grad(aa, x0, y0))
        val l2 = grad(ab, x0, y0 - 1f) + u * (grad(bb, x0 - 1f, y0 - 1f) - grad(ab, x0, y0 - 1f))
        return l1 + v * (l2 - l1)
    }

    /** Several octaves, each turned and twice as fine, normalised to about -1..1. */
    fun fbm(x: Float, y: Float, octaves: Int): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var fx = x
        var fy = y
        repeat(octaves) {
            sum += amp * noise(fx, fy)
            norm += amp
            amp *= 0.5f
            val nx = fx * 1.6f - fy * 1.2f
            val ny = fx * 1.2f + fy * 1.6f
            fx = nx + 3.1f
            fy = ny + 1.7f
        }
        return sum / norm
    }
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun mix(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

/** The landscape's colours by night and by day. */
private class TerrainInks(light: Boolean) {
    val fog = if (light) intArrayOf(239, 233, 220) else intArrayOf(20, 22, 17)
    val sky = if (light) intArrayOf(233, 226, 211) else intArrayOf(12, 13, 10)
    val shade = if (light) intArrayOf(221, 211, 189) else intArrayOf(15, 17, 12)
    val lit = if (light) intArrayOf(246, 241, 230) else intArrayOf(44, 43, 30)
    val pad = if (light) intArrayOf(231, 224, 208) else intArrayOf(30, 30, 22)
    val sand = if (light) intArrayOf(122, 94, 54) else intArrayOf(150, 138, 100)
    val ochre = if (light) intArrayOf(150, 92, 28) else intArrayOf(214, 160, 76)
    val thinA = if (light) 0.32f else 0.36f
    val majorA = if (light) 0.6f else 0.62f
    val vignette = if (light) argb(28, 90, 70, 40) else argb(120, 0, 0, 0)
    val warm = if (light) argb(0, 0, 0, 0) else argb(16, 255, 173, 51)
}

/**
 * The landscape for [key]: a perspective grid whose columns follow the
 * screen and whose rows grow with distance (so every row is about as tall
 * on screen), heights from the noise flattened round the pad, each vertex lit
 * by its slope and fogged by its distance; then far to near, each strip of
 * ground filled and its contour lines drawn on it, so nearer hills cover
 * what lies behind them.
 */
private fun renderTerrain(key: TerrainKey, density: Float): TerrainArt {
    val w = key.w
    val h = key.h
    val (fx, fy) = terrainPadAt(w, h)
    val cam = TerrainCam(w * fx, h * fy, 1.865f * min(w, h))
    val ink = TerrainInks(key.light)
    val noise = TerrainNoise(key.seed)
    val ridges = TerrainNoise(key.seed * 31 + 7)

    val zNear = max(6f, cam.groundZ(h.toFloat(), 2000f) * 0.78f)
    val zFar = min(cam.groundZ(0f, 1100f) * 1.6f, 1100f).coerceAtLeast(zNear * 3f)
    val rows = (h / (4.5f * density)).toInt().coerceIn(90, 190)
    val cols = (w / (6f * density)).toInt().coerceIn(110, 240)
    val margin = w * 0.18f
    val sLeft = -cam.cx - margin
    val sRight = w - cam.cx + margin

    val xs = Array(rows) { FloatArray(cols) }
    val zs = FloatArray(rows)
    val hs = Array(rows) { FloatArray(cols) }
    val ox = (key.seed and 1023) * 0.37f
    val oz = ((key.seed shr 10) and 1023) * 0.41f
    for (j in 0 until rows) {
        val z = zNear * exp(ln(zFar / zNear) * j / (rows - 1f))
        zs[j] = z
        val d = cam.groundDepth(z)
        for (i in 0 until cols) {
            val s = sLeft + (sRight - sLeft) * i / (cols - 1f)
            val x = s * d / cam.f
            xs[j][i] = x
            val r = sqrt(x * x + (z - cam.z0) * (z - cam.z0))
            val flat = smooth(FLAT_IN, FLAT_OUT, r)
            val base = noise.fbm(x / 170f + ox, z / 170f + oz, 5)
            val ridge = 1f - abs(ridges.noise(x / 95f + oz, z / 95f + ox))
            hs[j][i] = flat * (82f * base + 16f * (ridge - 0.55f)) - 2f
        }
    }

    // Projection, light and fog per vertex.
    val px = Array(rows) { FloatArray(cols) }
    val py = Array(rows) { FloatArray(cols) }
    val litK = Array(rows) { FloatArray(cols) }
    val colors = Array(rows) { IntArray(cols) }
    val pt = FloatArray(3)
    // The light from the upper left, a little toward the camera.
    val lx = -0.55f; val ly = 0.75f; val lz = -0.36f
    val ln = sqrt(lx * lx + ly * ly + lz * lz)
    for (j in 0 until rows) {
        val fog = smooth(zFar * 0.42f, zFar * 0.96f, zs[j])
        for (i in 0 until cols) {
            val x = xs[j][i]
            val z = zs[j]
            val y = hs[j][i]
            if (cam.project(x, y, z, pt)) {
                px[j][i] = pt[0]; py[j][i] = pt[1]
            } else {
                px[j][i] = cam.cx; py[j][i] = h * 2f
            }
            val i0 = max(0, i - 1); val i1 = min(cols - 1, i + 1)
            val j0 = max(0, j - 1); val j1 = min(rows - 1, j + 1)
            val dhdx = (hs[j][i1] - hs[j][i0]) / (xs[j][i1] - xs[j][i0]).let { if (abs(it) < 1e-3f) 1e-3f else it }
            val dhdz = (hs[j1][i] - hs[j0][i]) / (zs[j1] - zs[j0]).let { if (abs(it) < 1e-3f) 1e-3f else it }
            // Normal (-dh/dx, 1, -dh/dz), its light.
            val nl = sqrt(dhdx * dhdx + 1f + dhdz * dhdz)
            val sh = (-dhdx * lx + ly - dhdz * lz) / (nl * ln)
            val lit = ((sh - 0.5f) * 2.2f).coerceIn(0f, 1f)
            litK[j][i] = lit
            var r = mix(ink.shade[0], ink.lit[0], lit)
            var g = mix(ink.shade[1], ink.lit[1], lit)
            var b = mix(ink.shade[2], ink.lit[2], lit)
            val rr = sqrt(x * x + (z - cam.z0) * (z - cam.z0))
            val pad = (1f - smooth(36f, 50f, rr)) * 0.7f
            if (pad > 0f) {
                r = mix(r, ink.pad[0], pad); g = mix(g, ink.pad[1], pad); b = mix(b, ink.pad[2], pad)
            }
            r = mix(r, ink.fog[0], fog); g = mix(g, ink.fog[1], fog); b = mix(b, ink.fog[2], fog)
            colors[j][i] = argb(255, r, g, b)
        }
    }

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // The sky past the far rows: the fog, darker toward the top.
    val skyPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), argb(255, ink.sky[0], ink.sky[1], ink.sky[2]), argb(255, ink.fog[0], ink.fog[1], ink.fog[2]), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)

    val fill = Paint().apply { isAntiAlias = false; color = 0xFFFFFFFF.toInt() }
    val linePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val verts = FloatArray(cols * 4)
    val vColors = IntArray(cols * 4)
    val idx = ShortArray((cols - 1) * 6)
    for (i in 0 until cols - 1) {
        val a = (2 * i).toShort(); val b = (2 * i + 1).toShort(); val c = (2 * i + 3).toShort(); val d = (2 * i + 2).toShort()
        val o = i * 6
        idx[o] = a; idx[o + 1] = b; idx[o + 2] = d
        idx[o + 3] = b; idx[o + 4] = c; idx[o + 5] = d
    }
    val edgeX = FloatArray(4)
    val edgeY = FloatArray(4)
    for (j in rows - 2 downTo 0) {
        // The strip between row j (near) and j + 1 (far).
        for (i in 0 until cols) {
            verts[4 * i] = px[j][i]; verts[4 * i + 1] = py[j][i]
            verts[4 * i + 2] = px[j + 1][i]; verts[4 * i + 3] = py[j + 1][i]
            vColors[2 * i] = colors[j][i]; vColors[2 * i + 1] = colors[j + 1][i]
        }
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, cols * 4, verts, 0, null, 0, vColors, 0, idx, 0, idx.size, fill)

        // Its contour lines.
        val zMid = (zs[j] + zs[j + 1]) / 2f
        val fog = smooth(zFar * 0.38f, zFar * 0.92f, zMid)
        if (fog >= 0.98f) continue
        val depthScale = (cam.groundDepth(cam.z0) / cam.groundDepth(zMid)).coerceIn(0.5f, 1.25f)
        for (i in 0 until cols - 1) {
            val ha = hs[j][i]; val hb = hs[j][i + 1]; val hc = hs[j + 1][i + 1]; val hd = hs[j + 1][i]
            val lo = min(min(ha, hb), min(hc, hd))
            val hi = max(max(ha, hb), max(hc, hd))
            var k = ceil(lo / CONTOUR_STEP).toInt()
            val kEnd = floor(hi / CONTOUR_STEP).toInt()
            if (k > kEnd) continue
            val xm = (xs[j][i] + xs[j + 1][i + 1]) / 2f
            val rr = sqrt(xm * xm + (zMid - cam.z0) * (zMid - cam.z0))
            val keep = smooth(58f, 118f, rr)
            if (keep <= 0.01f) continue
            val lit = (litK[j][i] + litK[j + 1][i + 1]) / 2f
            val warm = smooth(90f, 340f, rr)
            val cr = mix(ink.ochre[0], ink.sand[0], warm)
            val cg = mix(ink.ochre[1], ink.sand[1], warm)
            val cb = mix(ink.ochre[2], ink.sand[2], warm)
            val shadeK = (0.55f + 0.6f * lit) * keep * (1f - fog)
            while (k <= kEnd) {
                val level = k * CONTOUR_STEP + 0.0001f
                var n = 0
                fun edge(h1: Float, h2: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
                    if ((h1 - level) * (h2 - level) < 0f) {
                        val t = (level - h1) / (h2 - h1)
                        edgeX[n] = x1 + (x2 - x1) * t
                        edgeY[n] = y1 + (y2 - y1) * t
                        n++
                    }
                }
                edge(ha, hb, px[j][i], py[j][i], px[j][i + 1], py[j][i + 1])
                edge(hb, hc, px[j][i + 1], py[j][i + 1], px[j + 1][i + 1], py[j + 1][i + 1])
                edge(hc, hd, px[j + 1][i + 1], py[j + 1][i + 1], px[j + 1][i], py[j + 1][i])
                edge(hd, ha, px[j + 1][i], py[j + 1][i], px[j][i], py[j][i])
                if (n >= 2) {
                    val major = Math.floorMod(k, 5) == 0
                    val alpha = ((if (major) ink.majorA else ink.thinA) * shadeK).coerceIn(0f, 1f)
                    if (alpha > 0.02f) {
                        linePaint.color = argb((alpha * 255f).roundToInt(), cr, cg, cb)
                        linePaint.strokeWidth = (if (major) 1.7f else 1f) * density * depthScale
                        canvas.drawLine(edgeX[0], edgeY[0], edgeX[1], edgeY[1], linePaint)
                        if (n == 4) canvas.drawLine(edgeX[2], edgeY[2], edgeX[3], edgeY[3], linePaint)
                    }
                }
                k++
            }
        }
    }

    // Fog over the top, so the header and the cards there read; a warm light round the pad; the vignette.
    val top = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h * 0.32f, argb(if (key.light) 150 else 190, ink.sky[0], ink.sky[1], ink.sky[2]), argb(0, ink.sky[0], ink.sky[1], ink.sky[2]), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h * 0.32f, top)
    if (!key.light) {
        val warm = Paint().apply {
            shader = RadialGradient(cam.cx, cam.cy, w * 0.34f, ink.warm, 0, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), warm)
    }
    val vignette = Paint().apply {
        shader = RadialGradient(
            cam.cx, cam.cy, max(w, h) * 0.78f,
            intArrayOf(0, 0, ink.vignette), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignette)
    return TerrainArt(key, bmp.asImageBitmap(), cam)
}
