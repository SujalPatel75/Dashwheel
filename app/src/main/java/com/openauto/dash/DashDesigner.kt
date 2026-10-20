package com.openauto.dash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/*
 * A dashboard from the driver's words: "a motorway dashboard with the speed
 * limit, fuel to destination and the music". Gemini picks the tiles from
 * Dashwheel's own and places them on the grid; Dashwheel makes sure they fit
 * (in the grid, at least their kind's smallest size, none on top of another)
 * and hands the result to the share sheet as a dashboard code, so the driver
 * sees it before it replaces anything, and Undo is there after.
 */

/** A tile as Gemini proposed it, before it is made to fit. */
internal data class ProposedTile(val kind: String, val app: String?, val x: Int, val y: Int, val w: Int, val h: Int)

/** The question for Gemini and its answer read back. Pure, so it's unit-tested. */
internal object DesignPrompt {
    /** A tile showing an installed app: live in the tile, or its icon. */
    const val APP_WINDOW = "APP_WINDOW"
    const val APP_ICON = "APP_ICON"

    fun schema(kinds: List<String>): JSONObject {
        fun int() = JSONObject().put("type", "INTEGER")
        val tile = JSONObject().put("type", "OBJECT").put(
            "properties",
            JSONObject()
                .put("kind", JSONObject().put("type", "STRING").put("enum", JSONArray(kinds + listOf(APP_WINDOW, APP_ICON))))
                .put("app", JSONObject().put("type", "STRING"))
                .put("x", int()).put("y", int()).put("w", int()).put("h", int())
        ).put("required", JSONArray(listOf("kind", "x", "y", "w", "h")))
        return JSONObject().put("type", "OBJECT").put(
            "properties",
            JSONObject()
                .put("name", JSONObject().put("type", "STRING"))
                .put("tiles", JSONObject().put("type", "ARRAY").put("items", tile).put("minItems", 1).put("maxItems", MAX_TILES))
        ).put("required", JSONArray(listOf("name", "tiles")))
    }

    /**
     * [catalog]: each kind with what it shows and its smallest size; [apps]:
     * the installed apps (package to name). [request] is the driver's words,
     * or null when they come as audio.
     */
    fun build(
        request: String?,
        catalog: List<CatalogEntry>,
        apps: List<Pair<String, String>>,
        cols: Int,
        rows: Int,
        language: AiLanguage,
        car: String
    ): String = buildString {
        appendLine("You lay out a dashboard for a car's head unit screen. The car: $car.")
        appendLine(
            if (request != null) "The driver asks for: \"${request.trim().take(MAX_REQUEST)}\""
            else "The driver says what they want in the attached recording."
        )
        appendLine()
        appendLine("The screen is a grid of $cols columns (x 0 to ${cols - 1}) and $rows rows (y 0 to ${rows - 1}). Each tile is a rectangle of whole cells: x, y its top-left cell, w, h its width and height in cells.")
        appendLine("Tiles must stay inside the grid and must not overlap. Use the whole screen, with no big empty areas: the most important tile biggest, near the driver.")
        appendLine("Use 3 to 7 tiles; each kind at most once. Never make a tile smaller than its minimum size (w x h below).")
        appendLine()
        appendLine("Tiles to choose from (kind: what it shows, minimum w x h):")
        catalog.forEach { appendLine("- ${it.kind}: ${it.label}. ${it.blurb} (min ${it.minW}x${it.minH})") }
        if (apps.isNotEmpty()) {
            appendLine("- $APP_WINDOW: an installed app running inside the tile (a navigation or music app); set app to its package. (min 3x2)")
            appendLine("- $APP_ICON: an installed app's icon that opens it; set app to its package. (min 1x1)")
            appendLine("Installed apps (package = name): " + apps.joinToString("; ") { (pkg, name) -> "$pkg = $name" })
            appendLine("Use an app only when the driver asks for it by name or kind; prefer the built-in tiles otherwise.")
        }
        appendLine()
        append("name: a short name for this dashboard in ${language.promptName}, at most 3 words.")
    }

    /** Gemini's JSON read back; null when there's nothing usable in it. */
    fun parse(text: String): Pair<String?, List<ProposedTile>>? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        val list = json.optJSONArray("tiles") ?: return null
        val tiles = (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val kind = o.optString("kind").trim().uppercase()
            if (kind.isEmpty()) return@mapNotNull null
            ProposedTile(kind, o.optString("app").trim().takeIf { it.isNotEmpty() }, o.optInt("x"), o.optInt("y"), o.optInt("w", 1), o.optInt("h", 1))
        }
        if (tiles.isEmpty()) return null
        return json.optString("name").trim().takeIf { it.isNotEmpty() }?.take(MAX_NAME) to tiles
    }

    const val MAX_TILES = 9
    private const val MAX_REQUEST = 300
    private const val MAX_NAME = 40
}

