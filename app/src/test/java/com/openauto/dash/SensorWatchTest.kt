package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The sensor watch's drift test and its drive sampler (SensorWatch.kt). */
class SensorWatchTest {

    private val day = 24 * 3_600_000L
    private val now = 100 * day

    /** One drive a day up to [now], each value in turn. */
    private fun drives(vararg values: Double, outside: Double? = null, tag: Int = 0): List<WatchSample> =
        values.mapIndexed { i, v -> WatchSample(now - (values.size - i) * day, v, tag, outside) }

    // --- the drift test -------------------------------------------------------

    @Test
    fun nothingIsSaidWhileTheUsualIsStillBeingLearned() {
        val h = drives(12.6, 12.6, 12.5, 12.6, 12.6, 12.0, 12.0, 12.0)
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, h, now))
        val status = DriftRules.status(WatchSignal.RESTING_VOLTS, h, now)
        assertNull(status.usual)
        assertEquals(8, status.learned)
    }

    @Test
    fun aBatteryRestingLowerForThreeDrivesIsADrift() {
        val h = drives(12.6, 12.55, 12.65, 12.6, 12.6, 12.62, 12.58, 12.6, 12.2, 12.25, 12.2)
        val f = DriftRules.check(WatchSignal.RESTING_VOLTS, h, now)
        assertNotNull(f)
        assertEquals(12.6, f!!.usual, 0.01)
        assertEquals(12.2, f.recent, 0.01)
    }

    @Test
    fun oneOddDriveAmongTheLastThreeIsNotADrift() {
        val h = drives(12.6, 12.55, 12.65, 12.6, 12.6, 12.62, 12.58, 12.6, 12.1, 12.6, 12.1)
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, h, now))
    }

    @Test
    fun aChangeTheGoodWayIsNotADrift() {
        // A new battery rests higher: nothing to say.
        val h = drives(12.3, 12.3, 12.35, 12.3, 12.3, 12.3, 12.28, 12.3, 12.7, 12.7, 12.75)
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, h, now))
    }

    @Test
    fun aSmallChangeIsNotADrift() {
        val h = drives(12.6, 12.6, 12.6, 12.6, 12.6, 12.6, 12.6, 12.6, 12.45, 12.45, 12.45)
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, h, now))
    }

    @Test
    fun aReadingThatAlwaysWandersNeedsABiggerChange() {
        // Idle spread wide: 200 rpm higher stays inside three spreads.
        val h = drives(750.0, 900.0, 800.0, 1000.0, 700.0, 950.0, 760.0, 980.0, 1050.0, 1060.0, 1040.0)
        assertNull(DriftRules.check(WatchSignal.IDLE_RPM, h, now))
        val steady = drives(800.0, 805.0, 795.0, 800.0, 802.0, 798.0, 800.0, 801.0, 1000.0, 1010.0, 990.0)
        assertTrue(DriftRules.check(WatchSignal.IDLE_RPM, steady, now)!!.higher)
    }

    @Test
    fun aRelativeReadingIsJudgedAsAShareOfItsUsual() {
        val usual = DoubleArray(8) { 450.0 }
        val shorter = drives(*usual, 300.0, 290.0, 310.0)
        assertNotNull(DriftRules.check(WatchSignal.FILTER_KM, shorter, now))
        val alittle = drives(*usual, 400.0, 390.0, 410.0)
        assertNull(DriftRules.check(WatchSignal.FILTER_KM, alittle, now))
    }

    @Test
    fun aWeatherReadingIsComparedWithDrivesInLikeWeather() {
        // Summer drives warmed up in 6 minutes, winter ones take 10: that's the season, not the thermostat.
        val summer = (0 until 10).map { WatchSample(now - (20 - it) * day, 6.0, 0, 22.0) }
        val winter = (0 until 3).map { WatchSample(now - (3 - it) * day, 10.0, 0, 2.0) }
        assertNull(DriftRules.check(WatchSignal.WARMUP_MIN, summer + winter, now))
        // Settled coolant doesn't follow the weather: a cooler engine in winter is a finding.
        val warm = (0 until 10).map { WatchSample(now - (20 - it) * day, 90.0, 0, 22.0) }
        val cool = (0 until 3).map { WatchSample(now - (3 - it) * day, 78.0, 0, 2.0) }
        assertNotNull(DriftRules.check(WatchSignal.SETTLED_COOLANT, warm + cool, now))
    }

    @Test
    fun voltsFromAnotherSourceAreNotComparedWithTheUsual() {
        val ecu = drives(12.6, 12.6, 12.6, 12.6, 12.6, 12.6, 12.6, 12.6, tag = DriveSampler.TAG_ECU)
        val pin = (0 until 3).map { WatchSample(now - (2 - it) * 3_600_000L, 12.2, DriveSampler.TAG_PIN) }
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, ecu + pin, now))
    }

    @Test
    fun oldDrivesAreNoLongerRecent() {
        val h = drives(12.6, 12.55, 12.65, 12.6, 12.6, 12.62, 12.58, 12.6, 12.2, 12.25, 12.2)
        assertNull(DriftRules.check(WatchSignal.RESTING_VOLTS, h, now + 40 * day))
    }

    // --- one drive --------------------------------------------------------------

    private fun ObdData.at(t: Long, s: DriveSampler, outside: Double? = 10.0) = s.feed(this, t, outside)

    @Test
    fun theBatteryAtRestIsReadBeforeTheEngineStarts() {
        val s = DriveSampler(hotC = 90)
        var t = 1_000_000L
        repeat(5) { ObdData(voltage = 12.4, voltageFromEcu = true).at(t, s); t += 1_000 }
        // Cranking and running don't count as rest.
        repeat(5) { ObdData(rpm = 800, voltage = 14.2, voltageFromEcu = true).at(t, s); t += 1_000 }
        ObdData(voltage = 12.9, voltageFromEcu = true).at(t, s)
        val out = s.finish()
        assertEquals(12.4, out[WatchSignal.RESTING_VOLTS]!!.value, 0.001)
        assertEquals(DriveSampler.TAG_ECU, out[WatchSignal.RESTING_VOLTS]!!.tag)
        assertEquals(10.0, out[WatchSignal.RESTING_VOLTS]!!.outsideC!!, 0.001)
        assertNull(out[WatchSignal.CHARGING_VOLTS])
    }

    @Test
    fun theWarmUpIsTimedFrom40To70DegreesOfRunning() {
        val s = DriveSampler(hotC = 90)
        var t = 0L
        // From 20 °C, a degree every 10 s of running: 40 at 200 s, 70 at 500 s, so 5 minutes.
        for (i in 0..700) {
            ObdData(rpm = 1500, speedKmh = 40, coolantTempC = minOf(90, 20 + i / 10)).at(t, s)
            t += 1_000
        }
        assertEquals(5.0, s.finish()[WatchSignal.WARMUP_MIN]!!.value, 0.1)
    }

    @Test
    fun anEngineThatNeverGetsWarmIsReckonedFromHowFarItGot() {
        val s = DriveSampler(hotC = 90)
        var t = 0L
        // Stuck at 55 °C after 25 minutes: 15 of the 30 degrees, so about twice the time.
        for (i in 0..1800) {
            ObdData(rpm = 1500, speedKmh = 60, coolantTempC = if (i < 60) 30 else 55).at(t, s)
            t += 1_000
        }
        val minutes = s.finish()[WatchSignal.WARMUP_MIN]!!.value
        assertTrue(minutes > 50)
    }

    @Test
    fun aWarmStartTimesNoWarmUp() {
        val s = DriveSampler(hotC = 90)
        for (i in 0..600) ObdData(rpm = 900, coolantTempC = 85).at(i * 1_000L, s)
        assertNull(s.finish()[WatchSignal.WARMUP_MIN])
    }

    @Test
    fun theIdleIsReadOnlyStandingStillAndWarm() {
        val s = DriveSampler(hotC = 90)
        var t = 0L
        repeat(60) { ObdData(rpm = 1100, coolantTempC = 50).at(t, s); t += 1_000 }
        repeat(60) { ObdData(rpm = 820, coolantTempC = 88).at(t, s); t += 1_000 }
        repeat(60) { ObdData(rpm = 2000, speedKmh = 70, coolantTempC = 90).at(t, s); t += 1_000 }
        assertEquals(820.0, s.finish()[WatchSignal.IDLE_RPM]!!.value, 0.1)
    }

    @Test
    fun readingsComeOnceASecondAtMost() {
        val s = DriveSampler()
        // Ten a second for a second: one value, not enough for a resting voltage.
        for (i in 0 until 10) ObdData(voltage = 12.5).at(i * 100L, s)
        assertNull(s.finish()[WatchSignal.RESTING_VOLTS])
    }

    // --- the filter, the store, the words ---------------------------------------

    @Test
    fun aRegenerationEndsAnInterval() {
        assertNull(FilterIntervals.ended(null, 10.0))
        assertNull(FilterIntervals.ended(300.0, 290.0))
        assertEquals(420.0, FilterIntervals.ended(420.0, 2.0)!!, 0.0)
        // Too short to be one: a misread.
        assertNull(FilterIntervals.ended(40.0, 0.0))
    }

    @Test
    fun theHistoryReadsBackAsSaved() {
        val h = mapOf(
            WatchSignal.RESTING_VOLTS to listOf(WatchSample(1L, 12.4, 1, 8.5), WatchSample(2L, 12.3, 1, null)),
            WatchSignal.IDLE_RPM to listOf(WatchSample(3L, 810.0))
        )
        assertEquals(h, WatchStore.decodeHistory(WatchStore.encodeHistory(h)))
    }

    @Test
    fun theVoiceSaysNumbersItsOwnWay() {
        val units = UnitSystem()
        assertEquals("12,2", WatchLines.number(WatchSignal.RESTING_VOLTS, 12.21, Locale.FRANCE, units))
        assertEquals("194", WatchLines.number(WatchSignal.SETTLED_COOLANT, 90.0, Locale.US, UnitSystem(fahrenheit = true)))
        val f = WatchFinding(WatchSignal.FILTER_KM, 450.0, 300.0, now)
        val line = WatchLines.spoken(f, AiLanguage.ENGLISH, units)
        assertEquals(R.string.watch_say_filter_often, line.res)
        assertEquals(listOf("300", "450"), line.args.take(2))
    }
}
