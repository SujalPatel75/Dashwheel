package com.openauto.dash.carphoto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * The driver's own car from a 3D model (glTF binary, .glb): drawn here into the
 * pictures a car pack holds, as tools/mycar/bake_car.py draws them in Blender:
 * the side (nose to the right), from above (nose up), three-quarter front, and
 * each end, on a transparent background, with where the wheels, the bumpers and
 * the lamps are on each. A small software renderer in plain Kotlin (no GPU, no
 * Android), so the companion runs it on the phone and the JVM tests run it too.
 * The model itself goes in the pack as well, turned nose to +x, for the
 * launcher to show live in 3D (Car3D.kt).
 */

/** A triangle mesh of the car, in car space: x to the nose, y to the car's left, z up, wheels on z = 0. */
class CarModel internal constructor(
    internal val tris: Tris,
    internal val mats: List<ModelMat>,
    /** Name of each part (node, mesh and material names), by [Tris.part]. */
    internal val parts: List<String>,
    /** The file as given, and how to bring it to car space (see [glbForLive]). */
    private val glb: ByteArray,
    private val toCar: FloatArray,
    val length: Float,
    val width: Float,
    val height: Float
) {
    val triangles: Int get() = tris.count

    /**
     * The model for the launcher's live 3D view: the same file with one node
     * above its scene that turns it nose to +x, y up, centred, wheels on y = 0.
     */
    fun glbForLive(): ByteArray = CarModelKit.rewrap(glb, CarModelKit.carToGltf(toCar))

    /**
     * The same car nose the other way, as [CarModelKit.read] gives it with
     * turned the other way, without reading the file again: half a turn about
     * the upright, so only the corners and normals are new, the rest is shared.
     */
    fun turnedAround(): CarModel {
        val t = Tris(tris.count, FloatArray(tris.pos.size), FloatArray(tris.nrm.size), tris.uv, tris.mat, tris.part)
        for (i in tris.pos.indices) {
            val up = i % 3 == 2
            t.pos[i] = if (up) tris.pos[i] else -tris.pos[i]
            t.nrm[i] = if (up) tris.nrm[i] else -tris.nrm[i]
        }
        // Car x and y the other way: the matrix's first two rows, their shift included.
        val m = toCar.copyOf()
        for (c in 0 until 8) m[c] = -m[c]
        return CarModel(t, mats, parts, glb, m, length, width, height)
    }
}

/** Everything baked from a model, ready for a car pack. */
class CarModelResult(
    val name: String,
    val side: Argb,
    val top: Argb,
    val hero: Argb,
    val back: Argb,
    val front: Argb,
    /** The view from above as the door matcher takes it ([CarPhotoKit.openParts]). */
    val topView: CarTop,
    val sideWheels: List<CarPoint>,
    val sideNose: CarPoint,
    val sideTail: CarPoint,
    val backLights: List<CarPoint>,
    val frontLights: List<CarPoint>,
    /** Whether the wheels came from the model's part names (else from the side picture). */
    val wheelsNamed: Boolean,
    val open: List<CarOpenPart> = emptyList(),
    /** The model for the live 3D view, when it is light enough to send. */
    val model: ByteArray? = null
) {
    fun withOpen(parts: List<CarOpenPart>) = CarModelResult(
        name, side, top, hero, back, front, topView, sideWheels, sideNose, sideTail, backLights, frontLights, wheelsNamed, parts, model
    )

    /** The pictures of the pack by file name, for the caller to encode as PNG. */
    fun packEntries(): Map<String, Argb> = LinkedHashMap<String, Argb>().apply {
        put("side.png", side)
        put("top.png", top)
        put("hero.png", hero)
        put("back.png", back)
        put("front.png", front)
        for (p in open) put("top_${p.part.key}.png", p.image)
    }

    /** car.json as the launcher's pack import reads it (anchors as fractions of each picture). */
    fun toCarJson(): String = buildJsonObject {
        put("v", 1)
        put("name", name)
        putJsonObject("views") {
            for (v in listOf("side", "top", "hero", "back", "front")) put(v, "$v.png")
        }
        putJsonObject("anchors") {
            putJsonObject("side") {
                put("wheels", JsonArray(sideWheels.map(::point)))
                put("nose", point(sideNose))
                put("tail", point(sideTail))
            }
            putJsonObject("top") {
                put("wheels", JsonArray(topView.wheels.map(::point)))
                put("nose", point(topView.nose))
                put("tail", point(topView.tail))
            }
            putJsonObject("topBody") {
                put("wheels", JsonArray(topView.bodyWheels.map(::point)))
                put("box", JsonArray(topView.bodyBox.map { JsonPrimitive(round4(it)) }))
            }
            putJsonObject("back") { if (backLights.size == 2) put("lights", JsonArray(backLights.map(::point))) }
            putJsonObject("front") { if (frontLights.size == 2) put("lights", JsonArray(frontLights.map(::point))) }
        }
        if (open.isNotEmpty()) putJsonObject("parts") {
            putJsonObject("top") {
                for (p in open) putJsonObject(p.part.key) {
                    put("file", "top_${p.part.key}.png")
                    put("box", JsonArray(p.box.map { JsonPrimitive(round4(it)) }))
                }
            }
        }
        if (model != null) put("model", "model.glb")
    }.toString()

    private fun point(p: CarPoint) = JsonArray(listOf(JsonPrimitive(round4(p.x)), JsonPrimitive(round4(p.y))))

    private fun round4(v: Float): Double = (v * 10000.0).roundToInt() / 10000.0
}

/** Triangles as flat arrays: 9 floats a triangle for corners and normals, 6 for texture places. */
internal class Tris(
    val count: Int,
    val pos: FloatArray = FloatArray(count * 9),
    val nrm: FloatArray = FloatArray(count * 9),
    val uv: FloatArray = FloatArray(count * 6),
    val mat: IntArray = IntArray(count),
    val part: IntArray = IntArray(count)
)

/** A material as the renderer uses it; colours linear, the texture as given (sRGB). */
internal class ModelMat(
    val name: String,
    val base: FloatArray,
    val texture: Argb?,
    val metallic: Float,
    val roughness: Float,
    /** 0 opaque, 1 cut out below [cutoff], 2 blended. */
    val alphaMode: Int,
    val cutoff: Float,
    val emissive: FloatArray
)

object CarModelKit {
    /** The live 3D copy is left out of the pack above this many triangles (the head unit draws it). */
    const val MAX_LIVE_TRIANGLES = 300_000

    /** ...or this many bytes. */
    const val MAX_LIVE_BYTES = 16 * 1024 * 1024

    /** Textures are kept this long a side at most: plenty for pictures this size. */
    const val MAX_TEXTURE = 1024

    /** Pictures are drawn this many times bigger each way, then shrunk: smooth edges. */
    private const val SUPER = 2

