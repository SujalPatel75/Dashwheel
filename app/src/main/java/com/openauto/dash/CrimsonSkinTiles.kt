package com.openauto.dash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Crimson skin's tiles (CrimsonSkin.kt has the page, the bar and the shared
 * parts). Every tile sizes its type and its drawing from its own box, from a
 * 3 x 2 grid cell to the full screen, and drops its extras before it would crowd them.
 *
 * Home, as drawn in the mockup, is four of them on the 12 x 7 grid (CrimsonLayout):
 * the speedometer and the tachometer either side of the road, the player along the foot.
 */

/** The Home this look was drawn around: dials either side of the road, the player along the foot. */
internal val CrimsonLayout = SkinLayout(
    TemplatePage(listOf(BuiltinKind.SPEED_HUD, BuiltinKind.TELEMETRY, BuiltinKind.NAVIGATION, BuiltinKind.MEDIA)),
    wide = listOf(
        DashboardItem.BuiltinWidget(BuiltinKind.SPEED_HUD, x = 0, y = 0, w = 4, h = 6),
        DashboardItem.BuiltinWidget(BuiltinKind.NAVIGATION, x = 4, y = 0, w = 4, h = 6),
        DashboardItem.BuiltinWidget(BuiltinKind.TELEMETRY, x = 8, y = 0, w = 4, h = 6),
        DashboardItem.BuiltinWidget(BuiltinKind.MEDIA, x = 0, y = 6, w = 12, h = 1)
    )
)

/** Crimson's renderer for the main widgets; anything else keeps its standard renderer on the skin's palette. */
@Composable
internal fun CrimsonTile(item: DashboardItem, env: SkinTileEnv) {
    when (item) {
        is DashboardItem.BuiltinWidget -> when (item.kind) {
            BuiltinKind.SPEED_HUD -> CrimsonSpeedo(env)
            BuiltinKind.TELEMETRY -> CrimsonTacho(env)
            BuiltinKind.NAVIGATION -> CrimsonScene(env)
            BuiltinKind.MEDIA -> CrimsonPlayer(env)
            else -> StandardSkinnedTile(item, env)
        }
        else -> StandardSkinnedTile(item, env)
    }
}

// --- Dials --------------------------------------------------------------------------

/**
 * One dial's scale: the arc runs [sweepDeg] from [startDeg] (degrees clockwise from
 * 3 o'clock) and holds 0..[max]; ticks every [minorStep], longer ones every
 * [majorStep]; numbers at [labels]; the red zone from [redFrom] to the end.
 */
private class CrDialFace(
    val startDeg: Float,
    val sweepDeg: Float,
    val max: Float,
    val minorStep: Float,
    val majorStep: Float,
    val labels: List<Float>,
    val labelText: (Float) -> String,
    val redFrom: Float?
)

private const val CR_DIAL_START = 150f
private const val CR_DIAL_SWEEP = 240f

/** 0..260 km/h or 0..160 mph; numbers as in the mockup (every 20, then every 40 once past 100). */
private fun crSpeedFace(imperial: Boolean): CrDialFace {
    val max = if (imperial) 160f else 260f
    val labels = if (imperial) listOf(0f, 20f, 40f, 60f, 80f, 100f, 120f, 140f, 160f)
    else listOf(0f, 20f, 40f, 60f, 80f, 100f, 140f, 180f, 220f, 260f)
    return CrDialFace(CR_DIAL_START, CR_DIAL_SWEEP, max, 10f, 20f, labels, { it.roundToInt().toString() }, null)
}

/** 0..N x1000 r/min, N from the car's red line (8 for a petrol of 6 500), the red zone from the red line. */
private fun crTachFace(redline: Int): CrDialFace {
    val top = ((redline / 1000f) + 1.5f).roundToInt().coerceIn(6, 9)
    val max = top * 1000f
    return CrDialFace(
        CR_DIAL_START, CR_DIAL_SWEEP, max, 500f, 1000f,
        (1..top).map { it * 1000f }, { (it / 1000f).roundToInt().toString() },
        redline.toFloat().coerceIn(1000f, max - 500f)
    )
}

private fun crRad(deg: Float): Float = deg * (Math.PI / 180.0).toFloat()

/**
 * Draws [face] in a square box: a dark face, the faint full arc, the red zone, the arc lit up to
 * [frac] (0..1, read while drawing: a new reading redraws the dial and nothing else), ticks, numbers
 * and a tapered needle. Everything static is built once per size.
 */
