package com.openauto.dash

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A touchscreen that feels no touch in one upright strip (Settings → Expert →
 * Lab): a worn or damaged panel. One driver's K706 misses every touch from
 * about 265 to 435 px across, full height; Settings' pane began at 270 px, so
 * its back arrow and the left of every row were out of reach. With the strip
 * measured, Settings' side bar grows past it and its pane opens to the right
 * of it; nothing else moves.
 *
 * Measured once with a slide across the screen: the panel reports the finger up
 * to one edge of the strip and again from the other, so the strip is the
 * widest jump between two reports close in time ([find]). Kept in screen
 * pixels, which on these units are the window's (full screen, no bars).
 */
internal object DeadTouchStrip {
    private val FILE = PrefFile("dead_touch_strip")
    private val ON = FILE.bool("on", default = false)
    private val START = FILE.int("start_px", -1)
    private val END = FILE.int("end_px", -1)

    /** The strip, [startPx] to [endPx] across the screen, in pixels. */
    data class Strip(val startPx: Int, val endPx: Int)

    private val _on = MutableStateFlow(false)
    private val _measured = MutableStateFlow<Strip?>(null)
    @Volatile private var loaded = false

    /** Whether Dashwheel keeps clear of the strip. */
    val on: StateFlow<Boolean> = _on.asStateFlow()

    /** The strip as last measured, null before. */
    val measured: StateFlow<Strip?> = _measured.asStateFlow()

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = FILE.open(context)
        _on.value = prefs[ON]
        val start = prefs[START]
        val end = prefs[END]
        _measured.value = if (start >= 0 && end > start) Strip(start, end) else null
    }

    fun setOn(context: Context, value: Boolean) {
        _on.value = value
        FILE[context, ON] = value
    }

    fun save(context: Context, strip: Strip) {
        _measured.value = strip
        FILE.edit(context) {
            put(START, strip.startPx)
            put(END, strip.endPx)
        }
    }

    /** The strip to keep clear of: measured and switched on. */
    @Composable
    fun active(): Strip? {
        load(LocalContext.current)
        val on by on.collectAsState()
        val strip by measured.collectAsState()
        return strip.takeIf { on }
    }

    /**
     * How wide a side bar starting at [leftPx] (screen pixels) must be for the pane
     * beside it to start past [strip]: [normalPx] when the strip ends before the
     * pane, or starts more than [reachPx] into it (it then only crosses rows, their
     * start stays in reach); else up to [gapPx] past the strip, at most [maxPx].
     */
    fun sideBarWidth(strip: Strip, leftPx: Float, normalPx: Float, reachPx: Float, gapPx: Float, maxPx: Float): Float {
        val paneStart = leftPx + normalPx
        if (strip.endPx <= paneStart || strip.startPx >= paneStart + reachPx) return normalPx
        return (strip.endPx + gapPx - leftPx).coerceIn(normalPx, maxOf(normalPx, maxPx))
    }

    /** Reports this far apart in time belong to separate slides, not one finger lost in the strip. */
    private const val SAME_SLIDE_MS = 400L

    /** Pixels added on each side: the edge reports are where the finger was last felt, not where feeling stops. */
    private const val MARGIN_PX = 6

    /**
     * The dead strip from finger reports ([timeMs] to screen x), in the order they
     * came: the widest jump between two reports less than [SAME_SLIDE_MS] apart,
     * if it is at least [minJumpPx] and far wider than the slide's usual step.
     * The panel may drop the finger at the strip and find it again past it, so
     * the end of one touch and the start of the next count as one slide. Null
     * when nothing stands out.
     */
    fun find(reports: List<Pair<Long, Float>>, minJumpPx: Float): Strip? {
        if (reports.size < 3) return null
        val steps = reports.zipWithNext { a, b -> Triple(b.first - a.first, a.second, b.second) }
            .filter { it.first in 0..SAME_SLIDE_MS }
        if (steps.isEmpty()) return null
        val usual = steps.map { abs(it.third - it.second) }.sorted()[steps.size / 2]
        val widest = steps.maxByOrNull { abs(it.third - it.second) } ?: return null
        val jump = abs(widest.third - widest.second)
        if (jump < minJumpPx || jump < usual * 6) return null
        val from = minOf(widest.second, widest.third).roundToInt() + MARGIN_PX
        val to = maxOf(widest.second, widest.third).roundToInt() - MARGIN_PX
        return if (to > from) Strip(from, to) else null
    }
}

/**
 * The whole screen to slide a finger across: the strip found shows in red,
 * Save and Cancel sit at the right, clear of where a strip usually is.
 */
@Composable
internal fun DeadStripMeasure(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reports = remember { mutableStateListOf<Pair<Long, Float>>() }
    var found by remember { mutableStateOf(DeadTouchStrip.measured.value) }
    // From the dialog's own corner to the screen's: the strip is kept in screen pixels.
    var originX by remember { mutableStateOf(0f) }
    val minJump = with(density) { 40.dp.toPx() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DashColors.Card.copy(alpha = 1f))
                .onGloballyPositioned { coords ->
                    val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
                    originX = coords.positionInWindow().x + onScreen[0]
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        reports += down.uptimeMillis to down.position.x + originX
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            reports += change.uptimeMillis to change.position.x + originX
                            if (!change.pressed) break
                        }
                        DeadTouchStrip.find(reports, minJump)?.let { found = it }
                    }
                }
        ) {
            found?.let { strip ->
                val start = with(density) { (strip.startPx - originX).toDp() }
                val width = with(density) { (strip.endPx - strip.startPx).toFloat().toDp() }
                Box(
                    Modifier
                        .offset(x = start)
                        .width(width)
                        .fillMaxHeight()
                        .background(Color(0x66E5484D))
                )
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    stringResource(R.string.dead_strip_title),
                    color = DashColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dead_strip_hint),
                    color = DashColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    found?.let { stringResource(R.string.dead_strip_result, it.startPx, it.endPx) }
                        ?: stringResource(R.string.dead_strip_nothing),
                    color = if (found != null) DashColors.TextPrimary else DashColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Full buttons, as in a settings pane, not a dialog's text ones.
                CompositionLocalProvider(LocalSheetInPane provides true) {
                    SheetButton(stringResource(R.string.dash_cancel), primary = false, onClick = onDismiss)
                    if (found != null) SheetButton(stringResource(R.string.dead_strip_save)) {
                        found?.let {
                            DeadTouchStrip.save(context, it)
                            DeadTouchStrip.setOn(context, true)
                        }
                        onDismiss()
                    }
                }
            }
        }
    }
}