    private val WHEEL_NAME = Regex("(^|[^a-z])(wheel|tyre|tire|rim|roue|pneu|jante|rad|reifen|felge|rueda|ruota)s?([^a-z]|$)")
    private val TAIL_NAME = Regex("tail.?(light|lamp|clear|reflector)|rear.?(lamp|light)|stop.?lamp|brake.?light|feu.?arr|r[uü]ckl")
    private val HEAD_NAME = Regex("head.?(light|lamp|lens)|phare|scheinwerfer")

    /**
     * Reads a glTF binary. [decodeImage] turns an embedded PNG or JPEG into a
     * picture (at most [MAX_TEXTURE] a side is plenty), or null. [turned]: the
     * nose is the other way from what the model's shape suggests (see [bake]).
     */
    fun read(glb: ByteArray, decodeImage: (ByteArray) -> Argb?, turned: Boolean = false): CarModel {
        val doc = Glb.parse(glb)
        val required = doc.json["extensionsRequired"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val unsupported = required.filter { it != "KHR_materials_emissive_strength" && it != "KHR_texture_transform" && it != "KHR_materials_unlit" }
        require(unsupported.isEmpty()) { "compressed model (${unsupported.joinToString()}): export it without compression" }
        val raw = Glb.triangles(doc, decodeImage)
        require(raw.count > 0) { "no triangles in the model" }
        val toCar = carFrame(raw, turned)
        // Brought to car space in the arrays just read: a big model doesn't fit twice in a phone's memory.
        val tris = Tris(raw.count, raw.pos, raw.nrm, raw.uv, raw.mat, raw.part)
        val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in 0 until raw.count * 3) {
            val i = v * 3
            val x = tris.pos[i]; val y = tris.pos[i + 1]; val z = tris.pos[i + 2]
            val nx = tris.nrm[i]; val ny = tris.nrm[i + 1]; val nz = tris.nrm[i + 2]
            for (k in 0..2) {
                val c = toCar[k * 4] * x + toCar[k * 4 + 1] * y + toCar[k * 4 + 2] * z + toCar[k * 4 + 3]
                tris.pos[i + k] = c
                if (raw.keep[v / 3]) { lo[k] = min(lo[k], c); hi[k] = max(hi[k], c) }
                tris.nrm[i + k] = toCar[k * 4] * nx + toCar[k * 4 + 1] * ny + toCar[k * 4 + 2] * nz
            }
        }
        // Centred across and along, wheels on the ground.
        val dx = -(lo[0] + hi[0]) / 2; val dy = -(lo[1] + hi[1]) / 2; val dz = -lo[2]
        for (v in 0 until raw.count * 3) {
            tris.pos[v * 3] += dx; tris.pos[v * 3 + 1] += dy; tris.pos[v * 3 + 2] += dz
        }
        toCar[3] += dx; toCar[7] += dy; toCar[11] += dz
        // Ground planes and the like were left out of the size; their triangles go too.
        var kept = 0
        for (t in 0 until raw.count) if (raw.keep[t]) kept++
        val out = if (kept == raw.count) tris else Tris(kept).also { o ->
            var j = 0
            for (t in 0 until raw.count) if (raw.keep[t]) {
                tris.pos.copyInto(o.pos, j * 9, t * 9, t * 9 + 9)
                tris.nrm.copyInto(o.nrm, j * 9, t * 9, t * 9 + 9)
                tris.uv.copyInto(o.uv, j * 6, t * 6, t * 6 + 6)
                o.mat[j] = tris.mat[t]; o.part[j] = tris.part[t]
                j++
            }
        }
        return CarModel(out, raw.mats, raw.parts, glb, toCar, hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
    }

    /**
     * The pictures of [model] and where its wheels, ends and lamps are on them.
     * The wheels come from parts named so (wheel, tyre, rim...), else from the
     * side picture. [opened]: the car from above with its doors open, whose
     * open parts are laid on the view from above ([CarPhotoKit.openParts]).
     */
    fun bake(model: CarModel, name: String = "", opened: Argb? = null, withLive: Boolean = true): CarModelResult {
        val l = model.length; val w = model.width; val h = model.height
        val midZ = h / 2
        val shader = Shader(model)

        // Side: from the car's right, nose to the right.
        val sideCam = Ortho.side(max(l, h * 2) * 1.08f, 1400, 700, midZ)
        val side = render(model, shader, sideCam)
        // From above, nose up.
        val topCam = Ortho.top(max(l, w * 1.6f) * 1.08f, 600, 1000)
        val top = render(model, shader, topCam)
        // Each end: from behind the car's left is on the picture's left, from the front on its right.
        val endSpan = max(w, h * 1000f / 800f) * 1.08f
        val backCam = Ortho.back(endSpan, 1000, 800, midZ)
        val back = render(model, shader, backCam)
        val frontCam = Ortho.front(endSpan, 1000, 800, midZ)
        val front = render(model, shader, frontCam)
        // Three-quarter front, from the right.
        val size = max(l, max(w, h))
        val heroCam = Persp(floatArrayOf(size * 1.35f, -size * 1.2f, size * 0.5f), floatArrayOf(0f, 0f, h * 0.5f), 62f, 1400, 900)
        val hero = render(model, shader, heroCam)

        val named = wheelsByName(model)
        val wheels = named ?: wheelsByShape(model)
        val sideWheels = listOf(sideCam.at(wheels[1]), sideCam.at(wheels[3]))
        val topWheels = wheels.map { topCam.at(it) }
        val topBody = bodyBox(top)
        val topView = CarTop(
            top, topWheels,
            topCam.at(floatArrayOf(l / 2, 0f, h)), topCam.at(floatArrayOf(-l / 2, 0f, h)),
            topWheels.map { CarPoint((it.x - topBody[0]) / (topBody[2] - topBody[0]), (it.y - topBody[1]) / (topBody[3] - topBody[1])) },
            topBody,
            h * topCam.scale,
            CarPoint(top.w / 2f, top.h / 2f)
        )
        val backLights = lamps(model, TAIL_NAME, -1f)?.map { backCam.at(it) }.orEmpty()
        val frontLights = lamps(model, HEAD_NAME, 1f)?.map { frontCam.at(it) }.orEmpty()
        val live = if (withLive) liveCopy(model, wheels) else null
        val result = CarModelResult(
            name, side, top, hero, back, front, topView, sideWheels,
            sideCam.at(floatArrayOf(l / 2, 0f, midZ)), sideCam.at(floatArrayOf(-l / 2, 0f, midZ)),
            backLights, frontLights, named != null, model = live
        )
        return if (opened != null) result.withOpen(CarPhotoKit.openParts(opened, topView)) else result
    }

    /**
     * The car for the launcher's live 3D view: a model already made for it
     * (its parts named Lamp_..., Hinge_... as tools/mycar/bake_car.py
     * documents, so its doors open too) goes as it is, turned nose to +x; any
     * other is redrawn light with its lamps and wheels found and named
     * ([CarLiveModel]). Null when even that is too big to send.
     */
    private fun liveCopy(model: CarModel, wheels: List<FloatArray>): ByteArray? {
        val rigged = model.parts.any { it.startsWith("Lamp_") || it.startsWith("Hinge_") }
        if (rigged && model.triangles <= MAX_LIVE_TRIANGLES) model.glbForLive().takeIf { it.size <= MAX_LIVE_BYTES }?.let { return it }
        return runCatching { CarLiveModel.build(model, wheels) }.getOrNull()
    }

    /**
     * Whether the nose seems to point the other way: the red lamps are at the
     * nose's end, or (no telling red lamps) the side picture's outline (the
     * bonnet is low, the tail high) says the car faces left. The caller
     * takes [CarModel.turnedAround] then.
     */
    fun noseLooksBackwards(model: CarModel): Boolean {
        // Red lamps say where the back is better than the outline does.
        CarLiveModel.redLampsAtNose(model)?.let { return it }
        val cam = Ortho.side(max(model.length, model.height * 2) * 1.08f, 700, 350, model.height / 2)
        val pic = render(model, Shader(model), cam)
        return runCatching { CarPhotoKit.side(pic, CarFacing.AUTO).facedLeft }.getOrDefault(false)
    }

    // ---- the car's frame ----

    /**
     * The 3x4 matrix (row by row) from the model's space to car space. glTF has
     * y up; the car's length is the longer of x and z; its nose at the far end
     * of that axis unless [turned].
     */
    private fun carFrame(raw: RawTris, turned: Boolean): FloatArray {
        val lo = FloatArray(3) { Float.MAX_VALUE }
        val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until raw.count * 3) if (raw.keep[v / 3]) for (k in 0..2) {
            lo[k] = min(lo[k], raw.pos[v * 3 + k]); hi[k] = max(hi[k], raw.pos[v * 3 + k])
        }
        val alongZ = hi[2] - lo[2] >= hi[0] - lo[0]
        val s = if (turned) -1f else 1f
        // Rows: car x (nose), car y (left), car z (up), with x' = M x.
        return if (alongZ) {
            // Nose +z, up +y: the car's left is +x.
            floatArrayOf(0f, 0f, s, 0f, s, 0f, 0f, 0f, 0f, 1f, 0f, 0f)
        } else {
            // Nose +x, up +y: the car's left is -z.
            floatArrayOf(s, 0f, 0f, 0f, 0f, 0f, -s, 0f, 0f, 1f, 0f, 0f)
        }
    }

