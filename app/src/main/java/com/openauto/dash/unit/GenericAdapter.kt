package com.openauto.dash.unit

import com.openauto.dash.Cap
import com.openauto.dash.CapClaim
import com.openauto.dash.CameraControl
import com.openauto.dash.IgnitionSource
import com.openauto.dash.KeySource
import com.openauto.dash.LauncherTakeover
import com.openauto.dash.MediaBridge
import com.openauto.dash.PhoneBridge
import com.openauto.dash.PopupTakeover
import com.openauto.dash.SignalCatalogue
import com.openauto.dash.SleepSurvival
import com.openauto.dash.UnitAdapter
import com.openauto.dash.UnitCaps
import com.openauto.dash.VehicleBusSource
import com.openauto.dash.VolumeControl
import com.openauto.dash.isEmulator
import com.openauto.unitproto.UnitFamily

/**
 * Plain Android: a unit whose firmware Dashwheel knows nothing about, or a
 * family whose adapter isn't written yet. Nothing is claimed, so every
 * firmware feature stays out of the settings and the dashboard works on what
 * Android itself gives (GPS speed, OBD, Android's volume and keys).
 *
 * The emulator counts the status bar switch as present ([Cap.FREEFORM_BAR]),
 * so that setting and its shell calls can be tried there without root.
 */
internal class GenericAdapter(override val family: UnitFamily, emulator: Boolean = false) : UnitAdapter {
    override val caps: UnitCaps = UnitCaps(listOfNotNull(if (emulator) CapClaim(Cap.FREEFORM_BAR) else null))
    override val ignition: IgnitionSource? = null
    override val bus: VehicleBusSource? = null
    override val keys: KeySource? = null
    override val phone: PhoneBridge? = null
    override val media: MediaBridge? = null
    override val volume: VolumeControl? = null
    override val launcher: LauncherTakeover? = null
    override val popups: PopupTakeover? = null
    override val survival: SleepSurvival? = null
    override val camera: CameraControl? = null
    override val catalogue: SignalCatalogue = SignalCatalogue.NONE
    override val vendorPackages: Set<String> = emptySet()

    companion object {
        /** Before [com.openauto.dash.UnitPlatform.init] has run: claims nothing at all. */
        val EMPTY = GenericAdapter(UnitFamily.UNKNOWN)

        /** The adapter for a [family] without one of its own, on this device. */
        operator fun invoke(family: UnitFamily): GenericAdapter = GenericAdapter(family, runCatching { isEmulator }.getOrDefault(false))
    }
}
