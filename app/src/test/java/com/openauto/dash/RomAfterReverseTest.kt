package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ROM app's after-reverse views, turned off for Dashwheel's own camera and put back. */
class RomAfterReverseTest {

    @Test
    fun nothingToTouchWithoutAFrontOrRightCamera() {
        val a = RomAfterReverse("", "")
        assertTrue(a.off.isEmpty())
        assertTrue(a.back.isEmpty())
        assertTrue(RomAfterReverse("0", "false").off.isEmpty())
        assertTrue(RomAfterReverse(null, null).back.isEmpty())
    }

    @Test
    fun frontCameraTimeComesBackAsItWas() {
        val a = RomAfterReverse("2", "false")
        assertEquals(listOf("setprop persist.sys.foresight_time 0"), a.off)
        assertEquals(listOf("setprop persist.sys.foresight_time 2"), a.back)
    }

    @Test
    fun rightCameraToo() {
        val a = RomAfterReverse("3", "true")
        assertEquals(listOf("setprop persist.sys.foresight_time 0", "setprop persist.sys.right.camera false"), a.off)
        assertEquals(listOf("setprop persist.sys.foresight_time 3", "setprop persist.sys.right.camera true"), a.back)
    }
}
