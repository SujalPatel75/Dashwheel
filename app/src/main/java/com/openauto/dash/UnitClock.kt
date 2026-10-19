package com.openauto.dash

import android.content.Context

/**
 * Today's time for things the driver stamps ("Done today"). Many units start
 * with their clock at 1 January 2025 and only get the real time a while
 * later, from GPS or the network: a stamp taken before then landed in 2025.
 * A clock that reads earlier than a moment already known to be past is not
 * set yet, and that moment (the last switch-off, or Dashwheel's last update)
 * is the best guess for today.
 */
internal object UnitClock {

    fun now(context: Context): Long =
        trustedNow(System.currentTimeMillis(), lastKnown(context))

    private fun lastKnown(context: Context): Long {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        return maxOf(CarPower.lastSwitchOffAt(context), installed)
    }
}

/** [now], unless it is before [lastKnown], a time already gone by: the clock is not set yet then. */
internal fun trustedNow(now: Long, lastKnown: Long): Long =
    if (lastKnown > 0 && now < lastKnown) lastKnown else now
