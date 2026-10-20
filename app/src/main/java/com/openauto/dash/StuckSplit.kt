package com.openauto.dash

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A split the firmware left on under a dashboard that has the whole screen
 * again: Android still says multi-window, so the unit's top bar stays forced
 * and a minimized pane or the divider sits over the dashboard (the Glyph
 * look's Map key on some units), or the dashboard keeps its half beside a
 * pane left black. Once things settle, the dashboard leaves
 * that split: through the accessibility service's toggle, which exits split
 * on this ROM, or else by closing the docked stack through the privileged
 * shell ([DockShell]). At most [MAX_TRIES] times per split, so a ROM that
 * will not let go is not fought forever.
 */
internal object StuckSplit {

    private const val TAG = "SplitA11yService"

    /** Time for a split coming up or going away to settle before it is judged. */
    private const val SETTLE_MS = 1_500L

    /** A split asked for this recently may still be filling its other half. */
    private const val RECENT_REQUEST_MS = 6_000L

    private const val MAX_TRIES = 2

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Tries made in the current split; back to 0 once the dashboard is out of multi-window. */
    private var tries = 0
    private var pending: Runnable? = null

    /** Android's memory of the dashboard as a floating window was cleared in this process ([forgetFloating]). */
    private var floatingForgotten = false

    /** From the dashboard's resume / multi-window / configuration changes (main thread). */
    fun check(activity: MainActivity) {
        pending?.let(main::removeCallbacks)
        pending = null
        if (!activity.isInMultiWindowMode) {
            tries = 0
            return
        }
        if (tries >= MAX_TRIES) return
        val ref = WeakReference(activity)
        pending = Runnable { pending = null; ref.get()?.let(::settled) }.also { main.postDelayed(it, SETTLE_MS) }
    }

    private fun settled(activity: MainActivity) {
        if (activity.isFinishing || activity.isDestroyed || tries >= MAX_TRIES) return
        val display = Point()
        @Suppress("DEPRECATION")
        activity.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealSize(display) ?: return
        val config = activity.resources.configuration
        val density = activity.resources.displayMetrics.density
        val mode = windowingModeOf(config.toString())
        if (isFloatingMode(mode) && !floatingForgotten) forgetFloating(activity)
        if (isShrunkFloatingHome(
                windowingMode = mode,
                windowWidth = (config.screenWidthDp * density).toInt(),
                windowHeight = (config.screenHeightDp * density).toInt(),
                displayWidth = display.x,
                displayHeight = display.y,
                mapsDocked = PipAnchor.dockActive.value
            )
        ) {
            tries++
            growToFullScreen(activity, display)
            return
        }
        // A floating dashboard back on the whole screen: a later shrink gets its tries again.
        if (isFloatingMode(mode)) tries = 0
        val stuck = isStuckSplit(
            inMultiWindow = activity.isInMultiWindowMode,
            windowingMode = mode,
            windowWidth = (config.screenWidthDp * density).toInt(),
            windowHeight = (config.screenHeightDp * density).toInt(),
            displayWidth = display.x,
            displayHeight = display.y,
            mapsDocked = PipAnchor.dockActive.value,
            nothingBeside = SplitAccessibilityService.nothingBeside(),
            splitRequestedRecently = SystemClock.elapsedRealtime() - SplitAccessibilityService.lastRequestAt < RECENT_REQUEST_MS
        )
        if (!stuck) return
        tries++
        Log.i(TAG, "split left on with no app beside the dashboard: leaving it (try $tries of $MAX_TRIES)")
        // The toggle first; the second try, or with the service off, closes the docked stack.
        if (tries == 1 && SplitAccessibilityService.leaveStuckSplit()) {
            check(activity)
            return
        }
        val ref = WeakReference(activity)
        val self = activity.packageName
        val app = activity.applicationContext
        scope.launch {
            runCatching {
                val stack = WindowListing.dockedStackToClose(DockShell.listStacks(app), self)
                if (stack == null) {
                    Log.w(TAG, "stuck split: no docked stack to close")
                } else {
                    val out = DockShell.shell(app, "am stack remove $stack")
                    Log.i(TAG, "stuck split: docked stack $stack closed ${out.trim()}")
                }
            }.onFailure { Log.w(TAG, "stuck split: no shell to close the docked stack: ${it.message}") }
            ref.get()?.let(::check)
        }
    }

    /**
     * The home screen is a floating window: Android remembered the dashboard
     * as one and makes it one again at every start, and on a QF unit the
     * Recents screen then stayed over it with nothing to touch. That memory
     * is cleared ([LaunchParamsReset], through the privileged shell); the
     * home screen it already made stays floating until the unit restarts.
     * Once per process, the outcome in the event log.
     */
    private fun forgetFloating(activity: MainActivity) {
        floatingForgotten = true
        val app = activity.applicationContext
        scope.launch {
            val out = runCatching {
                DockShell.shell(app, LaunchParamsReset.command(app.applicationInfo.sourceDir, app.packageName)).trim()
            }.getOrElse { "no shell: ${it.message}" }
            Log.i(TAG, "home is a floating window: Android's memory of it cleared: $out")
            DebugLog.note(app, "home is a floating window: Android's memory of it cleared: ${out.lines().lastOrNull().orEmpty()}")
            // A QF unit opens its navigation app as a floating map window (its launchers'
            // map card): with the NAVI key on Dashwheel, that floated the home screen itself.
            if (navTargetFloatsHome(SysProps.get(KeyTargets.Key.NAVI.prop), app.packageName)) {
                val given = KeyTargets.set(app, KeyTargets.Key.NAVI, null)
                DebugLog.note(app, "home is a floating window: the NAVI key opened Dashwheel, handed back to the unit: $given")
            }
        }
    }

    /**
     * The dashboard is a floating window (some QF units' home) shrunk to part
     * of the screen, the rest left black: its task is sized back to the whole
     * display through the privileged shell ([DockShell]). Android lets a
     * floating task be resized; the split toggle would only have made a split.
     */
    private fun growToFullScreen(activity: MainActivity, display: Point) {
        val taskId = activity.taskId
        val app = activity.applicationContext
        val ref = WeakReference(activity)
        Log.i(TAG, "floating dashboard on part of the screen: task $taskId back to ${display.x}x${display.y} (try $tries of $MAX_TRIES)")
        scope.launch {
            runCatching {
                val out = DockShell.shell(app, "am task resize $taskId 0 0 ${display.x} ${display.y}")
                Log.i(TAG, "floating dashboard resized ${out.trim()}")
            }.onFailure { Log.w(TAG, "floating dashboard: no shell to resize it: ${it.message}") }
            ref.get()?.let(::check)
        }
    }
}

/**
 * Whether the dashboard is a floating window that no longer covers the whole
 * display (a black part beside it). Not while the Maps dock shares the screen.
 */
internal fun isShrunkFloatingHome(
    windowingMode: String?,
    windowWidth: Int,
    windowHeight: Int,
    displayWidth: Int,
    displayHeight: Int,
    mapsDocked: Boolean
): Boolean {
    if (!isFloatingMode(windowingMode) || mapsDocked) return false
    if (windowWidth <= 0 || windowHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) return false
    return windowWidth < displayWidth * 0.9f || windowHeight < displayHeight * 0.75f
}

/**
 * Whether the unit's navigation app, which a QF unit opens as a floating map
 * window, is the dashboard itself ([navTarget] is `persist.sys.maps`).
 */
internal fun navTargetFloatsHome(navTarget: String?, selfPackage: String): Boolean = navTarget == selfPackage

/** The freeform windowing mode, by name or number. */
internal fun isFloatingMode(windowingMode: String?): Boolean = windowingMode == "freeform" || windowingMode == "5"

/**
 * Whether the dashboard sits in a split the firmware left on: Android says
 * multi-window, yet the dashboard's window has (nearly) the whole display, so
 * no app can be beside it, or it has its half but the other half holds no app
 * ([nothingBeside], a pane left black; known only with the accessibility
 * service on). Never when the dashboard's window is known not to be in
 * split-screen ([windowingMode]): some QF units keep their home in freeform
 * at full screen, Android says multi-window there for good, and the toggle
 * then made a split of its own, the dashboard in one half beside a black one.
 * Not while the dashboard's own Maps dock is up, or
 * while a split asked for is still coming up. Sizes in pixels; the window's
 * height leaves out a forced top bar, hence the looser bound on it.
 */
internal fun isStuckSplit(
    inMultiWindow: Boolean,
    windowingMode: String?,
    windowWidth: Int,
    windowHeight: Int,
    displayWidth: Int,
    displayHeight: Int,
    mapsDocked: Boolean,
    splitRequestedRecently: Boolean,
    nothingBeside: Boolean = false
): Boolean {
    if (!inMultiWindow || mapsDocked || splitRequestedRecently) return false
    if (windowingMode != null && windowingMode !in SPLIT_MODES) return false
    if (nothingBeside) return true
    if (windowWidth <= 0 || windowHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) return false
    return windowWidth >= displayWidth * 0.9f && windowHeight >= displayHeight * 0.75f
}

/** The split-screen windowing modes, by name and by number (3 primary, 4 secondary). */
private val SPLIT_MODES = setOf("split-screen-primary", "split-screen-secondary", "3", "4")

private val WINDOWING_MODE = Regex("""mWindowingMode=([a-z-]+|\d+)""")

/**
 * The window's own windowing mode from a configuration's text, as Android
 * prints it (`mWindowingMode=freeform`); null when it is not there. The first
 * one is the window's; `mDisplayWindowingMode` is not matched.
 */
internal fun windowingModeOf(configuration: String): String? =
    WINDOWING_MODE.find(configuration)?.groupValues?.get(1)
