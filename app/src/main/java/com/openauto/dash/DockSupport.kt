package com.openauto.dash

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Whether this unit can float Google Maps in a window beside the dashboards,
 * which the layouts with the Maps dock need. Where it cannot, the system
 * opens Maps full screen instead, and the dock kept opening it again: Home
 * landed back on Maps every time (a Qualcomm unit on Android 13).
 *
 * Android floats apps when the build declares the freeform feature or the
 * developer switch "enable freeform windows" is on (the K706's ROM declares
 * the feature). "Force activities to be resizable" alone floats nothing, so
 * it is not asked. What the unit actually did is remembered on top: a window
 * seen docking, or a dock that had to give up.
 */
object DockSupport {

    private const val TAG = "DockSupport"
    private const val PREFS = "dock_support"
    private const val KEY_DOCKED = "docked_before"
    private const val KEY_REFUSED = "refused"

    /** The developer switch "Enable freeform windows" (`Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT`, hidden from apps). */
    private const val ENABLE_FREEFORM_SUPPORT = "enable_freeform_support"

    /** What the system says: apps can be opened in a window. */
    fun systemFloats(context: Context): Boolean {
        val feature = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        }.getOrDefault(false)
        val devSwitch = runCatching {
            Settings.Global.getInt(context.contentResolver, ENABLE_FREEFORM_SUPPORT, 0) != 0
        }.getOrDefault(false)
        return feature || devSwitch
    }

    /** Whether the Maps dock layouts are offered on this unit ([DockPolicy.dockOffered]). */
    fun offered(context: Context): Boolean =
        DockPolicy.dockOffered(systemFloats(context), dockedBefore(context), refused(context))

    fun dockedBefore(context: Context): Boolean = prefs(context).getBoolean(KEY_DOCKED, false)

    private fun refused(context: Context): Boolean = prefs(context).getBoolean(KEY_REFUSED, false)

    @Volatile private var dockedNoted = false

    /** A window docked on its tile: this unit floats apps. Written once per process. */
    fun noteDocked(context: Context) {
        if (dockedNoted) return
        dockedNoted = true
        if (dockedBefore(context)) return
        prefs(context).edit().putBoolean(KEY_DOCKED, true).remove(KEY_REFUSED).apply()
        Log.i(TAG, "a window docked: this unit floats apps")
    }

    /** The dock gave up and the dashboards took the screen back ([DockPolicy.GivenUp.DASHBOARDS_ONLY]). */
    fun noteRefused(context: Context) {
        prefs(context).edit().putBoolean(KEY_REFUSED, true).apply()
        Log.w(TAG, "the Maps dock never floated here; dock layouts no longer offered (system floats: ${systemFloats(context)})")
    }

    /** For the unit report: what decided whether the dock is offered. */
    fun describe(context: Context): String =
        "system ${systemFloats(context)}, docked before ${dockedBefore(context)}, refused ${refused(context)}"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
