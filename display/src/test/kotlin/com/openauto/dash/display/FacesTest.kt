package com.openauto.dash.display

import com.openauto.dash.link.ClusterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

/** The lines the head unit's page board shows that the Pi's own pages once lacked: the date, a running navigation app, doors not yet known. */
class FacesTest {

    private fun pixels(image: BufferedImage): List<Int> = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toList()

    private fun board(faces: List<String>, state: ClusterState = ClusterState(clock = 1_700_000_000_000, night = true)) =
        state.copy(layout = ClusterState.Layout("BIG_STACK", faces))

    @Test
    fun theDateGoesUnderTheClockWhenTheHeadUnitSendsOne() {
        val painter = Painter(1024, 600, overscanPct = 0)
        val without = pixels(painter.paintCluster(board(listOf("CLOCK", "SPEED", "RANGE")), 0))
        val with = pixels(painter.paintCluster(board(listOf("CLOCK", "SPEED", "RANGE"), ClusterState(clock = 1_700_000_000_000, night = true, date = "Jeudi 15 octobre")), 0))
        assertNotEquals(without, with)
    }

    @Test
    fun aNavigationAppRunningWithNothingToSayIsNamed_elseNoRoute() {
        val painter = Painter(1024, 600, overscanPct = 0)
        val idle = ClusterState(clock = 1_700_000_000_000, night = true, labels = ClusterState.Labels(navRunning = "%s est lancé", noRoute = "Aucun itinéraire"))
        val none = pixels(painter.paintCluster(board(listOf("NAV", "CLOCK", "RANGE"), idle), 0))
        val waze = pixels(painter.paintCluster(board(listOf("NAV", "CLOCK", "RANGE"), idle.copy(navApp = "Waze")), 0))
        assertNotEquals(none, waze)
        assertEquals("Waze est lancé", ClusterText(idle, idle.labels).navRunning("Waze"))
        // An older head unit's labels have no such line: English then.
        assertEquals("Waze is running", ClusterText(idle.copy(labels = null), null).navRunning("Waze"))
    }

    @Test
    fun doorsNotReportedYetAreSaid_notShownAsClosed() {
        val painter = Painter(1024, 600, overscanPct = 0)
        val closed = ClusterState(clock = 1_700_000_000_000, night = true, open = emptyList(), doorsKnown = true)
        val unknown = closed.copy(doorsKnown = false)
        assertNotEquals(
            pixels(painter.paintCluster(board(listOf("DOORS", "CLOCK", "RANGE"), closed), 0)),
            pixels(painter.paintCluster(board(listOf("DOORS", "CLOCK", "RANGE"), unknown), 0))
        )
        assertEquals(null, ClusterText(closed, null).doorsUnknown)
        assertEquals("Waiting for car box data", ClusterText(unknown, null).doorsUnknown)
        // An older head unit never sends the flag: its doors count as known, as before.
        assertTrue(ClusterState(clock = 0).doorsKnown)
    }
}
