package com.openauto.dash

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * OpenStreetMap's Overpass servers, for the speed limits, the speed cameras
 * and the fuel stations' names. The main server is shared by everyone and
 * goes down for hours at a time; the day it did, both speed tiles went blank
 * at once and a tester took it for the update. So a query goes to the first
 * server that answers ([SERVERS], the main one first), a server that failed
 * is left alone for a while ([SKIP_MS]), and whether the last query found
 * any server at all is said ([down]) so a tile can say so too.
 */
internal object Overpass {
    private const val TAG = "Overpass"

    /** The main server, then public mirrors of the same data. */
    internal val SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    /** A server that failed is not asked again for this long, while another answers. */
    internal const val SKIP_MS = 5 * 60_000L

    private val failedAt = LongArray(SERVERS.size)
    @Volatile private var preferred = 0

    private val _down = MutableStateFlow(false)
    /** The last query found no server answering: the tiles' blank is the server's, not the road's. */
    val down: StateFlow<Boolean> = _down

    /**
     * Runs [query] (Overpass QL) on the first server that answers and gives
     * back its JSON; throws when none does. Blocking: from an IO thread.
     */
    fun query(query: String): JSONObject {
        var last: Throwable? = null
        for (i in order(preferred, failedAt, SystemClock.elapsedRealtime())) {
            val result = runCatching { ask(SERVERS[i], query) }
            result.onSuccess {
                failedAt[i] = 0L
                if (preferred != i) Log.i(TAG, "answered by ${SERVERS[i]}")
                preferred = i
                _down.value = false
                return it
            }
            last = result.exceptionOrNull()
            failedAt[i] = SystemClock.elapsedRealtime()
            Log.w(TAG, "${SERVERS[i]}: ${last?.message}")
        }
        _down.value = true
        throw IllegalStateException("no Overpass server answered: ${last?.message}", last)
    }

    private fun ask(server: String, query: String): JSONObject {
        val request = Request.Builder().url(server)
            .header("User-Agent", OVERPASS_AGENT)
            .post(FormBody.Builder().add("data", query).build()).build()
        Http.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    /**
     * The servers to try, in order: from [preferred] round the list, the ones
     * that have not failed within [SKIP_MS] of [now] first; when every one
     * has, all of them again, since nothing else is left to try.
     */
    internal fun order(preferred: Int, failedAt: LongArray, now: Long): List<Int> {
        val round = SERVERS.indices.map { (preferred + it) % SERVERS.size }
        val fresh = round.filter { failedAt[it] == 0L || now - failedAt[it] >= SKIP_MS }
        return fresh.ifEmpty { round }
    }
}

/** How Dashwheel names itself to OpenStreetMap's servers, as their usage policy asks. */
internal const val OVERPASS_AGENT = "Dashwheel/1.0 (https://github.com/deviloufr-ai/Dashwheel)"
