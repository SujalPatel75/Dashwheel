package com.openauto.dash.carphoto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The car for the launcher's live 3D view (Car3D.kt) made from any model: a
 * fresh glTF binary, nose +x, y up, the car's right +z, whose lamps and wheels
 * are nodes of their own under the names the launcher looks for (Lamp_tail_l,
 * Lamp_head_r, Wheel_fl...), so the faulty one can light up; and no heavier
 * than [MAX_TRIANGLES], nearby points merged where the model has more.
 *
 * Lamps are found by their material: one whose faces nearly all sit where a
 * car's lamps are (a back or front corner, at lamp height) and that looks like
 * one (red at the back; glassy, shiny or light at the front). On a model with
 * one material for everything, red faces in the rear corners do for the tail
 * lamps, unless the car itself is red. Pure Kotlin, so the phone runs it and
 * the JVM tests run it too.
 */
internal object CarLiveModel {
    /** About twice what the head unit was measured turning smoothly (67k triangles at 30 images a second). */
    const val MAX_TRIANGLES = 120_000

    private val NAMES = listOf("Body", "Lamp_tail_l", "Lamp_tail_r", "Lamp_head_l", "Lamp_head_r", "Wheel_fl", "Wheel_fr", "Wheel_rl", "Wheel_rr")
    private const val BODY = 0
    private const val TAIL_L = 1
    private const val TAIL_R = 2
    private const val HEAD_L = 3
    private const val HEAD_R = 4
    private const val WHEEL = 5

    private val TAIL_NAME = Regex("tail.?(light|lamp|clear|reflector)|rear.?(lamp|light)|stop.?lamp|brake.?light|feu.?arr|r[uü]ckl")
    private val HEAD_NAME = Regex("head.?(light|lamp|lens)|phare|scheinwerfer")

    /** [model] as the launcher's live copy; [wheels]: centres front left, front right, rear left, rear right (car space). */
    fun build(model: CarModel, wheels: List<FloatArray>, maxTriangles: Int = MAX_TRIANGLES, maxBytes: Int = CarModelKit.MAX_LIVE_BYTES): ByteArray? {
        val group = groups(model, wheels)
        val mesh = simplify(model.tris, group, maxTriangles)
        // Textures as they come, then halved until the file fits.
        var scale = 1
        while (scale <= 8) {
            val glb = write(model, mesh, scale)
            if (glb.size <= maxBytes) return glb
            scale *= 2
        }
        return null
    }

    // ---- what each triangle is ----

