package com.openauto.dash

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Receives the "launcher music" updates of the FYT head units' stock player
 * (com.syu.music): it starts this service with [HeadUnitMedia.ACTION_FYT_MUSIC]
 * and the track in the extras, about once a second while it plays. The track is
 * handed to [HeadUnitMedia.fromFyt], which the media tile of every skin reads
 * through [CarMediaController].
 *
 * The service has to stay exported for the player to reach it, so what it gets
 * is never trusted: only text and numbers are read, and a malformed update is
 * dropped. It stops itself after a while of silence and starts again with the
 * next update. Protocol as worked out in vasyl91/Display-Media-Titles.
 */
class FytMusicService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val idleStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HeadUnitMedia.ACTION_FYT_MUSIC) {
            try {
                HeadUnitMedia.fromFyt(intent?.extras)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Ignoring a malformed player update", e)
            }
        }
        handler.removeCallbacks(idleStop)
        handler.postDelayed(idleStop, IDLE_STOP_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleStop)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FytMusicService"
        const val IDLE_STOP_MS = 30_000L
    }
}
