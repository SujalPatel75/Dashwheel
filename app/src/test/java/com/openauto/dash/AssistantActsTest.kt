package com.openauto.dash

import com.openauto.dash.AssistantRules.Match
import com.openauto.dash.AssistantRules.Saved
import com.openauto.dash.AssistantRules.StopWay
import com.openauto.dash.link.RecentCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Dashwheel's Gemini does for the driver (AssistantActs): who is called, where it guides, what it accepts. */
class AssistantActsTest {

    private val people = listOf(
        Callee("Marie Dupont", "0601"),
        Callee("Marie-Claire", "0602"),
        Callee("Paul Martin", "0603"),
        Callee("Maman", "0604"),
        Callee("Jérôme", "0605")
    )

    private fun names(m: Match<Callee>) = when (m) {
        Match.None -> emptyList()
        is Match.One -> listOf(m.item.name)
        is Match.Several -> m.items.map { it.name }
    }

    @Test
    fun aWholeNameIsThatPersonEvenWhenAWordIsShared() {
        assertEquals(listOf("Maman"), names(AssistantRules.match("maman", people) { it.name }))
        assertEquals(listOf("Marie Dupont"), names(AssistantRules.match("Marie Dupont", people) { it.name }))
        // Accents and case, as the speech recognition writes them.
        assertEquals(listOf("Jérôme"), names(AssistantRules.match("jerome", people) { it.name }))
    }

    @Test
    fun aFirstNameShownByTwoAsksWhichOne() {
        val m = AssistantRules.match("Marie", people) { it.name }
        assertTrue(m is Match.Several)
        assertEquals(listOf("Marie Dupont", "Marie-Claire"), names(m))
    }

    @Test
    fun aSurnameOrTheStartOfANameFindsThePerson() {
        assertEquals(listOf("Paul Martin"), names(AssistantRules.match("Martin", people) { it.name }))
        assertEquals(listOf("Paul Martin"), names(AssistantRules.match("martin paul", people) { it.name }))
        assertEquals(listOf("Paul Martin"), names(AssistantRules.match("Pau", people) { it.name }))
    }

    @Test
    fun aNearMissOfTheRecognitionStillFindsThem() {
        assertEquals(listOf("Jérôme"), names(AssistantRules.match("Jerom", people) { it.name }))
        assertEquals(listOf("Maman"), names(AssistantRules.match("Mamam", people) { it.name }))
    }

    @Test
    fun nobodyMatchingIsNobodyNeverAGuess() {
        assertEquals(Match.None, AssistantRules.match("Zoé", people) { it.name })
        assertEquals(Match.None, AssistantRules.match("", people) { it.name })
        assertEquals(Match.None, AssistantRules.match("  ", people) { it.name })
        // Short names aren't "near" anything: Bob is not Rob.
        assertEquals(Match.None, AssistantRules.match("Bob", listOf(Callee("Rob", "1"))) { it.name })
    }

    @Test
    fun theSameNameTwiceIsOnePerson() {
        val twice = listOf(Callee("Marie", "0601"), Callee("Marie", "0699"))
        val m = AssistantRules.match("marie", twice) { it.name }
        assertTrue(m is Match.One)
        assertEquals("0601", (m as Match.One).item.number)
    }

    @Test
    fun aCallGoesOnlyAfterTheSamePersonWasNamedToTheDriver() {
        val now = 1_000_000L
        val asked = "0601" to now - 30_000
        assertTrue(AssistantRules.mayGo(true, asked, "0601", now))
        // "Confirmed" on the first try, for someone else, too late, or not confirmed: not a yes.
        assertFalse(AssistantRules.mayGo(true, null, "0601", now))
        assertFalse(AssistantRules.mayGo(true, asked, "0602", now))
        assertFalse(AssistantRules.mayGo(true, "0601" to now - AssistantRules.CONFIRM_MS - 1, "0601", now))
        assertFalse(AssistantRules.mayGo(false, asked, "0601", now))
    }

    @Test
    fun calleesAreFavouritesThenNamedCallsEachNumberOnce() {
        val favourites = listOf(Favourite("Marie", "+33 6 01 02 03 04", null), Favourite("No number", null, null))
        val calls = listOf(
            RecentCall("0601020304", "Marie D", RecentCall.Type.INCOMING, 1),
            RecentCall("0700000000", null, RecentCall.Type.MISSED, 2),
            RecentCall("0711111111", "Garage", RecentCall.Type.OUTGOING, 3)
        )
        assertEquals(
            listOf(Callee("Marie", "+33 6 01 02 03 04"), Callee("Garage", "0711111111")),
            AssistantRules.callees(favourites, calls)
        )
        assertTrue(AssistantRules.callees(null, null).isEmpty())
    }

    @Test
    fun homeAndWorkInTheLaunchersLanguages() {
        listOf("home", "Home", "maison", "à la maison", "chez moi", "Zuhause", "nach Hause", "casa", "thuis", "dom", "домой")
            .forEach { assertEquals(it, Saved.HOME, AssistantRules.savedKind(it)) }
        listOf("work", "office", "travail", "boulot", "Arbeit", "Büro", "trabajo", "lavoro", "trabalho", "werk", "praca", "работа")
            .forEach { assertEquals(it, Saved.WORK, AssistantRules.savedKind(it)) }
        listOf("Maison du Pain", "Home Depot", "12 rue de Paris", "").forEach { assertNull(it, AssistantRules.savedKind(it)) }
    }

