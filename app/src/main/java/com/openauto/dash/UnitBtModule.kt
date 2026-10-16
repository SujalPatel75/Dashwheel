package com.openauto.dash

import android.content.Context
import android.os.SystemClock
import android.util.Log

/*
 * The QF firmware (ROCO K706) runs Bluetooth on an outside module, driven by
 * a root daemon (gocsdk_zj, log tag "sd"). Its BluetoothSocket.connect() only
 * writes the adapter's address to the daemon's "goc_spp" socket and says
 * "connected" at once: whether the adapter is reached is the daemon's doing.
 * Seen on the car 2026-10-12, every app's OBD (Torque too) dead in two ways:
 *  - after the unit slept, the daemon gave the adapter's link to wireless
 *    Android Auto: "obd_addr", "acl connected", "spp_connect", then
 *    "aa connected" where "spp connected" should be;
 *  - with a phone whose wireless Android Auto kept connecting and dropping
 *    (every 12 s), the daemon ignored the requests altogether: no "obd_addr"
 *    at all, and it stayed so after the phone stopped.
 * Restarting the daemon alone puts both right (the adapter is back about a
 * minute later, beside the phone's calls), as long as the phone's wireless
 * Android Auto doesn't jam it again. Needs the unit's root or ADB shell.
 */
internal object UnitBtModule {
    private const val TAG = "UnitBtModule"
    /** The daemon's service when the firmware names none other. */
    private const val DEFAULT_SERVICE = "gocsdk_zj"
    /** One restart in this long at most: it cuts the phone's Bluetooth for a few seconds. */
    private const val RESTART_GAP_MS = 10 * 60_000L
    /** This many Android Auto drops in the log read: a phone's wireless Android Auto going round. */
    private const val AA_LOOP_DROPS = 4

    @Volatile private var lastRestartAt = 0L

    /** What the daemon's log says about the adapter's link. */
    enum class Finding {
        /** Nothing wrong seen, or nothing could be read. */
        NONE,
        /** It gave the link to Android Auto. */
        MISROUTED,
        /** It never tried the adapter. */
        IGNORED
    }

    /** What [afterSilentLink] found and did; [logRead] false when the daemon's log couldn't be read (no root, other firmware). */
    data class Outcome(val finding: Finding, val restarted: Boolean, val androidAutoLoop: Boolean, val logRead: Boolean = true)

    /** This firmware's outside Bluetooth module is in charge. */
    fun outside(): Boolean = systemProperty("persist.sys.is.ext.bt") == "true"

    /**
     * Called when every way to the adapter at [mac] "connected" but it never
     * answered: reads the daemon's log, restarts it when it gave the link to
     * Android Auto or ignored the requests, and says whether a phone's
     * wireless Android Auto keeps it busy. The next redial finds the adapter.
     */
    suspend fun afterSilentLink(context: Context, mac: String): Outcome {
        val none = Outcome(Finding.NONE, restarted = false, androidAutoLoop = false, logRead = false)
        if (!outside() || !PrivilegedShell.access.value.shell) return none
        val app = context.applicationContext
        val log = runCatching { DockShell.shell(app, "logcat -d -v brief -s sd:D 2>/dev/null | tail -n 400") }.getOrDefault("")
        if (log.isBlank()) return none
        val finding = read(log, mac)
        val loop = androidAutoLoop(log)
        if (finding == Finding.NONE) return Outcome(finding, restarted = false, androidAutoLoop = loop)
        // While the phone's Android Auto still goes round, a restart is jammed again at once: the
        // driver is told instead. Never during a call: the restart drops the phone's Bluetooth.
        val due = lastRestartAt == 0L || SystemClock.elapsedRealtime() - lastRestartAt >= RESTART_GAP_MS
        if (!due || loop || HeadUnitPhone.call.value != null) return Outcome(finding, restarted = false, androidAutoLoop = loop)
        val service = runCatching { DockShell.shell(app, "getprop") }.getOrDefault("").let(::serviceName)
        Log.w(TAG, "the Bluetooth module ${if (finding == Finding.MISROUTED) "gave the OBD link to Android Auto" else "ignored the OBD adapter"}: restarting $service")
        lastRestartAt = SystemClock.elapsedRealtime()
        val ok = runCatching { DockShell.shell(app, "setprop ctl.restart $service") }
            .onFailure { Log.w(TAG, "could not restart $service", it) }.isSuccess
        return Outcome(finding, restarted = ok, androidAutoLoop = loop)
    }

    /**
     * What the daemon's [log] (its last lines, oldest first) says about the
     * adapter at [mac]: after its last "obd_addr:<mac>", "aa connected"
     * without "spp connected" is [Finding.MISROUTED]; no "obd_addr" at all
     * while the daemon is busy logging is [Finding.IGNORED].
     */
    internal fun read(log: String, mac: String): Finding {
        val bare = mac.replace(":", "").uppercase()
        if (bare.isEmpty()) return Finding.NONE
        val lines = log.lines().filter { it.isNotBlank() }
        val from = lines.indexOfLast { it.contains("obd_addr:") && it.substringAfter("obd_addr:").trim().uppercase().startsWith(bare) }
        if (from < 0) {
            // Minutes of the daemon's log (it writes a couple of lines a second) and not one try at the adapter.
            return if (lines.size >= MIN_LINES_FOR_IGNORED) Finding.IGNORED else Finding.NONE
        }
        val after = lines.drop(from + 1)
        // A newer try already under way: that one decides.
        val next = after.indexOfFirst { it.contains("obd_addr:") }
        val tried = if (next >= 0) after.take(next) else after
        return if (tried.any { it.contains("aa connected") } && tried.none { it.contains("spp connected") }) Finding.MISROUTED else Finding.NONE
    }

    /** A phone's wireless Android Auto connecting and dropping again and again in [log]. */
    internal fun androidAutoLoop(log: String): Boolean = log.lines().count { it.contains("AA DISCONN") } >= AA_LOOP_DROPS

    /** Fewer daemon lines than this (a minute or so) say too little to call the adapter ignored. */
    private const val MIN_LINES_FOR_IGNORED = 120

    /** The daemon's service from `getprop`'s "[init.svc.gocsdk…]: [running]" line. */
    internal fun serviceName(getprop: String): String =
        Regex("""\[init\.svc\.(gocsdk[A-Za-z0-9_]*)]""").find(getprop)?.groupValues?.get(1) ?: DEFAULT_SERVICE

    private fun systemProperty(name: String): String? = SysProps.get(name)
}
