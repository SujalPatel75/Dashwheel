package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The adapter is left alone while CarPlay or Android Auto starts. */
class ProjectionStartingTest {

    @Test
    fun theAdapterWaitsWhileThePhoneConnects() {
        // CarPlay's screen up, waiting for the phone.
        assertTrue(projectionStarting(onScreen = true, connected = false, since = null, now = 1_000L))
        // Connected a moment ago: still settling.
        assertTrue(projectionStarting(onScreen = true, connected = true, since = 1_000L, now = 1_000L + 10_000L))
        // Settled: the adapter may be dialled, CarPlay on screen or not.
        assertFalse(projectionStarting(onScreen = true, connected = true, since = 1_000L, now = 1_000L + PROJECTION_SETTLE_MS))
        // Nothing of the sort.
        assertFalse(projectionStarting(onScreen = false, connected = false, since = null, now = 5_000L))
    }
}
