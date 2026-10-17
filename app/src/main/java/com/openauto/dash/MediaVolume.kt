package com.openauto.dash

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.StringRes
import com.openauto.dash.unit.QfVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** How the volume is changed: found out by itself, always Android's volume, or always the volume keys. */
enum class VolumeWay(@StringRes val titleRes: Int, @StringRes val hintRes: Int) {
    AUTO(R.string.volume_way_auto, R.string.volume_way_auto_hint),
    ANDROID(R.string.volume_way_android, R.string.volume_way_android_hint),
    KEYS(R.string.volume_way_keys, R.string.volume_way_keys_hint)
}

/**
 * The media volume, changed the way this device listens to. Normally through
 * Android's own media volume. Many head units drive their amplifier from the
 * MCU, which only follows the volume keys: there the changes are made by
 * pressing those keys through the privileged shell (root or the internal ADB
 * socket, see [DockShell]), exactly like the knob does. Some keep Android's
 * volume fixed, which the first change finds out.
 *
 * QF firmware (the ROCO K706) is different again: Android's volume moves
 * without any effect on the sound, and injected volume keys are ignored too,
 * since its framework only hears the MCU's own keys. The sound follows the
 * unit's volume, 0..[UNIT_MAX] for the current source, which the framework
 * keeps in a system property and sends to the MCU (android.qf.os.VolumeState).
 * The same two steps are taken here through the privileged shell, so the knob
 * carries on from the new level. The driver can still force Android's volume
 * in Settings ([VolumeWay]).
 */
object MediaVolume {
    private const val TAG = "MediaVolume"
    private const val STREAM = AudioManager.STREAM_MUSIC
    private const val PREFS = "media_volume"
    private const val KEY_WAY = "way"

    /** The QF firmware's volume range, the one the demo alert shows too. */
    const val UNIT_MAX = QfVolume.MAX

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The unit's own volume control, where its firmware has one ([UnitPlatform.adapter]): the QF firmware's. */
    private val unit: VolumeControl? get() = UnitPlatform.adapter.volume

    /** A unit whose sound follows only its own volume (the QF firmware). */
    internal val qfUnit: Boolean get() = unit != null

    private val _way = MutableStateFlow(VolumeWay.AUTO)
    val way: StateFlow<VolumeWay> = _way.asStateFlow()
    private var loaded = false

    private val _onUnit = MutableStateFlow(false)
    /** The head unit's own volume is changed (QF firmware): the level is known, 0..[UNIT_MAX]. */
    val onUnit: StateFlow<Boolean> = _onUnit.asStateFlow()

    private val _byKeys = MutableStateFlow(false)
    /** Android's media volume has no effect here: the volume keys are pressed instead, and the level is unknown. */
    val byKeys: StateFlow<Boolean> = _byKeys.asStateFlow()

    private val _unavailable = MutableStateFlow(false)
    /** Neither way works: no effect from Android's volume and no shell to press the keys with. */
    val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()

    /** The level to come back to when unmuting a volume that was slid down to 0. */
    @Volatile private var lastAudible = 0

    /**
     * QF firmware: its own volume is the only one that works, so there is no
     * choice to offer (Settings hides it) and none is kept. Android's volume,
     * chosen there once, silenced every volume control.
     */
    val choiceOffered: Boolean get() = !qfUnit

