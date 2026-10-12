package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowRulesTest {

    private val now = 1_800_000_000_000L
    private val min = 60_000L

    /** Every card at once, each in its pressing form. */
    private val everything = NowInputs(
        faultCodes = listOf("P0217"), faultSeverity = Severity.STOP,
        guiding = true, turnMetres = 400,
        rangeKm = 50,
        parkingEndsAt = now + 5 * min,
        drivingMs = 130 * min,
        weatherWarning = true,
        nextEventAt = now + 90 * min,
        serviceStage = UpkeepStage.SOON
    )

    @Test
    fun cardsComeInTheirPriorityOrder() {
        assertEquals(
            listOf(
                NowKind.FAULT, NowKind.TURN, NowKind.FUEL, NowKind.PARKING, NowKind.BREAK,
                NowKind.WEATHER, NowKind.AGENDA, NowKind.SERVICE, NowKind.CALM
            ),
            NowRules.cards(everything, now).map { it.kind }
        )
        assertEquals(NowKind.FAULT, NowRules.pick(everything, now).kind)
        // Nothing pressing: the calm line.
        assertEquals(NowCard(NowKind.CALM, NowRules.CALM), NowRules.pick(NowInputs(), now))
        assertEquals(listOf(NowKind.CALM), NowRules.cards(NowInputs(), now).map { it.kind })
    }

    @Test
    fun aFaultIsCriticalWhenSeriousOrNew() {
        fun priority(severity: Severity?, newAt: Long = 0L) =
            NowRules.pick(NowInputs(faultCodes = listOf("P0401"), faultSeverity = severity, newFaultAt = newAt), now).priority
        assertEquals(NowRules.FAULT_STOP, priority(Severity.STOP))
        assertEquals(NowRules.FAULT_SOON, priority(Severity.SOON))
        assertEquals(NowRules.FAULT_MINOR, priority(Severity.OK))
        // Just found: news, unless the verdict is that it's nothing.
        assertEquals(NowRules.FAULT_STOP, priority(Severity.SOON, now - 2 * min))
        assertEquals(NowRules.FAULT_MINOR, priority(Severity.OK, now - 2 * min))
        assertEquals(NowRules.FAULT_SOON, priority(Severity.SOON, now - 20 * min))
        // A known fault to see a garage about sits under the turn and the agenda, beside the servicing.
        assertTrue(NowRules.FAULT_SOON < NowRules.AGENDA_SOON && NowRules.FAULT_SOON > NowRules.SERVICE_DUE)
    }

    @Test
    fun theTurnCountsWithinAKilometre() {
        assertEquals(NowRules.TURN_NEAR, NowRules.pick(NowInputs(guiding = true, turnMetres = 1_000), now).priority)
        assertEquals(NowRules.TURN_FAR, NowRules.pick(NowInputs(guiding = true, turnMetres = 1_200), now).priority)
        assertEquals(NowRules.TURN_FAR, NowRules.pick(NowInputs(guiding = true), now).priority)
        assertEquals(NowKind.CALM, NowRules.pick(NowInputs(guiding = false, turnMetres = 300), now).kind)
        assertEquals(300, NowRules.metres("300 m"))
        assertEquals(1_200, NowRules.metres("1,2 km"))
        assertEquals(644, NowRules.metres("0.4 mi"))
        assertEquals(152, NowRules.metres("500 ft"))
        assertNull(NowRules.metres(""))
    }

    @Test
    fun lowFuelHoldsUntilTheRangeIsClearlyBack() {
        assertTrue(NowRules.lowFuel(LowFuel.WARN_KM, wasLow = false))
        assertFalse(NowRules.lowFuel(LowFuel.WARN_KM + 1, wasLow = false))
        assertTrue(NowRules.lowFuel(LowFuel.WARN_KM + 5, wasLow = true))
        assertFalse(NowRules.lowFuel(NowRules.FUEL_CLEAR_KM, wasLow = true))
        // 0 is the car not saying.
        assertFalse(NowRules.lowFuel(0, wasLow = true))
        assertFalse(NowRules.lowFuel(null, wasLow = true))
    }

    @Test
    fun timedCardsHaveTheirWindows() {
        fun kinds(i: NowInputs) = NowRules.cards(i, now).map { it.kind }
        fun priorityOf(i: NowInputs, kind: NowKind) = NowRules.cards(i, now).first { it.kind == kind }.priority
        assertEquals(NowRules.PARKING_ENDING, priorityOf(NowInputs(parkingEndsAt = now + 10 * min), NowKind.PARKING))
        assertEquals(NowRules.PARKING_RUNNING, priorityOf(NowInputs(parkingEndsAt = now + 40 * min), NowKind.PARKING))
        assertFalse(NowKind.PARKING in kinds(NowInputs(parkingEndsAt = now - min)))
        assertTrue(NowKind.BREAK in kinds(NowInputs(drivingMs = CareRules.FIRST_BREAK_MIN * min)))
        assertFalse(NowKind.BREAK in kinds(NowInputs(drivingMs = CareRules.FIRST_BREAK_MIN * min - 1)))
        // Rain within the hour, or raining now; not rain that has stopped, nor in two hours.
        assertTrue(NowKind.WEATHER in kinds(NowInputs(rainFromMs = now + 50 * min)))
        assertTrue(NowKind.WEATHER in kinds(NowInputs(rainFromMs = now - 10 * min, rainUntilMs = now + 30 * min)))
        assertFalse(NowKind.WEATHER in kinds(NowInputs(rainFromMs = now - 60 * min, rainUntilMs = now - min)))
        assertFalse(NowKind.WEATHER in kinds(NowInputs(rainFromMs = now + 120 * min)))
        assertTrue(NowKind.AGENDA in kinds(NowInputs(nextEventAt = now + 120 * min)))
        assertFalse(NowKind.AGENDA in kinds(NowInputs(nextEventAt = now + 121 * min)))
        assertFalse(NowKind.SERVICE in kinds(NowInputs(serviceStage = UpkeepStage.OK)))
        assertTrue(NowKind.SERVICE in kinds(NowInputs(serviceStage = UpkeepStage.DUE)))
    }

    private fun cards(vararg kinds: Pair<NowKind, Int>) = kinds.map { NowCard(it.first, it.second) } + NowCard(NowKind.CALM, NowRules.CALM)

    @Test
    fun itNeverPagesWhileMoving() {
        val active = cards(NowKind.TURN to NowRules.TURN_FAR, NowKind.WEATHER to NowRules.WEATHER, NowKind.SERVICE to NowRules.SERVICE_DUE)
        var shown = NowRules.step(null, active, now, moving = true)
        assertEquals(NowKind.WEATHER, shown.kind)
        for (s in 1..120) {
            // A swipe does nothing either.
            shown = NowRules.step(shown, active, now + s * 1_000L, moving = true, page = if (s % 7 == 0) 1 else 0)
            assertEquals(NowKind.WEATHER, shown.kind)
        }
    }

    @Test
    fun parkedItPagesThroughTheActiveCards() {
        val active = cards(NowKind.TURN to NowRules.TURN_FAR, NowKind.WEATHER to NowRules.WEATHER, NowKind.SERVICE to NowRules.SERVICE_DUE)
        var shown = NowRules.step(null, active, now, moving = false)
        assertEquals(NowKind.WEATHER, shown.kind)
        shown = NowRules.step(shown, active, now + NowRules.PAGE_MS - 1, moving = false)
        assertEquals(NowKind.WEATHER, shown.kind)
        shown = NowRules.step(shown, active, now + NowRules.PAGE_MS, moving = false)
        assertEquals(NowKind.SERVICE, shown.kind)
        shown = NowRules.step(shown, active, now + 2 * NowRules.PAGE_MS, moving = false)
        assertEquals(NowKind.TURN, shown.kind)
        // Round again, never to the calm line while something else is active.
        shown = NowRules.step(shown, active, now + 3 * NowRules.PAGE_MS, moving = false)
        assertEquals(NowKind.WEATHER, shown.kind)
        // By hand, at once, both ways.
        shown = NowRules.step(shown, active, now + 3 * NowRules.PAGE_MS + 100, moving = false, page = -1)
        assertEquals(NowKind.TURN, shown.kind)
        shown = NowRules.step(shown, active, now + 3 * NowRules.PAGE_MS + 200, moving = false, page = 1)
        assertEquals(NowKind.WEATHER, shown.kind)
        // Only the calm line: it stays.
        val calm = cards()
        var alone = NowRules.step(null, calm, now, moving = false)
        alone = NowRules.step(alone, calm, now + 60_000L, moving = false, page = 1)
        assertEquals(NowKind.CALM, alone.kind)
    }

    @Test
    fun aLessPressingCardWaitsForTheHold() {
        // Guiding, the turn near: it is shown.
        var shown = NowRules.step(null, cards(NowKind.TURN to NowRules.TURN_NEAR, NowKind.FUEL to NowRules.FUEL_LOW), now, moving = true)
        assertEquals(NowKind.TURN, shown.kind)
        // Two seconds later the turn is taken and the next one is far: fuel is now on top, but the turn holds.
        val after = cards(NowKind.TURN to NowRules.TURN_FAR, NowKind.FUEL to NowRules.FUEL_LOW)
        shown = NowRules.step(shown, after, now + 2_000L, moving = true)
        assertEquals(NowKind.TURN, shown.kind)
        shown = NowRules.step(shown, after, now + NowRules.HOLD_MS - 1, moving = true)
        assertEquals(NowKind.TURN, shown.kind)
        shown = NowRules.step(shown, after, now + NowRules.HOLD_MS, moving = true)
        assertEquals(NowKind.FUEL, shown.kind)
    }

    @Test
    fun aMorePressingCardCutsInAtOnce() {
        val before = cards(NowKind.FUEL to NowRules.FUEL_LOW)
        var shown = NowRules.step(null, before, now, moving = true)
        assertEquals(NowKind.FUEL, shown.kind)
        // A turn coming within a kilometre half a second later: no hold for it.
        val turn = cards(NowKind.TURN to NowRules.TURN_NEAR, NowKind.FUEL to NowRules.FUEL_LOW)
        shown = NowRules.step(shown, turn, now + 500L, moving = true)
        assertEquals(NowKind.TURN, shown.kind)
        // Parked too, in the middle of a page.
        var parked = NowRules.step(null, before, now, moving = false)
        parked = NowRules.step(parked, cards(NowKind.FAULT to NowRules.FAULT_STOP, NowKind.FUEL to NowRules.FUEL_LOW), now + 1_000L, moving = false)
        assertEquals(NowKind.FAULT, parked.kind)
        // A card that was already there doesn't cut in: parked on a lower one by hand, the top waits its turn.
        parked = NowRules.step(parked, cards(NowKind.FAULT to NowRules.FAULT_STOP, NowKind.FUEL to NowRules.FUEL_LOW), now + 2_000L, moving = false, page = 1)
        assertEquals(NowKind.FUEL, parked.kind)
        parked = NowRules.step(parked, cards(NowKind.FAULT to NowRules.FAULT_STOP, NowKind.FUEL to NowRules.FUEL_LOW), now + 3_000L, moving = false)
        assertEquals(NowKind.FUEL, parked.kind)
    }

    @Test
    fun aCardThatGoesIsReplacedAtOnce() {
        var shown = NowRules.step(null, cards(NowKind.PARKING to NowRules.PARKING_ENDING, NowKind.SERVICE to NowRules.SERVICE_DUE), now, moving = true)
        assertEquals(NowKind.PARKING, shown.kind)
        shown = NowRules.step(shown, cards(NowKind.SERVICE to NowRules.SERVICE_DUE), now + 1_000L, moving = true)
        assertEquals(NowKind.SERVICE, shown.kind)
        assertEquals(now + 1_000L, shown.since)
    }

    @Test
    fun tilesAreFoundOnTheBarsDashboards() {
        val dtc = DashboardItem.BuiltinWidget(BuiltinKind.OBD_DTC)
        val clock = DashboardItem.BuiltinWidget(BuiltinKind.CLOCK)
        val pages = listOf(listOf(dtc), listOf(clock), listOf(dtc, clock), emptyList())
        // Page 0 isn't in the bar: the first bar page holding each kind.
        val found = widgetPagesOf(listOf(1, 2, 3), pages)
        assertEquals(2, found[BuiltinKind.OBD_DTC])
        assertEquals(1, found[BuiltinKind.CLOCK])
        assertNull(found[BuiltinKind.SERVICE])
    }

    @Test
    fun theCarsOwnWarningsTakeTheirPlace() {
        // A stop-now warning is as pressing as a stop-now fault, ahead of the turn.
        val stop = NowRules.cards(NowInputs(carWarning = PsaWarnings.Level.STOP, guiding = true, turnMetres = 300), now).map { it.kind }
        assertEquals(NowKind.CAR, stop.first())
        // A bulb or a garage visit waits behind the appointment, beside the servicing.
        val soon = NowRules.cards(NowInputs(carWarning = PsaWarnings.Level.SERVICE, nextEventAt = now + 30 * min), now).map { it.kind }
        assertEquals(listOf(NowKind.AGENDA, NowKind.CAR, NowKind.CALM), soon)
        // What is only good to know gets no card.
        assertEquals(listOf(NowKind.CALM), NowRules.cards(NowInputs(carWarning = PsaWarnings.Level.INFO), now).map { it.kind })
    }
}
