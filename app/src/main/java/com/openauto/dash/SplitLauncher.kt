package com.openauto.dash

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.openauto.unitproto.UnitFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opens an app alongside the dashboard, preferring the system's real
 * split-screen and falling back to a freeform floating window.
 *
 * **Preferred path — system split via accessibility.** This head unit's ROM
 * ignores AOSP split-screen and blocks `am task resize` ("resizeTask not
 * allowed"), so we can't tile two windows through the windowing APIs. But the
 * ROM *does* support split the manual way — recents → long-press an app → drag
 * to a side — because that goes through SystemUI, not the AOSP APIs. When the
 * [SplitAccessibilityService] is enabled we drive that same SystemUI path with
 * `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`: it docks the foreground task (the
 * dashboard), then we fill the other half with [Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT].
 *
 * **Fallback — freeform.** If the service isn't enabled, we launch the app in
 * freeform mode: a movable/resizable floating window (the system picks the
 * position; our bounds are a hint it may ignore).
 */
object SplitLauncher {

    private const val TAG = "SplitLauncher"
    private const val WINDOWING_MODE_FREEFORM = 5

    /**
     * Delay between docking the foreground task and launching the second app,
     * so the system has settled into split mode and honours LAUNCH_ADJACENT.
     */
    private const val SPLIT_SETTLE_MS = 400L

    /**
     * Open [packageName] in split-screen next to the current app.
     *
     * Uses the system split path when [SplitAccessibilityService] is enabled,
     * otherwise falls back to a freeform floating window. Returns true when a
     * launch strategy was dispatched.
     */
    fun launchSplit(context: Context, packageName: String): Boolean {
        // An app that is gone (a tile kept after it was removed): nothing to open, so
        // the dashboard is not docked to half the screen with nothing beside it.
        if (AppLauncher.launchIntent(context, packageName) == null) return false
        // QF firmware from before 2026: the Map key's split screen left the home screen a
        // floating window for good, under the Recents screen (only a reinstall cleared it).
        // The app opens full screen there, neither split nor floating.
        if (!besideDashboardAllowed(UnitPlatform.adapter.family == UnitFamily.QF, Build.TIME)) {
            return AppLauncher.launch(context, packageName)
        }
        // A dashboard that is itself a floating window (some QF units' home): the
        // toggle there made a split with the dashboard in one half beside a black one.
        // Its not-resizable stand-in ([FloatingHome]) can't go into a split at all.
        if (!splitToggleUsable(MainActivity.homeFloatingNow() || FloatingHome.active(context))) return launchFreeform(context, packageName)
        // Already split: the toggle would *exit* split on this ROM and the app
        // would land full screen. The other half takes it as it is.
        if (SplitAccessibilityService.isSplit()) {
            if (launchIntoAdjacent(context, packageName)) return true
            return launchFreeform(context, packageName)
        }
        if (SplitAccessibilityService.requestSplit()) {
            // The foreground app (the dashboard) is now docked into one half.
            // Fill the other half once the system settles into split mode.
            Handler(Looper.getMainLooper()).postDelayed(
                { launchIntoAdjacent(context, packageName) },
                SPLIT_SETTLE_MS
            )
            return true
        }
        return launchFreeform(context, packageName)
    }

    /**
     * Time to wait for the freshly launched primary app to reach the foreground
     * before docking it, so [SplitAccessibilityService] splits the right task.
     */
    private const val PRIMARY_SETTLE_MS = 700L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Open a preselected pair of apps in split-screen: launch [primary] full
     * screen, dock it once it's foreground, then fill the other half with
     * [secondary]. Falls back to a freeform floating [secondary] window when the
     * accessibility service isn't enabled.
     *
     * Either app may already be running in a dashboard tile's floating window
     * (a "Maps window" tile on some page). Launched over that, the pair never
     * split: the launch reused the app's freeform task instead of opening it
     * full screen, so there was nothing for SystemUI to dock, and the tiles then
     * parked both windows into the bottom-right corner. So the windows are
     * first handed over to the split ([PipAnchor.lendToSplit]) — moved out of
     * freeform, still running, or closed when the system refuses — and the
     * tiles leave the two apps alone while the split comes up.
     *
     * Returns true when [primary] can be launched; the launch itself follows
     * once the windows are dealt with.
     */
    fun launchSplitPair(context: Context, primary: String, secondary: String): Boolean {
        val primaryIntent = AppLauncher.launchIntent(context, primary)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        scope.launch {
            PipAnchor.lendToSplit(context, listOf(primary, secondary))

            val launched = runCatching {
                context.startActivity(primaryIntent)
                true
            }.onFailure { Log.e(TAG, "primary launch failed", it) }.getOrDefault(false)
            if (!launched) return@launch

            delay(PRIMARY_SETTLE_MS)
            if (SplitAccessibilityService.requestSplit()) {
                delay(SPLIT_SETTLE_MS)
                launchIntoAdjacent(context, secondary)
            } else {
                launchFreeform(context, secondary)
            }
        }
        return true
    }

    /** Whether the system-split path is available (accessibility service on). */
    fun isSystemSplitAvailable(): Boolean = SplitAccessibilityService.isConnected

    /** Swap the two split-screen panes (left/right). Needs the accessibility service. */
    fun swapSplit(): Boolean = SplitAccessibilityService.swapSplit()

    /**
     * Deep-link the user to Accessibility settings to enable the split service.
     * The Play edition first says what the service is for ([AccessDisclosure]);
     * only that dialog's "Open settings" goes on to the system page.
     */
    fun openAccessibilitySettings(context: Context) {
        if (Edition.play) {
            AccessDisclosure.show(context, AccessDisclosure.Kind.ACCESSIBILITY)
            return
        }
        AccessDisclosure.openSystemPage(context, AccessDisclosure.Kind.ACCESSIBILITY)
    }

    /**
     * Launch [packageName] into the other split pane via the public flag. The
     * other pane is the dashboard, never the same app, so an app already
     * running is moved into the pane rather than started a second time (see
     * [launchFreeform]).
     */
    private fun launchIntoAdjacent(context: Context, packageName: String): Boolean {
        val intent = AppLauncher.launchIntent(context, packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        } ?: return false

        return runCatching {
            context.startActivity(intent)
            true
        }.onFailure { Log.e(TAG, "adjacent launch failed", it) }.getOrDefault(false)
    }

    /**
     * Fallback: launch [packageName] as a freeform floating window.
     *
     * An app already running (Waze started by the head unit at power-up, or
     * still running from before the engine was switched off) is brought into
     * the window, never started a second time: with
     * [Intent.FLAG_ACTIVITY_MULTIPLE_TASK] every tap on the tile's "Open … here",
     * and every reopen by a window tile, started one more copy of the app next
     * to the one already running, and each copy spoke every voice prompt.
     */
    fun launchFreeform(context: Context, packageName: String, launchBounds: Rect? = null): Boolean {
        val intent = AppLauncher.launchIntent(context, packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        val m = context.resources.displayMetrics
        // Right half of the screen unless the caller (a dashboard tile) says where.
        val bounds = launchBounds ?: Rect(m.widthPixels / 2, 0, m.widthPixels, m.heightPixels)

        val options = ActivityOptions.makeBasic()
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }.onFailure { Log.d(TAG, "setLaunchWindowingMode unavailable", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { options.setLaunchBounds(bounds) }
        }

        return runCatching {
            context.startActivity(intent, options.toBundle())
            true
        }.onFailure { Log.e(TAG, "freeform launch failed", it) }.getOrDefault(false)
    }
}

/** Whether the split-screen toggle may be used: not while the dashboard is itself a floating window. */
internal fun splitToggleUsable(homeFloating: Boolean): Boolean = !homeFloating

/** QF firmware built before this (Android's build time) is the 2025 kind that kept the home screen floating. */
internal const val QF_FLOATING_HOME_BEFORE = 1_767_225_600_000L // 2026-01-01 UTC

/**
 * Whether an app may be opened beside the dashboard (split screen or a
 * floating window): not on QF firmware from before 2026 ([QF_FLOATING_HOME_BEFORE]).
 */
internal fun besideDashboardAllowed(qf: Boolean, buildTime: Long): Boolean =
    !qf || buildTime <= 0 || buildTime >= QF_FLOATING_HOME_BEFORE
