package com.openauto.dash

import android.content.Context
import com.openauto.dash.link.LinkMessage
import com.openauto.dash.link.PhotoAnswer
import com.openauto.dash.link.PhotoQuestion
import com.openauto.dash.link.ServiceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/*
 * The phone's photo questions ([PhotoQuestion]), answered by the car with
 * its Gemini key: a warning light lit on the dashboard, said in plain words
 * with what to do, or a page of the service booklet read into the servicing
 * planner's items. Only the photo and the car's model go to Google; the
 * booklet's lines come back to the phone to check before any is kept.
 */

/** The questions for Gemini and its answers read back. Pure, so it's unit-tested. */
internal object PhotoPrompt {

    fun lightSchema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        return JSONObject().put("type", "OBJECT").put(
            "properties",
            JSONObject()
                .put("title", str())
                .put("severity", str().put("enum", JSONArray(listOf(PhotoAnswer.SEVERITY_OK, PhotoAnswer.SEVERITY_SOON, PhotoAnswer.SEVERITY_STOP))))
                .put("meaning", str())
                .put("steps", JSONObject().put("type", "ARRAY").put("items", str()))
        ).put("required", JSONArray(listOf("title", "severity", "meaning", "steps")))
    }

    fun light(car: String, language: AiLanguage): String = buildString {
        appendLine(MechanicPersona.of(car))
        appendLine("The driver photographed the instrument cluster or a warning light that is lit, and asks what it is.")
        appendLine("Answer in ${language.promptName}, with correct spelling and all accents:")
        appendLine("- title: what the light is, at most 6 words.")
        appendLine("- severity: \"ok\" = information only, keep driving; \"soon\" = drive gently and get it checked within days; \"stop\" = stop as soon as it is safe, risk of damage or danger.")
        appendLine("- meaning: 2 or 3 sentences: what it means on this car and the likely causes.")
        appendLine("- steps: 2 to 4 things to do, in order, cheapest and simplest first.")
        appendLine("Judge the light's colour: red usually means stop, amber means soon. Several lights lit: say what they mean together.")
        append("If no warning light can be made out in the photo, say so in title and meaning, with severity \"ok\" and no steps. Never guess a light you cannot see.")
    }

    fun bookletSchema(): JSONObject {
        fun int() = JSONObject().put("type", "INTEGER")
        val entry = JSONObject().put("type", "OBJECT").put(
            "properties",
            JSONObject()
                .put("kind", JSONObject().put("type", "STRING").put("enum", JSONArray(UpkeepKind.entries.map { it.name })))
                .put("every_km", int()).put("every_months", int())
                .put("done_date", JSONObject().put("type", "STRING"))
                .put("done_km", int())
        ).put("required", JSONArray(listOf("kind")))
        return JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("services", JSONObject().put("type", "ARRAY").put("items", entry)))
            .put("required", JSONArray(listOf("services")))
    }

    fun booklet(car: String): String = buildString {
        appendLine("This is a photo of a page of the service booklet (or a garage invoice) of this car: $car.")
        appendLine("Read from it the servicing items you can see, as kinds: " + UpkeepKind.entries.joinToString(", ") { it.name } +
            " (OIL = engine oil and its filter, ADDITIVE = the particulate filter's additive, INSPECTION = the roadworthiness test).")
        appendLine("For each item: every_km and every_months when the page says how often it is due; done_date (yyyy-MM-dd) and done_km when the page records it being done (a stamp, a dated line).")
        appendLine("An item done several times on the page: only the latest. Leave out what the page doesn't say; never invent a figure.")
        append("No servicing information on the page: an empty services list.")
    }

    /** The light's answer; null when unusable. */
    fun parseLight(text: String, id: Long): PhotoAnswer? {
        val json = jsonIn(text) ?: return null
        val title = json.optString("title").trim()
        if (title.isEmpty()) return null
        val severity = json.optString("severity").trim().lowercase().takeIf {
            it in setOf(PhotoAnswer.SEVERITY_OK, PhotoAnswer.SEVERITY_SOON, PhotoAnswer.SEVERITY_STOP)
        } ?: PhotoAnswer.SEVERITY_SOON
        val steps = json.optJSONArray("steps")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
        return PhotoAnswer(id, PhotoQuestion.LIGHT, ok = true, title = title, severity = severity, text = json.optString("meaning").trim(), steps = steps)
    }

    /** The booklet's lines, kinds the planner knows only, figures that make sense only; null when unusable. */
    fun parseBooklet(text: String, today: LocalDate): List<ServiceEntry>? {
        val json = jsonIn(text) ?: return null
        val list = json.optJSONArray("services") ?: return null
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val kind = UpkeepKind.entries.firstOrNull { it.name == o.optString("kind").trim().uppercase() } ?: return@mapNotNull null
            fun int(key: String, range: IntRange) = if (o.isNull(key)) null else o.optInt(key, -1).takeIf { it in range }
            val date = o.optString("done_date").trim().takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                ?.takeIf { !it.isAfter(today) && it.year >= 1970 }
            val entry = ServiceEntry(
                kind = kind.name,
                everyKm = int("every_km", 1_000..400_000),
                everyMonths = int("every_months", 1..240),
                doneDate = date?.toString(),
                doneKm = int("done_km", 0..2_000_000)
            )
            entry.takeIf { it.everyKm != null || it.everyMonths != null || it.doneDate != null || it.doneKm != null }
        }
    }

    private fun jsonIn(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }
}