@Composable
private fun Modifier.crimsonDial(face: CrDialFace, frac: State<Float>): Modifier {
    val measurer = rememberTextMeasurer()
    val light = DashColors.Light
    val effects = DashColors.Effects
    return this.cachedDraw(face, frac, measurer, light, effects) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val red = DashColors.Accent
        val ink = DashColors.TextPrimary
        val glow = effects.scale * CrimsonDrive.glow * (if (light) 0.5f else 1f)

        val ringStroke = Stroke(r * 0.012f)
        val ringR = r * 0.985f
        val arcR = r * 0.93f
        val arcTl = Offset(c.x - arcR, c.y - arcR)
        val arcSize = Size(arcR * 2f, arcR * 2f)
        val barW = r * 0.05f
        val trackStroke = Stroke(barW * 0.5f, cap = StrokeCap.Round)
        val activeStroke = Stroke(barW, cap = StrokeCap.Round)
        val glowStroke = Stroke(barW * 2.6f, cap = StrokeCap.Round)
        val redStroke = Stroke(barW * 0.7f)
        val ring = DashColors.Muted.copy(alpha = 0.5f)
        val track = DashColors.Muted.copy(alpha = 0.3f)
        val disc = Brush.radialGradient(
            0f to (if (light) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.55f)),
            0.82f to (if (light) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.45f)),
            1f to red.copy(alpha = (if (light) 0.05f else 0.16f) * effects.scale),
            center = c, radius = r * 0.82f
        )
        val innerRing = Stroke(r * 0.008f)

        // Ticks, from the zero of the scale to its end.
        val ticks = ArrayList<Triple<Offset, Offset, Boolean>>()
        var v = 0f
        while (v <= face.max + 0.001f) {
            val q = v / face.majorStep
            val major = abs(q - q.roundToInt()) < 0.001f
            val a = crRad(face.startDeg + face.sweepDeg * v / face.max)
            val from = r * (if (major) 0.835f else 0.86f)
            val to = r * 0.89f
            ticks += Triple(
                Offset(c.x + cos(a) * from, c.y + sin(a) * from),
                Offset(c.x + cos(a) * to, c.y + sin(a) * to),
                major
            )
            v += face.minorStep
        }
        val tickMajor = ink.copy(alpha = 0.85f)
        val tickMinor = ink.copy(alpha = 0.4f)
        val majorW = r * 0.012f
        val minorW = r * 0.006f

        // Numbers.
        val labelStyle = TextStyle(
            color = ink, fontFamily = FontFamily.SansSerif, fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium, fontSize = (r * 0.085f).toSp()
        )
        val redLabelStyle = labelStyle.copy(color = red)
        val numbers = face.labels.map { value ->
            val a = crRad(face.startDeg + face.sweepDeg * value / face.max)
            val inRed = face.redFrom != null && value >= face.redFrom
            val layout = measurer.measure(face.labelText(value), if (inRed) redLabelStyle else labelStyle)
            layout to Offset(
                c.x + cos(a) * r * 0.72f - layout.size.width / 2f,
                c.y + sin(a) * r * 0.72f - layout.size.height / 2f
            )
        }

        // The needle points along +x and is turned about the centre when drawn.
        val half = r * 0.016f
        val needle = Path().apply {
            moveTo(c.x + r * 0.96f, c.y)
            lineTo(c.x + r * 0.42f, c.y - half)
            lineTo(c.x + r * 0.42f, c.y + half)
            close()
        }
        val needleGlow = Path().apply {
            moveTo(c.x + r * 0.96f, c.y)
            lineTo(c.x + r * 0.42f, c.y - half * 2.6f)
            lineTo(c.x + r * 0.42f, c.y + half * 2.6f)
            close()
        }
        val redFrac = face.redFrom?.let { it / face.max }

        onDrawBehind {
            val f = frac.value.coerceIn(0f, 1f)
            drawCircle(disc, r * 0.82f, c)
            drawCircle(red.copy(alpha = 0.35f), r * 0.6f, c, style = innerRing)
            drawArc(ring, face.startDeg, face.sweepDeg, false, Offset(c.x - ringR, c.y - ringR), Size(ringR * 2f, ringR * 2f), style = ringStroke)
            drawArc(track, face.startDeg, face.sweepDeg, false, arcTl, arcSize, style = trackStroke)
            if (redFrac != null) {
                drawArc(
                    red.copy(alpha = 0.85f), face.startDeg + face.sweepDeg * redFrac, face.sweepDeg * (1f - redFrac),
                    false, arcTl, arcSize, style = redStroke
                )
            }
            if (f > 0.004f) {
                if (glow > 0f) drawArc(red.copy(alpha = (0.22f * glow).coerceAtMost(0.6f)), face.startDeg, face.sweepDeg * f, false, arcTl, arcSize, style = glowStroke)
                drawArc(red, face.startDeg, face.sweepDeg * f, false, arcTl, arcSize, style = activeStroke)
            }
            ticks.forEach { (a, b, major) ->
                drawLine(if (major) tickMajor else tickMinor, a, b, if (major) majorW else minorW)
            }
            numbers.forEach { (layout, at) -> drawText(layout, topLeft = at) }
            rotate(face.startDeg + face.sweepDeg * f, pivot = c) {
                if (glow > 0f) drawPath(needleGlow, red.copy(alpha = (0.25f * glow).coerceAtMost(0.6f)))
                drawPath(needle, red)
            }
        }
    }
}

