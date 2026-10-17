package com.openauto.fakeunit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * The scripted car: assets/script.json is a list of updates, each played
 * [atMs] after the service came up, as the firmware would push them:
 *
 *   [{"atMs": 0, "module": 7, "code": 1000, "ints": [2687274]},
 *    {"atMs": 3000, "module": 7, "code": 1031, "ints": [57]}, ...]
 *
 * A step with "repeatMs" plays again that often; "ints" may be a list of
 * lists, played in turn (a speed sweep).
 */
object Script {
    private const val TAG = "FakeScript"
    private val main = Handler(Looper.getMainLooper())
    private var played = false

    fun play(context: Context) {
        if (played) return
        played = true
        val text = runCatching { context.assets.open("script.json").bufferedReader().use { it.readText() } }
            .getOrElse { Log.w(TAG, "no script.json", it); return }
        val steps = JSONArray(text)
        for (i in 0 until steps.length()) schedule(steps.getJSONObject(i))
        Log.i(TAG, "${steps.length()} scripted steps")
    }

    private fun schedule(step: JSONObject) {
        val module = step.getInt("module")
        val code = step.getInt("code")
        val at = step.optLong("atMs", 0L)
        val repeat = step.optLong("repeatMs", 0L)
        val series = intSeries(step.opt("ints"))
        val flts = step.optJSONArray("floats")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
        val strs = step.optJSONArray("strs")?.let { a -> Array<String?>(a.length()) { a.optString(it, null) } }
        var turn = 0
        lateinit var run: Runnable
        run = Runnable {
            val ints = series?.let { it[turn % it.size] }
            FakeFirmware.push(module, code, ints, flts, strs)
            turn++
            if (repeat > 0) main.postDelayed(run, repeat)
        }
        main.postDelayed(run, at)
    }

    /** "ints": [57] is one value; [[0], [20], [50]] a series. */
    private fun intSeries(v: Any?): List<IntArray>? {
        val a = v as? JSONArray ?: return null
        if (a.length() == 0) return listOf(IntArray(0))
        return if (a.opt(0) is JSONArray) List(a.length()) { i -> a.getJSONArray(i).let { s -> IntArray(s.length()) { s.getInt(it) } } }
        else listOf(IntArray(a.length()) { a.getInt(it) })
    }
}
