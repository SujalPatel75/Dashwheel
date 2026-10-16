package com.openauto.dash

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What Dashwheel holds, noted in the event log ([DebugLog]) at start, every
 * [EVERY_MS] and at each ignition change: so a unit that slows down after a
 * day or two (a tester's report, 2026-10-16) sends with its report the trend
 * that says what grew. Its memory (Java, native, graphics, all of it), its
 * threads and open files, its displays, the memory the whole unit has left,
 * and, through a shell when there is one, the processes Dashwheel starts
 * outside its own (root shells, `logcat`, the display helper).
 *
 * Off the main thread throughout, one line each time, a few kilobytes a day.
 */
object ResourceWatch {

    private const val TAG = "ResourceWatch"

    /** How often a line is noted while the process lives. */
    private const val EVERY_MS = 30 * 60_000L

    /** What starts each line, to find them again in the event log. */
    internal const val MARK = "resources"

    /** The processes counted through a shell, by name: those Dashwheel starts or that a stuck one leaves. */
    private val COUNTED = listOf("su", "sh", "logcat", "getevent", "app_process")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Notes a first line now, then one every [EVERY_MS]. Once per process. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            // The process just started: a restart shows as this line after a short uptime gap.
            note(app, "start")
            while (true) {
                delay(EVERY_MS)
                note(app, "timer")
            }
        }
    }

    /** One line now, [why] naming the moment (ignition on, off). */
    fun noteNow(context: Context, why: String) {
        val app = context.applicationContext
        scope.launch { note(app, why) }
    }

    /** One line for the report's summary, read now. IO thread. */
    suspend fun lineNow(context: Context): String =
        runCatching { line(sample(context), "now") }.getOrElse { "not read: ${it.message}" }

    private suspend fun note(context: Context, why: String) {
        val line = runCatching { line(sample(context), why) }
            .onFailure { Log.w(TAG, "no sample", it) }.getOrNull() ?: return
        Log.i(TAG, line)
        DebugLog.note(context, line)
    }

    /** What Dashwheel and the unit hold now. IO thread: reads /proc, may run a shell command. */
    private suspend fun sample(context: Context): Sample {
        val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        fun stat(name: String) = mi.getMemoryStat(name)?.toIntOrNull()?.let { it / 1024 } ?: -1
        val am = context.getSystemService(ActivityManager::class.java)
        val device = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val displays = context.getSystemService(DisplayManager::class.java)?.displays.orEmpty()
        val shell = Edition.full && PrivilegedShell.access.value.shell
        val processes = if (!shell) null else runCatching {
            processCounts(DockShell.shell(context, "ps -A -o NAME,ARGS"), SystemDisplayHelper::class.java.name)
        }.getOrNull()
        return Sample(
            uptimeMin = SystemClock.elapsedRealtime() / 60_000,
            processMin = (SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 60_000,
            pssMb = mi.totalPss / 1024,
            javaMb = stat("summary.java-heap"),
            nativeMb = stat("summary.native-heap"),
            graphicsMb = stat("summary.graphics"),
            threads = count("/proc/self/task"),
            fds = count("/proc/self/fd"),
            ownDisplays = displays.count { it.name.orEmpty().startsWith("Dashwheel") },
            allDisplays = displays.size,
            unitFreeMb = (device.availMem / (1024 * 1024)).toInt(),
            unitTotalMb = (device.totalMem / (1024 * 1024)).toInt(),
            unitLow = device.lowMemory,
            processes = processes,
        )
    }

    private fun count(dir: String): Int = File(dir).list()?.size ?: -1

    /** One reading; -1 where it could not be read, [processes] null without a shell. */
    internal data class Sample(
        val uptimeMin: Long,
        val processMin: Long,
        val pssMb: Int,
        val javaMb: Int,
        val nativeMb: Int,
        val graphicsMb: Int,
        val threads: Int,
        val fds: Int,
        val ownDisplays: Int,
        val allDisplays: Int,
        val unitFreeMb: Int,
        val unitTotalMb: Int,
        val unitLow: Boolean,
        val processes: Map<String, Int>?,
    )

    /** The line noted for [s], [why] naming the moment. Short keys: one line a reading, read as a column. Pure, for the tests. */
    internal fun line(s: Sample, why: String): String = buildString {
        append("$MARK ($why): up ${hours(s.uptimeMin)}, app ${hours(s.processMin)}")
        append(" | pss ${s.pssMb} MB (java ${s.javaMb}, native ${s.nativeMb}, gfx ${s.graphicsMb})")
        append(" | threads ${s.threads}, fds ${s.fds}, displays ${s.ownDisplays}/${s.allDisplays}")
        append(" | unit free ${s.unitFreeMb}/${s.unitTotalMb} MB${if (s.unitLow) " LOW" else ""}")
        s.processes?.let { p -> append(" | procs " + p.entries.joinToString(" ") { "${it.key} ${it.value}" }) }
    }

    /** [min] minutes as "2d 3h 05m", "3h 05m" or "5m". Pure, for the tests. */
    internal fun hours(min: Long): String {
        val d = min / (24 * 60)
        val h = min / 60 % 24
        val m = min % 60
        return when {
            d > 0 -> "${d}d ${h}h ${"%02d".format(Locale.US, m)}m"
            h > 0 -> "${h}h ${"%02d".format(Locale.US, m)}m"
            else -> "${m}m"
        }
    }

    /**
     * From `ps -A -o NAME,ARGS`: how many processes of each [COUNTED] name run,
     * how many are Dashwheel's display helper ([helperClass] in its command
     * line, the process named app_process) and how many processes in all.
     * Pure, for the tests.
     */
    internal fun processCounts(ps: String, helperClass: String): Map<String, Int> {
        val rows = ps.lines().drop(1).map { it.trim() }.filter { it.isNotEmpty() }
        val names = rows.map { it.substringBefore(' ').substringAfterLast('/') }
        val counts = LinkedHashMap<String, Int>()
        counts["all"] = rows.size
        COUNTED.forEach { name -> counts[name] = names.count { it == name } }
        counts["helper"] = rows.count { helperClass in it }
        return counts
    }

    /**
     * Why Dashwheel's last processes ended, as Android kept it (Android 11 and
     * later): a crash, the low-memory killer, an ANR. The launcher restarting
     * over and over shows here with its reason. Empty on Android 10.
     */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val am = context.getSystemService(ActivityManager::class.java) ?: return ""
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        return runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 15).joinToString("\n") {
                "${stamp.format(Date(it.timestamp))} ${exitReason(it.reason)}, pss ${it.pss / 1024} MB, rss ${it.rss / 1024} MB" +
                    it.description?.let { d -> ": $d" }.orEmpty()
            }
        }.getOrDefault("")
    }

    /** ApplicationExitInfo's reason codes by name. Pure, for the tests. */
    internal fun exitReason(code: Int): String = when (code) {
        1 -> "exited itself"
        2 -> "signal"
        3 -> "low memory"
        4 -> "crash"
        5 -> "native crash"
        6 -> "ANR"
        7 -> "initialization failure"
        8 -> "permission change"
        9 -> "excessive resource usage"
        10 -> "user requested"
        11 -> "user stopped"
        12 -> "dependency died"
        13 -> "other"
        14 -> "freezer"
        15 -> "package state change"
        16 -> "package updated"
        else -> "unknown ($code)"
    }

    /** The resource lines of [events] (the event log), newest last. Pure, for the tests. */
    internal fun trend(events: String, max: Int = 200): String =
        events.lines().filter { " $MARK (" in it }.takeLast(max).joinToString("\n")
}