/** The bar under a dial: icon and reading on top, "E ▬▬▬ F" under it; [fraction] null leaves the bar empty. */
@Composable
private fun CrimsonFootBar(
    icon: ImageVector,
    reading: String,
    fraction: Float?,
    alert: Color?,
    startLabel: String,
    endLabel: String,
    modifier: Modifier
) {
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 52.dp
        val ink = alert ?: DashColors.TextPrimary
        val textSp = (maxHeight.value * 0.34f).coerceIn(15f, 24f)
        val fill = alert ?: DashColors.Accent
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size((textSp * 1.15f).dp))
                    Spacer(Modifier.width(8.dp))
                    CrText(reading, crBody(fixedSp(textSp), ink, FontWeight.Medium))
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(0.82f), verticalAlignment = Alignment.CenterVertically) {
                CrText(startLabel, crBody(fixedSp(textSp * 0.8f), if (alert != null) fill else DashColors.Accent, FontWeight.Bold))
                Spacer(Modifier.width(8.dp))
                if (compact) {
                    Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    CrText(reading, crBody(fixedSp(textSp * 0.8f), ink, FontWeight.Medium))
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DashColors.Muted.copy(alpha = 0.3f))
                ) {
                    if (fraction != null) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .background(Brush.horizontalGradient(listOf(fill, if (alert != null) fill else Color(0xFFFFB3B3))))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                CrText(endLabel, crBody(fixedSp(textSp * 0.8f), DashColors.TextPrimary, FontWeight.Bold))
            }
        }
    }
}

// --- Speedometer (SPEED_HUD) --------------------------------------------------------

/**
 * The speedometer: the speed in the driver's units at the centre of a dial whose arc
 * and needle follow it (the OBD's speed while the adapter is linked, else a fresh GPS
 * fix), with the fuel bar and range under it. With no speed at all a tap connects the
 * adapter.
 */
@Composable
internal fun CrimsonSpeedo(env: SkinTileEnv) {
    val units = LocalUnits.current
    val speedKmh = rememberSpeedKmh(env.obdData, env.obdConnection)
    val fuel = rememberFuel(env)
    val face = remember(units.imperial) { crSpeedFace(units.imperial) }
    val shown = speedKmh?.let { units.speed(it) }
    val frac = animateFloatAsState((shown ?: 0) / face.max, tween(300, easing = LinearEasing), label = "crimsonSpeed")
    val canConnect = speedKmh == null && env.obdConnection.isIdle && !env.editing
    val obd = env.obdConnection == ObdConnectionState.CONNECTED
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
            .crTap(canConnect, stringResource(R.string.skin_motorsport_connect_obd), env.onConnectObd)
    ) {
        val footH = (maxHeight.value * 0.15f).coerceIn(40f, 76f).dp
        val dial = minOf(maxWidth, (maxHeight - footH).coerceAtLeast(60.dp))
        val numberColor = when {
            shown == null -> DashColors.Muted
            speedOver(speedKmh) -> DashColors.Warning
            else -> DashColors.TextPrimary
        }
        val low = fuel != null && fuel.percent <= SKIN_LOW_FUEL_PCT
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dial).crimsonDial(face, frac), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CrText(shown?.toString() ?: "–", crHero(fixedSp(dial.value * 0.27f), numberColor, glow = shown != null))
                        CrText(
                            units.speedUnit,
                            crBody(fixedSp(dial.value * 0.065f), DashColors.TextSecondary).copy(fontStyle = FontStyle.Italic)
                        )
                        if (dial >= 240.dp) {
                            val noSignal = stringResource(R.string.info_speed_no_signal)
                            CrText(
                                speedSource(obd, speedKmh, noSignal).uppercase(),
                                crBody(fixedSp(dial.value * 0.04f), if (speedKmh != null) DashColors.Good else DashColors.Muted, FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            CrimsonFootBar(
                icon = Icons.Filled.LocalGasStation,
                reading = fuel?.let { "${units.distance(it.rangeKm)} ${units.distanceUnit}" } ?: "–",
                fraction = fuel?.let { it.percent / 100f },
                alert = if (low) DashColors.Warning else null,
                startLabel = "E",
                endLabel = "F",
                modifier = Modifier.fillMaxWidth().height(footH)
            )
        }
    }
}

