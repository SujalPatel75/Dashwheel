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
        val stuck = isStuckSplit(
            inMultiWindow = activity.isInMultiWindowMode,
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
}

/**
 * Whether the dashboard sits in a split the firmware left on: Android says
 * multi-window, yet the dashboard's window has (nearly) the whole display, so
 * no app can be beside it, or it has its half but the other half holds no app
 * ([nothingBeside], a pane left black; known only with the accessibility
 * service on). Not while the dashboard's own Maps dock is up, or
 * while a split asked for is still coming up. Sizes in pixels; the window's
 * height leaves out a forced top bar, hence the looser bound on it.
 */
internal fun isStuckSplit(
    inMultiWindow: Boolean,
    windowWidth: Int,
    windowHeight: Int,
    displayWidth: Int,
    displayHeight: Int,
    mapsDocked: Boolean,
    splitRequestedRecently: Boolean,
    nothingBeside: Boolean = false
): Boolean {
    if (!inMultiWindow || mapsDocked || splitRequestedRecently) return false
    if (nothingBeside) return true
    if (windowWidth <= 0 || windowHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) return false
    return windowWidth >= displayWidth * 0.9f && windowHeight >= displayHeight * 0.75f
}