    /** glTF for the live view: nose +x, y up, the car's left at -z, from car space. */
    internal fun carToGltf(toCar: FloatArray): FloatArray {
        // car -> glTF': X = car x, Y = car z, Z = -car y; composed with model -> car.
        val b = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f)
        val m = FloatArray(12)
        for (r in 0..2) for (c in 0..3) {
            var v = 0f
            for (k in 0..2) v += b[r * 3 + k] * toCar[k * 4 + c]
            m[r * 4 + c] = v
        }
        return m
    }

    /** [glb] with a node above its scene's roots that applies [m] (3x4, row by row). */
    internal fun rewrap(glb: ByteArray, m: FloatArray): ByteArray {
        val doc = Glb.parse(glb)
        val json = doc.json
        val nodes = json["nodes"]?.jsonArray?.toMutableList() ?: mutableListOf()
        val scenes = json["scenes"]?.jsonArray?.toMutableList() ?: mutableListOf()
        val sceneIndex = json["scene"]?.jsonPrimitive?.intOrNull ?: 0
        val roots: List<JsonElement> = if (scenes.isNotEmpty()) {
            scenes[sceneIndex].jsonObject["nodes"]?.jsonArray?.toList().orEmpty()
        } else {
            val children = nodes.flatMap { n -> n.jsonObject["children"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() }.orEmpty() }.toSet()
            nodes.indices.filter { it !in children }.map { JsonPrimitive(it) }
        }
        val colMajor = listOf(
            m[0], m[4], m[8], 0f,
            m[1], m[5], m[9], 0f,
            m[2], m[6], m[10], 0f,
            m[3], m[7], m[11], 1f
        ).map { JsonPrimitive(it.toDouble()) }
        val root = nodes.size
        nodes += buildJsonObject {
            put("name", "dashwheel_car")
            put("matrix", JsonArray(colMajor))
            put("children", JsonArray(roots))
        }
        val scene = JsonObject(
            (scenes.getOrNull(sceneIndex)?.jsonObject ?: JsonObject(emptyMap())) + ("nodes" to JsonArray(listOf(JsonPrimitive(root))))
        )
        if (scenes.isEmpty()) scenes += scene else scenes[sceneIndex] = scene
        val out = JsonObject(json + mapOf("nodes" to JsonArray(nodes), "scenes" to JsonArray(scenes), "scene" to JsonPrimitive(sceneIndex)))
        return Glb.write(out, doc.bin)
    }

    // ---- what is where ----

    /** Wheel centres front left, front right, rear left, rear right, from parts named so; null unless all four. */
    private fun wheelsByName(model: CarModel): List<FloatArray>? {
        val named = model.parts.map { WHEEL_NAME.containsMatchIn(it.lowercase()) }
        if (named.none { it }) return null
        // Each corner: the middle of the box of its wheel triangles.
        val lo = Array(4) { floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE) }
        val hi = Array(4) { floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE) }
        val t = model.tris
        val any = BooleanArray(4)
        for (i in 0 until t.count) {
            if (!named[t.part[i]]) continue
            val cx = (t.pos[i * 9] + t.pos[i * 9 + 3] + t.pos[i * 9 + 6]) / 3
            val cy = (t.pos[i * 9 + 1] + t.pos[i * 9 + 4] + t.pos[i * 9 + 7]) / 3
            val cz = (t.pos[i * 9 + 2] + t.pos[i * 9 + 5] + t.pos[i * 9 + 8]) / 3
            if (cz > model.height * 0.5f) continue
            val corner = (if (cx > 0) 0 else 2) + (if (cy > 0) 0 else 1)
            any[corner] = true
            for (v in 0..2) for (k in 0..2) {
                val c = t.pos[i * 9 + v * 3 + k]
                lo[corner][k] = min(lo[corner][k], c); hi[corner][k] = max(hi[corner][k], c)
            }
        }
        if (!any.all { it }) return null
        return (0..3).map { c -> FloatArray(3) { (lo[c][it] + hi[c][it]) / 2 } }
    }

    /**
     * Wheel centres from the shape alone (one mesh, parts unnamed): where the
     * car touches the ground along its length are the tyres; the frontmost and
     * rearmost of those, a fifth of the car's height up, just inside its sides.
     */
    private fun wheelsByShape(model: CarModel): List<FloatArray> {
        val l = model.length; val w = model.width; val h = model.height
        val bins = 200
        val low = FloatArray(bins) { Float.MAX_VALUE }
        val t = model.tris
        for (v in 0 until t.count * 3) {
            val x = t.pos[v * 3]; val z = t.pos[v * 3 + 2]
            val b = (((x / l) + 0.5f) * bins).toInt().coerceIn(0, bins - 1)
            low[b] = min(low[b], z)
        }
        val touch = BooleanArray(bins) { low[it] < h * 0.04f }
        val runs = ArrayList<IntRange>()
        var start = -1
        var gap = 0
        for (b in 0..bins) {
            if (b < bins && touch[b]) { if (start < 0) start = b; gap = 0; continue }
            if (start >= 0 && b < bins && gap < 2) { gap++; continue }
            if (start >= 0) runs += start..(b - 1 - gap)
            start = -1; gap = 0
        }
        fun xOf(bin: Float) = (bin / bins - 0.5f) * l
        val front = runs.maxByOrNull { it.last }?.let { xOf((it.first + it.last + 1) / 2f) } ?: (l * 0.33f)
        val rear = runs.minByOrNull { it.first }?.let { xOf((it.first + it.last + 1) / 2f) } ?: (-l * 0.33f)
        val fx = if (front - rear > l * 0.3f) front else l * 0.33f
        val rx = if (front - rear > l * 0.3f) rear else -l * 0.33f
        // Across: the tyres' outer faces, low down near each axle.
        fun outer(ax: Float, sign: Float): Float {
            var m = 0f
            for (v in 0 until t.count * 3) {
                val x = t.pos[v * 3]; val y = t.pos[v * 3 + 1]; val z = t.pos[v * 3 + 2]
                if (z < h * 0.15f && abs(x - ax) < l * 0.08f && y * sign > m) m = y * sign
            }
            return (if (m > 0f) m else w / 2) - w * 0.06f
        }
        val zc = h * 0.2f
        return listOf(
            floatArrayOf(fx, outer(fx, 1f), zc), floatArrayOf(fx, -outer(fx, -1f), zc),
            floatArrayOf(rx, outer(rx, 1f), zc), floatArrayOf(rx, -outer(rx, -1f), zc)
        )
    }

    /**
     * The car's left and right lamp as the area-weighted middle of the faces
     * named [pattern] (part or material), weighted by how much they face
     * [facing] (+1 the nose, -1 the tail); null unless both sides have some.
     */
    private fun lamps(model: CarModel, pattern: Regex, facing: Float): List<FloatArray>? {
        val t = model.tris
        val partHit = model.parts.map { pattern.containsMatchIn(it.lowercase()) }
        val matHit = model.mats.map { pattern.containsMatchIn(it.name.lowercase()) }
        val sum = Array(2) { FloatArray(4) }
        for (i in 0 until t.count) {
            if (!partHit[t.part[i]] && !(t.mat[i] >= 0 && matHit.getOrElse(t.mat[i]) { false })) continue
            val p = t.pos; val o = i * 9
            val ax = p[o + 3] - p[o]; val ay = p[o + 4] - p[o + 1]; val az = p[o + 5] - p[o + 2]
            val bx = p[o + 6] - p[o]; val by = p[o + 7] - p[o + 1]; val bz = p[o + 8] - p[o + 2]
            val nx = ay * bz - az * by; val ny = az * bx - ax * bz; val nz = ax * by - ay * bx
            val area = sqrt(nx * nx + ny * ny + nz * nz) / 2
            if (area <= 0f) continue
            // Faces turned to the car's end (either way round: models are often double-sided), at that end.
            val cx = (p[o] + p[o + 3] + p[o + 6]) / 3; val cy = (p[o + 1] + p[o + 4] + p[o + 7]) / 3; val cz = (p[o + 2] + p[o + 5] + p[o + 8]) / 3
            if (cx * facing <= 0f) continue
            val wgt = area * abs(nx) / (2 * area)
            if (wgt <= 0f) continue
            val s = sum[if (cy > 0) 0 else 1]
            s[0] += cx * wgt; s[1] += cy * wgt; s[2] += cz * wgt; s[3] += wgt
        }
        if (sum[0][3] <= 0f || sum[1][3] <= 0f) return null
        return sum.map { s -> floatArrayOf(s[0] / s[3], s[1] / s[3], s[2] / s[3]) }
    }

    /** The body's box in a picture from above (mirrors left out): left, top, right, bottom fractions. */
    private fun bodyBox(img: Argb): List<Float> {
        val lefts = ArrayList<Int>(); val rights = ArrayList<Int>()
        var r0 = img.h; var r1 = -1
        for (y in 0 until img.h) {
            var a = -1; var b = -1
            for (x in 0 until img.w) if ((img.px[y * img.w + x] ushr 24) > 128) { if (a < 0) a = x; b = x }
            if (a < 0) continue
            r0 = min(r0, y); r1 = y
            lefts += a; rights += b
        }
        require(r1 > r0) { "the model drew nothing from above" }
        val span = r1 - r0
        val mid = lefts.indices.filter { i -> val y = r0 + i; y > r0 + span * 0.15 && y < r0 + span * 0.85 }
        val ls = mid.map { lefts[it] }.sorted(); val rs = mid.map { rights[it] }.sorted()
        return listOf(ls[ls.size / 2].toFloat() / img.w, r0.toFloat() / img.h, (rs[rs.size / 2] + 1f) / img.w, (r1 + 1f) / img.h)
    }

    // ---- drawing ----

    internal interface Cam {
        val w: Int
        val h: Int
        /** Screen x, y in pixels and depth (smaller is nearer) of a car-space point, into [out]. */
        fun project(x: Float, y: Float, z: Float, out: FloatArray)
        /** The unit direction from a car-space point towards the eye. */
        fun toEye(x: Float, y: Float, z: Float, out: FloatArray)
        val perspective: Boolean
        fun at(p: FloatArray): CarPoint {
            val o = FloatArray(3)
            project(p[0], p[1], p[2], o)
            return CarPoint(o[0] / w, o[1] / h)
        }
    }

    /** A camera looking along one of the car's axes; [scale] pixels a unit. */
    internal class Ortho(
        override val w: Int, override val h: Int, val scale: Float,
        private val ax: FloatArray, private val ay: FloatArray, private val ad: FloatArray,
        private val cx: Float, private val cy: Float, private val eye: FloatArray
    ) : Cam {
        override val perspective = false
        override fun project(x: Float, y: Float, z: Float, out: FloatArray) {
            out[0] = w / 2f + (ax[0] * x + ax[1] * y + ax[2] * z - cx) * scale
            out[1] = h / 2f - (ay[0] * x + ay[1] * y + ay[2] * z - cy) * scale
            out[2] = ad[0] * x + ad[1] * y + ad[2] * z
        }
        override fun toEye(x: Float, y: Float, z: Float, out: FloatArray) { eye.copyInto(out) }

        companion object {
            // Picture right, picture up, depth (along the look) and the way to the eye, in car space.
            fun side(span: Float, w: Int, h: Int, midZ: Float) = Ortho(w, h, w / span, floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f), 0f, midZ, floatArrayOf(0f, -1f, 0f))
            fun top(span: Float, w: Int, h: Int) = Ortho(w, h, h / span, floatArrayOf(0f, -1f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, -1f), 0f, 0f, floatArrayOf(0f, 0f, 1f))
            fun back(span: Float, w: Int, h: Int, midZ: Float) = Ortho(w, h, w / span, floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), 0f, midZ, floatArrayOf(-1f, 0f, 0f))
            fun front(span: Float, w: Int, h: Int, midZ: Float) = Ortho(w, h, w / span, floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(-1f, 0f, 0f), 0f, midZ, floatArrayOf(1f, 0f, 0f))
        }
    }

    /** A perspective camera at [eye] looking at [target], a [lensMm] lens on a 36 mm wide frame. */
    internal class Persp(private val eye: FloatArray, target: FloatArray, lensMm: Float, override val w: Int, override val h: Int) : Cam {
        override val perspective = true
        private val f = norm(floatArrayOf(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]))
        private val r = norm(cross(f, floatArrayOf(0f, 0f, 1f)))
        private val u = cross(r, f)
        private val focal = w / 2f / (18f / lensMm)
        override fun project(x: Float, y: Float, z: Float, out: FloatArray) {
            val qx = x - eye[0]; val qy = y - eye[1]; val qz = z - eye[2]
            val zc = qx * f[0] + qy * f[1] + qz * f[2]
            val d = max(zc, 1e-4f)
            out[0] = w / 2f + (qx * r[0] + qy * r[1] + qz * r[2]) * focal / d
            out[1] = h / 2f - (qx * u[0] + qy * u[1] + qz * u[2]) * focal / d
            out[2] = zc
        }
        override fun toEye(x: Float, y: Float, z: Float, out: FloatArray) {
            val dx = eye[0] - x; val dy = eye[1] - y; val dz = eye[2] - z
            val n = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
            out[0] = dx / n; out[1] = dy / n; out[2] = dz / n
        }
    }

    /**
     * A plain studio, as the PC bake tool's: a soft sky over a dark floor
     * (reflected in the paint and the glass) and one sun from above the front
     * right. Physically loose; it should read as a clean product shot.
     */
    internal class Shader(model: CarModel) {
        private val mats = model.mats
        private val sun = norm(floatArrayOf(0.287f, -0.497f, 0.819f))

        /** The studio seen in direction [d] (unit), linear. */
        fun sky(dz: Float): Float {
            val t = ((dz + 1f) / 2f - 0.45f) / 0.30f
            val k = t.coerceIn(0f, 1f)
            return (0.05f + (0.9f - 0.05f) * k * k * (3 - 2 * k)) * 1.2f
        }

        /** Linear colour and alpha of a surface, into [out] (r, g, b, a). */
        fun shade(mat: Int, n: FloatArray, v: FloatArray, u: Float, w: Float, out: FloatArray) {
            val m = mats.getOrNull(mat)
            var r = m?.base?.get(0) ?: 0.6f
            var g = m?.base?.get(1) ?: 0.6f
            var b = m?.base?.get(2) ?: 0.62f
            var a = m?.base?.get(3) ?: 1f
            m?.texture?.let { tex ->
                val c = sample(tex, u, w)
                r *= srgb((c shr 16) and 255); g *= srgb((c shr 8) and 255); b *= srgb(c and 255)
                a *= ((c ushr 24) and 255) / 255f
            }
            val metal = m?.metallic ?: 0f
            val rough = (m?.roughness ?: 0.6f).coerceIn(0.04f, 1f)
            // Facing the eye, whichever side was modelled.
            var nx = n[0]; var ny = n[1]; var nz = n[2]
            if (nx * v[0] + ny * v[1] + nz * v[2] < 0) { nx = -nx; ny = -ny; nz = -nz }
            val ambient = sky(nz * 0.8f) * 0.55f + 0.08f
            val lambert = max(0f, nx * sun[0] + ny * sun[1] + nz * sun[2]) * 1.6f
            val light = ambient + lambert
            // Reflection of the studio, stronger at grazing angles and on smooth paint.
            val nv = (nx * v[0] + ny * v[1] + nz * v[2]).coerceIn(0f, 1f)
            val rz = 2 * nv * nz - v[2]
            val gloss = (1f - rough).pow(1.5f)
            val f0 = 0.04f
            val fresnel = f0 + (1 - f0) * (1 - nv).pow(5)
            val env = sky(rz) * gloss
            val hx = sun[0] + v[0]; val hy = sun[1] + v[1]; val hz = sun[2] + v[2]
            val hn = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-6f)
            val nh = max(0f, (nx * hx + ny * hy + nz * hz) / hn)
            val shine = nh.pow(2f + 200f * gloss) * gloss * 2.5f
            val diffuse = 1f - metal
            val specDiel = fresnel * env + shine * 0.3f
            // Metal: the studio's reflection when polished, lit like any surface when rough.
            fun mix(base: Float): Float = base * diffuse * light + specDiel * diffuse +
                metal * base * (light * (1 - gloss) + (env * 0.9f + 0.12f + shine) * gloss)
            out[0] = mix(r); out[1] = mix(g); out[2] = mix(b)
            m?.emissive?.let { e -> out[0] += e[0]; out[1] += e[1]; out[2] += e[2] }
            out[3] = if (m?.alphaMode == 2) min(1f, a + fresnel * 0.5f) else a
            if (m?.alphaMode == 1 && a < m.cutoff) out[3] = 0f
            if (m?.alphaMode == 0) out[3] = 1f
        }

        private fun sample(tex: Argb, u: Float, v: Float): Int {
            val fu = u - floor(u); val fv = v - floor(v)
            val x = (fu * tex.w).toInt().coerceIn(0, tex.w - 1)
            val y = (fv * tex.h).toInt().coerceIn(0, tex.h - 1)
            return tex.px[y * tex.w + x]
        }
    }

    /** [model] through [cam]: opaque parts first, then the glass over them back to front. */
    internal fun render(model: CarModel, shader: Shader, cam: Cam): Argb {
        val w = cam.w * SUPER; val h = cam.h * SUPER
        val depth = FloatArray(w * h) { Float.MAX_VALUE }
        val col = FloatArray(w * h * 3)
        val alpha = FloatArray(w * h)
        val t = model.tris
        val sx = FloatArray(3); val sy = FloatArray(3); val sz = FloatArray(3)
        val o = FloatArray(3)
        val blended = ArrayList<Int>()
        val n = FloatArray(3); val v = FloatArray(3); val c = FloatArray(4)

        fun draw(i: Int, blend: Boolean) {
            for (k in 0..2) {
                cam.project(t.pos[i * 9 + k * 3], t.pos[i * 9 + k * 3 + 1], t.pos[i * 9 + k * 3 + 2], o)
                sx[k] = o[0] * SUPER; sy[k] = o[1] * SUPER; sz[k] = o[2]
            }
            if (cam.perspective && (sz[0] <= 1e-3f || sz[1] <= 1e-3f || sz[2] <= 1e-3f)) return
            val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
            if (abs(area) < 1e-6f) return
            val x0 = max(0, floor(min(sx[0], min(sx[1], sx[2]))).toInt())
            val x1 = min(w - 1, ceil(max(sx[0], max(sx[1], sx[2]))).toInt())
            val y0 = max(0, floor(min(sy[0], min(sy[1], sy[2]))).toInt())
            val y1 = min(h - 1, ceil(max(sy[0], max(sy[1], sy[2]))).toInt())
            if (x0 > x1 || y0 > y1) return
            val inv = 1f / area
            val iz0 = if (cam.perspective) 1f / sz[0] else 1f
            val iz1 = if (cam.perspective) 1f / sz[1] else 1f
            val iz2 = if (cam.perspective) 1f / sz[2] else 1f
            for (py in y0..y1) {
                val fy = py + 0.5f
                for (px in x0..x1) {
                    val fx = px + 0.5f
                    var b0 = ((sx[1] - fx) * (sy[2] - fy) - (sx[2] - fx) * (sy[1] - fy)) * inv
                    var b1 = ((sx[2] - fx) * (sy[0] - fy) - (sx[0] - fx) * (sy[2] - fy)) * inv
                    var b2 = 1f - b0 - b1
                    if (b0 < 0f || b1 < 0f || b2 < 0f) continue
                    if (cam.perspective) {
                        val q0 = b0 * iz0; val q1 = b1 * iz1; val q2 = b2 * iz2
                        val s = q0 + q1 + q2
                        b0 = q0 / s; b1 = q1 / s; b2 = q2 / s
                    }
                    val z = b0 * sz[0] + b1 * sz[1] + b2 * sz[2]
                    val p = py * w + px
                    if (z >= depth[p]) continue
                    for (k in 0..2) n[k] = b0 * t.nrm[i * 9 + k] + b1 * t.nrm[i * 9 + 3 + k] + b2 * t.nrm[i * 9 + 6 + k]
                    val nl = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).coerceAtLeast(1e-6f)
                    n[0] /= nl; n[1] /= nl; n[2] /= nl
                    if (cam.perspective) {
                        val wx = b0 * t.pos[i * 9] + b1 * t.pos[i * 9 + 3] + b2 * t.pos[i * 9 + 6]
                        val wy = b0 * t.pos[i * 9 + 1] + b1 * t.pos[i * 9 + 4] + b2 * t.pos[i * 9 + 7]
                        val wz = b0 * t.pos[i * 9 + 2] + b1 * t.pos[i * 9 + 5] + b2 * t.pos[i * 9 + 8]
                        cam.toEye(wx, wy, wz, v)
                    } else cam.toEye(0f, 0f, 0f, v)
                    val tu = b0 * t.uv[i * 6] + b1 * t.uv[i * 6 + 2] + b2 * t.uv[i * 6 + 4]
                    val tv = b0 * t.uv[i * 6 + 1] + b1 * t.uv[i * 6 + 3] + b2 * t.uv[i * 6 + 5]
                    shader.shade(t.mat[i], n, v, tu, tv, c)
                    if (c[3] <= 0.01f) continue
                    if (!blend) {
                        depth[p] = z
                        col[p * 3] = c[0]; col[p * 3 + 1] = c[1]; col[p * 3 + 2] = c[2]
                        alpha[p] = 1f
                    } else {
                        val a = c[3]
                        col[p * 3] = c[0] * a + col[p * 3] * (1 - a)
                        col[p * 3 + 1] = c[1] * a + col[p * 3 + 1] * (1 - a)
                        col[p * 3 + 2] = c[2] * a + col[p * 3 + 2] * (1 - a)
                        alpha[p] = a + alpha[p] * (1 - a)
                    }
                }
            }
        }

        for (i in 0 until t.count) {
            if (model.mats.getOrNull(t.mat[i])?.alphaMode == 2) blended += i else draw(i, false)
        }
        if (blended.isNotEmpty()) {
            // Far first: the nearest glass ends up in front.
            val key = FloatArray(t.count)
            for (i in blended) {
                var z = 0f
                for (k in 0..2) { cam.project(t.pos[i * 9 + k * 3], t.pos[i * 9 + k * 3 + 1], t.pos[i * 9 + k * 3 + 2], o); z += o[2] }
                key[i] = z
            }
            blended.sortByDescending { key[it] }
            for (i in blended) draw(i, true)
        }
        return downsample(col, alpha, w, h)
    }

    /** The super-sampled picture shrunk to its size, tone-mapped and in sRGB. */
    private fun downsample(col: FloatArray, alpha: FloatArray, w: Int, h: Int): Argb {
        val ow = w / SUPER; val oh = h / SUPER
        val out = Argb(ow, oh)
        val nn = (SUPER * SUPER).toFloat()
        for (y in 0 until oh) for (x in 0 until ow) {
            var r = 0f; var g = 0f; var b = 0f; var a = 0f
            for (dy in 0 until SUPER) for (dx in 0 until SUPER) {
                val p = (y * SUPER + dy) * w + x * SUPER + dx
                val pa = alpha[p]
                r += col[p * 3] * pa; g += col[p * 3 + 1] * pa; b += col[p * 3 + 2] * pa; a += pa
            }
            if (a <= 0f) continue
            val ia = 1f / a
            val ai = (a / nn * 255f).roundToInt().coerceIn(0, 255)
            out.px[y * ow + x] = (ai shl 24) or (encode(r * ia) shl 16) or (encode(g * ia) shl 8) or encode(b * ia)
        }
        return out
    }

    /** Linear light to an sRGB byte, with a soft shoulder so highlights don't clip flat. */
    private fun encode(v: Float): Int {
        val e = v * 1.1f
        val t = e * (2.51f * e + 0.03f) / (e * (2.43f * e + 0.59f) + 0.14f)
        val c = t.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (s * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private val SRGB = FloatArray(256) { i -> val c = i / 255f; if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f) }
    internal fun srgb(v: Int): Float = SRGB[v]

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun norm(a: FloatArray): FloatArray {
        val n = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).coerceAtLeast(1e-9f)
        return floatArrayOf(a[0] / n, a[1] / n, a[2] / n)
    }
}