// --- Tachometer (TELEMETRY) ---------------------------------------------------------

/** The gear as a short mark: 1..9, R, N; null when it can't be told. */
private fun crGearMark(gear: Gear): String? = when (gear) {
    is Gear.Forward -> gear.n.toString()
    Gear.Reverse -> "R"
    Gear.Free -> "N"
    Gear.Stopped, Gear.Unknown -> null
}

/**
 * The tachometer: revs in thousands at the centre (the OBD's, or the car's own CANbox
 * while it is fresh), the gear the app works out beside "RPM", a needle and a red zone
 * from the car's red line, and the coolant bar under it, amber when hot and red when
 * overheating.
 */
@Composable
internal fun CrimsonTacho(env: SkinTileEnv) {
    val units = LocalUnits.current
    val gear = rememberGear()
    val rpm = gear.value.rpm
    val redline = CarProfileStore.current.redlineRpm ?: 6_500
    val face = remember(redline) { crTachFace(redline) }
    val frac = animateFloatAsState((rpm ?: 0) / face.max, tween(140, easing = LinearEasing), label = "crimsonRpm")
    val canConnect = rpm == null && env.obdConnection.isIdle && !env.editing
    val coolant = if (env.obdConnection == ObdConnectionState.CONNECTED) env.obdData.coolantTempC.takeIf { it > 0 } else null
    val alert = when {
        coolant == null -> null
        coolant >= 115 -> DashColors.Critical
        coolant >= 105 -> DashColors.Warning
        else -> null
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
            .crTap(canConnect, stringResource(R.string.skin_motorsport_connect_obd), env.onConnectObd)
    ) {
        val footH = (maxHeight.value * 0.15f).coerceIn(40f, 76f).dp
        val dial = minOf(maxWidth, (maxHeight - footH).coerceAtLeast(60.dp))
        val mark = crGearMark(gear.value.gear)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dial).crimsonDial(face, frac), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CrText(
                            rpm?.let { "%.1f".format(it / 1000f) } ?: "–",
                            crHero(fixedSp(dial.value * 0.27f), if (rpm == null) DashColors.Muted else DashColors.TextPrimary, glow = rpm != null)
                        )
                        CrText(
                            "x1000 RPM",
                            crBody(fixedSp(dial.value * 0.06f), DashColors.TextSecondary).copy(fontStyle = FontStyle.Italic)
                        )
                        if (mark != null && dial >= 220.dp) {
                            Spacer(Modifier.height((dial.value * 0.02f).dp))
                            CrText(
                                mark,
                                crHero(fixedSp(dial.value * 0.09f), DashColors.Accent),
                                Modifier
                                    .border(1.dp, DashColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = (dial.value * 0.03f).dp)
                            )
                        }
                    }
                }
            }
            CrimsonFootBar(
                icon = Icons.Filled.Thermostat,
                reading = coolant?.let { "${units.temp(it)}${units.tempUnit}" } ?: "–",
                fraction = coolant?.let { ((it - 50) / 80f) },
                alert = alert,
                startLabel = "C",
                endLabel = "H",
                modifier = Modifier.fillMaxWidth().height(footH)
            )
        }
    }
}

// --- The road (NAVIGATION) ----------------------------------------------------------

private const val CR_DASHES = 9

/**
 * Night road to a red horizon with the car seen from behind. Everything still is drawn once into
 * a layer; the centre dashes are the only thing that moves, and they run with the real speed
 * (none while the car stands, none at all with effects off). While guidance runs, the next turn
 * is laid over the top.
 */
