package com.openauto.dash

import org.junit.Assert.assertEquals
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
}
