package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading Tankerkönig's answer: open stations with their German grades mapped, a refusal as an error. */
class TankerkoenigTest {

    private val json = """{"ok": true, "license": "CC BY 4.0", "data": "MTS-K", "status": "ok", "stations": [
        {"id": "94e70fc4-b22f-4e5a-877f-bc1082cdae81", "name": "TotalEnergies Berlin", "brand": "TotalEnergies",
         "street": "Prenzlauer Allee", "place": "Berlin", "lat": 52.5289, "lng": 13.4181, "dist": 1.6,
         "diesel": 1.699, "e5": 1.859, "e10": 1.799, "isOpen": true, "houseNumber": "1-4", "postCode": 10405},
        {"id": "51d4b5a0-a095-1aa0-e100-80009459e03a", "name": "Freie Tankstelle", "brand": "",
         "street": "HAUPTSTRASSE", "place": "Bernau", "lat": 52.68, "lng": 13.58, "dist": 20.1,
         "diesel": 1.659, "e5": null, "e10": 1.749, "isOpen": true, "houseNumber": "7", "postCode": 16321},
        {"id": "c0000000-0000-0000-0000-000000000003", "name": "Closed", "brand": "Shell",
         "street": "x", "place": "y", "lat": 52.5, "lng": 13.4, "dist": 0.5, "diesel": 1.5, "e5": 1.6, "e10": 1.5, "isOpen": false}
    ]}"""

    @Test
    fun openStationsAreReadWithTheirGradesAndNames() {
        val list = Tankerkoenig.parse(json)
        assertEquals(2, list.size)
        val total = list[0]
        assertEquals("TotalEnergies", total.name)
        assertEquals("Prenzlauer Allee 1-4", total.address)
        assertEquals("Berlin", total.town)
        assertEquals(mapOf(FuelGrade.GAZOLE to 1.699, FuelGrade.SP95 to 1.859, FuelGrade.E10 to 1.799), total.prices)
        // No brand: the name; a null grade left out.
        assertEquals("Freie Tankstelle", list[1].name)
        assertEquals(mapOf(FuelGrade.GAZOLE to 1.659, FuelGrade.E10 to 1.749), list[1].prices)
        // The same station keeps the same number between fetches.
        assertEquals(Tankerkoenig.stationId("94e70fc4-b22f-4e5a-877f-bc1082cdae81"), total.id)
    }

    @Test
    fun aRefusalIsAnErrorWithTheServicesWords() {
        val e = runCatching { Tankerkoenig.parse("""{"ok": false, "message": "apikey not valid"}""") }.exceptionOrNull()
        assertEquals("apikey not valid", e?.message)
    }

    @Test
    fun theAddressAsksAroundTheCarWithTheKey() {
        val url = Tankerkoenig.url(52.52, 13.405, " abc-123 ")
        assertTrue(url.startsWith("https://creativecommons.tankerkoenig.de/json/list.php?"))
        assertTrue(url.contains("lat=52.5200&lng=13.4050&rad=15&sort=dist&type=all&apikey=abc-123"))
    }

    @Test
    fun theCountryPicksTheFeed() {
        assertEquals(FuelPlan.FRANCE, FuelPrices.plan("fr", hasTankerkoenigKey = true))
        assertEquals(FuelPlan.FRANCE, FuelPrices.plan("fr", hasTankerkoenigKey = false))
        assertEquals(FuelPlan.TANKERKOENIG, FuelPrices.plan("de", hasTankerkoenigKey = true))
        assertEquals(FuelPlan.NEEDS_TANKERKOENIG_KEY, FuelPrices.plan("de", hasTankerkoenigKey = false))
        // Unknown (a failed lookup, or another country): both when a key allows, the French feed otherwise.
        assertEquals(FuelPlan.TANKERKOENIG_THEN_FRANCE, FuelPrices.plan(null, hasTankerkoenigKey = true))
        assertEquals(FuelPlan.FRANCE, FuelPrices.plan("be", hasTankerkoenigKey = false))
        assertEquals(Pair(488, 23), CountryLookup.cell(48.853, 2.3499))
    }

    @Test
    fun theDieselCarRanksTheGermanStationsByPrice() {
        val list = Tankerkoenig.parse(json)
        val ranked = FuelPrices.rank(list, FuelGrade.GAZOLE, 52.52, 13.405)
        assertEquals(listOf("Freie Tankstelle", "TotalEnergies"), ranked.map { it.station.name })
    }
}