    /** Each triangle's group: [BODY], a lamp, or a wheel ([WHEEL] + corner). */
    internal fun groups(model: CarModel, wheels: List<FloatArray>): IntArray {
        val t = model.tris
        val l = model.length; val w = model.width; val h = model.height
        val group = IntArray(t.count)
        val nMats = model.mats.size

        // Each material's area, its area where a lamp would be, and its colour.
        val area = FloatArray(nMats + 1)
        val inTail = FloatArray(nMats + 1)
        val inHead = FloatArray(nMats + 1)
        fun slot(m: Int) = if (m in 0 until nMats) m else nMats
        val cx = FloatArray(t.count); val cy = FloatArray(t.count); val cz = FloatArray(t.count); val a = FloatArray(t.count)
        for (i in 0 until t.count) {
            val o = i * 9; val p = t.pos
            cx[i] = (p[o] + p[o + 3] + p[o + 6]) / 3; cy[i] = (p[o + 1] + p[o + 4] + p[o + 7]) / 3; cz[i] = (p[o + 2] + p[o + 5] + p[o + 8]) / 3
            a[i] = triArea(p, o)
            val s = slot(t.mat[i])
            area[s] += a[i]
            if (tailZone(cx[i], cy[i], cz[i], l, w, h)) inTail[s] += a[i]
            if (headZone(cx[i], cy[i], cz[i], l, w, h)) inHead[s] += a[i]
        }
        val partTail = model.parts.map { TAIL_NAME.containsMatchIn(it.lowercase()) }
        val partHead = model.parts.map { HEAD_NAME.containsMatchIn(it.lowercase()) }
        val tailMat = BooleanArray(nMats + 1)
        val headMat = BooleanArray(nMats + 1)
        for (m in 0 until nMats) {
            val mat = model.mats[m]
            val c = colourOf(mat)
            val name = mat.name.lowercase()
            val mostlyTail = area[m] > 0 && inTail[m] / area[m] > 0.7f
            val mostlyHead = area[m] > 0 && inHead[m] / area[m] > 0.7f
            tailMat[m] = TAIL_NAME.containsMatchIn(name) || (mostlyTail && isRed(c))
            // Nearly all in the front lamp corners and neither red nor dark: lens, reflector or bulb.
            headMat[m] = HEAD_NAME.containsMatchIn(name) || (mostlyHead && !isRed(c) && luminance(c) > 0.15f)
        }
        // One material for (nearly) everything: red faces in the rear corners, unless the car is red.
        val main = (0 until nMats).maxByOrNull { area[it] }
        val oneMaterial = main != null && area[main] > area.sum() * 0.8f
        val redCar = oneMaterial && main != null && model.mats[main].texture != null && isRed(textureAverage(model.mats[main]))

        val radius = max(wheels.map { it[2] }.average().toFloat(), h * 0.17f) * 1.08f
        for (i in 0 until t.count) {
            val m = slot(t.mat[i])
            val left = cy[i] > 0
            val tail = tailMat[m] || (t.part[i] < partTail.size && partTail[t.part[i]]) ||
                (oneMaterial && !redCar && tailZone(cx[i], cy[i], cz[i], l, w, h) && isRed(colourAt(model, i)))
            val head = headMat[m] || (t.part[i] < partHead.size && partHead[t.part[i]])
            group[i] = when {
                tail && cx[i] < 0 -> if (left) TAIL_L else TAIL_R
                head && cx[i] > 0 -> if (left) HEAD_L else HEAD_R
                else -> {
                    // A wheel: every corner within the tyre seen from the side, and out at the side
                    // (not the arch around it).
                    var g = BODY
                    for ((k, c) in wheels.withIndex()) {
                        if (abs(cy[i] - c[1]) >= w * 0.16f || abs(cy[i]) <= w * 0.25f) continue
                        var inside = true
                        for (v in 0..2) {
                            val dx = t.pos[i * 9 + v * 3] - c[0]; val dz = t.pos[i * 9 + v * 3 + 2] - c[2]
                            if (dx * dx + dz * dz > radius * radius) { inside = false; break }
                        }
                        if (inside) { g = WHEEL + k; break }
                    }
                    g
                }
            }
        }
        return group
    }

    private fun tailZone(x: Float, y: Float, z: Float, l: Float, w: Float, h: Float) =
        x < -l / 2 + l * 0.13f && z in h * 0.35f..h * 0.88f && abs(y) > w * 0.15f

    private fun headZone(x: Float, y: Float, z: Float, l: Float, w: Float, h: Float) =
        x > l / 2 - l * 0.12f && z in h * 0.28f..h * 0.72f && abs(y) > w * 0.15f

    /** Red, dark lamp reds included (linear colour). */
    private fun isRed(c: FloatArray) = c[0] > 0.08f && c[0] > c[1] * 2.2f && c[0] > c[2] * 1.8f

    private fun luminance(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]

    /**
     * Which end the red lamps are at: true when they are at the nose end (the
     * model was read the wrong way round), false at the tail, null when red
     * doesn't tell (no red lamps, or a red car).
     */
    fun redLampsAtNose(model: CarModel): Boolean? {
        val t = model.tris
        val l = model.length; val w = model.width; val h = model.height
        var front = 0f; var back = 0f
        for (i in 0 until t.count) {
            val o = i * 9; val p = t.pos
            val x = (p[o] + p[o + 3] + p[o + 6]) / 3; val y = (p[o + 1] + p[o + 4] + p[o + 7]) / 3; val z = (p[o + 2] + p[o + 5] + p[o + 8]) / 3
            if (z !in h * 0.35f..h * 0.88f || abs(y) <= w * 0.15f) continue
            val atFront = x > l / 2 - l * 0.13f
            val atBack = x < -l / 2 + l * 0.13f
            if (!atFront && !atBack) continue
            if (!isRed(colourAt(model, i))) continue
            val a = triArea(p, o)
            if (atFront) front += a else back += a
        }
        // A red car is red at both ends: only a clear difference counts.
        return when {
            front > 0f && front > back * 4f -> true
            back > 0f && back > front * 4f -> false
            else -> null
        }
    }

