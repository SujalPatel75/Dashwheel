package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A dashboard from the driver's words: Gemini's answer read back and made to fit (DashDesigner.kt). */
class DashDesignerTest {

    private val kinds = setOf("SPEED_LIMIT", "MEDIA", "FUEL_TO_DEST", "NAVMAP", "CLOCK")

    private fun fit(vararg t: ProposedTile, cols: Int = 12, rows: Int = 7, installed: Set<String> = emptySet()) =
        DesignFit.items(t.toList(), cols, rows, kinds, installed)

    private fun overlapFree(items: List<DashboardItem>): Boolean = items.indices.all { i ->
        (i + 1 until items.size).none { j ->
            DashboardStore.rectanglesOverlap(items[i].x, items[i].y, items[i].w, items[i].h, items[j].x, items[j].y, items[j].w, items[j].h)
        }
    }

    @Test
    fun anAnswerInACodeFenceIsRead() {
        val text = "```json\n{\"name\":\"Motorway\",\"tiles\":[{\"kind\":\"navmap\",\"x\":0,\"y\":0,\"w\":7,\"h\":7}]}\n```"
        val (name, tiles) = DesignPrompt.parse(text)!!
        assertEquals("Motorway", name)
        assertEquals(listOf(ProposedTile("NAVMAP", null, 0, 0, 7, 7)), tiles)
        assertNull(DesignPrompt.parse("no json here"))
        assertNull(DesignPrompt.parse("{\"name\":\"x\",\"tiles\":[]}"))
    }

    @Test
    fun aGoodLayoutIsKeptAsItCame() {
        val items = fit(
            ProposedTile("NAVMAP", null, 0, 0, 7, 7),
            ProposedTile("SPEED_LIMIT", null, 7, 0, 5, 3),
            ProposedTile("MEDIA", null, 7, 3, 5, 4)
        )
        assertEquals(3, items.size)
        val map = items[0] as DashboardItem.BuiltinWidget
        assertEquals(BuiltinKind.NAVMAP, map.kind)
        assertEquals(listOf(0, 0, 7, 7), listOf(map.x, map.y, map.w, map.h))
    }

    @Test
    fun unknownKindsMissingAppsAndRepeatsAreLeftOut() {
        val items = fit(
            ProposedTile("CLOCK", null, 0, 0, 3, 2),
            ProposedTile("TELEPORTER", null, 3, 0, 3, 2),
            ProposedTile("CLOCK", null, 6, 0, 3, 2),
            ProposedTile(DesignPrompt.APP_WINDOW, "com.waze", 0, 2, 6, 5),
            ProposedTile(DesignPrompt.APP_ICON, "com.spotify.music", 6, 2, 1, 1),
            installed = setOf("com.spotify.music")
        )
        assertEquals(2, items.size)
        assertTrue(items[1] is DashboardItem.AppShortcut)
    }

    @Test
    fun overlappingTilesAreMovedApart() {
        val items = fit(
            ProposedTile("NAVMAP", null, 0, 0, 8, 7),
            ProposedTile("MEDIA", null, 4, 2, 4, 3),
            ProposedTile("CLOCK", null, 9, 0, 3, 2)
        )
        assertEquals(3, items.size)
        assertTrue(overlapFree(items))
        // The first keeps its place.
        assertEquals(0, items[0].x)
        assertEquals(8, items[0].w)
    }

    @Test
    fun tilesAreKeptInTheGridAndAtTheirSmallestSize() {
        val items = fit(
            ProposedTile("NAVMAP", null, 10, 5, 1, 1),
            ProposedTile("MEDIA", null, -3, 0, 20, 2)
        )
        val map = items.first { (it as DashboardItem.BuiltinWidget).kind == BuiltinKind.NAVMAP }
        val (minW, minH) = BuiltinKind.NAVMAP.minSize()
        assertTrue(map.w >= minW && map.h >= minH)
        assertTrue(map.x + map.w <= 12 && map.y + map.h <= 7)
        items.forEach { assertTrue(it.x >= 0 && it.x + it.w <= 12) }
        assertTrue(overlapFree(items))
    }

    @Test
    fun aTileWithNoRoomLeftShrinksOrIsLeftOut() {
        val items = fit(
            ProposedTile("NAVMAP", null, 0, 0, 12, 7),
            ProposedTile("CLOCK", null, 0, 0, 3, 2)
        )
        assertEquals(1, items.size)
        val upright = fit(
            ProposedTile("NAVMAP", null, 0, 0, 7, 10),
            ProposedTile("MEDIA", null, 0, 0, 7, 4),
            cols = 7, rows = 12
        )
        assertEquals(2, upright.size)
        assertTrue(overlapFree(upright))
        assertFalse(upright.any { it.y + it.h > 12 })
    }

    @Test
    fun aDesignReachesTheShareSheetAsAnyCodeDoes() {
        val items = fit(
            ProposedTile("NAVMAP", null, 0, 0, 7, 7),
            ProposedTile(DesignPrompt.APP_WINDOW, "com.waze", 7, 0, 5, 4),
            ProposedTile("CLOCK", null, 7, 4, 5, 3),
            installed = setOf("com.waze")
        )
        val back = DashShare.decode(DashShare.encode(SharedDashboard("Motorway", false, items)))!!
        assertEquals("Motorway", back.name)
        assertEquals(items, back.items)
    }

    @Test
    fun thePromptGivesTheGridAndTheTiles() {
        val p = DesignPrompt.build(
            "speed limit and music", listOf(CatalogEntry("MEDIA", "Media", "What plays.", 2, 2)),
            listOf("com.waze" to "Waze"), 12, 7, AiLanguage.FRENCH, "Citroën C4 Picasso"
        )
        assertTrue(p.contains("12 columns"))
        assertTrue(p.contains("7 rows"))
        assertTrue(p.contains("MEDIA: Media. What plays. (min 2x2)"))
        assertTrue(p.contains("com.waze = Waze"))
        assertTrue(p.contains("French"))
        assertTrue(DesignPrompt.build(null, emptyList(), emptyList(), 7, 12, AiLanguage.ENGLISH, "car").contains("recording"))
    }
}