@Composable
internal fun CrimsonScene(env: SkinTileEnv) {
    val speed = rememberSpeedKmh(env.obdData, env.obdConnection) ?: 0
    val nav by NavDirections.state.collectAsState()
    val kmh = rememberUpdatedState(speed)
    val phase = remember { mutableFloatStateOf(0f) }
    val active = LocalPageActive.current
    val still = DashColors.Effects == DashEffects.NONE
    LaunchedEffect(active, still) {
        if (!active || still) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(40)
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now
            val v = kmh.value
            if (v > 2) phase.floatValue = (phase.floatValue + dt * v / 40f) % 1f
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer()) {
        Box(Modifier.fillMaxSize().crimsonRoad(phase))
        if (nav.active) {
            CrimsonTurn(nav, Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Modifier.crimsonRoad(phase: State<Float>): Modifier {
    val light = DashColors.Light
    val effects = DashColors.Effects
    return this.cachedDraw(phase, light, effects) {
        val w = size.width
        val h = size.height
        val red = DashColors.Accent
        val k = effects.scale
        val vp = Offset(w / 2f, h * 0.40f)
        val roadHalfAtFoot = w * 0.62f
        val carW = w * 0.58f
        val carH = carW * 0.5f
        val carLeft = (w - carW) / 2f
        val carTop = h * 0.88f - carH
        fun px(fx: Float) = carLeft + fx * carW
        fun py(fy: Float) = carTop + fy * carH

        val body = Path().apply {
            moveTo(px(0.02f), py(0.82f))
            lineTo(px(0.00f), py(0.58f))
            quadraticTo(px(0.01f), py(0.42f), px(0.07f), py(0.40f))
            lineTo(px(0.20f), py(0.36f))
            lineTo(px(0.27f), py(0.04f))
            quadraticTo(px(0.50f), py(-0.03f), px(0.73f), py(0.04f))
            lineTo(px(0.80f), py(0.36f))
            lineTo(px(0.93f), py(0.40f))
            quadraticTo(px(0.99f), py(0.42f), px(1.00f), py(0.58f))
            lineTo(px(0.98f), py(0.82f))
            close()
        }
        val glass = Path().apply {
            moveTo(px(0.31f), py(0.10f))
            lineTo(px(0.69f), py(0.10f))
            lineTo(px(0.75f), py(0.34f))
            lineTo(px(0.25f), py(0.34f))
            close()
        }
        val lampL = Path().apply {
            moveTo(px(0.035f), py(0.46f))
            lineTo(px(0.33f), py(0.50f))
            lineTo(px(0.31f), py(0.61f))
            lineTo(px(0.045f), py(0.58f))
            close()
        }
        val lampR = Path().apply {
            moveTo(px(0.965f), py(0.46f))
            lineTo(px(0.67f), py(0.50f))
            lineTo(px(0.69f), py(0.61f))
            lineTo(px(0.955f), py(0.58f))
            close()
        }
        val diffuser = Path().apply {
            moveTo(px(0.12f), py(0.80f))
            lineTo(px(0.88f), py(0.80f))
            lineTo(px(0.82f), py(0.93f))
            lineTo(px(0.18f), py(0.93f))
            close()
        }
        val farRidge = crRidge(w, vp.y, h, listOf(0f to 0.00f, 0.08f to 0.10f, 0.16f to 0.05f, 0.26f to 0.17f, 0.36f to 0.08f, 0.44f to 0.12f, 0.50f to 0.04f, 0.56f to 0.12f, 0.66f to 0.20f, 0.76f to 0.09f, 0.86f to 0.15f, 0.94f to 0.06f, 1f to 0.09f))
        val nearRidge = crRidge(w, vp.y, h, listOf(0f to 0.05f, 0.10f to 0.03f, 0.20f to 0.09f, 0.30f to 0.02f, 0.70f to 0.02f, 0.80f to 0.10f, 0.90f to 0.04f, 1f to 0.07f))
        val road = Path().apply {
            moveTo(vp.x - 1f, vp.y)
            lineTo(w / 2f - roadHalfAtFoot, h)
            lineTo(w / 2f + roadHalfAtFoot, h)
            lineTo(vp.x + 1f, vp.y)
            close()
        }
        val footL = Offset(w / 2f - roadHalfAtFoot, h)
        val footR = Offset(w / 2f + roadHalfAtFoot, h)
        val laneL = Offset(w / 2f - w * 0.30f, h)
        val laneR = Offset(w / 2f + w * 0.30f, h)

        val still = obtainGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
        still.record {
            // Horizon glow and the half sun.
            drawCircle(
                Brush.radialGradient(
                    listOf(red.copy(alpha = (if (light) 0.18f else 0.55f) * k), red.copy(alpha = 0f)),
                    center = vp, radius = w * 0.75f
                ),
                w * 0.75f, vp
            )
            val sun = w * 0.028f
            drawArc(red, 180f, 180f, true, Offset(vp.x - sun, vp.y - sun), Size(sun * 2f, sun * 2f))
            // Mountains, far then near, with a faint red rim light.
            drawPath(farRidge, if (light) Color(0xFFD9B8B8) else Color(0xFF2A0508))
            drawPath(farRidge, red.copy(alpha = 0.3f * k), style = Stroke(1.dp.toPx()))
            drawPath(nearRidge, if (light) Color(0xFFC49A9A) else Color(0xFF130304))
            // A few lights of a town on the horizon.
            for (i in 0 until 22) {
                val left = i < 11
                val fx = if (left) 0.16f + ((i * 37) % 24) / 100f else 0.60f + ((i * 41) % 24) / 100f
                val fy = ((i * 53) % 5) * 0.004f
                drawCircle(red.copy(alpha = 0.8f * k), 1.2.dp.toPx(), Offset(w * fx, vp.y - h * fy - 1.dp.toPx()))
            }
            // The ground and the road.
            drawRect(if (light) Color(0xFFE3D3D3) else Color(0xFF040303), topLeft = Offset(0f, vp.y), size = Size(w, h - vp.y))
            drawPath(
                road,
                Brush.verticalGradient(
                    listOf(if (light) Color(0xFFD9C3C3) else Color(0xFF1C0808), if (light) Color(0xFFC9AFAF) else Color(0xFF0B0505)),
                    startY = vp.y, endY = h
                )
            )
            // Red light along the road's edges and the lanes beside the car.
            val edgeW = 2.dp.toPx()
            if (k > 0f) {
                drawLine(red.copy(alpha = 0.22f * k), vp, footL, edgeW * 4f)
                drawLine(red.copy(alpha = 0.22f * k), vp, footR, edgeW * 4f)
            }
            drawLine(red.copy(alpha = 0.9f), vp, footL, edgeW)
            drawLine(red.copy(alpha = 0.9f), vp, footR, edgeW)
            drawLine(red.copy(alpha = 0.5f), vp, laneL, edgeW * 0.6f)
            drawLine(red.copy(alpha = 0.5f), vp, laneR, edgeW * 0.6f)
            // Red glow on the road under the car.
            scale(1f, 0.28f, Offset(w / 2f, h * 0.88f)) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(red.copy(alpha = 0.55f * k), red.copy(alpha = 0f)),
                        center = Offset(w / 2f, h * 0.88f), radius = carW * 0.75f
                    ),
                    carW * 0.75f, Offset(w / 2f, h * 0.88f)
                )
            }
            // The car, from behind.
            drawPath(body, Brush.verticalGradient(listOf(Color(0xFF222226), Color(0xFF060607)), startY = py(0f), endY = py(0.85f)))
            if (k > 0f) drawPath(body, red.copy(alpha = 0.35f * k), style = Stroke(1.2.dp.toPx()))
            drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF2C1416), Color(0xFF080809)), startY = py(0.10f), endY = py(0.34f)))
            drawLine(Color.White.copy(alpha = 0.14f), Offset(px(0.08f), py(0.40f)), Offset(px(0.92f), py(0.40f)), 1.dp.toPx())
            drawRoundRect(Color(0xFF0B0B0E), Offset(px(0.05f), py(0.62f)), Size(carW * 0.90f, carH * 0.22f), CornerRadius(carH * 0.06f))
            drawPath(diffuser, Color(0xFF040405))
            for (wheel in listOf(0.0f, 0.88f)) {
                drawRoundRect(Color(0xFF030303), Offset(px(wheel), py(0.74f)), Size(carW * 0.12f, carH * 0.24f), CornerRadius(carH * 0.04f))
            }
            for (fx in listOf(0.28f, 0.72f)) {
                val tw = carW * 0.075f
                val th = carH * 0.075f
                drawOval(Color(0xFF8E8E96), Offset(px(fx) - tw, py(0.90f) - th), Size(tw * 2f, th * 2f))
                drawOval(Color(0xFF050506), Offset(px(fx) - tw * 0.7f, py(0.90f) - th * 0.7f), Size(tw * 1.4f, th * 1.4f))
            }
            drawCircle(Color(0xFF0A0A0D), carW * 0.03f, Offset(px(0.5f), py(0.50f)))
            drawCircle(Color.White.copy(alpha = 0.45f), carW * 0.03f, Offset(px(0.5f), py(0.50f)), style = Stroke(1.dp.toPx()))
            // Tail lights, with their glow.
            if (k > 0f) {
                for (fx in listOf(0.18f, 0.82f)) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(red.copy(alpha = 0.55f * k), red.copy(alpha = 0f)),
                            center = Offset(px(fx), py(0.53f)), radius = carW * 0.16f
                        ),
                        carW * 0.16f, Offset(px(fx), py(0.53f))
                    )
                }
            }
            drawPath(lampL, Color(0xFFFF2A2A))
            drawPath(lampR, Color(0xFFFF2A2A))
        }

        val dash = Path()
        val roadH = h - vp.y
        onDrawBehind {
            drawLayer(still)
            // The centre dashes, running towards the car; they stop above its roof.
            val p = phase.value
            clipRect(bottom = carTop) {
                for (n in 0 until CR_DASHES) {
                    val t0 = (n + p) / CR_DASHES
                    val t1 = min(t0 + 0.55f / CR_DASHES, 1f)
                    val y0 = vp.y + roadH * t0.pow(2.2f)
                    val y1 = vp.y + roadH * t1.pow(2.2f)
                    val hw0 = w * 0.010f * ((y0 - vp.y) / roadH) + 0.4f
                    val hw1 = w * 0.010f * ((y1 - vp.y) / roadH) + 0.4f
                    dash.reset()
                    dash.moveTo(vp.x - hw0, y0)
                    dash.lineTo(vp.x + hw0, y0)
                    dash.lineTo(vp.x + hw1, y1)
                    dash.lineTo(vp.x - hw1, y1)
                    dash.close()
                    drawPath(dash, red.copy(alpha = 0.15f + 0.85f * t0))
                }
            }
        }
    }
}

