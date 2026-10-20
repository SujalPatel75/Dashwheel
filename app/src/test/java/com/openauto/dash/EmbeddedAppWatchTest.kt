package com.openauto.dash

import com.openauto.dash.EmbeddedApp.Remedy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is done about an app that does not show on its tile after it was put there. */
class EmbeddedAppWatchTest {

    @Test
    fun anAppShowingOnItsTileIsLeftAlone() {
        for (look in 0..3) assertEquals(Remedy.DONE, EmbeddedApp.remedy(look, onTile = true, black = false, mayReopen = true))
    }

    @Test
    fun anAppElsewhereIsPlacedAgainThenOpenedAfresh() {
        // Back from YouTube Music opened full screen: still on the main screen, the tile black.
        assertEquals(Remedy.PLACE, EmbeddedApp.remedy(0, onTile = false, black = true, mayReopen = true))
        assertEquals(Remedy.PLACE, EmbeddedApp.remedy(1, onTile = false, black = true, mayReopen = true))
        assertEquals(Remedy.REOPEN, EmbeddedApp.remedy(2, onTile = false, black = true, mayReopen = true))
    }

    @Test
    fun aBlackPictureGetsTimeThenItsPictureAgainAndIsClosedLast() {
        assertEquals(Remedy.WAIT, EmbeddedApp.remedy(0, onTile = true, black = true, mayReopen = true))
        assertEquals(Remedy.REFRESH, EmbeddedApp.remedy(1, onTile = true, black = true, mayReopen = true))
        assertEquals(Remedy.WAIT, EmbeddedApp.remedy(2, onTile = true, black = true, mayReopen = true))
        assertEquals(Remedy.REOPEN, EmbeddedApp.remedy(3, onTile = true, black = true, mayReopen = true))
    }

    @Test
    fun anAppJustStartedIsNeverClosed() {
        // Still loading, or closed a minute ago already: closing it again would go on for ever.
        assertEquals(Remedy.GIVE_UP, EmbeddedApp.remedy(2, onTile = false, black = true, mayReopen = false))
        assertEquals(Remedy.GIVE_UP, EmbeddedApp.remedy(3, onTile = true, black = true, mayReopen = false))
    }

    @Test
    fun anAppNoLongerRunningIsStartedAfreshWhateverTheClock() {
        // Waze's process ended, its stack left empty on the tile (issue #60): placing
        // it again twice then giving up kept its last picture frozen there.
        assertEquals(Remedy.START, EmbeddedApp.remedy(0, onTile = false, black = true, mayReopen = false, running = false))
        assertEquals(Remedy.START, EmbeddedApp.remedy(1, onTile = false, black = true, mayReopen = false, running = false))
        // An app that dies again as soon as it starts is not started over and over.
        assertEquals(Remedy.GIVE_UP, EmbeddedApp.remedy(2, onTile = false, black = true, mayReopen = true, running = false))
        assertEquals(Remedy.GIVE_UP, EmbeddedApp.remedy(3, onTile = true, black = true, mayReopen = true, running = false))
    }

    @Test
    fun aTileStackLeftEmptyIsNotTheApp() {
        // A Hizpo S6's listing once Waze's process ended: task 6080 on the tile's display, no screen in it.
        val listing = """
            Stack id=0 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=home} }
              taskId=6078: com.openauto.dash/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true topActivity=ComponentInfo{com.openauto.dash/com.openauto.dash.MainActivity}
            Stack id=53 bounds=[0,0][800,480] displayId=10 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=6080: com.waze/com.waze.MainActivity bounds=[0,0][800,480] userId=0 visible=false
            Stack id=54 bounds=[0,0][800,480] displayId=10 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=6082: com.waze/com.waze.MainActivity bounds=[0,0][800,480] userId=0 visible=true topActivity=ComponentInfo{com.waze/com.waze.MainActivity}
        """.trimIndent()
        assertEquals(
            listOf(
                WindowListing.AppStack(53, 10, "fullscreen", visible = false, taskId = 6080, empty = true),
                WindowListing.AppStack(54, 10, "fullscreen", taskId = 6082)
            ),
            WindowListing.appStacks(listing, "com.waze")
        )
    }