/** One built-in tile as Gemini is told of it (in English, whatever the screen's language). */
internal data class CatalogEntry(val kind: String, val label: String, val blurb: String, val minW: Int, val minH: Int)

/**
 * Makes proposed tiles a dashboard that fits a [cols] x [rows] grid: kinds
 * and apps that don't exist dropped, each kind once, every tile in the grid
 * and at least its smallest size, the later of two overlapping ones moved to
 * the nearest free place or shrunk, or left out when there's no room at all.
 * Pure, so it's unit-tested.
 */
internal object DesignFit {

    fun items(tiles: List<ProposedTile>, cols: Int, rows: Int, kinds: Set<String>, installed: Set<String>): List<DashboardItem> {
        val seen = HashSet<String>()
        val wanted = tiles.mapNotNull { t ->
            val item = itemFor(t, kinds, installed) ?: return@mapNotNull null
            val key = when (item) {
                is DashboardItem.BuiltinWidget -> item.kind.name
                is DashboardItem.AppWindow -> "w:" + item.packageName
                is DashboardItem.AppShortcut -> "i:" + item.packageName
                else -> return@mapNotNull null
            }
            if (!seen.add(key)) return@mapNotNull null
            val (minW, minH) = minSize(item)
            val w = t.w.coerceIn(minOf(minW, cols), cols)
            val h = t.h.coerceIn(minOf(minH, rows), rows)
            item to Rect(t.x.coerceIn(0, cols - w), t.y.coerceIn(0, rows - h), w, h)
        }
        val occupied = Array(rows) { BooleanArray(cols) }
        val placed = ArrayList<DashboardItem>()
        for ((item, r) in wanted) {
            val (minW, minH) = minSize(item)
            val spot = if (free(occupied, r)) r
            else nearest(occupied, r.w, r.h, r.x, r.y, cols, rows)
                ?: nearest(occupied, minOf(minW, cols), minOf(minH, rows), r.x, r.y, cols, rows)
                ?: continue
            mark(occupied, spot)
            placed += place(item, spot)
        }
        return placed
    }

    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun itemFor(t: ProposedTile, kinds: Set<String>, installed: Set<String>): DashboardItem? = when (t.kind) {
        DesignPrompt.APP_WINDOW -> t.app?.takeIf { it in installed }?.let { DashboardItem.AppWindow(it) }
        DesignPrompt.APP_ICON -> t.app?.takeIf { it in installed }?.let { DashboardItem.AppShortcut(it) }
        in kinds -> BuiltinKind.entries.firstOrNull { it.name == t.kind }?.let { DashboardItem.BuiltinWidget(it) }
        else -> null
    }

    private fun minSize(item: DashboardItem): Pair<Int, Int> = when (item) {
        is DashboardItem.BuiltinWidget -> item.kind.minSize()
        is DashboardItem.AppWindow -> 3 to 2
        else -> 1 to 1
    }

    private fun place(item: DashboardItem, r: Rect): DashboardItem = when (item) {
        is DashboardItem.BuiltinWidget -> item.copy(x = r.x, y = r.y, w = r.w, h = r.h)
        is DashboardItem.AppWindow -> item.copy(x = r.x, y = r.y, w = r.w, h = r.h)
        is DashboardItem.AppShortcut -> item.copy(x = r.x, y = r.y, w = r.w, h = r.h)
        else -> item
    }

    private fun free(occ: Array<BooleanArray>, r: Rect): Boolean {
        for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) if (occ[y][x]) return false
        return true
    }

    private fun mark(occ: Array<BooleanArray>, r: Rect) {
        for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) occ[y][x] = true
    }

    private fun nearest(occ: Array<BooleanArray>, w: Int, h: Int, nearX: Int, nearY: Int, cols: Int, rows: Int): Rect? {
        var best: Rect? = null
        var bestDist = Int.MAX_VALUE
        for (y in 0..rows - h) for (x in 0..cols - w) {
            val r = Rect(x, y, w, h)
            if (!free(occ, r)) continue
            val d = abs(x - nearX) + abs(y - nearY)
            if (d < bestDist) {
                bestDist = d
                best = r
            }
        }
        return best
    }
}

/**
 * The live side: asks Gemini from typed words or a recording, for the share
 * sheet (its state shows there) and Dashwheel's Gemini (design_dashboard).
 */
