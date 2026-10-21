package com.openauto.dash

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.max

/*
 * Crimson skin: a red-and-black sports-car cockpit. The page is near-black with
 * a red glow where the road meets the horizon and flares at both edges. The top
 * bar carries the clock, the drive-mode strip (ECO, COMFORT, SPORT, SPORT+) and
 * the link lamps (the OBD adapter's Bluetooth, the GPS fix, the outside
 * temperature). The tiles are the cluster: a speedometer (SPEED_HUD) with the fuel
 * bar and range, a tachometer (TELEMETRY) with the coolant bar, a night road
 * with the car (NAVIGATION: its lines run with the real speed and the next turn
 * is laid over it) and a slim player bar (MEDIA). Everything else keeps its
 * standard renderer on this palette.
 *
 * The drive mode is a look, not a car control (a head unit cannot change the
 * car's mode): it scales how hard the gauges glow, and is remembered.
 *
 * Performance on the head unit: the page, the dial faces and the scene's still
 * parts are drawn once per size (cachedDraw); live values (the needle, the
 * lines, the equaliser) are read inside draw lambdas, so a new reading
 * redraws a dial and does not recompose the page.
 */

internal val CrimsonPalette = DashPalette(
    Background = Color(0xFF050505), Bar = Color.Transparent, Card = Color(0xFF110909), CardHi = Color(0xFF241010),
    Accent = Color(0xFFFF1E2D), Secondary = Color(0xFFFF6B6B), Critical = Color(0xFFFF2E2E), Good = Color(0xFF2BD96B),
    Muted = Color(0xFF9A8686), TextPrimary = Color(0xFFF7F3F3), TextSecondary = Color(0xFFC9BABA), Accent2 = Color(0xFF9E0010),
    Line = Color(0x33FF3B3B), Glow = 0.85f, Warning = Color(0xFFFFB020), Font = DashFont.CONDENSED, HeroWeight = FontWeight.Bold,
    Bare = true, Skin = DashSkin.CRIMSON
)

internal val CrimsonLightPalette = DashPalette(
    Background = Color(0xFFF3EEEE), Bar = Color.Transparent, Card = Color.White, CardHi = Color(0xFFE6DCDC),
    Accent = Color(0xFFB0000F), Secondary = Color(0xFF8A1A22), Critical = Color(0xFFC8101A), Good = Color(0xFF13914A),
    Muted = Color(0xFF6B5A5A), TextPrimary = Color(0xFF1A0A0A), TextSecondary = Color(0xFF4A3838), Accent2 = Color(0xFF7A0009),
    Line = Color(0x2A000000), Glow = 0.25f, Light = true, Warning = AmberDay, Font = DashFont.CONDENSED, HeroWeight = FontWeight.Bold,
    Bare = true, Skin = DashSkin.CRIMSON
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val CrimsonShapes = skinShapes(22, 18, 10)

// --- Drive mode ---------------------------------------------------------------------

/**
 * ECO, COMFORT, SPORT or SPORT+ as the driver picked it in the bar (kept across
 * restarts). It only changes the look: [glow] scales every gauge's halo.
 */
internal object CrimsonDrive {
    private const val PREFS = "crimson_skin"
    private const val KEY = "drive_mode"
    const val DEFAULT = 2

    var mode by mutableIntStateOf(DEFAULT)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        mode = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT).coerceIn(0, 3)
    }

    fun set(context: Context, value: Int) {
        mode = value.coerceIn(0, 3)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
    }

    /** How strong the gauges glow: calm in ECO, hottest in SPORT+. */
    val glow: Float get() = when (mode) {
        0 -> 0.55f
        1 -> 0.8f
        2 -> 1f
        else -> 1.25f
    }
}

private val DriveModes = listOf("ECO", "COMFORT", "SPORT", "SPORT+")

// --- Type ---------------------------------------------------------------------------

/** The wide italic of the numerals (speed, revs, clock), with a faint glow of its colour at night. */
internal fun crHero(size: androidx.compose.ui.unit.TextUnit, color: Color, glow: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    lineHeight = 1.0.em,
    letterSpacing = (-0.02).em,
    shadow = if (glow && !DashColors.Light) softTextShadow(color.copy(alpha = 0.55f), size.value * 0.3f) else null
)

/** Plain sans for words (a song, a street, a unit). */
internal fun crBody(size: androidx.compose.ui.unit.TextUnit, color: Color = DashColors.TextSecondary, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
    color = color,
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = readable(size),
    lineHeight = 1.2.em
)

/** One line of text that ends in an ellipsis rather than wrapping or spilling. */
@Composable
internal fun CrText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(text, modifier = modifier, style = style, maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis)
}

