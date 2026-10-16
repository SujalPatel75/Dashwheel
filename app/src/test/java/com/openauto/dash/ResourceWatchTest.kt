package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The resource line noted in the event log, and what the report reads back
 * from it: one line a reading, the processes counted from `ps`, the trend
 * taken out of the other events.
 */
class ResourceWatchTest {

    private val sample = ResourceWatch.Sample(
        uptimeMin = 2 * 24 * 60 + 3 * 60 + 5, processMin = 47,
        pssMb = 412, javaMb = 61, nativeMb = 138, graphicsMb = 97,
        threads = 183, fds = 241, ownDisplays = 3, allDisplays = 5,
        unitFreeMb = 312, unitTotalMb = 1938, unitLow = false, processes = null,
    )

    @Test
    fun aLineSaysEverythingOnOneRow() {
        assertEquals(
            "resources (timer): up 2d 3h 05m, app 47m | pss 412 MB (java 61, native 138, gfx 97)" +
                " | threads 183, fds 241, displays 3/5 | unit free 312/1938 MB",
            ResourceWatch.line(sample, "timer"),
        )
    }

    @Test
    fun aUnitShortOfMemoryAndTheProcessesShow() {
        val line = ResourceWatch.line(
            sample.copy(unitLow = true, processes = linkedMapOf("all" to 290, "su" to 2, "helper" to 1)), "ignition on",
        )
        assertEquals(true, line.contains("unit free 312/1938 MB LOW | procs all 290 su 2 helper 1"))
    }

    @Test
    fun durationsReadAsDaysHoursMinutes() {
        assertEquals("0m", ResourceWatch.hours(0))
        assertEquals("59m", ResourceWatch.hours(59))
        assertEquals("1h 00m", ResourceWatch.hours(60))
        assertEquals("23h 59m", ResourceWatch.hours(24 * 60 - 1))
        assertEquals("1d 0h 00m", ResourceWatch.hours(24 * 60))
    }

    @Test
    fun processesAreCountedByNameAndTheHelperByItsCommandLine() {
        val ps = """
            NAME                        ARGS
            init                        /system/bin/init second_stage
            su                          su
            sh                          sh
            /system/bin/sh              /system/bin/sh -c ps
            logcat                      logcat -b main -v brief McuService:I
            app_process                 app_process /system/bin com.openauto.dash.SystemDisplayHelper ab12 com.openauto.dash
            app_process                 app_process /system/bin com.openauto.dash.SystemDisplayHelper cd34 com.openauto.dash
            com.openauto.dash           com.openauto.dash
        """.trimIndent()
        val counts = ResourceWatch.processCounts(ps, "com.openauto.dash.SystemDisplayHelper")
        assertEquals(
            linkedMapOf("all" to 8, "su" to 1, "sh" to 2, "logcat" to 1, "getevent" to 0, "app_process" to 2, "helper" to 2),
            counts,
        )
    }

    @Test
    fun theTrendKeepsOnlyTheResourceLinesNewestLast() {
        val events = """
            10-16 08:00:01 ignition on, off since 600 min
            10-16 08:00:01 resources (ignition on): up 9h 12m
            10-16 08:30:01 resources (timer): up 9h 42m
            10-16 08:45:10 deep sleep lost: wifi
            10-16 09:00:01 resources (timer): up 10h 12m
        """.trimIndent()
        assertEquals(
            "10-16 08:30:01 resources (timer): up 9h 42m\n10-16 09:00:01 resources (timer): up 10h 12m",
            ResourceWatch.trend(events, max = 2),
        )
        assertEquals("", ResourceWatch.trend("10-16 08:00:01 ignition off"))
    }

    @Test
    fun exitReasonsByName() {
        assertEquals("low memory", ResourceWatch.exitReason(3))
        assertEquals("ANR", ResourceWatch.exitReason(6))
        assertEquals("unknown (99)", ResourceWatch.exitReason(99))
    }
}
