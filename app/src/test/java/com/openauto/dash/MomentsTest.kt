package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which moment Home shows, what a copy keeps, and how far a template reaches. */
class MomentsTest {

    private val all = Moment.entries.toSet()

    @Test
    fun aFaultComesFirst_thenTheRoute_thenTheRest() {
        assertEquals(Moment.FAULT, Moment.pick(all, all))
        assertEquals(Moment.GUIDING, Moment.pick(setOf(Moment.GUIDING, Moment.PARKED, Moment.ENGINE_ON), all))
        assertEquals(Moment.LONG_DRIVE, Moment.pick(setOf(Moment.LONG_DRIVE, Moment.ENGINE_ON), all))
        assertEquals(Moment.ENGINE_ON, Moment.pick(setOf(Moment.ENGINE_ON), all))
        assertNull(Moment.pick(emptySet(), all))
    }

    @Test
    fun aMomentSwitchedOffIsPassedOver() {
        assertEquals(Moment.PARKED, Moment.pick(setOf(Moment.GUIDING, Moment.PARKED), all - Moment.GUIDING))
        assertNull(Moment.pick(setOf(Moment.GUIDING), all - Moment.GUIDING))
    }

    @Test
    fun nothingShowsWhileMomentsIsOff() {
        assertTrue(MomentSettings().live.isEmpty())
        assertEquals(MomentSettings().on, MomentSettings(master = true).live)
        // Long drive starts off; the others on.
        assertFalse(Moment.LONG_DRIVE in MomentSettings().on)
        assertTrue(Moment.GUIDING in MomentSettings().on)
    }

    @Test
    fun theDriveLineHoldsEveryMomentButTheFault() {
        assertEquals(Moment.entries.toSet() - Moment.FAULT, Moment.DRIVE.toSet())
        assertEquals(Moment.entries.toSet(), Moment.PRIORITY.toSet())
    }

    @Test
    fun aCopyLeavesAppsInWindowsAndSystemWidgetsBehind() {
        val page = listOf(
            DashboardItem.BuiltinWidget(BuiltinKind.MEDIA, 0, 0, 5, 3),
            DashboardItem.AppWindow("com.waze", 5, 0, 7, 6),
            DashboardItem.SystemWidget(42, 0, 3, 5, 3),
            DashboardItem.LaunchBar(listOf("a"), 0, 6, 12, 1)
        )
        val copy = MomentStore.copyable(page)
        assertEquals(2, copy.size)
        assertTrue(copy.none { it is DashboardItem.AppWindow || it is DashboardItem.SystemWidget })
        assertTrue(page[1].holdsAnApp())
        assertTrue(DashboardItem.BuiltinWidget(BuiltinKind.MAPS_INSIDE, 0, 0, 5, 3).holdsAnApp())
        assertFalse(page[0].holdsAnApp())
    }

    @Test
    fun everyMomentHasAHome_onEveryScreen() {
        val full = TemplateScreen.of(1280f, 576f, obdPaired = true, driverOnRight = false, mapsDocked = false, dockApps = listOf("a"))
        val bare = TemplateScreen.of(640f, 576f, obdPaired = false, driverOnRight = true, mapsDocked = true, dockApps = emptyList(), canbox = false)
        for (moment in Moment.entries) for (screen in listOf(full, bare)) {
            val items = MomentStore.itemsFor(MomentSettings(), moment, "", screen)
            assertTrue("$moment", items.any { it is DashboardItem.BuiltinWidget })
        }
    }

    @Test
    fun aTemplateReachesOnlyThePagesItHas() {
        // Calm has nothing above or below Home: "all seven" leaves the column alone.
        assertEquals(TemplatePlacer.ROW_PAGES, templateTargets(DashTemplate.CALM, TemplateReach.ALL.pages))
        assertEquals(TemplatePlacer.ALL_PAGES, templateTargets(DashTemplate.DAILY, TemplateReach.ALL.pages))
        assertEquals(setOf(DashboardStore.CENTER), templateTargets(DashTemplate.ROAD_TRIP, TemplateReach.HOME.pages))
    }
}