    @Test
    fun aListingThatNeverNamesTopScreensHasNoEmptyStacks() {
        // Some builds print no "topActivity=" at all: every stack is then taken as holding its app.
        val listing = """
            Stack id=20 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=81: com.waze/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertFalse(WindowListing.appStacks(listing, "com.waze").single().empty)
    }

    @Test
    fun anAndroid12RootTaskLeftEmptyIsToldToo() {
        val listing = """
            RootTask id=1 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=home} }
              taskId=7: com.openauto.dash/com.openauto.dash.MainActivity bounds=[0,0][1280,720] userId=0 visible=true topActivity=ComponentInfo{com.openauto.dash/com.openauto.dash.MainActivity}
            RootTask id=9 bounds=[0,0][640,360] displayId=12 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=11: com.waze/com.waze.MainActivity bounds=[0,0][640,360] userId=0 visible=false
        """.trimIndent()
        assertTrue(WindowListing.appStacks(listing, "com.waze").single().empty)
    }

    @Test
    fun onlyOneFlatBlackIsABlackPicture() {
        val black = 0xFF000000.toInt()
        assertTrue(EmbeddedApp.allBlack(IntArray(16) { black }))
        // Nothing drawn at all yet.
        assertTrue(EmbeddedApp.allBlack(IntArray(16)))
        // A dark app is not a black tile: one pixel of it is enough.
        assertFalse(EmbeddedApp.allBlack(IntArray(16) { if (it == 9) 0xFF030303.toInt() else black }))
        assertFalse(EmbeddedApp.allBlack(IntArray(0)))
    }

    @Test
    fun theListingTellsATaskHiddenOnItsTile() {
        val listing = """
            Stack id=12 bounds=[0,0][522,362] displayId=7 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=80: com.google.android.apps.youtube.music/.activities.MusicActivity bounds=[0,0][522,362] userId=0 visible=false
            Stack id=20 bounds=[0,0][1280,720] displayId=0 userId=0
             configuration={ winConfig={ mWindowingMode=fullscreen mActivityType=standard} }
              taskId=81: com.waze/.MainActivity bounds=[0,0][1280,720] userId=0 visible=true
        """.trimIndent()
        assertEquals(
            listOf(WindowListing.AppStack(12, 7, "fullscreen", visible = false, taskId = 80)),
            WindowListing.appStacks(listing, "com.google.android.apps.youtube.music")
        )
        assertEquals(listOf(WindowListing.AppStack(20, 0, "fullscreen", taskId = 81)), WindowListing.appStacks(listing, "com.waze"))
    }

    @Test
    fun aMapWokenBlankIsMostlyOneColour() {
        val white = 0xFFFFFFFF.toInt()
        val bar = 0xFFE0E0E0.toInt()
        // Maps woken on its tile: a grey band and a search box over a white map
        val woken = IntArray(1000) { i -> if (i < 120) bar else if (i in 200..230) 0xFF5F6368.toInt() else white }
        assertTrue(EmbeddedApp.mostlyFlat(woken))
        // a compressed white, a step or two off, is the same white
        assertTrue(EmbeddedApp.mostlyFlat(IntArray(1000) { i -> if (i % 2 == 0) white else 0xFFFDFEFC.toInt() }))
        // a map: land, roads, parks, water, labels
        val colours = intArrayOf(0xFFF2EFE9.toInt(), white, 0xFFC8E6C9.toInt(), 0xFFAADAFF.toInt(), 0xFFFFE082.toInt(), 0xFF5F6368.toInt())
        assertFalse(EmbeddedApp.mostlyFlat(IntArray(1000) { i -> colours[(i * 7 + i / 13) % colours.size] }))
        assertFalse(EmbeddedApp.mostlyFlat(IntArray(0)))
    }
}