/** Makes the box tappable while [enabled]; otherwise leaves it alone (a tile that is only a reading). */
@Composable
internal fun Modifier.crTap(enabled: Boolean, label: String, onClick: () -> Unit): Modifier {
    if (!enabled) return this
    val tap = rememberTapFeedback()
    return this.clickable(onClickLabel = label, role = Role.Button) { tap(); onClick() }
}

// --- Page background ----------------------------------------------------------------

/**
 * The whole screen: near-black, a red glow where the road meets the horizon,
 * a flare at each side edge, a thin red light under the top bar and a vignette.
 * Built once per size and appearance; nothing on it moves. With effects off it
 * is the plain page colour, for the clearest read in full sun.
 */
@Composable
internal fun crimsonBackground(): Modifier {
    val light = DashColors.Light
    val effects = DashColors.Effects
    return Modifier.cachedDraw(light, effects) {
        val w = size.width
        val h = size.height
        val red = DashColors.Accent
        val page = DashColors.Background
        val flat = effects == DashEffects.NONE
        val k = effects.scale
        val horizon = Offset(w / 2f, h * 0.46f)
        val core = Brush.radialGradient(
            listOf(red.copy(alpha = (if (light) 0.10f else 0.34f) * k), red.copy(alpha = 0f)),
            center = horizon, radius = max(w, h) * 0.5f
        )
        val flareL = Brush.radialGradient(
            listOf(red.copy(alpha = (if (light) 0.08f else 0.28f) * k), red.copy(alpha = 0f)),
            center = Offset(0f, h * 0.55f), radius = h * 0.55f
        )
        val flareR = Brush.radialGradient(
            listOf(red.copy(alpha = (if (light) 0.08f else 0.28f) * k), red.copy(alpha = 0f)),
            center = Offset(w, h * 0.55f), radius = h * 0.55f
        )
        val vignette = Brush.radialGradient(
            0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = if (light) 0.08f else 0.55f),
            center = Offset(w / 2f, h / 2f), radius = max(w, h) * 0.75f
        )
        val lineLeft = w * 0.16f
        val lineRight = w * 0.84f
        val topLine = Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to red.copy(alpha = 0.9f), 1f to Color.Transparent,
            startX = lineLeft, endX = lineRight
        )
        onDrawBehind {
            drawRect(page)
            if (!flat) {
                drawRect(core)
                drawRect(flareL)
                drawRect(flareR)
                drawRect(vignette)
                drawRect(topLine, topLeft = Offset(lineLeft, 2.dp.toPx()), size = Size(lineRight - lineLeft, 2.dp.toPx()))
            }
        }
    }
}

// --- Panels -------------------------------------------------------------------------

/**
 * A smoked-glass panel with a red rim that is brightest along its lower edge (the
 * mockup's player bar). Gradients are built once per size.
 */
@Composable
internal fun Modifier.crimsonPanel(radius: Dp = 22.dp): Modifier {
    val light = DashColors.Light
    val effects = DashColors.Effects
    return this.cachedDraw(radius, light, effects) {
        val r = radius.toPx()
        val red = DashColors.Accent
        val fill = Brush.verticalGradient(
            listOf(
                if (light) Color.White else Color(0xFF160B0B),
                if (light) Color(0xFFEFE6E6) else Color(0xFF070404)
            )
        )
        val rim = Brush.verticalGradient(listOf(red.copy(alpha = 0.18f), red.copy(alpha = if (effects == DashEffects.NONE) 0.6f else 0.95f)))
        val stroke = Stroke(1.5.dp.toPx())
        val inset = stroke.width / 2f
        onDrawBehind {
            drawRoundRect(fill, cornerRadius = CornerRadius(r))
            drawRoundRect(
                rim, topLeft = Offset(inset, inset), size = Size(size.width - stroke.width, size.height - stroke.width),
                cornerRadius = CornerRadius(max(0f, r - inset)), style = stroke
            )
        }
    }
}

/** What a widget this skin does not redraw sits on, so a page keeps one look. */
@Composable
internal fun Modifier.crimsonFallbackPanel(): Modifier = this.crimsonPanel(18.dp)

// --- Top bar ------------------------------------------------------------------------

private val CR_BAR_H = 72.dp

/**
 * Same order as every other look's bar: APPS and LAYOUT buttons, the clock and
 * the dashboard's position on the left; the drive-mode strip in the middle
 * (only where it fits); on the right the setup pill, alerts, the phone, the
 * link lamps and ⋮. While guidance runs a red arrow sits beside the clock.
 */
