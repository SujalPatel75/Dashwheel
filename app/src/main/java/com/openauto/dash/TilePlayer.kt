package com.openauto.dash

import android.app.ActivityOptions
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log

/*
 * A music app shown in a tile (inside it, EmbeddedApp, or as a window kept
 * over it, PipAnchor) comes to its player when a new song starts: the screen
 * its media session names as its own (YouTube Music's player, Spotify's Now
 * playing). YouTube Music in Video mode then shows the song's clip in the
 * tile. Not while the driver uses the tile: a song that starts within
 * [HANDS_OFF_MS] of a touch in it leaves the app where it is (a library
 * being browsed, a search). Nor on a unit that opens every screen asked
 * for on a tile's display on the main screen first
 * ([EmbeddedApp.opensOnMainScreen]): there the player flashed over the
 * dashboard at every song, which is worse than no player.
 */
internal object TilePlayer {
    private const val TAG = "TilePlayer"

    /** How long after a touch in a tile its app is left alone. */
    const val HANDS_OFF_MS = 30_000L

    /** The last song seen playing: package, title, artist. */
    private var lastSong: List<String?>? = null

    /** Every update of the playing session ([CarMediaController]); acts only on a new song playing. */
    fun noted(controller: MediaController) {
        val pkg = controller.packageName ?: return
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val metadata = controller.metadata
        val song = listOf(pkg, metadata?.getString(MediaMetadata.METADATA_KEY_TITLE), metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
        // A window tile only while the dashboard is in front: over another app, the player would cover it.
        val docked = pkg in PipAnchor.dockedPackages.value && EmbeddedApp.dashboardActivity() != null
        val open = opens(lastSong, song, playing, EmbeddedApp.holds(pkg) || docked, EmbeddedApp.touchedWithin(pkg, HANDS_OFF_MS))
        if (playing) lastSong = song
        if (!open) return
        if (!worthOpening(EmbeddedApp.holds(pkg), EmbeddedApp.opensOnMainScreen)) {
            Log.i(TAG, "$pkg: player left where it is, this unit would open it on the main screen first")
            return
        }
        val screen = controller.sessionActivity ?: return
        val sent = if (EmbeddedApp.holds(pkg)) EmbeddedApp.openInside(pkg, screen) else runCatching {
            // A window tile: the screen opens in the app's own window, where it already is.
            val options = ActivityOptions.makeBasic()
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            screen.send(null, 0, null, null, null, null, options.toBundle())
        }.isSuccess
        Log.i(TAG, "$pkg: player ${if (sent) "opened" else "refused"} in its tile")
    }

    /**
     * Whether the player opens: a [song] playing that isn't the [last] one,
     * by an app in a tile, which the driver hasn't [touched] lately. Pure,
     * so it is unit-tested.
     */
    fun opens(last: List<String?>?, song: List<String?>, playing: Boolean, inTile: Boolean, touched: Boolean): Boolean =
        playing && inTile && !touched && song != last && song.drop(1).any { !it.isNullOrBlank() }

    /**
     * Whether opening the player is worth it: not for an app [inside] a tile
     * on a unit whose screens [openOnMainScreen] first, where it would only
     * flash over the dashboard. A window tile's player opens in its own window.
     */
    fun worthOpening(inside: Boolean, openOnMainScreen: Boolean): Boolean = !(inside && openOnMainScreen)
}
