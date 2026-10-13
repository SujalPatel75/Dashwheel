package com.openauto.dash.companion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openauto.dash.carphoto.Argb
import com.openauto.dash.carphoto.CarModel
import com.openauto.dash.carphoto.CarModelKit
import com.openauto.dash.carphoto.CarModelResult
import com.openauto.dash.carphoto.CarPhotoKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * EXPERIMENTAL: the driver's car from a 3D model (.glb). The phone draws every
 * view the car's screen uses from it (side, from above, three-quarter, each
 * end) with where the wheels and lamps are ([CarModelKit]), and sends the model
 * too, for the car to show it live in 3D. Only the doors come from a picture:
 * the view from above redrawn with them open (the Gemini app does it), whose
 * open parts are laid on the model's own view from above.
 */

/** A model as drawn, ready to look at and send. */
private class Baked(
    val model: CarModel,
    val result: CarModelResult,
    val side: Bitmap,
    val top: Bitmap,
    val hero: Bitmap,
    val doors: Bitmap?
)

/** A model file bigger than this is not read (a car for a phone screen needs far less). */
private const val MAX_MODEL_BYTES = 64 * 1024 * 1024

@Composable
internal fun CarFromModelScreen(connected: Boolean, onClose: () -> Unit, newModel: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baked by remember { mutableStateOf<Baked?>(null) }
    var opened by remember { mutableStateOf<Shot?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Which way round the model is read; saved with the model file across a restart of the screen.
    var turned by rememberSaveable { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val status by CarLookSender.status.collectAsState()

    /** Reads and draws the kept model; [autoTurn]: the nose's way from the model's shape. */
    fun bake(autoTurn: Boolean) {
        val file = modelFile(context).takeIf { it.exists() } ?: return
        busy = true
        val asTurned = turned
        val doors = opened?.photo
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = file.readBytes()
                    var way = asTurned
                    var model = CarModelKit.read(bytes, ::decodeTexture, way)
                    if (autoTurn && CarModelKit.noseLooksBackwards(model)) {
                        way = !way
                        model = CarModelKit.read(bytes, ::decodeTexture, way)
                    }
                    draw(model, doors) to way
                }
            }
            busy = false
            outcome.onSuccess { (car, way) -> baked = car; turned = way }.onFailure {
                baked = null
                Toast.makeText(context, R.string.car_model_unreadable, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Back after a restart of the screen: the model and the doors picture already picked.
    LaunchedEffect(Unit) {
        opened = withContext(Dispatchers.IO) { runCatching { openedFile(context).takeIf { it.exists() }?.let(::decode) }.getOrNull() }
        // A model just chosen on the main screen: its nose found as for one picked here.
        bake(autoTurn = newModel)
    }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { keepModel(context, uri) }.getOrDefault(false) }
            busy = false
            if (!ok) Toast.makeText(context, R.string.car_model_unreadable, Toast.LENGTH_LONG).show()
            else {
                turned = false
                bake(autoTurn = true)
            }
        }
    }

    /** The doors picture changed: only its parts are found again, the views stay. */
    fun takeOpened(uri: Uri) {
        busy = true
        scope.launch {
            val shot = withContext(Dispatchers.IO) { runCatching { keepOpened(context, uri) }.getOrNull() }
            if (shot == null) {
                busy = false
                Toast.makeText(context, R.string.car_look_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            opened = shot
            val current = baked
            if (current != null) baked = withContext(Dispatchers.Default) { withDoors(current, shot.photo) }
            busy = false
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && asking) takeOpened(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && asking) takeOpened(cameraUri(context, CarShot.OPENED))
    }
    val cameraAllowed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && asking) camera.launch(cameraUri(context, CarShot.OPENED))
    }

    // The model's own view from above, handed to the Gemini app to redraw with the doors open.
    fun askGemini(top: Argb) {
        busy = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) { shareUri(context, CarShot.ABOVE, onWhite(top)) }
            busy = false
            val prompt = context.getString(R.string.car_photos_gemini_open_prompt)
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(prompt, prompt))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, prompt)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val started = runCatching { context.startActivity(Intent(send).setPackage(GEMINI_APP)) }.isSuccess ||
                runCatching { context.startActivity(Intent.createChooser(send, null)) }.isSuccess
            if (started) Toast.makeText(context, R.string.car_photos_prompt_copied, Toast.LENGTH_LONG).show()
        }
    }

    val close = {
        modelFile(context).delete()
        openedFile(context).delete()
        onClose()
    }
    BackHandler(onBack = close)
    Surface(color = CompanionColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.car_model_title),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.car_photos_close)) }
            }
            Text(stringResource(R.string.car_model_detail), color = CompanionColors.Muted, style = MaterialTheme.typography.bodyMedium)
            val car = baked
            if (busy && car == null) {
                Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.car_model_drawing))
                }
            }
            if (car == null) {
                Button(
                    onClick = { pickModel.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp)
                ) { Text(stringResource(R.string.car_model_pick)) }
            } else {
            CarPreview(car.side, car.result.sideWheels, Modifier.padding(top = 14.dp).fillMaxWidth().height(160.dp))
            CarPreview(car.hero, emptyList(), Modifier.padding(top = 10.dp).fillMaxWidth().height(200.dp))
            CarPreview(car.doors ?: car.top, car.result.topView.wheels.takeIf { car.doors == null }.orEmpty(), Modifier.padding(top = 10.dp).fillMaxWidth().height(260.dp))
            OutlinedButton(
                onClick = { turned = !turned; bake(autoTurn = false) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp)
            ) { Text(stringResource(R.string.car_model_turn)) }
            if (car.result.model == null) {
                Text(stringResource(R.string.car_model_no_live), color = CompanionColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }

            // The doors: the only part drawn from a picture.
            Panel(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        ShotSlot(CarShot.OPENED, opened) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.car_photos_take)) }, onClick = {
                                menu = false
                                asking = true
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    camera.launch(cameraUri(context, CarShot.OPENED))
                                } else cameraAllowed.launch(Manifest.permission.CAMERA)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.car_photos_choose)) }, onClick = {
                                menu = false
                                asking = true
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }
                    OutlinedButton(
                        onClick = { askGemini(car.result.top) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text(stringResource(R.string.car_photos_gemini_open)) }
                }
            }

            val line = when {
                status == CarLookSender.Status.SENDING -> stringResource(R.string.car_look_sending)
                status == CarLookSender.Status.SENT -> stringResource(R.string.car_look_sent)
                status == CarLookSender.Status.TOO_BIG -> stringResource(R.string.car_look_too_big)
                status == CarLookSender.Status.FAILED -> stringResource(R.string.car_look_failed)
                !connected -> stringResource(R.string.car_look_offline)
                else -> null
            }
            line?.let {
                val tint = if (status == CarLookSender.Status.SENT) CompanionColors.Teal else if (connected) CompanionColors.Muted else CompanionColors.Amber
                Text(it, color = tint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
            Button(
                onClick = {
                    scope.launch {
                        val pack = withContext(Dispatchers.Default) { packOf(car.result) }
                        CarLookSender.send(pack)
                    }
                },
                enabled = connected && !busy && status != CarLookSender.Status.SENDING,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CompanionColors.Background)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(R.string.car_photos_send))
            }
            OutlinedButton(
                onClick = { pickModel.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp)
            ) { Text(stringResource(R.string.car_model_change)) }
            }
        }
    }
}

