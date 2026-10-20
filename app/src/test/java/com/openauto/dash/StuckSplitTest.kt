package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckSplitTest {

    private fun stuck(
        inMultiWindow: Boolean = true,
        w: Int = 1280,
        h: Int = 684,
        dw: Int = 1280,
        dh: Int = 720,
        mapsDocked: Boolean = false,
        recent: Boolean = false,
        nothingBeside: Boolean = false,
        mode: String? = null
    ) = isStuckSplit(inMultiWindow, mode, w, h, dw, dh, mapsDocked, recent, nothingBeside)

    @Test
    fun aWholeScreenDashboardInMultiWindowIsAStuckSplit() {
        // The top bar forced over it takes a little of the height.
        assertTrue(stuck())
        assertTrue(stuck(w = 1280, h = 720))
        // An upright unit, whole screen too.
        assertTrue(stuck(w = 720, h = 1240, dw = 720, dh = 1280))
    }

    @Test
    fun notInMultiWindowIsNothingToLeave() {
        assertFalse(stuck(inMultiWindow = false))
    }

    @Test
    fun aRealSplitPaneIsLeftAlone() {
        // Side by side on a wide screen.
        assertFalse(stuck(w = 636, h = 684))
        // One above the other on an upright screen: wide but half as tall.
        assertFalse(stuck(w = 720, h = 630, dw = 720, dh = 1280))
        // A big pane with a third of the screen left to the app beside it.
        assertFalse(stuck(w = 850, h = 684))
    }

    @Test
    fun aHalfPaneBesideABlackPaneIsAStuckSplit() {
        assertTrue(stuck(w = 636, h = 684, nothingBeside = true))
        assertFalse(stuck(w = 636, h = 684, nothingBeside = true, recent = true))
        assertFalse(stuck(w = 636, h = 684, nothingBeside = true, mapsDocked = true))
        assertFalse(stuck(inMultiWindow = false, nothingBeside = true))
    }

    @Test
    fun aHomeInFreeformIsNoSplitToLeave() {
        // A QF unit keeps its home in freeform at full screen: multi-window for good.
        assertFalse(stuck(mode = "freeform"))
        assertFalse(stuck(mode = "5", w = 636, nothingBeside = true))
        assertFalse(stuck(mode = "fullscreen"))
        assertTrue(stuck(mode = "split-screen-secondary"))
        assertTrue(stuck(mode = "4", w = 636, nothingBeside = true))
    }

    @Test
    fun aFloatingHomeOnPartOfTheScreenIsShrunk() {
        // The right half, beside a black left half.
        assertTrue(isShrunkFloatingHome("freeform", 640, 648, 1280, 720, mapsDocked = false))
        assertTrue(isShrunkFloatingHome("5", 1280, 300, 1280, 720, mapsDocked = false))
        // The whole screen, the forced top bar taken off its height.
        assertFalse(isShrunkFloatingHome("freeform", 1280, 648, 1280, 720, mapsDocked = false))
        // Not floating, the Maps dock, or sizes not known.
        assertFalse(isShrunkFloatingHome("split-screen-secondary", 640, 648, 1280, 720, mapsDocked = false))
        assertFalse(isShrunkFloatingHome(null, 640, 648, 1280, 720, mapsDocked = false))
        assertFalse(isShrunkFloatingHome("freeform", 640, 648, 1280, 720, mapsDocked = true))
        assertFalse(isShrunkFloatingHome("freeform", 0, 648, 1280, 720, mapsDocked = false))
    }

    @Test
    fun theSplitToggleIsNotUsedOnAFloatingHome() {
        assertFalse(splitToggleUsable(homeFloating = true))
        assertTrue(splitToggleUsable(homeFloating = false))
        assertTrue(isFloatingMode("freeform"))
        assertTrue(isFloatingMode("5"))
        assertFalse(isFloatingMode("fullscreen"))
        assertFalse(isFloatingMode(null))
    }

    @Test
    fun theFloatingHomeIsReopenedOnlyOnceItsMemoryIsCleared() {
        assertTrue(shouldReopenHome("cleared", stillFloating = true, alive = true))
        assertFalse(shouldReopenHome("no shell: refused", stillFloating = true, alive = true))
        assertFalse(shouldReopenHome("cleared", stillFloating = false, alive = true))
        assertFalse(shouldReopenHome("cleared", stillFloating = true, alive = false))
    }

    @Test
    fun aFloatingHomeIsReopenedFirstThenSwitchedToTheFullscreenOne() {
        assertEquals(FloatingStep.REOPEN, floatingHomeStep(fullscreenHomeOn = false, reopenedBefore = false))
        assertEquals(FloatingStep.SWITCH, floatingHomeStep(fullscreenHomeOn = false, reopenedBefore = true))
        assertEquals(FloatingStep.NONE, floatingHomeStep(fullscreenHomeOn = true, reopenedBefore = true))
    }

    @Test
    fun oldQfFirmwareOpensAppsFullScreenInsteadOfBeside() {
        val march2025 = 1_742_792_685_000L
        val july2026 = 1_784_518_991_000L
        assertFalse(besideDashboardAllowed(qf = true, buildTime = march2025))
        assertTrue(besideDashboardAllowed(qf = true, buildTime = july2026))
        assertTrue(besideDashboardAllowed(qf = false, buildTime = march2025))
        assertTrue(besideDashboardAllowed(qf = true, buildTime = 0L))
    }

    @Test
    fun aNaviKeyOnTheDashboardIsWhatFloatsTheHome() {
        assertTrue(navTargetFloatsHome("com.openauto.dash", "com.openauto.dash"))
        assertFalse(navTargetFloatsHome("com.waze", "com.openauto.dash"))
        assertFalse(navTargetFloatsHome("nothing", "com.openauto.dash"))
        assertFalse(navTargetFloatsHome(null, "com.openauto.dash"))
    }

    @Test
    fun theFloatingMemoryIsClearedByNameThroughTheShell() {
        assertEquals(
            "CLASSPATH=/data/app/x/base.apk app_process /system/bin com.openauto.dash.LaunchParamsReset com.openauto.dash",
            LaunchParamsReset.command("/data/app/x/base.apk", "com.openauto.dash")
        )
    }

    @Test
    fun theWindowsOwnModeIsRead() {
        val config = "{1.0 ?mcc?mnc [en_US] ldltr sw720dp w1280dp h648dp 160dpi xlrg long land finger -keyb/v/h -nav/h " +
            "winConfig={ mBounds=Rect(0, 0 - 1280, 720) mAppBounds=Rect(0, 0 - 1280, 720) mWindowingMode=freeform " +
            "mDisplayWindowingMode=fullscreen mActivityType=home mAlwaysOnTop=undefined mRotation=ROTATION_0} s.4}"
        assertEquals("freeform", windowingModeOf(config))
        assertEquals("split-screen-secondary", windowingModeOf("winConfig={ mWindowingMode=split-screen-secondary mDisplayWindowingMode=fullscreen }"))
        assertNull(windowingModeOf("{1.0 ?mcc?mnc sw720dp}"))
    }

    @Test
    fun theMapsDockOrASplitComingUpIsLeftAlone() {
        assertFalse(stuck(mapsDocked = true))
        assertFalse(stuck(recent = true))
    }

    @Test
    fun unknownSizesAreLeftAlone() {
        assertFalse(stuck(w = 0))
        assertFalse(stuck(h = 0))
        assertFalse(stuck(dw = 0, dh = 0))
    }

    private val self = "com.openauto.dash"

    @Test
    fun theDockedStackOfAnotherAppIsClosed() {
        val listing = """
            Stack id=7 bounds=[0,0][24,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=split-screen-primary mActivityType=standard} }
              taskId=31: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,0][24,720] userId=0 visible=true
            Stack id=0 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=split-screen-secondary mActivityType=home} }
              taskId=2: com.openauto.dash/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertEquals(7, WindowListing.dockedStackToClose(listing, self))
    }

    @Test
    fun theDashboardsOwnDockedStackIsNeverClosed() {
        val listing = """
            Stack id=3 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=split-screen-primary mActivityType=home} }
              taskId=2: com.openauto.dash/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertNull(WindowListing.dockedStackToClose(listing, self))
    }

    @Test
    fun aNumericModeIsReadToo() {
        val listing = """
            Stack id=9 bounds=[0,0][24,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=3 mActivityType=standard} }
              taskId=40: com.waze/.MainActivity bounds=[0,0][24,720] userId=0 visible=true
            Stack id=0 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=1 mActivityType=home} }
              taskId=2: com.openauto.dash/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertEquals(9, WindowListing.dockedStackToClose(listing, self))
    }

    @Test
    fun noDockedStackOrOneOnATileDisplayIsNothingToClose() {
        val listing = """
            Stack id=12 bounds=[0,0][522,362] displayId=7 userId=0
             configuration={ winConfig={ mWindowingMode=split-screen-primary mActivityType=standard} }
              taskId=80: com.waze/.MainActivity bounds=[0,0][522,362] userId=0 visible=true
            Stack id=0 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=home} }
              taskId=2: com.openauto.dash/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertNull(WindowListing.dockedStackToClose(listing, self))
    }
}
