package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitClockTest {

    private val jan2025 = 1_735_689_617_000L   // 2025-01-01 01:00:17, the unit's clock at power-up
    private val oct2026 = 1_792_420_075_000L   // the last switch-off, by the clock then

    @Test
    fun aClockNotSetYetGivesTheLastKnownTime() {
        assertEquals(oct2026, trustedNow(jan2025, oct2026))
    }

    @Test
    fun aSetClockIsKept() {
        assertEquals(oct2026 + 60_000, trustedNow(oct2026 + 60_000, oct2026))
    }

    @Test
    fun nothingKnownKeepsTheClock() {
        assertEquals(jan2025, trustedNow(jan2025, 0L))
    }
}