internal object DashDesigner {
    sealed interface State {
        data object Idle : State
        data object Listening : State
        data object Thinking : State
        /** A dashboard code ready for the share sheet's preview. */
        data class Ready(val code: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var recorder: QuestionRecorder? = null

    /** From typed words. */
    fun fromText(context: Context, words: String) {
        if (words.isBlank()) return
        job?.cancel()
        job = scope.launch { run(context.applicationContext, words, null) }
    }

    /**
     * From the driver's voice: listens until a pause, then asks. A second
     * call while listening sends what was said so far.
     */
    fun listen(context: Context) {
        val app = context.applicationContext
        when (_state.value) {
            State.Listening -> return send(app)
            State.Thinking -> return
            else -> Unit
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.Failed(app.getString(R.string.ai_ask_mic_denied))
            return
        }
        job?.cancel()
        job = scope.launch {
            val r = QuestionRecorder(app, "design.aac")
            try {
                r.start()
            } catch (e: Exception) {
                _state.value = State.Failed(app.getString(R.string.ai_ask_mic_error))
                return@launch
            }
            recorder = r
            _state.value = State.Listening
            val detector = SilenceDetector()
            while (isActive) {
                delay(100)
                when (detector.feed(r.amplitude(), System.currentTimeMillis())) {
                    SilenceDetector.Verdict.LISTEN -> Unit
                    SilenceDetector.Verdict.DONE -> return@launch send(app)
                    SilenceDetector.Verdict.NOTHING_HEARD -> {
                        stopRecorder()
                        _state.value = State.Failed(app.getString(R.string.design_nothing_heard))
                        return@launch
                    }
                }
            }
        }
    }

    private fun send(app: Context) {
        val audio = stopRecorder() ?: run {
            _state.value = State.Failed(app.getString(R.string.design_nothing_heard))
            return
        }
        job?.cancel()
        job = scope.launch { run(app, null, audio) }
    }

    private fun stopRecorder(): ByteArray? {
        val r = recorder ?: return null
        recorder = null
        return r.stop()
    }

    /** The sheet closed: stop listening or waiting. */
    fun cancel() {
        job?.cancel()
        stopRecorder()
        _state.value = State.Idle
    }

    /** The code was taken into the sheet's preview. */
    fun taken() {
        if (_state.value is State.Ready) _state.value = State.Idle
    }

    /**
     * For Dashwheel's Gemini, hands-free: designs from [words] and, once
     * done, sends the code where a code from the phone goes, so the share
     * sheet shows it as soon as the car stands.
     */
    fun forAssistant(context: Context, words: String) {
        val app = context.applicationContext
        scope.launch {
            design(app, words, null).onSuccess { DashShare.receive(it) }
        }
    }

    private suspend fun run(app: Context, words: String?, audio: ByteArray?) {
        _state.value = State.Thinking
        _state.value = design(app, words, audio).fold(
            onSuccess = { State.Ready(it) },
            onFailure = { State.Failed(if (it is NoTilesException) app.getString(R.string.design_no_tiles) else AiMechanic.describe(app, it)) }
        )
    }

    /** A dashboard code for what was asked, for this screen's grid. */
    suspend fun design(app: Context, words: String?, audio: ByteArray?): Result<String> {
        val config = AiSettings.load(app)
        if (config.apiKey.isBlank()) return Result.failure(IllegalStateException(app.getString(R.string.ai_note_no_key)))
        val upright = ScreenShape.vertical
        val cols = GRID_COLS
        val rows = GRID_ROWS
        val english = AiLanguage.ENGLISH.resources(app)
        val catalog = BuiltinKind.entries.filter { it.offered && it !in NOT_DESIGNED }.map {
            val (w, h) = it.minSize()
            CatalogEntry(it.name, english.getString(it.labelRes), english.getString(it.blurbRes), w, h)
        }
        val apps = withContext(Dispatchers.IO) {
            runCatching { AppLauncher.loadApps(app).filter { '/' !in it.packageName }.map { it.packageName to it.label } }.getOrDefault(emptyList())
        }.take(MAX_APPS)
        val prompt = DesignPrompt.build(words, catalog, apps, cols, rows, config.language, CarProfileStore.current.promptDescription())
        val kinds = catalog.map { it.kind }
        return GeminiClient.generate(
            config.apiKey, prompt, DesignPrompt.schema(kinds), budgetMs = BUDGET_MS,
            audio = audio, audioMime = "audio/aac"
        ).mapCatching { reply ->
            val (name, tiles) = DesignPrompt.parse(reply.text) ?: throw UnreadableAnswerException()
            val items = DesignFit.items(tiles, cols, rows, kinds.toSet(), apps.map { it.first }.toSet())
            if (items.isEmpty()) throw NoTilesException()
            DashShare.encode(SharedDashboard(name, upright, items))
        }
    }

    /** Gemini answered with nothing that fits. */
    private class NoTilesException : Exception()

    /** Tiles that only make sense set up by hand, or on a second screen. */
    private val NOT_DESIGNED = setOf(BuiltinKind.SCREEN_LIGHT, BuiltinKind.CAN_MON, BuiltinKind.DASH_BAR)
    private const val MAX_APPS = 60
    private const val BUDGET_MS = 60_000L
}