private fun folder(context: Context) = File(context.cacheDir, "car_model").apply { mkdirs() }
private fun modelFile(context: Context) = File(folder(context), "kept_model.glb")
private fun openedFile(context: Context) = File(folder(context), "kept_opened")

/**
 * A file picked with "Choose a file" ([CarLookCard]): kept for this screen when it is a 3D
 * model (a .glb starts "glTF"), as the car can't use one as it is. Null for anything else,
 * false for a model too big to read.
 */
internal suspend fun keepIfModel(context: Context, uri: Uri): Boolean? = withContext(Dispatchers.IO) {
    val glb = runCatching {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val head = ByteArray(4)
            s.read(head) == 4 && String(head, Charsets.US_ASCII) == "glTF"
        }
    }.getOrNull() ?: false
    if (!glb) null else runCatching { keepModel(context, uri) }.getOrDefault(false)
}

/** The model at [uri] kept for this screen; false when it is too big or no glTF binary. */
private fun keepModel(context: Context, uri: Uri): Boolean {
    val incoming = File(folder(context), "incoming.glb")
    try {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            incoming.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_MODEL_BYTES) return false
                    out.write(buf, 0, n)
                }
            }
        }
        val head = incoming.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        if (String(head, Charsets.US_ASCII) != "glTF") return false
        return incoming.renameTo(modelFile(context).also { it.delete() })
    } finally {
        incoming.delete()
    }
}