/** Triangles straight out of the file, in the model's own space. */
internal class RawTris(
    val count: Int, val pos: FloatArray, val nrm: FloatArray, val uv: FloatArray, val mat: IntArray, val part: IntArray,
    /** Counted in the car's size (a ground plane is not). */
    val keep: BooleanArray,
    val mats: List<ModelMat>, val parts: List<String>
)

/** The glTF binary container: its JSON and its one binary buffer. */
internal object Glb {
    class Doc(val json: JsonObject, val bin: ByteArray)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(bytes: ByteArray): Doc {
        require(bytes.size >= 20) { "not a glTF binary" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.getInt(0) == 0x46546C67) { "not a glTF binary (.glb)" }
        require(b.getInt(4) == 2) { "glTF ${b.getInt(4)} is not supported" }
        var at = 12
        var doc: JsonObject? = null
        var bin = ByteArray(0)
        while (at + 8 <= bytes.size) {
            val len = b.getInt(at); val type = b.getInt(at + 4)
            require(len >= 0 && at + 8 + len <= bytes.size) { "damaged glTF binary" }
            if (type == 0x4E4F534A) doc = json.parseToJsonElement(String(bytes, at + 8, len, Charsets.UTF_8)).jsonObject
            else if (type == 0x004E4942) bin = bytes.copyOfRange(at + 8, at + 8 + len)
            at += 8 + len
        }
        return Doc(requireNotNull(doc) { "no JSON in the glTF binary" }, bin)
    }