    fun setContext(context: Context) {
        if (loaded) return
        loaded = true
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WAY, null)
        val saved = VolumeWay.entries.firstOrNull { it.name == name } ?: VolumeWay.AUTO
        if (qfUnit && saved != VolumeWay.AUTO) saveWay(context, VolumeWay.AUTO) else _way.value = saved
        applyWay()
    }

    fun saveWay(context: Context, way: VolumeWay) {
        _way.value = way
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_WAY, way.name).apply()
        applyWay()
    }

    /** Starts over from the chosen way; in AUTO, a fixed volume is found out again on the next change. */
    private fun applyWay() {
        // Pressed keys never reach the QF volume, so the unit's own volume stands in for them too.
        _onUnit.value = qfUnit && _way.value != VolumeWay.ANDROID
        _byKeys.value = !_onUnit.value && _way.value == VolumeWay.KEYS
        _unavailable.value = false
    }

    fun audio(context: Context): AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun max(audio: AudioManager): Int = if (_onUnit.value) unitMax() else audio.getStreamMaxVolume(STREAM).coerceAtLeast(1)

    private fun unitMax(): Int = unit?.max ?: UNIT_MAX

    /** The level heard: 0 while muted on the unit, as its own volume bar shows it. */
    fun level(audio: AudioManager): Int =
        if (_onUnit.value) unitState().let { if (it.muted) 0 else it.level } else audio.getStreamVolume(STREAM)

    /** Muted, or slid down to nothing. */
    fun isMuted(audio: AudioManager): Boolean =
        if (_onUnit.value) unitState().let { it.muted || it.level == 0 } else audio.isStreamMute(STREAM) || level(audio) == 0

    /** Checked once the audio tile shows: a fixed volume can't be changed through Android at all. */
    fun check(audio: AudioManager) {
        if (_way.value == VolumeWay.AUTO && !_onUnit.value && audio.isVolumeFixed) _byKeys.value = true
    }

    /**
     * Sets the level (the slider). Android's or the unit's volume only: the keys can't reach a given level.
     * [show]: Dashwheel's volume bar comes up for it ([VolumeOverlay]), as for a key the driver pressed;
     * a tile, or the volume following the speed, shows nothing.
     */
    fun set(context: Context, level: Int, show: Boolean = false) {
        VolumeOverlay.ownChange(show)
        if (_onUnit.value) {
            if (level > 0) lastAudible = level
            setUnit(context, UnitState(level.coerceIn(0, unitMax()), muted = level == 0))
            return
        }
        val audio = audio(context)
        if (_byKeys.value) return
        if (level > 0) lastAudible = level
        val before = level(audio)
        runCatching { audio.setStreamVolume(STREAM, level, 0) }
        // Nothing moved at all: the ROM handles the volume itself. (A level merely
        // capped, e.g. by the safe-volume limit, still moved and doesn't count.)
        if (level != before && level(audio) == before && !audio.isStreamMute(STREAM)) useKeys()
    }

    fun raise(context: Context, show: Boolean = false) {
        VolumeOverlay.ownChange(show)
        step(context, AudioManager.ADJUST_RAISE, KeyEvent.KEYCODE_VOLUME_UP)
    }

    fun lower(context: Context, show: Boolean = false) {
        VolumeOverlay.ownChange(show)
        step(context, AudioManager.ADJUST_LOWER, KeyEvent.KEYCODE_VOLUME_DOWN)
    }

    fun toggleMute(context: Context, show: Boolean = false) {
        VolumeOverlay.ownChange(show)
        if (_onUnit.value) {
            val now = unitState()
            if (now.muted || now.level == 0) {
                // Slid down to 0 rather than muted: unmuting alone would leave it silent.
                val back = if (now.level > 0) now.level else lastAudible.takeIf { it > 0 } ?: (unitMax() / 3)
                setUnit(context, UnitState(back, muted = false))
            } else {
                lastAudible = now.level
                setUnit(context, now.copy(muted = true))
            }
            return
        }
        val audio = audio(context)
        if (_byKeys.value) {
            press(context, KeyEvent.KEYCODE_VOLUME_MUTE)
            return
        }
        if (isMuted(audio)) {
            if (audio.isStreamMute(STREAM)) runCatching { audio.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0) }
            // Slid down to 0 rather than muted: unmuting alone would leave it silent.
            if (level(audio) == 0) {
                val back = lastAudible.takeIf { it > 0 } ?: (max(audio) / 3).coerceAtLeast(1)
                runCatching { audio.setStreamVolume(STREAM, back, 0) }
            }
            if (isMuted(audio)) useKeys(context, KeyEvent.KEYCODE_VOLUME_MUTE)
        } else {
            lastAudible = level(audio)
            runCatching { audio.adjustStreamVolume(STREAM, AudioManager.ADJUST_MUTE, 0) }
            if (!isMuted(audio)) useKeys(context, KeyEvent.KEYCODE_VOLUME_MUTE)
        }
    }

    private fun step(context: Context, direction: Int, keyCode: Int) {
        if (_onUnit.value) {
            // Like the knob: one notch from the saved level, and any change unmutes.
            val now = unitState()
            val next = (now.level + if (direction == AudioManager.ADJUST_RAISE) 1 else -1).coerceIn(0, unitMax())
            if (next != now.level || now.muted) setUnit(context, UnitState(next, muted = next == 0))
            return
        }
        val audio = audio(context)
        if (_byKeys.value) {
            press(context, keyCode)
            return
        }
        val before = level(audio)
        val wasMuted = audio.isStreamMute(STREAM)
        // Already at the end of the range: nothing to change, nothing learnt.
        val atEnd = !wasMuted && if (direction == AudioManager.ADJUST_RAISE) before >= max(audio) else before <= 0
        runCatching { audio.adjustStreamVolume(STREAM, direction, 0) }
        val moved = level(audio) != before || audio.isStreamMute(STREAM) != wasMuted
        if (!moved && !atEnd) useKeys(context, keyCode)
    }

    /** From now on the keys are pressed; [keyCode] is the press the system just ignored. */
    private fun useKeys(context: Context? = null, keyCode: Int? = null) {
        if (_way.value == VolumeWay.ANDROID) return
        if (!_byKeys.value) Log.i(TAG, "Android's media volume has no effect here: using the volume keys")
        _byKeys.value = true
        if (context != null && keyCode != null) press(context, keyCode)
    }

    private fun press(context: Context, keyCode: Int) {
        val app = context.applicationContext
        scope.launch {
            runCatching { DockShell.shell(app, "input keyevent $keyCode") }
                .onSuccess { _unavailable.value = false }
                .onFailure {
                    Log.w(TAG, "volume key $keyCode not pressed: ${it.message}")
                    _unavailable.value = true
                }
        }
    }

    // --- The unit's own volume (QfVolume on the QF firmware) ---------------------------------

    private data class UnitState(val level: Int, val muted: Boolean)

    /** A change asked for and not yet made: what the tile shows meanwhile. Only the latest one is made. */
    private val unitWanted = AtomicReference<UnitState?>(null)
    private val unitWrites = Channel<Unit>(Channel.CONFLATED)
    private var unitWriter = false

    private fun unitState(): UnitState = unitWanted.get() ?: unit?.read()?.let { UnitState(it.level, it.muted) } ?: UnitState(0, false)

    /** The unit's volume as the firmware has it now (or as last asked), for [VolumeOverlay]. */
    internal fun unitNow(): VolumeShown {
        val state = unitState()
        return VolumeShown(state.level, unitMax(), state.muted, VolumeSource.of(unitSource()))
    }

    /** The sound source the unit's volume applies to now ("radio_type", "aux_type"...); "" on a unit with one volume for all. */
    internal fun unitSource(): String = unit?.source().orEmpty()
    internal const val UNIT_CALL_SOURCE = "btcall_type"

    private fun setUnit(context: Context, state: UnitState) {
        unitWanted.set(state)
        startUnitWriter(context.applicationContext)
        unitWrites.trySend(Unit)
    }

    @Synchronized
    private fun startUnitWriter(app: Context) {
        if (unitWriter) return
        unitWriter = true
        scope.launch {
            for (tick in unitWrites) {
                val want = unitWanted.get() ?: continue
                runCatching { writeUnit(app, want) }
                    .onSuccess { _unavailable.value = false }
                    .onFailure {
                        Log.w(TAG, "unit volume not set: ${it.message}")
                        _unavailable.value = true
                    }
                // A newer change keeps showing until it's made in turn.
                unitWanted.compareAndSet(want, null)
            }
        }
    }

    /** The firmware's own way of setting its volume ([VolumeControl.write]). */
    private suspend fun writeUnit(app: Context, want: UnitState) {
        val control = unit ?: throw IllegalStateException("no unit volume")
        control.write(app, want.level, want.muted)
        UnitPlatform.markUsed(Cap.VOLUME_UNIT)
    }
}
