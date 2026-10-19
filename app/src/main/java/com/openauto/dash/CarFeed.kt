package com.openauto.dash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a vehicle bus other than the QF car app's pushes what it decodes
 * ([VehicleBusSource]): one sink, forwarding to the flows the dashboard
 * already reads (doors in [McuReader], reverse and steering in [CarBox],
 * headlights and volts in [UnitSignals]), plus speed and RPM of its own for
 * units whose bus says them. On the QF firmware nothing writes here: its car
 * app and MCU feed the same flows directly, as they always did.
 */
object CarFeed : CarSink {
    private val _speedKmh = MutableStateFlow<Float?>(null)
    /** The car's speed from the unit's bus, km/h, or null when no bus says it. */
    val speedKmh: StateFlow<Float?> = _speedKmh

    private val _rpm = MutableStateFlow<Int?>(null)
    val rpm: StateFlow<Int?> = _rpm

    @Volatile private var doorsSeen = false

    // Guarded by itself: the bus calls [raw] from its own thread.
    private val rawLatest = java.util.TreeMap<String, McuReader.Entry>()
    private val _rawEntries = MutableStateFlow<List<McuReader.Entry>>(emptyList())
    /** The bus's raw values for the CAN monitor, as [McuReader.entries] rows: the key, the value as text, when it last changed. */
    val rawEntries: StateFlow<List<McuReader.Entry>> = _rawEntries

    private var sniffers = 0

    /**
     * Someone looks at every value ([on]) or stops (the Signal Finder, the CAN
     * monitor): a bus that has to ask for each value (FYT) asks for them all
     * while at least one looks. Each true wants its false.
     */
    @Synchronized
    fun sniff(on: Boolean) {
        if (on) {
            if (sniffers++ == 0) UnitPlatform.adapter.bus?.sniff(true)
        } else if (sniffers > 0 && --sniffers == 0) {
            UnitPlatform.adapter.bus?.sniff(false)
        }
    }

    /** Whether a bus here has fed what the alert of [kind] is built on: a unit without the QF car app can still show it. */
    fun provides(kind: RomPopups.Kind): Boolean = when (kind) {
        RomPopups.Kind.DOORS, RomPopups.Kind.BELT -> doorsSeen
        else -> false
    }

    override fun doors(state: McuReader.DoorState) {
        doorsSeen = true
        UnitPlatform.markUsed(Cap.DOORS)
        McuReader.busDoorsWrite(state)
    }

    override fun reverse(on: Boolean) {
        UnitPlatform.markUsed(Cap.REVERSE)
        CarBox.reverseWrite(on)
    }

    override fun headlights(on: Boolean) {
        UnitPlatform.markUsed(Cap.HEADLIGHTS)
        UnitSignals.headlightsWrite(on)
    }

    override fun speedKmh(v: Float?) {
        if (v != null) UnitPlatform.markUsed(Cap.SPEED_RPM)
        _speedKmh.value = v
    }

    override fun rpm(v: Int?) {
        if (v != null) UnitPlatform.markUsed(Cap.SPEED_RPM)
        _rpm.value = v
    }

    override fun volts(v: Double) {
        UnitPlatform.markUsed(Cap.VOLTS)
        UnitSignals.mcuVolts(v)
    }

    override fun outsideTempC(c: Double?) {
        if (c != null) UnitPlatform.markUsed(Cap.OUTSIDE_TEMP)
        UnitSignals.outsideTempWrite(c)
    }

    override fun steeringDeg(d: Float) {
        UnitPlatform.markUsed(Cap.STEERING)
        CarBox.steeringWrite(d)
    }

    override fun raw(key: String, value: String) {
        SignalSources.busValue(key, value)
        synchronized(rawLatest) {
            if (rawLatest[key]?.hex == value) return
            rawLatest[key] = McuReader.Entry(key, -1, emptyList(), value, System.currentTimeMillis())
            _rawEntries.value = rawLatest.values.toList()
        }
    }
}