    /** A material's colour (linear): its factor times its texture's average, plus its glow. */
    private fun colourOf(m: ModelMat): FloatArray {
        val c = textureAverage(m)
        return floatArrayOf(
            (c[0] + m.emissive[0]).coerceAtMost(1f),
            (c[1] + m.emissive[1]).coerceAtMost(1f),
            (c[2] + m.emissive[2]).coerceAtMost(1f)
        )
    }

    private fun textureAverage(m: ModelMat): FloatArray {
        val tex = m.texture ?: return floatArrayOf(m.base[0], m.base[1], m.base[2])
        var r = 0f; var g = 0f; var b = 0f
        val step = max(1, tex.px.size / 4096)
        var n = 0
        for (i in tex.px.indices step step) {
            val c = tex.px[i]
            r += srgb((c shr 16) and 255); g += srgb((c shr 8) and 255); b += srgb(c and 255); n++
        }
        return floatArrayOf(m.base[0] * r / n, m.base[1] * g / n, m.base[2] * b / n)
    }

    /** One triangle's colour (linear) where its middle samples the texture. */
    private fun colourAt(model: CarModel, i: Int): FloatArray {
        val m = model.mats.getOrNull(model.tris.mat[i]) ?: return floatArrayOf(0.6f, 0.6f, 0.6f)
        val tex = m.texture ?: return floatArrayOf(m.base[0], m.base[1], m.base[2])
        val uv = model.tris.uv
        val u = (uv[i * 6] + uv[i * 6 + 2] + uv[i * 6 + 4]) / 3
        val v = (uv[i * 6 + 1] + uv[i * 6 + 3] + uv[i * 6 + 5]) / 3
        val x = ((u - kotlin.math.floor(u)) * tex.w).toInt().coerceIn(0, tex.w - 1)
        val y = ((v - kotlin.math.floor(v)) * tex.h).toInt().coerceIn(0, tex.h - 1)
        val c = tex.px[y * tex.w + x]
        return floatArrayOf(m.base[0] * srgb((c shr 16) and 255), m.base[1] * srgb((c shr 8) and 255), m.base[2] * srgb(c and 255))
    }

