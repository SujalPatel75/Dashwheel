package com.openauto.dash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One problem to place on the car: its part, how serious it is (2 stop, 1 garage, 0 good to know), and its words. */
internal data class MapProblem(val spot: CarSpot, val serious: Int, val label: String)

/**
 * The car with its problems on it ([CarProblemMap]): the driver's own car
 * (MyCarLook.kt), from behind, the front or above, whichever shows the most
 * serious problem best, else the drawn car from above; each problem a glow
 * on its part. With problems on more than one view, chips switch between
 * them and the other view waits in a corner. [compact]: the picture alone.
 */
@Composable
internal fun CarProblemsMap(problems: List<MapProblem>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val mine by MyCarLook.shown.collectAsState()
    val style by MyCarLook.style.collectAsState()
    val car by CarProfileStore.profile.collectAsState()
    val top = mine?.top
    val back = mine?.back?.takeIf { it.lights.size == 2 }
    val front = mine?.front?.takeIf { it.lights.size == 2 }
    val topFrame = (top?.let(::topFrameOf) ?: TopFrame.DRAWN).copy(driverOnRight = car.driverOnRight)
    val backFrame = back?.let { EndFrame(it.lights[0], it.lights[1]) }
    val frontFrame = front?.let { EndFrame(it.lights[0], it.lights[1]) }
    val available = buildSet {
        add(CarMapView.TOP)
        if (backFrame != null) add(CarMapView.BACK)
        if (frontFrame != null) add(CarMapView.FRONT)
    }
    val placed = problems.mapNotNull { p -> CarProblemMap.viewFor(p.spot, available)?.let { p to it } }
    if (placed.isEmpty()) return
    // The views in use, the one with the most serious problem first.
    val views = placed.sortedByDescending { it.first.serious }.map { it.second }.distinct()

    fun picture(view: CarMapView): CarView? = when (view) {
        CarMapView.TOP -> top
        CarMapView.BACK -> back
        CarMapView.FRONT -> front
    }
    fun marks(view: CarMapView): List<Pair<Offset, Int>> = placed.filter { it.second == view }.flatMap { (p, _) ->
        CarProblemMap.places(p.spot, view, topFrame, backFrame, frontFrame).map { it to p.serious }
    }

    val pulse by rememberInfiniteTransition(label = "problems").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulse"
    )
    val stop = DashColors.Critical
    val garage = DashColors.Warning
    val info = DashColors.TextSecondary
    fun tone(serious: Int) = when (serious) { 2 -> stop; 1 -> garage; else -> info }
    val body = DashColors.CardHi.compositeOver(DashColors.Card.copy(alpha = 1f))
    val edge = DashColors.TextSecondary
    val ink = DashColors.Accent

    // Every view with a problem on it, side by side and the same size (the most serious first);
    // a small tile shows the first alone.
    val drawn = if (compact) views.take(1) else views
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (v in drawn) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (drawn.size > 1) {
                    val n = placed.count { it.second == v }
                    val worst = placed.filter { it.second == v }.maxOf { it.first.serious }
                    Text(
                        "${stringResource(v.titleRes)}  $n",
                        color = DashColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(DashShape.Small)
                            .border(1.dp, tone(worst), DashShape.Small)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val where = drawView(picture(v), v, style, Rect(Offset.Zero, size), body, edge, ink, garage)
                    for ((at, serious) in marks(v)) glowMark(where.at(at), min(size.width, size.height) * 0.05f, tone(serious), pulse)
                }
            }
        }
    }
}

/** The chip's name of each view. */
private val CarMapView.titleRes: Int
    get() = when (this) {
        CarMapView.BACK -> R.string.car_map_back
        CarMapView.FRONT -> R.string.car_map_front
        CarMapView.TOP -> R.string.car_map_top
    }

/** A view from above's anchors, when it has them all. */
private fun topFrameOf(view: CarView): TopFrame? {
    val nose = view.nose ?: return null
    val tail = view.tail ?: return null
    if (view.wheels.size < 4) return null
    return TopFrame(nose, tail, view.wheels[0], view.wheels[1], view.wheels[2], view.wheels[3])
}

/**
 * Draws [view]'s picture fitted in [area] (or, from above without one, the
 * drawn car) and returns the rectangle its fractions are measured in.
 */
private fun DrawScope.drawView(
    view: CarView?, which: CarMapView, style: CarLookStyle, area: Rect, body: Color, edge: Color, ink: Color, warn: Color
): Rect {
    if (view != null) {
        val dst = fitCar(view, area)
        drawCarView(view, style, dst, ink, warn)
        return dst
    }
    // Only the view from above has a drawing to fall back on.
    check(which == CarMapView.TOP)
    val s = min(area.width / VIEW_W, area.height / VIEW_H)
    val left = area.left + (area.width - VIEW_W * s) / 2f
    val top = area.top + (area.height - VIEW_H * s) / 2f
    translate(left, top) {
        scale(s, s, Offset.Zero) {
            drawCar(body, edge.copy(alpha = 0.24f), edge, warn, 0f, 0f, 0f, 0f, 0f, 0f)
        }
    }
    return Rect(Offset(left, top), Size(VIEW_W * s, VIEW_H * s))
}

/** A problem's light on its part: a soft glow, a bright core and a ring that widens as it fades. */
private fun DrawScope.glowMark(at: Offset, r: Float, tone: Color, pulse: Float) {
    drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.55f), tone.copy(alpha = 0.18f), Color.Transparent), at, r * 3.2f), r * 3.2f, at)
    drawCircle(tone.copy(alpha = 0.95f), r * 0.75f, at)
    drawCircle(Color.White.copy(alpha = 0.9f), r * 0.38f, at)
    drawCircle(tone.copy(alpha = 0.9f), r * 1.2f, at, style = Stroke(r * 0.18f))
    drawCircle(tone.copy(alpha = (1f - pulse) * 0.8f), r * (1.3f + pulse * 1.6f), at, style = Stroke(r * 0.14f))
}