/**
 * What a booklet line changes in the servicing planner. Pure, so it's
 * unit-tested. Its interval figures replace the planner's (the booklet is
 * the maker's word); a figure it doesn't give keeps the planner's. Its
 * "done" counts only when later than the one logged: an old page read
 * again never puts the planner back.
 */
internal object BookletRules {

    fun interval(entry: ServiceEntry, kind: UpkeepKind, current: UpkeepInterval?): UpkeepInterval? {
        if (entry.everyKm == null && entry.everyMonths == null) return null
        return UpkeepInterval(kind, entry.everyKm ?: current?.everyKm, entry.everyMonths ?: current?.everyMonths, own = true)
    }

    fun done(entry: ServiceEntry, current: UpkeepDone?, zone: ZoneId): UpkeepDone? {
        val at = entry.doneDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull() }
        val km = entry.doneKm
        if (at == null && km == null) return null
        val newer = when {
            current == null -> true
            at != null && current.at != null -> at > current.at
            km != null && current.km != null -> km > current.km
            else -> current.at == null && current.km == null
        }
        return if (newer) UpkeepDone(km = km, at = at) else null
    }
}

/** The live side, on the unit: one question at a time from the phone link. */
internal object PhotoAsk {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val BUDGET_MS = 60_000L

    /** Answers [q] through [reply] (the link's send), whatever happens. */
    fun answer(context: Context, q: PhotoQuestion, reply: (LinkMessage) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            reply(ask(app, q))
        }
    }

    private suspend fun ask(app: Context, q: PhotoQuestion): PhotoAnswer {
        val res = AppLanguage.wrap(app).resources
        fun failed(why: String) = PhotoAnswer(q.id, q.kind, ok = false, error = why)
        val jpeg = runCatching { Base64.getDecoder().decode(q.jpeg) }.getOrNull()
            ?.takeIf { it.isNotEmpty() && it.size <= PhotoQuestion.MAX_BYTES }
            ?: return failed(res.getString(R.string.photo_error_picture))
        val config = AiSettings.load(app)
        if (config.apiKey.isBlank()) return failed(res.getString(R.string.ai_note_no_key))
        val car = CarProfileStore.current.promptDescription()
        val light = q.kind != PhotoQuestion.BOOKLET
        val prompt = if (light) PhotoPrompt.light(car, config.language) else PhotoPrompt.booklet(car)
        val schema = if (light) PhotoPrompt.lightSchema() else PhotoPrompt.bookletSchema()
        val reply = GeminiClient.generate(config.apiKey, prompt, schema, budgetMs = BUDGET_MS, audio = jpeg, audioMime = "image/jpeg")
            .getOrElse { return failed(AiMechanic.describe(app, it)) }
        if (light) return PhotoPrompt.parseLight(reply.text, q.id) ?: failed(res.getString(R.string.ai_error_unreadable))
        val entries = PhotoPrompt.parseBooklet(reply.text, LocalDate.now()) ?: return failed(res.getString(R.string.ai_error_unreadable))
        if (entries.isEmpty()) return failed(res.getString(R.string.photo_booklet_nothing))
        // Named in the launcher's language, for the phone to show.
        val named = entries.map { e -> e.copy(label = UpkeepKind.valueOf(e.kind).let { res.getString(it.labelRes) }) }
        return PhotoAnswer(q.id, q.kind, ok = true, services = named)
    }

    /** The booklet lines the driver checked on the phone, into the servicing planner. */
    fun keep(entries: List<ServiceEntry>) {
        val zone = ZoneId.systemDefault()
        entries.forEach { e ->
            val kind = UpkeepKind.entries.firstOrNull { it.name == e.kind } ?: return@forEach
            val state = Maintenance.state.value
            BookletRules.interval(e, kind, state.plan.firstOrNull { it.kind == kind })?.let(Maintenance::setInterval)
            BookletRules.done(e, state.done[kind], zone)?.let { Maintenance.setDone(kind, it) }
        }
    }
}
