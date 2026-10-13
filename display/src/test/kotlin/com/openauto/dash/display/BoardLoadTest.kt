package com.openauto.dash.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The board's processor load and memory, as the Pi's /proc files give them. */
class BoardLoadTest {

    @Test
    fun theLoadIsTheBusyShareBetweenTwoReadings() {
        val before = BoardHealth.cpuTimes("cpu  100 0 100 700 100 0 0 0 0 0\ncpu0 1 2 3 4\n")
        val now = BoardHealth.cpuTimes("cpu  200 0 150 1000 150 0 0 0 0 0\n")
        // 150 busy ticks out of 500.
        assertEquals(30, BoardHealth.cpuLoad(before, now))
        assertNull(BoardHealth.cpuLoad(now, now))
    }

    @Test
    fun memoryInUseIsWhatIsNotAvailable() {
        val meminfo = "MemTotal:         948304 kB\nMemFree:          100000 kB\nMemAvailable:     624592 kB\nBuffers: 1 kB\n"
        assertEquals(316 to 926, BoardHealth.memory(meminfo))
    }
}
