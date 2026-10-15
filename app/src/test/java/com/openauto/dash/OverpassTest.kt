package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which Overpass server is asked first: the one that answered last, failed ones left alone for a while, all of them when all failed. */
class OverpassTest {

    private val n = Overpass.SERVERS.size

    @Test
    fun theServerThatAnsweredLastGoesFirst_thenTheOthersRoundTheList() {
        assertEquals((0 until n).toList(), Overpass.order(0, LongArray(n), 1_000L))
        assertEquals(listOf(2, 3, 0, 1), Overpass.order(2, LongArray(n), 1_000L))
    }

    @Test
    fun aServerThatJustFailedIsLeftAlone_untilTheWaitIsOver() {
        val failed = LongArray(n).also { it[0] = 100_000L }
        assertEquals(listOf(1, 2, 3), Overpass.order(0, failed, 100_000L + Overpass.SKIP_MS - 1))
        assertEquals(listOf(0, 1, 2, 3), Overpass.order(0, failed, 100_000L + Overpass.SKIP_MS))
    }

    @Test
    fun whenEveryServerFailedAllAreTriedAgain() {
        val failed = LongArray(n) { 100_000L }
        assertEquals(listOf(1, 2, 3, 0), Overpass.order(1, failed, 100_100L))
    }
}
