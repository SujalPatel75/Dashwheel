package com.openauto.dash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Paper skin, the next turn as a diagram. Dashwheel knows the next turn
 * (its words, its distance, which way it goes), not the shape of the route
 * or the streets around it, so the tile draws an honest schematic of that
 * one junction rather than a map: the streets as blank lines in ink casings
 * over a dithered block texture, the route as a thick ink line taking the
 * turn, an arrow for the car, a small boxed street label where the words
 * name one. The town's blocks are decoration and say nothing of the place.
 */

/** The junction the diagram draws for the next turn. */
internal enum class PaperJunction { LEFT, RIGHT, AHEAD, UTURN, ROUNDABOUT, ARRIVE }

/**
 * The diagram for a turn: the navigation's own [maneuver] when it names one
 * (in-app guidance), else the side the [instruction]'s words say. A
 * roundabout keeps the side its words give for where it leaves the ring
 * ([roundaboutExit]); without one the diagram stops at the ring.
 */
internal fun paperJunction(maneuver: String?, instruction: String): PaperJunction = when (maneuver) {
    "UTURN" -> PaperJunction.UTURN
    "ROUNDABOUT" -> PaperJunction.ROUNDABOUT
    "ARRIVE" -> PaperJunction.ARRIVE
    "STRAIGHT" -> PaperJunction.AHEAD
    "LEFT" -> PaperJunction.LEFT
    "RIGHT" -> PaperJunction.RIGHT
    else -> when (turnSideOf(instruction)) {
        TurnSide.LEFT -> PaperJunction.LEFT
        TurnSide.RIGHT -> PaperJunction.RIGHT
        TurnSide.AHEAD -> PaperJunction.AHEAD
    }
}

/** Where a roundabout is left, from its words: a side, or null when they don't say (the route then stops at the ring). */
internal fun roundaboutExit(instruction: String): TurnSide? = turnSideOf(instruction).takeIf { it != TurnSide.AHEAD }

/** "onto Rue Alsace-Lorraine", "sur la Rue de Paris", "auf die Hauptstraße": the word before a street's name. */
private val STREET_AFTER = Regex(
    """(?:^|\s)(?:onto|on|into|toward|towards|sur|vers|dans|auf|in|nach|en|por|hacia|na|op|naar|su|verso|per|para|em|w|ku|на|в|по)\s+(?:the\s+|la\s+|le\s+|les\s+|l'|die\s+|den\s+|der\s+|das\s+|el\s+|la\s+|a\s+|o\s+|de\s+|het\s+)?(\p{Lu}[^,;]*)""",
    RegexOption.IGNORE_CASE
)

/** A name starting with a capital or a digit after a connector ("onto A6"), so "turn on the left" names nothing. */
private val STARTS_NAME = Regex("""^[\p{Lu}\d]""")

/**
 * The street the [instruction] names, for the diagram's label: what follows
 * "onto", "sur", "auf" and the like in the app's languages when it starts
 * with a capital or a digit; null when the words name none.
 */
internal fun streetOf(instruction: String): String? {
    for (m in STREET_AFTER.findAll(instruction)) {
        val name = m.groupValues[1].trim().trimEnd('.')
        if (name.isNotEmpty() && STARTS_NAME.containsMatchIn(name)) return name
    }
    return null
}

/** The metres in a distance as the navigation writes it ("300 m", "1,2 km", "0.3 mi", "500 ft"); null when it has none. */
internal fun distanceMetres(distance: String): Double? {
    val m = NavState.DISTANCE.find(distance) ?: return null
    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    return v * when (m.groupValues[2]) {
        "km" -> 1000.0
        "mi" -> 1609.344
        "ft" -> 0.3048
        "yd" -> 0.9144
        else -> 1.0
    }
}

/** The steps a turn's distance refreshes at: each one crossed is worth a look. */
private val DISTANCE_STEPS = doubleArrayOf(50.0, 100.0, 200.0, 300.0, 500.0, 1000.0, 2000.0, 5000.0)

/** Which step of [DISTANCE_STEPS] [distance] is in (0 nearest); -1 when it has no figure. */
internal fun distanceBucket(distance: String): Int {
    val m = distanceMetres(distance) ?: return -1
    val i = DISTANCE_STEPS.indexOfFirst { m <= it }
    return if (i < 0) DISTANCE_STEPS.size else i
}

/** A seed for the town's blocks: the same turn draws the same town, a new turn a new one. */
private fun townSeed(junction: PaperJunction, instruction: String): Long = junction.ordinal * 7919L + instruction.hashCode()

