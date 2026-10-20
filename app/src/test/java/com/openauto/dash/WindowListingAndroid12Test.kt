package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `am stack list` from Android 12 on names its blocks "RootTask id=" (this
 * sample is an Android 15 emulator's, a tile's display added): read all the
 * same. Read on "Stack id=" alone, a newer unit's listing held nothing.
 */
class WindowListingAndroid12Test {

    private val listing = """
RootTask id=1 bounds=[0,0][2560,1600] displayId=0 userId=0
 configuration={1.0 ?mcc0mnc [en_US] ldltr sw800dp w1280dp h800dp 320dpi xlrg land finger qwerty/v/v dpad/v winConfig={ mBounds=Rect(0, 0 - 2560, 1600) mAppBounds=Rect(0, 0 - 2560, 1600) mMaxBounds=Rect(0, 0 - 2560, 1600) mDisplayRotation=ROTATION_0 mWindowingMode=fullscreen mActivityType=home mAlwaysOnTop=undefined mRotation=ROTATION_0} as.2 s.8 fontWeightAdjustment=0}
  taskId=7: com.openauto.dash/com.openauto.dash.MainActivity bounds=[0,0][2560,1600] userId=0 visible=true topActivity=ComponentInfo{com.openauto.dash/com.openauto.dash.MainActivity}

RootTask id=9 bounds=[0,0][640,360] displayId=12 userId=0
 configuration={1.0 ?mcc0mnc [en_US] ldltr sw360dp w640dp h360dp 160dpi nrml land finger qwerty/v/v dpad/v winConfig={ mBounds=Rect(0, 0 - 640, 360) mAppBounds=Rect(0, 0 - 640, 360) mMaxBounds=Rect(0, 0 - 640, 360) mDisplayRotation=ROTATION_0 mWindowingMode=freeform mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_0} as.2 s.8 fontWeightAdjustment=0}
  taskId=11: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,0][640,360] userId=0 visible=true topActivity=ComponentInfo{com.google.android.apps.maps/com.google.android.maps.MapsActivity}

RootTask id=2 bounds=[0,0][2560,1600] displayId=0 userId=0
 configuration={1.0 ?mcc0mnc [en_US] ldltr sw800dp w1280dp h800dp 320dpi xlrg land finger qwerty/v/v dpad/v winConfig={ mBounds=Rect(0, 0 - 2560, 1600) mAppBounds=Rect(0, 0 - 2560, 1600) mMaxBounds=Rect(0, 0 - 2560, 1600) mDisplayRotation=ROTATION_0 mWindowingMode=fullscreen mActivityType=undefined mAlwaysOnTop=undefined mRotation=ROTATION_0} as.2 s.8 fontWeightAdjustment=0}
  taskId=3: unknown bounds=[0,0][2560,1600] userId=0 visible=false
"""

    @Test
    fun aNewerListingsRootTasksAreReadLikeTheOldStacks() {
        val maps = WindowListing.appStacks(listing, "com.google.android.apps.maps")
        assertEquals(1, maps.size)
        val stack = maps.single()
        assertEquals(9, stack.stackId)
        assertEquals(12, stack.displayId)
        assertEquals("freeform", stack.mode)
        assertEquals(11, stack.taskId)
        assertTrue(stack.visible)
        val self = WindowListing.appStacks(listing, "com.openauto.dash").single()
        assertEquals(0, self.displayId)
        assertEquals("fullscreen", self.mode)
    }

    @Test
    fun theOldWordStillReads() {
        val old = listing.replace("RootTask id=", "Stack id=")
        assertEquals(WindowListing.appStacks(listing, "com.google.android.apps.maps"), WindowListing.appStacks(old, "com.google.android.apps.maps"))
    }

    /**
     * A unit that cannot float apps (Android 13): the dock's Maps came up full
     * screen over the dashboard. The tracker must see no window, and the check
     * after the dashboard stopped must see Maps in front, or Home stays trapped.
     */
    private val mapsFullscreen = """
RootTask id=14 bounds=[0,0][1200,1666] displayId=0 userId=0
 configuration={1.0 ?mcc0mnc [en_AU] ldltr sw800dp w800dp h1110dp 240dpi xlrg port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1200, 1666) mAppBounds=Rect(0, 0 - 1200, 1666) mMaxBounds=Rect(0, 0 - 1200, 1666) mDisplayRotation=ROTATION_0 mWindowingMode=fullscreen mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_0} s.4 fontWeightAdjustment=0}
  taskId=14: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,0][1200,1666] userId=0 visible=true topActivity=ComponentInfo{com.google.android.apps.maps/com.google.android.maps.MapsActivity}

RootTask id=1 bounds=[0,0][1200,1666] displayId=0 userId=0
 configuration={1.0 ?mcc0mnc [en_AU] ldltr sw800dp w800dp h1110dp 240dpi xlrg port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1200, 1666) mAppBounds=Rect(0, 0 - 1200, 1666) mMaxBounds=Rect(0, 0 - 1200, 1666) mDisplayRotation=ROTATION_0 mWindowingMode=fullscreen mActivityType=home mAlwaysOnTop=undefined mRotation=ROTATION_0} s.4 fontWeightAdjustment=0}
  taskId=9: com.openauto.dash/com.openauto.dash.MainActivity bounds=[0,0][1200,1666] userId=0 visible=false topActivity=ComponentInfo{com.openauto.dash/com.openauto.dash.MainActivity}
"""

    @Test
    fun mapsFullscreenInsteadOfItsWindowIsSeenOnANewerListing() {
        assertNull(WindowListing.parseFloatingWindow(mapsFullscreen, packageName = "com.google.android.apps.maps"))
        val full = WindowListing.fullscreenInFront(mapsFullscreen, "com.google.android.apps.maps")
        assertEquals(14, full?.taskId)
        assertEquals(ScreenRect(0, 0, 1200, 1666), full?.bounds)
        // The dashboard back in front: Maps no longer covers it.
        val (mapsBlock, dashBlock) = mapsFullscreen.trim().split("\n\n")
        val dashFront = dashBlock + "\n\n" + mapsBlock.replace("visible=true", "visible=false")
        assertNull(WindowListing.fullscreenInFront(dashFront, "com.google.android.apps.maps"))
    }
}