@Composable
internal fun CrimsonTopBar(m: TopBarModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { CrimsonDrive.load(context) }
    val nav by NavDirections.state.collectAsState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(CR_BAR_H)
            .graphicsLayer()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        val narrow = maxWidth < NARROW_BAR
        val shared = barIsShared()
        val showModes = maxWidth >= 1_000.dp
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrimsonBarButton(onClick = m.onApps, description = stringResource(R.string.dash_all_apps)) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = DashColors.TextPrimary, modifier = Modifier.size(22.dp))
                }
                LayoutPicker(m) { open ->
                    CrimsonBarButton(onClick = open, description = stringResource(R.string.dash_screen_layout, m.layout.title)) {
                        LayoutIcon(m.layout, null, DashColors.TextPrimary, Modifier.size(22.dp))
                    }
                }
                // The head unit's status bar shows the time while it is up.
                if (!m.merged) {
                    Spacer(Modifier.width(8.dp))
                    CrimsonClock(m.clock)
                }
                if (nav.active) {
                    Icon(
                        Icons.Filled.Navigation, contentDescription = null, tint = DashColors.Accent,
                        modifier = Modifier.padding(start = 10.dp).size(22.dp)
                    )
                }
                PagePositionChip(Modifier.padding(start = 8.dp))
            }
            if (showModes) {
                Box(Modifier.weight(1.3f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { CrimsonModes() }
            }
            BarEnd(modifier = Modifier.weight(1f)) {
                if (m.setupPending) {
                    Box(Modifier.layoutId(BarRank.SETUP).padding(end = 6.dp)) {
                        SetupPill(onClick = { m.onSetup(false) }, compact = narrow || shared)
                    }
                }
                Row(modifier = Modifier.layoutId(BarRank.ALERTS), verticalAlignment = Alignment.CenterVertically) {
                    VehicleAlerts(m.obdConnection, m.obd)
                }
                Box(Modifier.layoutId(BarRank.PHONE)) { PhonePill() }
                Box(Modifier.layoutId(BarRank.TEMP).padding(horizontal = 6.dp)) { CrimsonGpsAndTemp() }
                Box(Modifier.layoutId(BarRank.OBD)) { CrimsonObdLamp(m.obdConnection, m.onConnectObd) }
                Box(Modifier.layoutId(BarRank.MORE)) {
                    MorePicker(m) { open ->
                        CrimsonBarButton(onClick = open, description = stringResource(R.string.dash_more)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = DashColors.TextPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A round smoked button with a red rim, in a 48 dp target. */
@Composable
private fun CrimsonBarButton(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val tap = rememberTapFeedback()
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(DashColors.CardHi, DashColors.Card)), CircleShape)
            .border(1.5.dp, DashColors.Accent.copy(alpha = 0.75f), CircleShape)
            .clickable(onClickLabel = description, role = Role.Button) { tap(); onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** "10:24" in the wide italic, AM / PM small beside it. */
@Composable
private fun CrimsonClock(clock: String) {
    val (digits, amPm) = splitClock(clock)
    Row(verticalAlignment = Alignment.Bottom) {
        CrText(digits, crHero(30.sp, DashColors.TextPrimary), Modifier.alignByBaseline())
        if (amPm != null) {
            Spacer(Modifier.width(4.dp))
            CrText(amPm, crBody(15.sp, DashColors.TextSecondary, FontWeight.SemiBold), Modifier.alignByBaseline())
        }
    }
}

/**
 * ECO | COMFORT | SPORT | SPORT+: the chosen one in red with a light under it.
 * Each label is a 48 dp target; the choice is kept.
 */
@Composable
private fun CrimsonModes() {
    val context = LocalContext.current
    val selected = CrimsonDrive.mode
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(DashColors.Card.copy(alpha = 0.55f), shape)
            .border(1.dp, DashColors.Line, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DriveModes.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = label, role = Role.Button) { CrimsonDrive.set(context, i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CrText(
                    label,
                    crBody(15.sp, if (on) DashColors.Accent else DashColors.TextPrimary, if (on) FontWeight.Bold else FontWeight.Medium)
                        .copy(letterSpacing = 0.06.em, shadow = if (on) softTextShadow(DashColors.Accent.copy(alpha = 0.6f), 14f) else null)
                )
                if (on) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(30.dp)
                            .height(2.dp)
                            .background(DashColors.Accent, RoundedCornerShape(1.dp))
                    )
                }
            }
            if (i < DriveModes.lastIndex) {
                Box(Modifier.width(1.dp).height(20.dp).background(DashColors.Line))
            }
        }
    }
}

/** The OBD adapter's Bluetooth: lit while linked, dim when not (tap to connect), a pulse-free "connecting" in between. */
@Composable
private fun CrimsonObdLamp(state: ObdConnectionState, onConnect: () -> Unit) {
    val tap = rememberTapFeedback()
    val label = stringResource(R.string.skin_motorsport_connect_obd)
    val connected = state == ObdConnectionState.CONNECTED
    val tint = when {
        connected -> DashColors.Accent
        state == ObdConnectionState.CONNECTING -> DashColors.Warning
        else -> DashColors.Muted
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = state.isIdle, onClickLabel = label, role = Role.Button) { tap(); onConnect() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (connected || state == ObdConnectionState.CONNECTING) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)
        )
    }
}

/** Four signal bars for the GPS fix (all lit while a fresh fix is in, dim without) and the outside temperature. */
@Composable
private fun CrimsonGpsAndTemp() {
    UseLocationFeed()
    val fix by LocationFeed.freshSpeedKmh.collectAsState()
    val units = LocalUnits.current
    val outside = rememberOutsideTempC()
    val lit = DashColors.TextPrimary
    val dim = DashColors.Muted.copy(alpha = 0.4f)
    val hasFix = fix != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 1..4) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight(i / 4f)
                        .background(if (hasFix) lit else dim, RoundedCornerShape(1.dp))
                )
            }
        }
        if (outside != null) {
            Spacer(Modifier.width(10.dp))
            CrText("${units.temp(outside)}${units.tempUnit}", crBody(18.sp, DashColors.TextPrimary, FontWeight.Medium))
        }
    }
}