/**
 * The junction diagram filling [modifier]: the town, its streets, the route
 * taking the turn, the arrow for the car, the street's label, and with
 * [refreshed] the boxed REFRESHED stamp in the bottom-left corner. Drawn
 * once per turn and size: nothing in it moves.
 */
@Composable
internal fun PaperJunctionMap(
    junction: PaperJunction,
    instruction: String,
    street: String?,
    refreshed: String?,
    modifier: Modifier
) {
    val light = DashColors.Light
    val ink = DashColors.TextPrimary
    val page = DashColors.Background
    val blank = paperBlankFor(light)
    val exit = if (junction == PaperJunction.ROUNDABOUT) roundaboutExit(instruction) else null
    val seed = townSeed(junction, instruction)
    Box(
        modifier
            .clipToBounds()
            .semantics { contentDescription = instruction }
            .cachedDraw(junction, exit, seed, ink, page, blank, light) {
                val g = JunctionGeometry(size.width, size.height, junction, exit, density)
                val dot = paperDot()
                val town = paperTownInk(light, ink, page)
                val base = PaperTextures.dither(town, page, 2, dot)
                val lots = townLots(size.width, size.height, g.u, seed)
                val lotBrushes = LOT_LEVELS.map { PaperTextures.dither(town, page, it, dot) }
                val casing = Stroke(g.casing, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                val fill = Stroke(g.street, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                val sideCasing = Stroke(g.sideStreet + (g.casing - g.street), cap = StrokeCap.Butt)
                val sideFill = Stroke(g.sideStreet, cap = StrokeCap.Butt)
                val routeStroke = Stroke(g.routeW, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                val outline = Stroke(max(2f * density, g.u * 0.5f), join = StrokeJoin.Miter)
                val ring = g.ring
                onDrawBehind {
                    drawRect(page)
                    drawRect(base)
                    for (lot in lots) drawRect(lotBrushes[lot.level], lot.rect.topLeft, lot.rect.size)
                    drawPath(g.sideStreets, ink, style = sideCasing)
                    drawPath(g.sideStreets, blank, style = sideFill)
                    drawPath(g.streets, ink, style = casing)
                    if (ring != null) drawCircle(ink, ring.second, ring.first, style = Stroke(g.casing))
                    drawPath(g.streets, blank, style = fill)
                    if (ring != null) {
                        drawCircle(blank, ring.second, ring.first, style = Stroke(g.street))
                        // The island in the middle keeps the town's dither, in its own casing.
                        val island = ring.second - g.street / 2f
                        drawCircle(ink, island, ring.first, style = Stroke((g.casing - g.street) / 2f))
                    }
                    drawPath(g.routeLine, ink, style = routeStroke)
                    g.head?.let { drawPath(it, ink) }
                    g.goal?.let { (c, r) ->
                        drawCircle(ink, r, c)
                        drawCircle(blank, r * 0.45f, c)
                    }
                    drawPath(g.car, blank)
                    drawPath(g.car, ink, style = outline)
                }
            }
    ) {
        if (street != null) {
            StreetLabel(street, junction, modifier = Modifier.fillMaxSize())
        }
        if (refreshed != null) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = androidx.compose.ui.Alignment.BottomStart) {
                PaperStampBox(refreshed)
            }
        }
    }
}

/** The town's dither ink: the ink by day; by night half way to the page, so the blocks recede and the route leads. */
internal fun paperTownInk(light: Boolean, ink: Color, page: Color): Color =
    if (light) ink else androidx.compose.ui.graphics.lerp(page, ink, 0.45f)

/** A boxed stamp in the typewriter face ("REFRESHED 21:07"), panel grey in its ink frame. */
@Composable
internal fun PaperStampBox(text: String) {
    PaperLine(
        text,
        paperText(14f, DashColors.TextPrimary, FontWeight.Normal, mono = true, caps = true),
        Modifier.paperFrame(fill = true).padding(horizontal = 12.dp, vertical = 7.dp),
        caps = true
    )
}

/**
 * The street's name boxed in ink, set beside the street the route takes:
 * above the cross street on the turn's side, beside the street ahead
 * otherwise. Placed from the same geometry as the drawing.
 */
@Composable
private fun StreetLabel(street: String, junction: PaperJunction, modifier: Modifier) {
    Layout(
        content = {
            PaperLine(
                street,
                paperText(16f, paperBlank, FontWeight.Bold, caps = true),
                Modifier.drawBehind { drawRect(paperInk) }.padding(horizontal = 9.dp, vertical = 4.dp),
                caps = true
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val g = JunctionGeometry(w.toFloat(), h.toFloat(), junction, null, density)
        val p = measurables.first().measure(Constraints(maxWidth = (w * 0.62f).roundToInt().coerceAtLeast(1)))
        val gap = g.casing / 2f + 8.dp.toPx()
        val (x, y) = when (junction) {
            PaperJunction.LEFT -> (g.j.x - gap - p.width) to (g.j.y - gap - p.height)
            PaperJunction.RIGHT -> (g.j.x + gap) to (g.j.y - gap - p.height)
            else -> (g.j.x + gap) to (g.j.y * 0.42f - p.height / 2f)
        }
        layout(w, h) {
            p.place(
                x.roundToInt().coerceIn(6, (w - p.width - 6).coerceAtLeast(6)),
                y.roundToInt().coerceIn(6, (h - p.height - 6).coerceAtLeast(6))
            )
        }
    }
}

/** A lot of the town: its rectangle and which of [LOT_LEVELS] fills it. */
private class Lot(val rect: Rect, val level: Int)

/** How dark the town's lots are, of 16: mid greys mostly, a few darker. */
private val LOT_LEVELS = intArrayOf(4, 5, 6, 8, 10)

/**
 * The town's lots for a [w] x [h] diagram: a grid of blocks about 15 units
 * across, each split once or not, with blank alleys between, a few left
 * empty, each with a grey of its own. Seeded, so a turn keeps its town.
 */
private fun townLots(w: Float, h: Float, u: Float, seed: Long): List<Lot> {
    val r = java.util.Random(seed)
    val out = ArrayList<Lot>()
    val cell = 15f * u
    val alley = 1.6f * u
    var y = -cell * r.nextFloat()
    while (y < h) {
        var x = -cell * r.nextFloat()
        val rowH = cell * (0.75f + 0.5f * r.nextFloat())
        while (x < w) {
            val cw = cell * (0.75f + 0.6f * r.nextFloat())
            if (r.nextFloat() > 0.18f) {
                if (r.nextFloat() < 0.45f) {
                    val split = 0.35f + 0.3f * r.nextFloat()
                    out += Lot(Rect(x + alley, y + alley, x + cw * split, y + rowH - alley), r.nextInt(LOT_LEVELS.size))
                    out += Lot(Rect(x + cw * split + alley, y + alley, x + cw - alley, y + rowH - alley), r.nextInt(LOT_LEVELS.size))
                } else {
                    out += Lot(Rect(x + alley, y + alley, x + cw - alley, y + rowH - alley), r.nextInt(LOT_LEVELS.size))
                }
            }
            x += cw
        }
        y += rowH
    }
    return out
}

/**
 * Where everything of the diagram goes in a [w] x [h] box, in pixels: the
 * junction's centre [j], a unit [u] (a hundredth of the smaller side), the
 * streets' and the route's widths, the junction's streets and two quiet side
 * streets as paths, the ring of a roundabout, the route with its arrowhead
 * or its goal, and the car's arrow on the street it comes from.
 */
private class JunctionGeometry(w: Float, h: Float, junction: PaperJunction, exit: TurnSide?, density: Float) {
    val u = min(w, h) / 100f
    val j = Offset(w * 0.5f, h * 0.42f)
    val street = max(14f * density, 6.4f * u)
    val casing = street + 2f * max(2f * density, 0.8f * u)
    val sideStreet = street * 0.5f
    val routeW = street * 0.6f
    val streets = Path()
    val sideStreets = Path()
    val routeLine = Path()
    var ring: Pair<Offset, Float>? = null
    var head: Path? = null
    var goal: Pair<Offset, Float>? = null
    val car = Path()

    init {
        val far = max(w, h)
        val left = -far * 0.1f
        val right = w + far * 0.1f
        val top = -far * 0.1f
        val bottom = h + far * 0.1f
        // Two quiet side streets, a block away, so the junction sits in a town.
        sideStreets.moveTo(left, j.y + (h - j.y) * 0.62f); sideStreets.lineTo(right, j.y + (h - j.y) * 0.62f)
        sideStreets.moveTo(w * 0.14f, top); sideStreets.lineTo(w * 0.14f, bottom)
        sideStreets.moveTo(w * 0.86f, top); sideStreets.lineTo(w * 0.86f, bottom)
        sideStreets.moveTo(left, j.y * 0.32f); sideStreets.lineTo(right, j.y * 0.32f)
        val carAt = Offset(j.x, j.y + (h - j.y) * 0.5f)
        var carX = carAt.x
        when (junction) {
            PaperJunction.LEFT, PaperJunction.RIGHT, PaperJunction.AHEAD -> {
                crossroads(left, right, top, bottom)
                routeLine.moveTo(j.x, bottom)
                routeLine.lineTo(j.x, j.y)
                when (junction) {
                    PaperJunction.LEFT -> {
                        routeLine.lineTo(w * 0.16f, j.y)
                        head = arrowHead(Offset(w * 0.06f, j.y), Offset(-1f, 0f))
                    }
                    PaperJunction.RIGHT -> {
                        routeLine.lineTo(w * 0.84f, j.y)
                        head = arrowHead(Offset(w * 0.94f, j.y), Offset(1f, 0f))
                    }
                    else -> {
                        routeLine.lineTo(j.x, h * 0.12f)
                        head = arrowHead(Offset(j.x, h * 0.03f), Offset(0f, -1f))
                    }
                }
            }
            PaperJunction.ARRIVE -> {
                crossroads(left, right, top, bottom)
                routeLine.moveTo(j.x, bottom)
                routeLine.lineTo(j.x, j.y)
                goal = j to routeW * 1.35f
            }
            PaperJunction.UTURN -> {
                streets.moveTo(j.x, bottom); streets.lineTo(j.x, top)
                // The two-way street: its two sides drawn as one wide street.
                val lane = street * 0.9f
                streets.moveTo(j.x - lane, bottom); streets.lineTo(j.x - lane, top)
                streets.moveTo(j.x + lane, bottom); streets.lineTo(j.x + lane, top)
                val r = lane
                routeLine.moveTo(j.x + lane, bottom)
                routeLine.lineTo(j.x + lane, j.y)
                routeLine.arcTo(Rect(j.x - r, j.y - r, j.x + r, j.y + r), 0f, -180f, false)
                routeLine.lineTo(j.x - lane, j.y + (h - j.y) * 0.72f)
                head = arrowHead(Offset(j.x - lane, j.y + (h - j.y) * 0.86f), Offset(0f, 1f))
                carX = j.x + lane
            }
            PaperJunction.ROUNDABOUT -> {
                val r = max(street * 1.6f, 13f * u)
                ring = j to r
                streets.moveTo(j.x, bottom); streets.lineTo(j.x, j.y + r)
                streets.moveTo(left, j.y); streets.lineTo(j.x - r, j.y)
                streets.moveTo(right, j.y); streets.lineTo(j.x + r, j.y)
                streets.moveTo(j.x, top); streets.lineTo(j.x, j.y - r)
                routeLine.moveTo(j.x, bottom)
                routeLine.lineTo(j.x, j.y + r)
                val box = Rect(j.x - r, j.y - r, j.x + r, j.y + r)
                when (exit) {
                    TurnSide.RIGHT -> {
                        routeLine.arcTo(box, 90f, -90f, false)
                        routeLine.lineTo(w * 0.84f, j.y)
                        head = arrowHead(Offset(w * 0.94f, j.y), Offset(1f, 0f))
                    }
                    TurnSide.LEFT -> {
                        routeLine.arcTo(box, 90f, -270f, false)
                        routeLine.lineTo(w * 0.16f, j.y)
                        head = arrowHead(Offset(w * 0.06f, j.y), Offset(-1f, 0f))
                    }
                    else -> {
                        // The words don't say which exit: the route stops on the ring, heading round it.
                        routeLine.arcTo(box, 90f, -40f, false)
                        val a = Math.toRadians(50.0)
                        val end = Offset(j.x + r * Math.cos(a).toFloat(), j.y + r * Math.sin(a).toFloat())
                        head = arrowHead(end, Offset(Math.sin(a).toFloat(), -Math.cos(a).toFloat()))
                    }
                }
            }
        }
        // The car's arrow, pointing up its street, as in the mockup.
        val s = max(street * 1.15f, 9f * u) / 2f
        val c = Offset(carX, carAt.y)
        car.moveTo(c.x, c.y - s * 1.3f)
        car.lineTo(c.x + s, c.y + s)
        car.lineTo(c.x, c.y + s * 0.5f)
        car.lineTo(c.x - s, c.y + s)
        car.close()
    }

    /** The junction's crossroads: the street the car comes up, on past it, and the cross street. */
    private fun crossroads(left: Float, right: Float, top: Float, bottom: Float) {
        streets.moveTo(j.x, bottom); streets.lineTo(j.x, top)
        streets.moveTo(left, j.y); streets.lineTo(right, j.y)
    }

    /** A solid arrowhead with its tip at [tip], pointing along [dir] (a unit vector). */
    private fun arrowHead(tip: Offset, dir: Offset): Path {
        val len = routeW * 2.4f
        val half = routeW * 1.45f
        val back = Offset(tip.x - dir.x * len, tip.y - dir.y * len)
        val n = Offset(-dir.y, dir.x)
        return Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(back.x + n.x * half, back.y + n.y * half)
            lineTo(back.x - n.x * half, back.y - n.y * half)
            close()
        }
    }
}

/**
 * The turn as a glyph in ink, after the mockup's arrow (square ends, a
 * rounded corner): the navigation app's own picture tinted ink when it
 * sends one, else drawn for the junction.
 */
@Composable
internal fun PaperTurnGlyph(nav: NavState, junction: PaperJunction, size: Dp) {
    val ink = DashColors.TextPrimary
    val bitmap = remember(nav.icon) { nav.icon?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = nav.instruction, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(size))
        return
    }
    val exit = if (junction == PaperJunction.ROUNDABOUT) roundaboutExit(nav.instruction) else null
    Spacer(
        Modifier
            .size(size)
            .semantics { contentDescription = nav.instruction }
            .cachedDraw(junction, exit, ink) {
                val s = this.size.minDimension / 24f
                val path = turnGlyphPath(junction, exit, s)
                val stroke = Stroke(2.6f * s, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                onDrawBehind { drawPath(path, ink, style = stroke) }
            }
    )
}

/** The glyph's strokes in a 24 grid scaled by [s]. */
private fun turnGlyphPath(junction: PaperJunction, exit: TurnSide?, s: Float): Path = Path().apply {
    fun mx(v: Float, mirror: Boolean) = (if (mirror) 24f - v else v) * s
    when (junction) {
        PaperJunction.LEFT, PaperJunction.RIGHT -> {
            val m = junction == PaperJunction.RIGHT
            // The mockup's "M17 21V11a3 3 0 0 0-3-3H5" and "M9 4 5 8l4 4".
            moveTo(mx(17f, m), 21f * s); lineTo(mx(17f, m), 11f * s)
            cubicTo(mx(17f, m), 9.3f * s, mx(15.7f, m), 8f * s, mx(14f, m), 8f * s)
            lineTo(mx(5f, m), 8f * s)
            moveTo(mx(9f, m), 4f * s); lineTo(mx(5f, m), 8f * s); lineTo(mx(9f, m), 12f * s)
        }
        PaperJunction.AHEAD -> {
            moveTo(12f * s, 21f * s); lineTo(12f * s, 4f * s)
            moveTo(8f * s, 8f * s); lineTo(12f * s, 4f * s); lineTo(16f * s, 8f * s)
        }
        PaperJunction.UTURN -> {
            moveTo(16f * s, 21f * s); lineTo(16f * s, 9f * s)
            cubicTo(16f * s, 5.5f * s, 8f * s, 5.5f * s, 8f * s, 9f * s)
            lineTo(8f * s, 18f * s)
            moveTo(4.5f * s, 14.5f * s); lineTo(8f * s, 18f * s); lineTo(11.5f * s, 14.5f * s)
        }
        PaperJunction.ROUNDABOUT -> {
            addOval(Rect(8f * s, 6f * s, 16f * s, 14f * s))
            moveTo(12f * s, 21f * s); lineTo(12f * s, 14f * s)
            when (exit) {
                TurnSide.LEFT -> {
                    moveTo(8f * s, 10f * s); lineTo(3f * s, 10f * s)
                    moveTo(5.5f * s, 7.5f * s); lineTo(3f * s, 10f * s); lineTo(5.5f * s, 12.5f * s)
                }
                TurnSide.RIGHT -> {
                    moveTo(16f * s, 10f * s); lineTo(21f * s, 10f * s)
                    moveTo(18.5f * s, 7.5f * s); lineTo(21f * s, 10f * s); lineTo(18.5f * s, 12.5f * s)
                }
                else -> Unit
            }
        }
        PaperJunction.ARRIVE -> {
            moveTo(12f * s, 21f * s); lineTo(12f * s, 13f * s)
            addOval(Rect(8f * s, 5f * s, 16f * s, 13f * s))
            addOval(Rect(10.6f * s, 7.6f * s, 13.4f * s, 10.4f * s))
        }
    }
}
