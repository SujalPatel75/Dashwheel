package com.openauto.dash

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.openauto.dash.link.NowPlaying
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

/*
 * The head unit's own players on the QF firmware (ROCO K706), which publish
 * a media session with nothing in it: Bluetooth music (com.qf.bluetooth) and
 * the FM/AM radio (com.android.fmradio.ext). What they play comes from their
 * own broadcasts instead, and their buttons from their own control actions
 * (worked out from the firmware). [CarMediaController] shows it in the media
 * tile whenever one of them is the source.
 *
 * The radio only talks while its screen exists: after it was closed, next /
 * previous / play open it again.
 *
 * FYT head units (UIS7862 / 8581) have a stock player (com.syu.music) with no
 * media session at all. It starts a service with the "launcher music" action
 * and the track in its extras about once a second ([FytMusicService] receives
 * them, [fromFyt] keeps them), and takes com.syu.music.next / .prev / .playpause
 * as service intents. Worked out from vasyl91/Display-Media-Titles.
 */
object HeadUnitMedia {
    private const val TAG = "HeadUnitMedia"
    const val BT_PACKAGE = "com.qf.bluetooth"
    const val RADIO_PACKAGE = "com.android.fmradio.ext"
    private const val RADIO_ACTIVITY = "com.android.fmradio.FmMainActivity"

    private const val ACTION_BT_INFO = "com.qf.action.BT.MUSIC.INFO"
    private const val ACTION_BT_CONTROL = "com.qf.action.BT.MUSIC.CONTROL"
    private const val BT_PREVIOUS = 1
    private const val BT_NEXT = 2
    private const val BT_TOGGLE = 3

    /** FYT's stock player and the actions it speaks (see the header). */
    const val FYT_PACKAGE = "com.syu.music"
    const val ACTION_FYT_MUSIC = "com.fyt.launcher.music"
    private const val FYT_NEXT = "com.syu.music.next"
    private const val FYT_PREVIOUS = "com.syu.music.prev"
    private const val FYT_PLAY_PAUSE = "com.syu.music.playpause"

    /** The player reports about every second while it plays; silence this long means it is gone. */
    private const val FYT_STALE_MS = 6_000L

    private const val ACTION_RADIO = "com.qf.radio.update_action"
    private const val RADIO_NEXT = "/customize/radio/next"
    private const val RADIO_PREVIOUS = "/customize/radio/pre"
    private const val RADIO_CLOSE = "/customize/radio/close"

    data class BtTrack(val title: String, val artist: String, val album: String)

    /** [freq] in 10 kHz on FM (8750 = 87.50 MHz), kHz on AM; [band] 0..2 = FM1..3, 3..5 = AM1..3. */
    data class RadioStation(val freq: Int, val band: Int, val name: String?)

    /** What the FYT stock player last said; [art] is the cover embedded in the file, once read. */
    data class FytTrack(
        val title: String,
        val artist: String,
        val album: String,
        val path: String,
        val playing: Boolean,
        val durationMs: Long,
        val positionMs: Long,
        val receivedAt: Long,
        val art: Bitmap? = null
    )

    @Volatile var fyt: FytTrack? = null
        private set
    @Volatile var bt: BtTrack? = null
        private set
    @Volatile var radio: RadioStation? = null
        private set

    // The station's RDS as the MCU sends it ([McuFeed]), whether or not the radio's screen is open.
    @Volatile private var rdsName: String? = null
    @Volatile private var rdsText: String? = null

    /** The tuner's band and frequency from the MCU: a new station forgets the last one's RDS. */
    internal fun mcuTuner(freq: Int, band: Int) {
        if (freq <= 0) return
        val was = radio
        if (was != null && was.freq == freq && was.band == band) return
        rdsName = null
        rdsText = null
        radio = RadioStation(freq, band, was?.takeIf { it.freq == freq }?.name)
        main.post { tell() }
    }

    /** RDS from the MCU: the station's name, its radio text, or [clear] when the station has none. */
    internal fun mcuRds(name: String?, text: String?, clear: Boolean = false) {
        when {
            clear -> if (rdsName == null && rdsText == null) return else { rdsName = null; rdsText = null }
            name != null -> if (name == rdsName) return else rdsName = name
            text != null -> if (text == rdsText) return else rdsText = text
            else -> return
        }
        main.post { tell() }
    }

    /** What the linked phone says it plays ([NowPlaying]): the cover the unit's Bluetooth doesn't pass on. */
    data class PhoneTrack(val title: String, val artist: String, val album: String, val playing: Boolean, val durationMs: Long, val artKey: Int, val art: Bitmap?)

    @Volatile var phone: PhoneTrack? = null
        private set
    private val main = Handler(Looper.getMainLooper())