    private fun srgb(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    private fun triArea(p: FloatArray, o: Int): Float {
        val ax = p[o + 3] - p[o]; val ay = p[o + 4] - p[o + 1]; val az = p[o + 5] - p[o + 2]
        val bx = p[o + 6] - p[o]; val by = p[o + 7] - p[o + 1]; val bz = p[o + 8] - p[o + 2]
        val nx = ay * bz - az * by; val ny = az * bx - ax * bz; val nz = ax * by - ay * bx
        return sqrt(nx * nx + ny * ny + nz * nz) / 2
    }

    // ---- fewer triangles ----

    /** Vertices and triangles of one group in one material. */
    internal class Bucket(val group: Int, val mat: Int) {
        val pos = FloatList(); val nrm = FloatList(); val uv = FloatList(); val idx = IntList()
        val vertices get() = pos.size / 3
    }

    internal class Mesh(val buckets: List<Bucket>) {
        val triangles get() = buckets.sumOf { it.idx.size / 3 }
    }

    /**
     * The triangles with their corners shared: as given (same place, normal and
     * texture point) under [maxTriangles], else merged within a grid cell grown
     * until few enough triangles are left (the triangles whose corners fall in
     * fewer than three cells go). Corners only merge with their own group,
     * material and facing, so lamps, wheels and the two sides of a thin panel
     * keep their own.
     */
    internal fun simplify(t: Tris, group: IntArray, maxTriangles: Int): Mesh {
        var cell = 0f
        if (t.count > maxTriangles) {
            var lo = FloatArray(3) { Float.MAX_VALUE }; var hi = FloatArray(3) { -Float.MAX_VALUE }
            for (v in 0 until t.count * 3) for (k in 0..2) { lo[k] = min(lo[k], t.pos[v * 3 + k]); hi[k] = max(hi[k], t.pos[v * 3 + k]) }
            cell = (hi[0] - lo[0]) / 900f
            var tries = 0
            while (survivors(t, group, cell) > maxTriangles && tries < 24) { cell *= 1.25f; tries++ }
        }
        val buckets = LinkedHashMap<Long, Bucket>()
        val vertex = HashMap<Key, Int>()
        val sums = HashMap<Key, FloatArray>()
        // First pass: each corner's cell and, when merging, the running sums of what falls in it.
        val corner = arrayOfNulls<Key>(t.count * 3)
        for (i in 0 until t.count) for (c in 0..2) {
            val key = key(t, group, i, c, cell)
            corner[i * 3 + c] = key
            if (cell > 0f) {
                val s = sums.getOrPut(key) { FloatArray(9) }
                val v = i * 9 + c * 3
                for (k in 0..2) { s[k] += t.pos[v + k]; s[3 + k] += t.nrm[v + k] }
                s[6] += t.uv[i * 6 + c * 2]; s[7] += t.uv[i * 6 + c * 2 + 1]; s[8] += 1f
            }
        }
        for (i in 0 until t.count) {
            val k0 = corner[i * 3]!!; val k1 = corner[i * 3 + 1]!!; val k2 = corner[i * 3 + 2]!!
            if (cell > 0f && (k0 == k1 || k1 == k2 || k0 == k2)) continue
            val b = buckets.getOrPut(group[i].toLong() shl 32 or (t.mat[i].toLong() and 0xffffffffL)) { Bucket(group[i], t.mat[i]) }
            for ((c, k) in listOf(k0, k1, k2).withIndex()) {
                val id = vertex.getOrPut(k) {
                    val n = b.vertices
                    if (cell > 0f) {
                        val s = sums[k]!!
                        val cnt = s[8]
                        b.pos.add(s[0] / cnt); b.pos.add(s[1] / cnt); b.pos.add(s[2] / cnt)
                        val l = sqrt(s[3] * s[3] + s[4] * s[4] + s[5] * s[5]).coerceAtLeast(1e-6f)
                        b.nrm.add(s[3] / l); b.nrm.add(s[4] / l); b.nrm.add(s[5] / l)
                        b.uv.add(s[6] / cnt); b.uv.add(s[7] / cnt)
                    } else {
                        val v = i * 9 + c * 3
                        for (q in 0..2) b.pos.add(t.pos[v + q])
                        for (q in 0..2) b.nrm.add(t.nrm[v + q])
                        b.uv.add(t.uv[i * 6 + c * 2]); b.uv.add(t.uv[i * 6 + c * 2 + 1])
                    }
                    n
                }
                b.idx.add(id)
            }
        }
        return Mesh(buckets.values.toList())
    }

    /** How many triangles a [cell] grid leaves. */
    private fun survivors(t: Tris, group: IntArray, cell: Float): Int {
        var n = 0
        for (i in 0 until t.count) {
            val a = key(t, group, i, 0, cell); val b = key(t, group, i, 1, cell); val c = key(t, group, i, 2, cell)
            if (a != b && b != c && a != c) n++
        }
        return n
    }

    internal data class Key(val g: Int, val m: Int, val x: Int, val y: Int, val z: Int, val n: Int, val u: Int, val v: Int)

    /** A corner's place: exact (a tenth of a millimetre, its normal and texture point) when [cell] is 0, else its grid cell and facing. */
    private fun key(t: Tris, group: IntArray, i: Int, c: Int, cell: Float): Key {
        val v = i * 9 + c * 3
        val nx = t.nrm[v]; val ny = t.nrm[v + 1]; val nz = t.nrm[v + 2]
        if (cell <= 0f) {
            fun q(f: Float, s: Float) = Math.round(f * s)
            val n = (q(nx, 64f) and 0x3ff) or ((q(ny, 64f) and 0x3ff) shl 10) or ((q(nz, 64f) and 0x3ff) shl 20)
            return Key(group[i], t.mat[i], q(t.pos[v], 10000f), q(t.pos[v + 1], 10000f), q(t.pos[v + 2], 10000f), n,
                q(t.uv[i * 6 + c * 2], 8192f), q(t.uv[i * 6 + c * 2 + 1], 8192f))
        }
        // Facing: which of the six directions the normal is nearest.
        val ax = abs(nx); val ay = abs(ny); val az = abs(nz)
        val facing = when {
            ax >= ay && ax >= az -> if (nx >= 0) 0 else 1
            ay >= az -> if (ny >= 0) 2 else 3
            else -> if (nz >= 0) 4 else 5
        }
        return Key(group[i], t.mat[i], kotlin.math.floor(t.pos[v] / cell).toInt(), kotlin.math.floor(t.pos[v + 1] / cell).toInt(),
            kotlin.math.floor(t.pos[v + 2] / cell).toInt(), facing, 0, 0)
    }

    // ---- the file ----

    /** [mesh] as a glTF binary: a node per group, a material per group and material, textures shrunk by [scale]. */
    private fun write(model: CarModel, mesh: Mesh, scale: Int): ByteArray {
        val bin = ByteArrayOutputStream()
        val views = ArrayList<JsonObject>()
        val accessors = ArrayList<JsonObject>()
        fun view(bytes: ByteArray, target: Int?): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            val off = bin.size()
            bin.write(bytes)
            views += buildJsonObject {
                put("buffer", 0); put("byteOffset", off); put("byteLength", bytes.size)
                if (target != null) put("target", target)
            }
            return views.size - 1
        }
        fun floats(values: FloatArray, comps: Int, type: String, minMax: Boolean): Int {
            val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in values) bb.putFloat(f)
            val v = view(bb.array(), 34962)
            accessors += buildJsonObject {
                put("bufferView", v); put("componentType", 5126); put("count", values.size / comps); put("type", type)
                if (minMax) {
                    val lo = FloatArray(comps) { Float.MAX_VALUE }; val hi = FloatArray(comps) { -Float.MAX_VALUE }
                    for (i in values.indices) { lo[i % comps] = min(lo[i % comps], values[i]); hi[i % comps] = max(hi[i % comps], values[i]) }
                    put("min", JsonArray(lo.map { JsonPrimitive(it) })); put("max", JsonArray(hi.map { JsonPrimitive(it) }))
                }
            }
            return accessors.size - 1
        }
        fun indices(values: IntArray, vertices: Int): Int {
            val short = vertices < 65536
            val bb = ByteBuffer.allocate(values.size * (if (short) 2 else 4)).order(ByteOrder.LITTLE_ENDIAN)
            for (i in values) if (short) bb.putShort(i.toShort()) else bb.putInt(i)
            val v = view(bb.array(), 34963)
            accessors += buildJsonObject {
                put("bufferView", v); put("componentType", if (short) 5123 else 5125); put("count", values.size); put("type", "SCALAR")
            }
            return accessors.size - 1
        }