/** The doors picture at [uri] kept for this screen, decoded. */
private fun keepOpened(context: Context, uri: Uri): Shot {
    val kept = openedFile(context)
    context.contentResolver.openInputStream(uri)!!.use { input -> kept.outputStream().use { input.copyTo(it) } }
    return runCatching { decode(kept) }.onFailure { kept.delete() }.getOrThrow()
}

/** A texture from the model, no longer than [CarModelKit.MAX_TEXTURE] a side. */
private fun decodeTexture(bytes: ByteArray): Argb? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CarModelKit.MAX_TEXTURE) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bitmap.toArgb().also { bitmap.recycle() }
}

private fun draw(model: CarModel, opened: Argb?): Baked {
    val result = CarModelKit.bake(model, opened = opened)
    return Baked(model, result, result.side.toBitmap(), result.top.toBitmap(), result.hero.toBitmap(), doorsOf(result))
}

/** The same views with the doors found in [opened] laid on the view from above. */
private fun withDoors(baked: Baked, opened: Argb): Baked {
    val parts = runCatching { CarPhotoKit.openParts(opened, baked.result.topView) }.getOrDefault(emptyList())
    val result = baked.result.withOpen(parts)
    return Baked(baked.model, result, baked.side, baked.top, baked.hero, doorsOf(result))
}

/** The view from above with its open parts laid on it, as the car will show them, with room for the doors. */
private fun doorsOf(result: CarModelResult): Bitmap? {
    if (result.open.isEmpty()) return null
    val top = result.top
    val l = min(0f, result.open.minOf { it.box[0] })
    val t = min(0f, result.open.minOf { it.box[1] })
    val r = max(1f, result.open.maxOf { it.box[2] })
    val b = max(1f, result.open.maxOf { it.box[3] })
    val out = Bitmap.createBitmap(((r - l) * top.w).roundToInt(), ((b - t) * top.h).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    fun place(picture: Argb, box: List<Float>) {
        val bitmap = picture.toBitmap()
        canvas.drawBitmap(bitmap, null, RectF((box[0] - l) * top.w, (box[1] - t) * top.h, (box[2] - l) * top.w, (box[3] - t) * top.h), paint)
        bitmap.recycle()
    }
    place(top, listOf(0f, 0f, 1f, 1f))
    for (p in result.open) place(p.image, p.box)
    return out
}

/** A transparent picture on plain white, as the Gemini prompt asks to keep. */
private fun onWhite(a: Argb): Argb {
    val out = Argb(a.w, a.h)
    for (i in a.px.indices) {
        val c = a.px[i]
        val al = (c ushr 24) and 255
        fun ch(s: Int) = (((c shr s) and 255) * al + 255 * (255 - al)) / 255
        out.px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    return out
}

/** The car pack the head unit imports: car.json, the pictures as PNG and the model. */
private fun packOf(result: CarModelResult): ByteArray {
    val files = LinkedHashMap<String, ByteArray>()
    files["car.json"] = result.toCarJson().toByteArray()
    for ((name, picture) in result.packEntries()) files[name] = picture.toPng()
    result.model?.let { files["model.glb"] = it }
    return CarPhotoKit.zip(files)
}
