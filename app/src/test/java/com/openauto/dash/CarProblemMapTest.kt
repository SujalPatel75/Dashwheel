package com.openauto.dash

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each problem on its part of the car, on the view that shows it best. */
class CarProblemMapTest {

    // The user's C4 Picasso from above (look.json on the unit, 2026-10-12): nose up.
    private val c4Top = TopFrame(
        nose = Offset(0.5009f, 0f), tail = Offset(0.5009f, 1f),
        fl = Offset(0.3485f, 0.1811f), fr = Offset(0.6534f, 0.1811f),
        rl = Offset(0.3485f, 0.8467f), rr = Offset(0.6534f, 0.8467f)
    )
    // Its rear view's lamps, as the lamp finder found them on the user's photo.
    private val c4Back = EndFrame(left = Offset(0.12f, 0.378f), right = Offset(0.87f, 0.38f))

    @Test
    fun theBrokenLeftBrakeBulbIsOnTheRearViewsLeftLamp() {
        val spot = CarProblemMap.spotOf(0x84)
        assertEquals(CarSpot.REAR_LIGHT_LEFT, spot)
        val view = CarProblemMap.viewFor(spot!!, setOf(CarMapView.TOP, CarMapView.BACK))
        assertEquals(CarMapView.BACK, view)
        assertEquals(listOf(c4Back.left), CarProblemMap.places(spot, view!!, c4Top, c4Back, null))
    }

    @Test
    fun withoutARearViewItGoesOnTheViewFromAboveAtTheBackLeft() {
        val view = CarProblemMap.viewFor(CarSpot.REAR_LIGHT_LEFT, setOf(CarMapView.TOP))
        assertEquals(CarMapView.TOP, view)
        val at = CarProblemMap.places(CarSpot.REAR_LIGHT_LEFT, view!!, c4Top, null, null).single()
        assertTrue("left of the middle: ${at.x}", at.x < 0.5f)
        assertTrue("at the back: ${at.y}", at.y > 0.9f)
        // The drawn car too.
        val drawn = CarProblemMap.places(CarSpot.REAR_LIGHT_LEFT, CarMapView.TOP, TopFrame.DRAWN, null, null).single()
        assertTrue(drawn.x < 0.5f && drawn.y > 0.8f)
    }

    @Test
    fun theEngineIsUnderTheBonnetAndTheTyresOnTheWheels() {
        val engine = CarProblemMap.places(CarSpot.ENGINE, CarMapView.TOP, c4Top, null, null).single()
        assertEquals(0.5f, engine.x, 0.01f)
        assertTrue(engine.y < 0.2f)
        assertEquals(listOf(c4Top.fl), CarProblemMap.places(CarProblemMap.spotOf(0x82)!!, CarMapView.TOP, c4Top, null, null))
    }

    @Test
    fun theFrontViewHasTheCarsLeftOnThePicturesRight() {
        val front = EndFrame(left = Offset(0.8f, 0.45f), right = Offset(0.2f, 0.45f))
        assertEquals(listOf(Offset(0.8f, 0.45f)), CarProblemMap.places(CarSpot.HEADLIGHT_LEFT, CarMapView.FRONT, null, null, front))
    }

    @Test
    fun everyWarningAboutAPartHasAPlace() {
        // The ones about no one part: ice, the key's battery.
        val none = setOf(0x31, 0x36)
        for (code in 0x01..0x87) {
            if (PsaWarnings.of(code) == null || code in none) continue
            val spot = CarProblemMap.spotOf(code)
            assertNotNull("code ${code.toString(16)}", spot)
            assertTrue(CarProblemMap.places(spot!!, CarMapView.TOP, TopFrame.DRAWN, null, null).isNotEmpty())
        }
    }

    @Test
    fun engineCodesGoToTheirSystem() {
        assertEquals(CarSpot.ENGINE, CarProblemMap.spotOfCode("P0301"))
        assertEquals(CarSpot.EXHAUST, CarProblemMap.spotOfCode("P0401"))
        assertEquals(CarSpot.EXHAUST, CarProblemMap.spotOfCode("P2463"))
        assertEquals(CarSpot.GEARBOX, CarProblemMap.spotOfCode("P0730"))
        assertEquals(CarSpot.BRAKES, CarProblemMap.spotOfCode("C0035"))
        assertEquals(null, CarProblemMap.spotOfCode("X"))
    }

    @Test
    fun theRearLampsAreFoundByTheirRed() {
        val w = 100
        val h = 60
        val silver = (0xFF shl 24) or (0xB2 shl 16) or (0xB4 shl 8) or 0xB3
        val red = (0xFF shl 24) or (0xC8 shl 16) or (0x20 shl 8) or 0x20
        val px = IntArray(w * h) { silver }
        for (y in 18..26) {
            for (x in 5..17) px[y * w + x] = red
            for (x in 82..94) px[y * w + x] = red
        }
        val (left, right) = rearLamps(px, w, h)
        assertEquals(0.11f, left.x, 0.02f)
        assertEquals(0.88f, right.x, 0.02f)
        assertEquals(0.37f, left.y, 0.03f)
        // No red at all: where rear lamps usually are.
        val plain = rearLamps(IntArray(w * h) { silver }, w, h)
        assertEquals(0.14f, plain[0].x, 0.001f)
    }

    @Test
    fun marksOnTheSameSpotStepApart() {
        val out = spread(listOf(Offset(100f, 50f) to 1, Offset(102f, 50f) to 2, Offset(300f, 50f) to 3), gap = 30f)
        assertEquals(Offset(100f, 50f), out[0].first)
        assertTrue("moved: ${out[1].first}", (out[1].first - out[0].first).getDistance() >= 30f)
        assertEquals(Offset(300f, 50f), out[2].first)
    }

    @Test
    fun theVerdictIsTheWorstProblem() {
        val service = listOf(PsaWarnings.Level.SERVICE)
        assertEquals(CarVerdict.DRIVE, carVerdict(service, emptyList(), null, lampOn = false))
        assertEquals(CarVerdict.STOP, carVerdict(service + PsaWarnings.Level.STOP, null, null, lampOn = false))
        assertEquals(CarVerdict.STOP, carVerdict(emptyList(), listOf("P0217"), Severity.STOP, lampOn = true))
        // Codes read and none, nothing from the car: fine; the lamp on says otherwise.
        assertEquals(CarVerdict.FINE, carVerdict(emptyList(), emptyList(), null, lampOn = false))
        assertEquals(CarVerdict.DRIVE, carVerdict(emptyList(), emptyList(), null, lampOn = true))
        // Nothing read yet.
        assertEquals(CarVerdict.UNREAD, carVerdict(emptyList(), null, null, lampOn = false))
    }
}