    @Test
    fun aLastDestinationOnlyByItsExactName() {
        val places = Places(recent = listOf(Place("Gym", 1.0, 1.0), Place("12 Rue de Rivoli, Paris", 2.0, 2.0)))
        assertEquals("Gym", AssistantRules.recentNamed("gym", places)?.name)
        // "Paris" is the city, not the last address that happens to be in it.
        assertNull(AssistantRules.recentNamed("Paris", places))
        assertNull(AssistantRules.recentNamed("", places))
    }

    @Test
    fun parkingMinutesMustBeSensible() {
        assertEquals(120, AssistantRules.parkingMinutes(120.0))
        assertEquals(90, AssistantRules.parkingMinutes(89.6))
        assertNull(AssistantRules.parkingMinutes(0.0))
        assertNull(AssistantRules.parkingMinutes(-30.0))
        assertNull(AssistantRules.parkingMinutes(Double.NaN))
        assertNull(AssistantRules.parkingMinutes(AssistantRules.PARKING_MAX_MIN + 1.0))
    }

    @Test
    fun aRefuelNeedsLitresAndAPlausiblePrice() {
        assertEquals(AssistantRules.Fill.Ok(40.0, 1.8), AssistantRules.fill(40.0, 1.8, Double.NaN))
        // Only the amount paid: the price per litre comes from it.
        assertEquals(AssistantRules.Fill.Ok(40.0, 1.75), AssistantRules.fill(40.0, Double.NaN, 70.0))
        assertEquals(AssistantRules.Fill.Ok(40.0, null), AssistantRules.fill(40.0, Double.NaN, Double.NaN))
        assertTrue(AssistantRules.fill(Double.NaN, 1.8, Double.NaN) is AssistantRules.Fill.Bad)
        assertTrue(AssistantRules.fill(0.5, 1.8, Double.NaN) is AssistantRules.Fill.Bad)
        assertTrue(AssistantRules.fill(500.0, 1.8, Double.NaN) is AssistantRules.Fill.Bad)
        // The total said as the price per litre.
        assertTrue(AssistantRules.fill(40.0, 72.0, Double.NaN) is AssistantRules.Fill.Bad)
    }

    @Test
    fun aStopGoesThroughMapsOnlyWhenItCanAndNeverReplacesTheRouteUnasked() {
        val maps = NavHandoff.MAPS
        val waze = NavHandoff.WAZE
        assertEquals(StopWay.VIA_MAPS, AssistantRules.stopWay(fuel = true, ownGuidance = false, runningApp = maps, destinationKnown = true, instead = false))
        assertEquals(StopWay.ASK_INSTEAD, AssistantRules.stopWay(fuel = true, ownGuidance = false, runningApp = maps, destinationKnown = false, instead = false))
        assertEquals(StopWay.ASK_INSTEAD, AssistantRules.stopWay(fuel = true, ownGuidance = false, runningApp = waze, destinationKnown = true, instead = false))
        assertEquals(StopWay.ASK_INSTEAD, AssistantRules.stopWay(fuel = true, ownGuidance = true, runningApp = null, destinationKnown = true, instead = false))
        assertEquals(StopWay.ASK_INSTEAD, AssistantRules.stopWay(fuel = false, ownGuidance = false, runningApp = maps, destinationKnown = true, instead = false))
        // Agreed by the driver, or nothing to replace: straight there.
        assertEquals(StopWay.GO_THERE, AssistantRules.stopWay(fuel = true, ownGuidance = false, runningApp = waze, destinationKnown = true, instead = true))
        assertEquals(StopWay.GO_THERE, AssistantRules.stopWay(fuel = false, ownGuidance = false, runningApp = null, destinationKnown = false, instead = false))
    }

    @Test
    fun theCheapestStationAheadRatherThanBehind() {
        fun st(id: Long, lat: Double, price: Double) = RankedStation(FuelStation(id, "Rue", "Town", lat, 2.0, emptyMap()), price, 1.0)
        val behind = st(1, 47.9, 1.70)
        val ahead = st(2, 48.1, 1.80)
        val ranked = listOf(behind, ahead)
        // Heading north from 48.0: the one to the south is behind.
        assertEquals(ahead, AssistantRules.ahead(ranked, 48.0, 2.0, 0f))
        assertEquals(behind, AssistantRules.ahead(ranked, 48.0, 2.0, 180f))
        assertEquals(behind, AssistantRules.ahead(ranked, 48.0, 2.0, null))
        // None ahead: the cheapest all the same.
        assertEquals(behind, AssistantRules.ahead(listOf(behind), 48.0, 2.0, 0f))
        assertNull(AssistantRules.ahead(emptyList(), 48.0, 2.0, 0f))
    }

    @Test
    fun mapsIsGivenTheStopThenTheDestination() {
        val byPosition = NavHandoff.viaUri(48.1, 2.2, NavHandoff.Target("Mum's", 48.85, 2.35, null, 0))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=48.850000%2C2.350000&waypoints=48.100000%2C2.200000&travelmode=driving&dir_action=navigate",
            byPosition
        )
        val byWords = NavHandoff.viaUri(48.1, 2.2, NavHandoff.Target("Gare de Lyon", null, null, "Gare de Lyon", 0))
        assertTrue(byWords!!.contains("destination=Gare%20de%20Lyon&"))
        assertNull(NavHandoff.viaUri(48.1, 2.2, NavHandoff.Target("", null, null, null, 0)))
    }
}
