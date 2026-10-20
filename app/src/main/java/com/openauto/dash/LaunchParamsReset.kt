package com.openauto.dash

import kotlin.system.exitProcess

/**
 * Makes Android forget the window an app last had. Some QF units remember,
 * across reboots, how each app was last shown; once the dashboard was shown as
 * a floating window, every start made the home screen a floating window again,
 * and the Recents screen then stayed over it for good (nothing could be
 * touched, Home did nothing). The firmware clears that memory itself for the
 * apps it raises, through the same call.
 *
 * Run by the privileged shell ([DockShell]) as
 * `CLASSPATH=<apk> app_process /system/bin com.openauto.dash.LaunchParamsReset <package>`:
 * the shell may make the call (MANAGE_ACTIVITY_STACKS), the app may not.
 * Prints "cleared" when it was made.
 */
object LaunchParamsReset {

    @JvmStatic
    fun main(args: Array<String>) {
        val pkg = args.getOrNull(0)
        if (pkg.isNullOrBlank()) {
            System.err.println("LaunchParamsReset: no package")
            exitProcess(2)
        }
        try {
            val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
                ?: error("no activity task service")
            service.javaClass.getMethod("clearLaunchParamsForPackages", List::class.java).invoke(service, listOf(pkg))
            println("cleared")
            exitProcess(0)
        } catch (e: Throwable) {
            System.err.println("LaunchParamsReset: ${e.cause ?: e}")
            exitProcess(1)
        }
    }

    /** The shell line that runs it. Pure, for the tests. */
    internal fun command(apk: String, packageName: String): String =
        "CLASSPATH=$apk app_process /system/bin ${LaunchParamsReset::class.java.name} $packageName"
}