        // Images: one per textured material, shared by the groups that use it.
        val images = ArrayList<JsonObject>()
        val textureOf = HashMap<Int, Int>()
        fun texture(m: Int): Int? {
            val tex = model.mats.getOrNull(m)?.texture ?: return null
            return textureOf.getOrPut(m) {
                val v = view(png(shrink(tex, scale)), null)
                images += buildJsonObject { put("bufferView", v); put("mimeType", "image/png") }
                images.size - 1
            }
        }
        val materials = ArrayList<JsonObject>()
        val meshes = ArrayList<JsonObject>()
        val nodes = ArrayList<JsonObject>()
        val roots = ArrayList<Int>()
        for (g in NAMES.indices) {
            val parts = mesh.buckets.filter { it.group == g && it.idx.size > 0 }
            if (parts.isEmpty()) continue
            val prims = buildJsonArray {
                for (b in parts) {
                    // Car space (x nose, y left, z up) to glTF (x nose, y up, z right).
                    val p = b.pos.toArray(); val n = b.nrm.toArray()
                    val gp = FloatArray(p.size); val gn = FloatArray(n.size)
                    for (i in 0 until p.size / 3) {
                        gp[i * 3] = p[i * 3]; gp[i * 3 + 1] = p[i * 3 + 2]; gp[i * 3 + 2] = -p[i * 3 + 1]
                        gn[i * 3] = n[i * 3]; gn[i * 3 + 1] = n[i * 3 + 2]; gn[i * 3 + 2] = -n[i * 3 + 1]
                    }
                    val tex = texture(b.mat)
                    val pa = floats(gp, 3, "VEC3", true)
                    val na = floats(gn, 3, "VEC3", false)
                    val ua = if (tex != null) floats(b.uv.toArray(), 2, "VEC2", false) else null
                    val ia = indices(b.idx.toArray(), b.vertices)
                    val mat = model.mats.getOrNull(b.mat)
                    materials += buildJsonObject {
                        put("name", "${NAMES[g]}_${mat?.name ?: "plain"}")
                        put("pbrMetallicRoughness", buildJsonObject {
                            val base = mat?.base ?: floatArrayOf(0.6f, 0.6f, 0.62f, 1f)
                            put("baseColorFactor", JsonArray(base.take(4).map { JsonPrimitive(it) }))
                            put("metallicFactor", mat?.metallic ?: 0f)
                            put("roughnessFactor", mat?.roughness ?: 0.6f)
                            if (tex != null) put("baseColorTexture", buildJsonObject { put("index", tex) })
                        })
                        mat?.emissive?.takeIf { e -> e.any { it > 0f } }?.let { e -> put("emissiveFactor", JsonArray(e.take(3).map { JsonPrimitive(it.coerceIn(0f, 1f)) })) }
                        when (mat?.alphaMode) {
                            2 -> put("alphaMode", "BLEND")
                            1 -> { put("alphaMode", "MASK"); put("alphaCutoff", mat.cutoff) }
                            else -> Unit
                        }
                        put("doubleSided", true)
                    }
                    add(buildJsonObject {
                        put("attributes", buildJsonObject {
                            put("POSITION", pa); put("NORMAL", na)
                            if (ua != null) put("TEXCOORD_0", ua)
                        })
                        put("indices", ia)
                        put("material", materials.size - 1)
                    })
                }
            }
            meshes += buildJsonObject { put("name", NAMES[g]); put("primitives", prims) }
            nodes += buildJsonObject { put("name", NAMES[g]); put("mesh", meshes.size - 1) }
            roots += nodes.size - 1
        }
        nodes += buildJsonObject { put("name", "Car"); put("children", JsonArray(roots.map { JsonPrimitive(it) })) }
        val doc = buildJsonObject {
            put("asset", buildJsonObject { put("version", "2.0"); put("generator", "Dashwheel CarLiveModel") })
            put("scene", 0)
            put("scenes", buildJsonArray { add(buildJsonObject { put("nodes", buildJsonArray { add(JsonPrimitive(nodes.size - 1)) }) }) })
            put("nodes", JsonArray(nodes)); put("meshes", JsonArray(meshes)); put("materials", JsonArray(materials))
            put("accessors", JsonArray(accessors)); put("bufferViews", JsonArray(views))
            if (images.isNotEmpty()) {
                put("images", JsonArray(images))
                put("samplers", buildJsonArray { add(buildJsonObject { put("magFilter", 9729); put("minFilter", 9987) }) })
                put("textures", JsonArray(images.indices.map { i -> buildJsonObject { put("source", i); put("sampler", 0) } }))
            }
            while (bin.size() % 4 != 0) bin.write(0)
            put("buffers", buildJsonArray { add(buildJsonObject { put("byteLength", bin.size()) }) })
        }
        return Glb.write(doc, bin.toByteArray())
    }

    private fun shrink(a: Argb, scale: Int): Argb {
        if (scale <= 1) return a
        val w = max(1, a.w / scale); val h = max(1, a.h / scale)
        val out = Argb(w, h)
        for (y in 0 until h) for (x in 0 until w) out.px[y * w + x] = a.px[min(a.h - 1, y * scale) * a.w + min(a.w - 1, x * scale)]
        return out
    }

    /** [a] as a PNG file (RGBA, no filter). */
    internal fun png(a: Argb): ByteArray {
        val raw = ByteArray(a.h * (1 + a.w * 4))
        var o = 0
        for (y in 0 until a.h) {
            raw[o++] = 0
            for (x in 0 until a.w) {
                val c = a.px[y * a.w + x]
                raw[o++] = (c shr 16).toByte(); raw[o++] = (c shr 8).toByte(); raw[o++] = c.toByte(); raw[o++] = (c ushr 24).toByte()
            }
        }
        val deflater = Deflater(6)
        deflater.setInput(raw); deflater.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) z.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val len = ByteBuffer.allocate(4).putInt(data.size).array()
            out.write(len)
            val td = type.toByteArray(Charsets.US_ASCII) + data
            out.write(td)
            val crc = CRC32().apply { update(td) }.value.toInt()
            out.write(ByteBuffer.allocate(4).putInt(crc).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(a.w).putInt(a.h).put(8).put(6).put(0).put(0).put(0).array())
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}

/** A growing list of floats, without boxing. */
internal class FloatList {
    private var a = FloatArray(1024)
    var size = 0; private set
    fun add(f: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = f }
    fun toArray(): FloatArray = a.copyOf(size)
}

/** A growing list of ints, without boxing. */
internal class IntList {
    private var a = IntArray(1024)
    var size = 0; private set
    fun add(i: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = i }
    fun toArray(): IntArray = a.copyOf(size)
}