/** A closed ridge line along the horizon [y]: each pair is (x as a share of [w], height as a share of [h]). */
private fun crRidge(w: Float, y: Float, h: Float, points: List<Pair<Float, Float>>): Path = Path().apply {
    moveTo(0f, y)
    points.forEach { (fx, rise) -> lineTo(w * fx, y - h * rise) }
    lineTo(w, y)
    close()
}

/** The next turn over the road: its distance large, its words beside it, on smoked glass with a red rim. */
@Composable
private fun CrimsonTurn(nav: NavState, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(DashColors.Card.copy(alpha = 0.82f), shape)
            .border(1.dp, DashColors.Accent.copy(alpha = 0.7f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = nav.icon
        if (icon != null) {
            val bmp = remember(icon) { icon.asImageBitmap() }
            Image(bmp, contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
        }
        if (nav.distance.isNotBlank()) {
            CrText(nav.distance, crHero(fixedSp(24f), DashColors.TextPrimary))
            Spacer(Modifier.width(10.dp))
        }
        CrText(nav.instruction, crBody(fixedSp(15f), DashColors.TextSecondary), Modifier.weight(1f, fill = false), maxLines = 2)
    }
}

// --- Player (MEDIA) -----------------------------------------------------------------

/**
 * The player bar: the cover (a tap opens the player), title and artist, previous / play-pause / next,
 * a lit equaliser while it plays and, under it all, the position and length with a progress bar.
 * It reads the active media session, and on an FYT unit the stock player's own updates
 * ([HeadUnitMedia]), so it needs no notification access for the latter. With neither, a tap on the
 * bar opens the access page.
 */
@Composable
internal fun CrimsonPlayer(env: SkinTileEnv) {
    val ms = env.mediaState
    val hasTrack = ms.hasMedia && ms.title.isNotBlank()
    val access = env.hasMediaAccess || hasTrack
    val controller = env.mediaController
    val context = env.context
    val position = rememberMediaPosition(ms, controller)
    val fraction = if (ms.durationMs > 0L) (position.toFloat() / ms.durationMs).coerceIn(0f, 1f) else 0f
    val tap = rememberTapFeedback()
    val openPlayer = {
        val pkg = if (HeadUnitMedia.fyt != null && ms.hasMedia && ms.title == HeadUnitMedia.fyt?.title) HeadUnitMedia.FYT_PACKAGE
        else CarMediaController.getLastMediaPackage(context)
        if (pkg != null) env.onLaunchApp(pkg)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
            .crimsonPanel(24.dp)
            .crTap(!access && !env.editing, stringResource(R.string.info_media_access_needed)) {
                CarMediaController.openNotificationAccessSettings(context)
            }
    ) {
        val h = maxHeight
        val w = maxWidth
        val showProgress = hasTrack && ms.durationMs > 0L && h >= 76.dp
        val padV = (h.value * 0.09f).coerceIn(5f, 14f).dp
        val rowH = (h - padV * 2 - (if (showProgress) 20.dp else 0.dp)).coerceAtLeast(34.dp)
        val art = minOf(rowH, 110.dp)
        val titleSp = (rowH.value * 0.30f).coerceIn(14f, 24f)
        val btn = (rowH.value * 0.80f).coerceIn(40f, 62f)
        val wide = w >= 760.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = padV)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val coverShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .size(art)
                        .clip(coverShape)
                        .background(DashColors.CardHi)
                        .border(1.dp, DashColors.Accent.copy(alpha = 0.6f), coverShape)
                        .clickable(enabled = access && !env.editing, role = Role.Button) { tap(); openPlayer() },
                    contentAlignment = Alignment.Center
                ) {
                    val cover = ms.artwork
                    if (cover != null && access) {
                        val bmp = remember(cover) { cover.asImageBitmap() }
                        Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DashColors.Accent, modifier = Modifier.size(art * 0.45f))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    CrText(
                        when {
                            !access -> stringResource(R.string.info_media_access_needed)
                            hasTrack -> ms.title
                            else -> stringResource(R.string.info_nothing_playing)
                        },
                        crBody(fixedSp(titleSp), if (hasTrack && access) DashColors.TextPrimary else DashColors.TextSecondary, FontWeight.Bold)
                    )
                    val second = when {
                        !access -> stringResource(R.string.skin_motorsport_tap_allow_caps)
                        hasTrack -> ms.artist
                        else -> ""
                    }
                    if (second.isNotBlank()) {
                        CrText(second, crBody(fixedSp(titleSp * 0.72f), if (access) DashColors.Muted else DashColors.Accent))
                    }
                }
                if (access) {
                    Spacer(Modifier.width(10.dp))
                    CrControl(Icons.Filled.SkipPrevious, stringResource(R.string.info_media_previous), (btn * 0.78f).dp) { controller.previous() }
                    Spacer(Modifier.width(6.dp))
                    CrPlayButton(ms.isPlaying, btn.dp) { controller.playPause() }
                    Spacer(Modifier.width(6.dp))
                    CrControl(Icons.Filled.SkipNext, stringResource(R.string.info_media_next), (btn * 0.78f).dp) { controller.next() }
                }
                if (wide && access) {
                    Spacer(Modifier.width(24.dp))
                    CrEqualizer(ms.isPlaying, Modifier.width(34.dp).height((rowH.value * 0.55f).dp))
                }
            }
            if (showProgress) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CrText(formatTrackTime(position), crBody(fixedSp(12f), DashColors.Muted))
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(DashColors.Muted.copy(alpha = 0.3f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Brush.horizontalGradient(listOf(DashColors.Accent2, DashColors.Accent)))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    CrText(formatTrackTime(ms.durationMs), crBody(fixedSp(12f), DashColors.Muted))
                }
            }
        }
    }
}

