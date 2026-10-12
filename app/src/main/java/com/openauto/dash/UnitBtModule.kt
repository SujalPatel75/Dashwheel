package com.openauto.dash

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * The QF firmware (ROCO K706) runs Bluetooth on an outside module, driven by
 * a root daemon (gocsdk_zj, log tag "sd"), and hands an app's Bluetooth
 * socket to it. After the unit sleeps and wakes, the daemon can take the OBD
 * adapter's link for wireless Android Auto: "obd_addr", "acl connected",
 * "spp_connect", then "aa connected" where "spp connected" should be. The
 * adapter's answers go there, the app hears nothing, and every app's OBD stays
 * dead until the unit restarts (seen on the car 2026-10-12, Torque too).
 * Restarting the daemon alone puts it right: the link comes back about a
 * minute later, routed as it should be. Needs the unit's root or ADB shell.
 */
internal object UnitBtModule {
    private const val TAG = "UnitBtModule"
    /** The daemon's service when the firmware names none other. */
    private const val DEFAULT_SERVICE = "gocsdk_zj"
    /** One restart in this long at most: it cuts the phone's Bluetooth for a few seconds. */
    private const val RESTART_GAP_MS = 10 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastRestartAt = 0L
    @Volatile private var busy = false

    /** This firmware's outside Bluetooth module is in charge. */
    fun outside(): Boolean = systemProperty("persist.sys.is.ext.bt") == "true"

    /**
     * Called when every way to the adapter at [mac] connected but it never
     * answered: looks at the daemon's log and restarts it when it gave the
     * link to Android Auto. The next redial then finds the adapter.
     */
    fun afterSilentLink(context: Context, mac: String) {
        if (!outside() || busy || !PrivilegedShell.access.value.shell) return
        if (lastRestartAt != 0L && SystemClock.elapsedRealtime() - lastRestartAt < RESTART_GAP_MS) return
        val app = context.applicationContext
        busy = true
        scope.launch {
            try {
                val log = runCatching { DockShell.shell(app, "logcat -d -v brief -s sd:D 2>/dev/null | tail -n 400") }.getOrDefault("")
                if (!misrouted(log, mac)) return@launch
                val service = runCatching { DockShell.shell(app, "getprop") }.getOrDefault("").let(::serviceName)
                Log.w(TAG, "the Bluetooth module gave the OBD link to Android Auto: restarting $service")
                lastRestartAt = SystemClock.elapsedRealtime()
                runCatching { DockShell.shell(app, "setprop ctl.restart $service") }
                    .onFailure { Log.w(TAG, "could not restart $service", it) }
            } finally {
                busy = false
            }
        }
    }

    /**
     * Whether the daemon's last link to the adapter at [mac] went to Android
     * Auto: after its last "obd_addr:<mac>", "aa connected" came and
     * "spp connected" didn't.
     */
    internal fun misrouted(log: String, mac: String): Boolean {
        val bare = mac.replace(":", "").uppercase()
        if (bare.isEmpty()) return false
        val lines = log.lines()
        val from = lines.indexOfLast { it.contains("obd_addr:") && it.substringAfter("obd_addr:").trim().uppercase().startsWith(bare) }
        if (from < 0) return false
        val after = lines.drop(from + 1)
        // A newer try already under way: that one decides.
        val next = after.indexOfFirst { it.contains("obd_addr:") }
        val tried = if (next >= 0) after.take(next) else after
        return tried.any { it.contains("aa connected") } && tried.none { it.contains("spp connected") }
    }

    /** The daemon's service from `getprop`'s "[init.svc.gocsdk…]: [running]" line. */
    internal fun serviceName(getprop: String): String =
        Regex("""\[init\.svc\.(gocsdk[A-Za-z0-9_]*)]""").find(getprop)?.groupValues?.get(1) ?: DEFAULT_SERVICE

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrNull()
}
