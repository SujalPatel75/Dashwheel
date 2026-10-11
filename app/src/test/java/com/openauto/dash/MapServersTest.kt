package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which addresses the map and the router may be pointed at. */
class MapServersTest {

    @Test
    fun webAddressesWithAHostAreUsable() {
        assertTrue(MapServers.usable(MapServers.DEFAULT_STYLE_DARK))
        assertTrue(MapServers.usable("http://192.168.1.10:8002/route"))
        assertTrue(MapServers.usable(" https://tiles.example.com/styles/dark "))
    }

    @Test
    fun anythingElseFallsBack() {
        assertFalse(MapServers.usable(""))
        assertFalse(MapServers.usable("tiles.example.com/style.json"))
        assertFalse(MapServers.usable("file:///sdcard/style.json"))
        assertFalse(MapServers.usable("https://"))
    }

    @Test
    fun theFreeServersAreTheDefault() {
        assertTrue(MapServers.Servers().isDefault)
        assertFalse(MapServers.Servers(routing = "https://valhalla.example.com/route").isDefault)
    }
}
