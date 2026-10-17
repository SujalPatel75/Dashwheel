package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TpmsResetTest {
    private val min = 60_000L
    private val spacing = 30 * min

    @Test
    fun drivingThreeMinutesWithoutATyreResetsTheBox() {
        val now = 100 * min
        assertTrue(TpmsReset.stuck(now, drivingSince = now - 4 * min, heardAt = now - 10 * min, resetThisDrive = false, resetAt = 0, spacingMs = spacing))
        // Never heard since the unit started: stuck too.
        assertTrue(TpmsReset.stuck(now, now - 4 * min, heardAt = 0, resetThisDrive = false, resetAt = 0, spacingMs = spacing))
    }

    @Test
    fun notParkedNorJustStartedNorWithTyresHeardNorTwice() {
        val now = 100 * min
        assertFalse(TpmsReset.stuck(now, drivingSince = 0, heardAt = 0, resetThisDrive = false, resetAt = 0, spacingMs = spacing))
        assertFalse(TpmsReset.stuck(now, drivingSince = now - min, heardAt = 0, resetThisDrive = false, resetAt = 0, spacingMs = spacing))
        assertFalse(TpmsReset.stuck(now, now - 4 * min, heardAt = now - 20_000, resetThisDrive = false, resetAt = 0, spacingMs = spacing))
        assertFalse(TpmsReset.stuck(now, now - 4 * min, heardAt = 0, resetThisDrive = true, resetAt = 0, spacingMs = spacing))
        assertFalse(TpmsReset.stuck(now, now - 4 * min, heardAt = 0, resetThisDrive = false, resetAt = now - 10 * min, spacingMs = spacing))
    }

    @Test
    fun theBoxIsFoundByItsBusAndNumber() {
        assertEquals(1 to 4, TpmsReset.busAndDevice("/dev/bus/usb/001/004"))
        assertNull(TpmsReset.busAndDevice("weird"))
        val cmd = TpmsReset.portCycleCommand(1, 4)
        assertTrue(cmd.contains("= \"1\" ]") && cmd.contains("= \"4\" ]"))
        assertTrue(cmd.contains("echo 0 > \$d/authorized && sleep 2 && echo 1 > \$d/authorized && echo \"reset \$d\""))
    }
}
