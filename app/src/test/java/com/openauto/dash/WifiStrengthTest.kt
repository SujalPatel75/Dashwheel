package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiStrengthTest {
    @Test
    fun theBarsLightTheSymbolFromTheDotOut() {
        // Connected with no bars still lights the dot; four bars light it all.
        assertEquals(1, wifiLit(0))
        assertEquals(1, wifiLit(1))
        assertEquals(2, wifiLit(2))
        assertEquals(4, wifiLit(4))
        assertEquals(4, wifiLit(7))
    }
}