    /** The phone's latest [NowPlaying]; a cover sent earlier is kept while its key stays the same. */
    fun fromPhone(message: NowPlaying) {
        val before = phone
        val art = when {
            message.artJpeg != null -> runCatching {
                val bytes = android.util.Base64.decode(message.artJpeg, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            message.artKey != 0 && message.artKey == before?.artKey -> before.art
            else -> null
        }
        phone = message.title.takeIf { it.isNotBlank() }?.let {
            PhoneTrack(it, message.artist, message.album, message.playing, message.durationMs, message.artKey, art)
        }
        main.post { tell() }
    }

    /** The phone link is gone: so is what it said. */
    fun phoneGone() {
        if (phone == null) return
        phone = null
        main.post { tell() }
    }

    private fun tell() = listeners.forEach { runCatching { it() }.onFailure { e -> Log.w(TAG, "listener failed", e) } }

    private var started = false
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        if (!UnitPlatform.hasPackage(app, BT_PACKAGE) && !UnitPlatform.hasPackage(app, RADIO_PACKAGE)) return
        started = true
        val filter = IntentFilter().apply {
            addAction(ACTION_BT_INFO)
            addAction(ACTION_RADIO)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** Told (main thread) whenever a track or station changes. */
    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    fun isStock(pkg: String?): Boolean = pkg == BT_PACKAGE || pkg == FYT_PACKAGE || pkg?.startsWith("com.android.fmradio") == true

    /**
     * The FYT player's track is what the unit plays: it is playing (and still
     * reporting), or it is paused and no other player is ([otherPlaying]).
     */
    fun fytLive(otherPlaying: Boolean): Boolean {
        val t = fyt ?: return false
        return if (t.playing) SystemClock.elapsedRealtime() - t.receivedAt < FYT_STALE_MS else !otherPlaying
    }

    /**
     * One update from the FYT player ([FytMusicService], main thread). Title,
     * artist and album come as strings, the length and position as numbers (ms).
     * An "Unknown" title is the player starting up or closing: a paused track is
     * forgotten then, a playing one is kept. The tiles are told only when what
     * they show changes, not at every one-second tick.
     */
    internal fun fromFyt(extras: Bundle?) {
        extras ?: return
        val playing = extras.getBoolean("play_state", false)
        val title = extras.getString("title").orEmpty().trim()
        if (title.isEmpty() || title.contains("Unknown", ignoreCase = true)) {
            if (!playing && fyt != null) {
                fyt = null
                tell()
            }
            return
        }
        val path = extras.getString("play_path").orEmpty()
        val previous = fyt
        val sameFile = previous != null && path.isNotEmpty() && previous.path == path
        val track = FytTrack(
            title = title,
            artist = extras.getString("play_artist").orEmpty().trim(),
            album = extras.getString("play_album").orEmpty().trim(),
            path = path,
            playing = playing,
            durationMs = extras.number("play_total"),
            positionMs = extras.number("play_cur"),
            receivedAt = SystemClock.elapsedRealtime(),
            art = if (sameFile) previous?.art else null
        )
        fyt = track
        if (!sameFile && path.isNotEmpty()) loadFytArt(path)
        if (previous == null || previous.title != track.title || previous.artist != track.artist ||
            previous.album != track.album || previous.playing != track.playing || previous.durationMs != track.durationMs ||
            !sameFile
        ) tell()
    }

    @Suppress("DEPRECATION")
    private fun Bundle.number(key: String): Long = (get(key) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L

    private val artReader by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    /** The cover embedded in [path] (ID3 / FLAC / MP4 tag), read off the main thread; nothing when the file can't be read. */
    private fun loadFytArt(path: String) {
        artReader.execute {
            val cover = runCatching {
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(path)
                    reader.embeddedPicture?.let { decodeCover(it) }
                } finally {
                    runCatching { reader.release() }
                }
            }.getOrNull() ?: return@execute
            Handler(Looper.getMainLooper()).post {
                val now = fyt
                if (now != null && now.path == path) {
                    fyt = now.copy(art = cover)
                    tell()
                }
            }
        }
    }

    /** [bytes] as a bitmap no bigger than about 600 px a side. */
    private fun decodeCover(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The head unit's current audio source (a package name), as its framework records it. */
    fun source(): String? = systemProperty("persist.sys.qf.last_audio_src")?.takeIf { it.isNotBlank() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_BT_INFO -> bt = BtTrack(
                    intent.getStringExtra("songName").orEmpty().trim(),
                    intent.getStringExtra("songSinger").orEmpty().trim(),
                    intent.getStringExtra("songAlbum").orEmpty().trim()
                )
                ACTION_RADIO -> {
                    val freq = intent.getIntExtra("${ACTION_RADIO}_freq_key", -1)
                    if (freq <= 0) return
                    if (radio?.freq != freq) { rdsName = null; rdsText = null }
                    radio = RadioStation(
                        freq = freq,
                        band = intent.getIntExtra("${ACTION_RADIO}_band_key", 0),
                        name = intent.getStringExtra("${ACTION_RADIO}_name_key")?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
                else -> return
            }
            tell()
        }
    }

    /** The unit's own track, unless its Bluetooth only said it had none ("Not Provided"). */
    private fun btTrack(): BtTrack? = bt?.takeIf { it.title.isNotEmpty() && !it.title.equals(NOT_PROVIDED, ignoreCase = true) }

    private const val NOT_PROVIDED = "Not Provided"

    /** What [pkg] (one of the head unit's players) is playing, for the media tile; null when it said nothing yet. */
    fun state(pkg: String): MediaState? = when {
        // Bluetooth music is the phone's: its title where the unit has none, and its cover always.
        pkg == BT_PACKAGE -> {
            val t = btTrack()
            val p = phone
            if (t == null && p == null) null else MediaState(
                title = t?.title ?: p!!.title,
                artist = (if (t != null) listOf(t.artist, t.album) else listOf(p!!.artist, p.album)).filter { it.isNotEmpty() }.joinToString(" · "),
                isPlaying = systemProperty("sys.qf.bt.music.state") == "1" || p?.playing == true,
                hasMedia = true,
                durationMs = btProgress()?.second?.toLong()?.takeIf { it > 0 } ?: p?.durationMs ?: 0L,
                artwork = p?.art
            )
        }
        pkg == FYT_PACKAGE -> fyt?.let { t ->
            MediaState(
                title = t.title,
                artist = listOf(t.artist, t.album).filter { it.isNotEmpty() }.joinToString(" · "),
                isPlaying = t.playing,
                hasMedia = true,
                durationMs = t.durationMs,
                artwork = t.art
            )
        }
        isStock(pkg) -> radio?.let { r ->
            val frequency = radioLabel(r)
            val name = r.name ?: rdsName
            MediaState(
                title = name ?: frequency,
                artist = rdsText ?: listOfNotNull(name?.let { frequency }, bandLabel(r.band)).joinToString(" · "),
                isPlaying = systemProperty("sys.qf.radio.status") == "true",
                hasMedia = true
            )
        }
        else -> null
    }

    /** Bluetooth music's or the FYT player's position in ms, when it reports one (the FYT one runs on between its updates). */
    fun positionMs(pkg: String): Long? = when (pkg) {
        BT_PACKAGE -> btProgress()?.first?.toLong()
        FYT_PACKAGE -> fyt?.let { t ->
            if (!t.playing) t.positionMs
            else (t.positionMs + (SystemClock.elapsedRealtime() - t.receivedAt)).let { if (t.durationMs > 0L) it.coerceAtMost(t.durationMs) else it }
        }
        else -> null
    }

    fun playPause(context: Context, pkg: String) {
        if (pkg == FYT_PACKAGE) {
            fytCommand(context, FYT_PLAY_PAUSE)
            // The button turns at once; the player's next update confirms it.
            fyt?.let { fyt = it.copy(playing = !it.playing, receivedAt = SystemClock.elapsedRealtime()) }
            return tell()
        }
        if (pkg == BT_PACKAGE) return btCommand(context, BT_TOGGLE)
        // The radio has no pause: closing its screen stops it, opening it plays.
        if (radioAlive()) context.sendBroadcast(Intent(RADIO_CLOSE)) else openRadio(context)
    }

    fun next(context: Context, pkg: String) {
        if (pkg == FYT_PACKAGE) return fytCommand(context, FYT_NEXT)
        if (pkg == BT_PACKAGE) return btCommand(context, BT_NEXT)
        if (radioAlive()) context.sendBroadcast(Intent(RADIO_NEXT)) else openRadio(context)
    }

    fun previous(context: Context, pkg: String) {
        if (pkg == FYT_PACKAGE) return fytCommand(context, FYT_PREVIOUS)
        if (pkg == BT_PACKAGE) return btCommand(context, BT_PREVIOUS)
        if (radioAlive()) context.sendBroadcast(Intent(RADIO_PREVIOUS)) else openRadio(context)
    }

    /** The FYT player takes its buttons as service intents addressed to it. */
    private fun fytCommand(context: Context, action: String) {
        runCatching { context.startService(Intent(action).setPackage(FYT_PACKAGE)) }
            .onFailure { Log.w(TAG, "Cannot send $action to $FYT_PACKAGE", it) }
    }

    private fun btCommand(context: Context, command: Int) {
        context.sendBroadcast(Intent(ACTION_BT_CONTROL).putExtra("command", command))
    }

    private fun radioAlive(): Boolean = systemProperty("sys.qf.radio.status") == "true"

    private fun openRadio(context: Context) {
        context.launchSafely(Intent().setComponent(ComponentName(RADIO_PACKAGE, RADIO_ACTIVITY)))
    }

    /** "position,duration" as the Bluetooth app records it. */
    private fun btProgress(): Pair<Int, Int>? = systemProperty("sys.qf.bt.music.progress")
        ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.size == 2 && it[1] > 0 }
        ?.let { it[0] to it[1] }

    private fun systemProperty(name: String): String? = SysProps.get(name)
}

/** "98.5 MHz" / "1008 kHz". */
internal fun radioLabel(r: HeadUnitMedia.RadioStation): String =
    if (r.band in 0..2) "${(r.freq / 100.0).toBigDecimal().stripTrailingZeros().toPlainString()} MHz" else "${r.freq} kHz"

/** "FM1".."FM3", "AM1".."AM3". */
internal fun bandLabel(band: Int): String? = when (band) {
    in 0..2 -> "FM${band + 1}"
    in 3..5 -> "AM${band - 2}"
    else -> null
}
