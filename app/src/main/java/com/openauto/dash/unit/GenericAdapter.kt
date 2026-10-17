package com.openauto.dash.unit

import com.openauto.dash.Cap
import com.openauto.dash.CapClaim
import com.openauto.dash.CameraControl
import com.openauto.dash.IgnitionSource
import com.openauto.dash.KeySource
import com.openauto.dash.LauncherTakeover
import com.openauto.dash.MediaBridge
import com.openauto.dash.Need
import com.openauto.dash.PhoneBridge
import com.openauto.dash.PopupTakeover
import com.openauto.dash.SignalCatalogue
import com.openauto.dash.SleepSurvival
import com.openauto.dash.UnitAdapter
import com.openauto.dash.UnitCaps
import com.openauto.dash.VehicleBusSource
import com.openauto.dash.VolumeControl
import com.openauto.unitproto.UnitFamily

/**
 * Plain Android, with whatever small hooks a family has: a unit whose
 * firmware Dashwheel knows little about (Topway, Allwinner, Reglink, NWD),
 * or none at all. The ignition and the wheel keys come from the vendor
 * broadcasts seen in the wild ([PlainAdapters]); a family may add how to
 * survive its sleep or point its launcher choice at Dashwheel. Everything
 * else works on what Android itself gives (GPS speed, OBD, Android's volume).
 *
 * The emulator counts the status bar switch as present ([Cap.FREEFORM_BAR]),
 * so that setting and its shell calls can be tried there without root.
 */
internal class GenericAdapter(
    override val family: UnitFamily,
    emulator: Boolean = false,
    override val ignition: IgnitionSource? = null,
    override val keys: KeySource? = null,
    override val survival: SleepSurvival? = null,
    override val launcher: LauncherTakeover? = null,
    override val catalogue: SignalCatalogue = SignalCatalogue.NONE,
    override val vendorPackages: Set<String> = emptySet()
) : UnitAdapter {
    override val caps: UnitCaps = UnitCaps(
        buildList {
            if (emulator) add(CapClaim(Cap.FREEFORM_BAR))
            if (ignition != null) add(CapClaim(Cap.IGNITION))
            if (keys != null) add(CapClaim(Cap.UNIT_KEYS))
            if (survival != null) add(CapClaim(Cap.SLEEP_SURVIVAL, survival.steps().minOfOrNull { it.need } ?: Need.NONE))
            if (launcher != null) add(CapClaim(Cap.LAUNCHER_TAKEOVER, launcher.need))
        }
    )
    override val bus: VehicleBusSource? = null
    override val phone: PhoneBridge? = null
    override val media: MediaBridge? = null
    override val volume: VolumeControl? = null
    override val popups: PopupTakeover? = null
    override val camera: CameraControl? = null

    companion object {
        /** Before [com.openauto.dash.UnitPlatform.init] has run: claims nothing at all. */
        val EMPTY = GenericAdapter(UnitFamily.UNKNOWN)
    }
}