    fun write(doc: JsonObject, bin: ByteArray): ByteArray {
        var j = doc.toString().toByteArray(Charsets.UTF_8)
        if (j.size % 4 != 0) j += ByteArray(4 - j.size % 4) { 0x20 }
        val b = if (bin.size % 4 != 0) bin + ByteArray(4 - bin.size % 4) else bin
        val total = 12 + 8 + j.size + (if (b.isNotEmpty()) 8 + b.size else 0)
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(total)
        out.putInt(j.size).putInt(0x4E4F534A).put(j)
        if (b.isNotEmpty()) out.putInt(b.size).putInt(0x004E4942).put(b)
        return out.array()
    }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.floats(key: String): FloatArray? = this[key]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull?.toFloat() ?: 0f }?.toFloatArray()

    /** Every triangle of the default scene, in the model's space. */
    fun triangles(doc: Doc, decodeImage: (ByteArray) -> Argb?): RawTris {
        val j = doc.json
        val accessors = j["accessors"]?.jsonArray.orEmpty()
        val views = j["bufferViews"]?.jsonArray.orEmpty()
        val meshes = j["meshes"]?.jsonArray.orEmpty()
        val nodes = j["nodes"]?.jsonArray.orEmpty()
        val bin = ByteBuffer.wrap(doc.bin).order(ByteOrder.LITTLE_ENDIAN)

        fun read(index: Int): Array<FloatArray> {
            val a = accessors[index].jsonObject
            val count = a.int("count") ?: 0
            val comps = when (a.str("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1 }
            val ct = a.int("componentType") ?: 5126
            val norm = a["normalized"]?.jsonPrimitive?.booleanOrNull == true
            val size = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val view = a.int("bufferView") ?: return Array(count) { FloatArray(comps) }
            val bv = views[view].jsonObject
            require((bv.int("buffer") ?: 0) == 0) { "the model uses outside files: export it as one .glb" }
            val stride = bv.int("byteStride") ?: (size * comps)
            val start = (bv.int("byteOffset") ?: 0) + (a.int("byteOffset") ?: 0)
            return Array(count) { i ->
                FloatArray(comps) { c ->
                    val p = start + i * stride + c * size
                    when (ct) {
                        5126 -> bin.getFloat(p)
                        5125 -> (bin.getInt(p).toLong() and 0xFFFFFFFFL).toFloat()
                        5123 -> (bin.getShort(p).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                        5122 -> bin.getShort(p).toInt().let { if (norm) max(it / 32767f, -1f) else it.toFloat() }
                        5121 -> (bin.get(p).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                        else -> bin.get(p).toInt().let { if (norm) max(it / 127f, -1f) else it.toFloat() }
                    }
                }
            }
        }

        // Pictures and materials.
        val images = j["images"]?.jsonArray.orEmpty().map { im ->
            val o = im.jsonObject
            val view = o.int("bufferView") ?: return@map null
            val bv = views[view].jsonObject
            val start = bv.int("byteOffset") ?: 0
            val len = bv.int("byteLength") ?: 0
            runCatching { decodeImage(doc.bin.copyOfRange(start, start + len)) }.getOrNull()
        }
        val textures = j["textures"]?.jsonArray.orEmpty().map { t ->
            val o = t.jsonObject
            val src = o.int("source")
                ?: o["extensions"]?.jsonObject?.values?.firstNotNullOfOrNull { it.jsonObject.int("source") }
            src?.let { images.getOrNull(it) }
        }
        val mats = j["materials"]?.jsonArray.orEmpty().map { m ->
            val o = m.jsonObject
            val pbr = o["pbrMetallicRoughness"]?.jsonObject
            val f = pbr?.floats("baseColorFactor") ?: floatArrayOf(1f, 1f, 1f, 1f)
            val tex = pbr?.get("baseColorTexture")?.jsonObject?.int("index")?.let { textures.getOrNull(it) }
            val unlit = o["extensions"]?.jsonObject?.containsKey("KHR_materials_unlit") == true
            val strength = o["extensions"]?.jsonObject?.get("KHR_materials_emissive_strength")?.jsonObject?.get("emissiveStrength")?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 1f
            val em = o.floats("emissiveFactor") ?: floatArrayOf(0f, 0f, 0f)
            ModelMat(
                o.str("name").orEmpty(),
                floatArrayOf(f[0], f[1], f[2], f.getOrElse(3) { 1f }),
                tex,
                // Left unsaid, glTF means bare rough metal: downloaded and generated cars often
                // just don't say, and look like a black cut-out that way. Read as glossy paint.
                if (unlit) 0f else pbr?.get("metallicFactor")?.jsonPrimitive?.doubleOrNull?.toFloat()
                    ?: if (pbr?.containsKey("metallicRoughnessTexture") == true) 1f else 0f,
                pbr?.get("roughnessFactor")?.jsonPrimitive?.doubleOrNull?.toFloat()?.takeIf { pbr.containsKey("metallicFactor") }
                    ?: pbr?.get("roughnessFactor")?.jsonPrimitive?.doubleOrNull?.toFloat()?.coerceAtMost(0.45f) ?: 0.45f,
                when (o.str("alphaMode")) { "MASK" -> 1; "BLEND" -> 2; else -> 0 },
                o["alphaCutoff"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.5f,
                floatArrayOf(em[0] * strength * 0.6f, em[1] * strength * 0.6f, em[2] * strength * 0.6f)
            )
        }

        // Walk the scene, gathering triangles in world space.
        val pos = FloatList(); val nrm = FloatList(); val uv = FloatList()
        val matOf = IntList(); val partOf = IntList()
        val parts = ArrayList<String>()
        val partBoxes = ArrayList<FloatArray>()

        fun local(n: JsonObject): FloatArray {
            n.floats("matrix")?.let { m -> return FloatArray(16) { i -> m[(i % 4) * 4 + i / 4] } }
            val t = n.floats("translation") ?: floatArrayOf(0f, 0f, 0f)
            val q = n.floats("rotation") ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = n.floats("scale") ?: floatArrayOf(1f, 1f, 1f)
            val (x, y, z, w) = listOf(q[0], q[1], q[2], q[3])
            // Row by row: rotation times scale, then translation.
            return floatArrayOf(
                (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y - z * w)) * s[1], (2 * (x * z + y * w)) * s[2], t[0],
                (2 * (x * y + z * w)) * s[0], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z - x * w)) * s[2], t[1],
                (2 * (x * z - y * w)) * s[0], (2 * (y * z + x * w)) * s[1], (1 - 2 * (x * x + y * y)) * s[2], t[2],
                0f, 0f, 0f, 1f
            )
        }

        fun mul(a: FloatArray, b: FloatArray) = FloatArray(16) { i ->
            val r = i / 4; val c = i % 4
            a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c] + a[r * 4 + 3] * b[12 + c]
        }

        fun addMesh(meshIndex: Int, m: FloatArray, nodeName: String) {
            val mesh = meshes.getOrNull(meshIndex)?.jsonObject ?: return
            // The normals' matrix: the cofactors of the upper 3x3 (the inverse transpose, up to scale).
            val c = floatArrayOf(
                m[5] * m[10] - m[6] * m[9], m[6] * m[8] - m[4] * m[10], m[4] * m[9] - m[5] * m[8],
                m[2] * m[9] - m[1] * m[10], m[0] * m[10] - m[2] * m[8], m[1] * m[8] - m[0] * m[9],
                m[1] * m[6] - m[2] * m[5], m[2] * m[4] - m[0] * m[6], m[0] * m[5] - m[1] * m[4]
            )
            val meshName = mesh.str("name").orEmpty()
            for (pe in mesh["primitives"]?.jsonArray.orEmpty()) {
                val prim = pe.jsonObject
                val mode = prim.int("mode") ?: 4
                if (mode !in 4..6) continue
                val attrs = prim["attributes"]?.jsonObject ?: continue
                val p = read(attrs.int("POSITION") ?: continue)
                val n = attrs.int("NORMAL")?.let { read(it) }
                val t = attrs.int("TEXCOORD_0")?.let { read(it) }
                val idx = prim.int("indices")?.let { read(it).map { v -> v[0].toInt() } } ?: List(p.size) { it }
                val tri = ArrayList<Int>()
                when (mode) {
                    4 -> tri.addAll(idx.take(idx.size - idx.size % 3))
                    5 -> for (i in 0 until idx.size - 2) if (i % 2 == 0) tri += listOf(idx[i], idx[i + 1], idx[i + 2]) else tri += listOf(idx[i + 1], idx[i], idx[i + 2])
                    6 -> for (i in 1 until idx.size - 1) tri += listOf(idx[0], idx[i], idx[i + 1])
                }
                // No normals given (generated models): each corner gets the average of the faces
                // sharing it, so curved panels look smooth rather than cut in facets.
                val smooth = if (n == null) Array(p.size) { FloatArray(3) }.also { acc ->
                    var q = 0
                    while (q + 2 < tri.size) {
                        val a = p[tri[q]]; val b = p[tri[q + 1]]; val d = p[tri[q + 2]]
                        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
                        val vx = d[0] - a[0]; val vy = d[1] - a[1]; val vz = d[2] - a[2]
                        val fx = uy * vz - uz * vy; val fy = uz * vx - ux * vz; val fz = ux * vy - uy * vx
                        for (r in 0..2) { val o = acc[tri[q + r]]; o[0] += fx; o[1] += fy; o[2] += fz }
                        q += 3
                    }
                } else null
                val mat = prim.int("material") ?: -1
                val matName = mats.getOrNull(mat)?.name.orEmpty()
                val part = parts.size
                parts += listOf(nodeName, meshName, matName).filter { it.isNotBlank() }.joinToString(" ")
                val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                var k = 0
                while (k + 2 < tri.size) {
                    val corners = Array(3) { q ->
                        val v = p[tri[k + q]]
                        floatArrayOf(
                            m[0] * v[0] + m[1] * v[1] + m[2] * v[2] + m[3],
                            m[4] * v[0] + m[5] * v[1] + m[6] * v[2] + m[7],
                            m[8] * v[0] + m[9] * v[1] + m[10] * v[2] + m[11]
                        )
                    }
                    val ax = corners[1][0] - corners[0][0]; val ay = corners[1][1] - corners[0][1]; val az = corners[1][2] - corners[0][2]
                    val bx = corners[2][0] - corners[0][0]; val by = corners[2][1] - corners[0][1]; val bz = corners[2][2] - corners[0][2]
                    val fn = floatArrayOf(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
                    for (q in 0..2) {
                        val v = corners[q]
                        pos.add(v[0]); pos.add(v[1]); pos.add(v[2])
                        for (d in 0..2) { box[d] = min(box[d], v[d]); box[d + 3] = max(box[d + 3], v[d]) }
                        val sn = n?.get(tri[k + q]) ?: smooth?.get(tri[k + q])?.takeIf { it[0] != 0f || it[1] != 0f || it[2] != 0f }
                        val nn = if (sn != null) floatArrayOf(
                            c[0] * sn[0] + c[1] * sn[1] + c[2] * sn[2],
                            c[3] * sn[0] + c[4] * sn[1] + c[5] * sn[2],
                            c[6] * sn[0] + c[7] * sn[1] + c[8] * sn[2]
                        ) else fn
                        val l = sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]).coerceAtLeast(1e-12f)
                        nrm.add(nn[0] / l); nrm.add(nn[1] / l); nrm.add(nn[2] / l)
                        val tv = t?.get(tri[k + q])
                        uv.add(tv?.get(0) ?: 0f); uv.add(tv?.get(1) ?: 0f)
                    }
                    matOf.add(mat); partOf.add(part)
                    k += 3
                }
                partBoxes += box
            }
        }

        fun walk(index: Int, parent: FloatArray, depth: Int) {
            if (depth > 64) return
            val n = nodes.getOrNull(index)?.jsonObject ?: return
            val m = mul(parent, local(n))
            n.int("mesh")?.let { addMesh(it, m, n.str("name").orEmpty()) }
            n["children"]?.jsonArray?.forEach { walk(it.jsonPrimitive.int, m, depth + 1) }
        }

        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val scenes = j["scenes"]?.jsonArray.orEmpty()
        val roots = if (scenes.isNotEmpty()) {
            scenes[(j.int("scene") ?: 0).coerceIn(0, scenes.size - 1)].jsonObject["nodes"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
        } else {
            val children = nodes.flatMap { n -> n.jsonObject["children"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty() }.toSet()
            nodes.indices.filter { it !in children }
        }
        roots.forEach { walk(it, identity, 0) }

        // A ground plane, a backdrop: a part flat in height and as wide as everything else together.
        val count = matOf.size
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (b in partBoxes) for (d in 0..2) { lo[d] = min(lo[d], b[d]); hi[d] = max(hi[d], b[d + 3]) }
        val flat = partBoxes.map { b ->
            val tall = b[4] - b[1]
            val across = max(b[3] - b[0], b[5] - b[2])
            val all = max(hi[0] - lo[0], hi[2] - lo[2])
            tall < across * 0.01f && across > all * 0.9f && partBoxes.size > 1
        }
        val keep = BooleanArray(count) { !flat[partOf[it]] }
        return RawTris(count, pos.toArray(), nrm.toArray(), uv.toArray(), matOf.toArray(), partOf.toArray(), keep, mats, parts)
    }

    private val JsonPrimitive.int: Int get() = content.toDouble().toInt()

    private class FloatList {
        private var a = FloatArray(1 shl 16)
        var size = 0
        fun add(v: Float) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }

    private class IntList {
        private var a = IntArray(1 shl 14)
        var size = 0
        fun add(v: Int) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        operator fun get(i: Int) = a[i]
        fun toArray() = a.copyOf(size)
    }
}
