package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which Overpass server is asked first: the driver's own, then the one that answered last; failed ones left alone for a while, all of them when all failed. */
class OverpassTest {

    private val s = Overpass.SERVERS

    @Test
    fun theServerThatAnsweredLastGoesFirst_thenTheOthersRoundTheList() {
        assertEquals(s, Overpass.order(s, null, emptyMap(), 1_000L))
        assertEquals(listOf(s[2], s[3], s[0], s[1]), Overpass.order(s, s[2], emptyMap(), 1_000L))
    }

    @Test
    fun aServerThatJustFailedIsLeftAlone_untilTheWaitIsOver() {
        val failed = mapOf(s[0] to 100_000L)
        assertEquals(listOf(s[1], s[2], s[3]), Overpass.order(s, s[0], failed, 100_000L + Overpass.SKIP_MS - 1))
        assertEquals(s, Overpass.order(s, s[0], failed, 100_000L + Overpass.SKIP_MS))
    }

    @Test
    fun whenEveryServerFailedAllAreTriedAgain() {
        val failed = s.associateWith { 100_000L }
        assertEquals(listOf(s[1], s[2], s[3], s[0]), Overpass.order(s, s[1], failed, 100_100L))
    }

    @Test
    fun theDriversOwnServerGoesFirst_unlessItIsOneOfThePublicOnes() {
        assertEquals(s, Overpass.servers(""))
        assertEquals(s, Overpass.servers(" " + s[1] + " "))
        assertEquals(listOf("https://overpass.example.org/api/interpreter") + s, Overpass.servers("https://overpass.example.org/api/interpreter"))
    }

    @Test
    fun aKeyIsMaskedToItsEnds() {
        assertEquals("", ApiKeys.mask("  "))
        assertEquals("••••••••", ApiKeys.mask("short123"))
        assertEquals("AIza••••••k3Q", ApiKeys.mask("AIzaSyExampleKeyValue_k3Q"))
    }
}