// --- Maps window frame --------------------------------------------------------------

/**
 * A dark bezel with a red inner rim round a docked app window (Google Maps): the
 * corners rounded off in the page colour and a glow down from the top edge. Takes
 * no touches and leaves the middle clear.
 */
@Composable
internal fun CrimsonWindowFrame(modifier: Modifier) {
    val light = DashColors.Light
    Box(
        modifier = modifier
            .fillMaxSize()
            .cachedDraw(light) {
                val w = size.width
                val h = size.height
                val r = 18.dp.toPx()
                val bezel = 4.dp.toPx()
                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, w, h))
                    addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(r)))
                }
                val page = DashColors.Background
                val red = DashColors.Accent
                val bezelInk = Brush.verticalGradient(
                    listOf(if (light) Color(0xFFF4EDED) else Color(0xFF2A1212), if (light) Color(0xFFCFC2C2) else Color(0xFF0B0505))
                )
                val glowH = 28.dp.toPx()
                val glow = Brush.verticalGradient(
                    listOf(red.copy(alpha = 0.35f * DashColors.Effects.scale), red.copy(alpha = 0f)),
                    startY = bezel, endY = bezel + glowH
                )
                val bezelStroke = Stroke(bezel)
                val rimStroke = Stroke(2.dp.toPx())
                onDrawBehind {
                    drawPath(mask, page)
                    if (DashColors.Effects != DashEffects.NONE) drawRect(glow, Offset(bezel, bezel), Size(w - bezel * 2f, glowH))
                    drawRoundRect(bezelInk, Offset(bezel / 2f, bezel / 2f), Size(w - bezel, h - bezel), CornerRadius(r - bezel / 2f), style = bezelStroke)
                    val inset = bezel + rimStroke.width / 2f
                    drawRoundRect(red, Offset(inset, inset), Size(w - inset * 2f, h - inset * 2f), CornerRadius(max(0f, r - inset)), style = rimStroke)
                }
            }
    )
}

// --- Theme stamp --------------------------------------------------------------------

/** The skin as a stamp in the theme picker: two dials with red arcs, the glow behind and the player bar. */
internal fun DrawScope.crimsonStamp(p: DashPalette, number: StampNumber) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            listOf(p.Accent.copy(alpha = if (p.Light) 0.14f else 0.38f), Color.Transparent),
            center = Offset(w / 2f, h * 0.5f), radius = w * 0.6f
        )
    )
    val r = h * 0.3f
    val cy = h * 0.46f
    for (cx in listOf(w * 0.25f, w * 0.75f)) {
        val tl = Offset(cx - r, cy - r)
        val box = Size(r * 2f, r * 2f)
        drawArc(p.Line, 150f, 240f, false, tl, box, style = Stroke(2f, cap = StrokeCap.Round))
        drawArc(p.Accent, 150f, if (cx < w / 2f) 150f else 120f, false, tl, box, style = Stroke(4f, cap = StrokeCap.Round))
    }
    number("120", w * 0.25f, cy, 14f, p.TextPrimary)
    number("3.8", w * 0.75f, cy, 14f, p.TextPrimary)
    // The player bar with its red lower edge.
    drawRoundRect(p.Card, Offset(w * 0.07f, h * 0.8f), Size(w * 0.86f, h * 0.15f), CornerRadius(h * 0.07f))
    drawRoundRect(p.Accent, Offset(w * 0.12f, h * 0.9f), Size(w * 0.36f, h * 0.02f), CornerRadius(2f))
    drawCircle(p.Accent, h * 0.045f, Offset(w * 0.5f, h * 0.875f), style = Stroke(2f))
}
