package com.openauto.dash

import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Which country a position is in, as a lower-case ISO code ("fr", "de"),
 * from OpenStreetMap's Nominatim (free, no key, one request a second at
 * most). The answer is kept per cell of a tenth of a degree, about 10 km,
 * so a drive asks a few times an hour, not at every fix. Null when the
 * lookup fails: the caller falls back to what it did before.
 */
internal object CountryLookup {
    private const val TAG = "CountryLookup"
    private const val REVERSE = "https://nominatim.openstreetmap.org/reverse"
    private const val CELLS_KEPT = 64

    private val cache = object : LinkedHashMap<Pair<Int, Int>, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, String>?) = size > CELLS_KEPT
    }

    /** The country at ([lat], [lng]); blocking, from an IO thread. */
    fun code(lat: Double, lng: Double): String? {
        val key = cell(lat, lng)
        synchronized(cache) { cache[key]?.let { return it } }
        val url = String.format(Locale.US, "%s?format=json&zoom=3&lat=%.4f&lon=%.4f", REVERSE, lat, lng)
        val code = runCatching {
            val request = Request.Builder().url(url).header("User-Agent", OVERPASS_AGENT).build()
            Http.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                JSONObject(r.body?.string() ?: "{}").optJSONObject("address")?.optString("country_code").orEmpty().lowercase(Locale.ROOT)
            }
        }.onFailure { Log.w(TAG, "country unknown at %.3f,%.3f: ${it.message}".format(Locale.US, lat, lng)) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        synchronized(cache) { cache[key] = code }
        return code
    }

    /** The cell a position falls in: a tenth of a degree each way. */
    internal fun cell(lat: Double, lng: Double): Pair<Int, Int> = (lat * 10).toInt() to (lng * 10).toInt()
}