/** A transport key without a plate: just its icon in a 48 dp (or bigger) target. */
@Composable
private fun CrControl(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    val tap = rememberTapFeedback()
    Box(
        Modifier
            .size(size.coerceAtLeast(48.dp))
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button) { tap(); onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = DashColors.TextPrimary, modifier = Modifier.size(size * 0.6f))
    }
}

/** The play / pause button: a red ring (with a glow at night) round the icon. */
@Composable
private fun CrPlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val tap = rememberTapFeedback()
    val label = stringResource(R.string.info_media_play_pause)
    Box(
        Modifier
            .size(size)
            .glowHalo(DashColors.Accent.copy(alpha = 0.45f), 0.9f)
            .clip(CircleShape)
            .background(DashColors.Card.copy(alpha = 0.7f), CircleShape)
            .border(2.dp, DashColors.Accent, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button) { tap(); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null, tint = DashColors.TextPrimary, modifier = Modifier.size(size * 0.5f)
        )
    }
}

private val EqPeriods = intArrayOf(520, 410, 640, 470, 580)

/** Five bars that dance while the music plays (still with effects off, low while paused). */
@Composable
private fun CrEqualizer(playing: Boolean, modifier: Modifier) {
    if (playing && DashColors.Effects != DashEffects.NONE) {
        val transition = rememberInfiniteTransition(label = "crimsonEq")
        val bars = EqPeriods.map { ms ->
            transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse),
                label = "crimsonEqBar"
            )
        }
        CrEqCanvas(modifier) { i -> bars[i].value }
    } else {
        CrEqCanvas(modifier) { _ -> 0.25f }
    }
}

@Composable
private fun CrEqCanvas(modifier: Modifier, level: (Int) -> Float) {
    val red = DashColors.Accent
    Canvas(modifier) {
        val n = EqPeriods.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = size.height * level(i).coerceIn(0.1f, 1f)
            drawRoundRect(
                red, Offset(i * (bw + gap), size.height - bh), Size(bw, bh), CornerRadius(bw / 2f)
            )
        }
    }
}
