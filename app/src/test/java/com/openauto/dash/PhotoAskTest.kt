package com.openauto.dash

import com.openauto.dash.link.PhotoAnswer
import com.openauto.dash.link.PhotoQuestion
import com.openauto.dash.link.ServiceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** A warning light or a booklet page read by Gemini, and what reaches the servicing planner (PhotoAsk.kt). */
class PhotoAskTest {

    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun aLightIsReadWithItsSeverityAndSteps() {
        val text = """```json
            {"title":"Glow plug light flashing","severity":"SOON","meaning":"The engine computer found a fault.","steps":["Check the code"," ",""]}
        ```"""
        val a = PhotoPrompt.parseLight(text, 7)!!
        assertEquals(7, a.id)
        assertEquals(PhotoQuestion.LIGHT, a.kind)
        assertEquals(PhotoAnswer.SEVERITY_SOON, a.severity)
        assertEquals(listOf("Check the code"), a.steps)
        // An unknown severity is taken as "soon", never as "ok".
        assertEquals(PhotoAnswer.SEVERITY_SOON, PhotoPrompt.parseLight("""{"title":"X","severity":"fine","meaning":"","steps":[]}""", 1)!!.severity)
        assertNull(PhotoPrompt.parseLight("""{"title":"","severity":"ok"}""", 1))
    }

    @Test
    fun aBookletKeepsOnlyWhatMakesSense() {
        val text = """{"services":[
            {"kind":"oil","every_km":20000,"every_months":24,"done_date":"2025-03-14","done_km":182000},
            {"kind":"TIMING_BELT","every_km":5},
            {"kind":"WIPERS","every_km":20000},
            {"kind":"BRAKE_FLUID","done_date":"2031-01-01"},
            {"kind":"COOLANT"}
        ]}"""
        val lines = PhotoPrompt.parseBooklet(text, today)!!
        assertEquals(listOf("OIL"), lines.map { it.kind })
        assertEquals(ServiceEntry("OIL", everyKm = 20000, everyMonths = 24, doneDate = "2025-03-14", doneKm = 182000), lines[0])
        assertEquals(emptyList<ServiceEntry>(), PhotoPrompt.parseBooklet("""{"services":[]}""", today))
        assertNull(PhotoPrompt.parseBooklet("nothing", today))
    }

    @Test
    fun theBookletsFiguresReplaceThePlannersAndKeepTheRest() {
        val current = UpkeepInterval(UpkeepKind.OIL, everyKm = 30000, everyMonths = 12)
        val only = BookletRules.interval(ServiceEntry("OIL", everyKm = 20000), UpkeepKind.OIL, current)!!
        assertEquals(20000, only.everyKm)
        assertEquals(12, only.everyMonths)
        assertTrue(only.own)
        assertNull(BookletRules.interval(ServiceEntry("OIL", doneKm = 1000), UpkeepKind.OIL, current))
    }

    @Test
    fun anOlderServiceNeverPutsThePlannerBack() {
        val zone = ZoneOffset.UTC
        val march = LocalDate.of(2025, 3, 14).atStartOfDay(zone).toInstant().toEpochMilli()
        val logged = UpkeepDone(km = 182000, at = march)
        assertNull(BookletRules.done(ServiceEntry("OIL", doneDate = "2024-03-01", doneKm = 160000), logged, zone))
        val newer = BookletRules.done(ServiceEntry("OIL", doneDate = "2026-01-10", doneKm = 201000), logged, zone)!!
        assertEquals(201000, newer.km)
        // Nothing logged yet: taken as it is.
        assertEquals(UpkeepDone(km = 90000, at = null), BookletRules.done(ServiceEntry("OIL", doneKm = 90000), null, zone))
        // By mileage when only that can be compared.
        assertNull(BookletRules.done(ServiceEntry("OIL", doneKm = 150000), UpkeepDone(km = 182000), zone))
        assertNull(BookletRules.done(ServiceEntry("OIL", everyKm = 20000), logged, zone))
    }

    @Test
    fun theQuestionsAskForTheRightThings() {
        assertTrue(PhotoPrompt.light("Citroën C4 Picasso", AiLanguage.GERMAN).contains("German"))
        val booklet = PhotoPrompt.booklet("Citroën C4 Picasso")
        UpkeepKind.entries.forEach { assertTrue(booklet.contains(it.name)) }
    }
}
