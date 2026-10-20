package com.openauto.dash.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.FileProvider
import com.openauto.dash.link.PhotoAnswer
import com.openauto.dash.link.PhotoQuestion
import com.openauto.dash.link.ServiceEntries
import com.openauto.dash.link.ServiceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/*
 * "What's this light?" and the service booklet, from a photo: taken here,
 * sent to the car, which asks its Gemini ([PhotoQuestion]) and answers. A
 * light comes back in plain words with what to do; a booklet page as the
 * servicing lines read off it, ticked here before they go to the car's
 * planner. The photo is shrunk, sent, and deleted from the phone.
 */

/** The question in flight and its answer. */
object PhotoQuestions {
    /** Gemini has a minute on the car; the photo takes a moment to get there. */
    private const val ANSWER_MS = 80_000L
    /** The long side the photo is shrunk to: a lamp or a stamp stays readable. */
    private const val SIDE = 1600

    sealed interface Status {
        data object Idle : Status
        data object Asking : Status
        data class Answered(val answer: PhotoAnswer) : Status
        /** The car didn't answer: a Dashwheel too old to know photos, or the link dropped. */
        data object NoAnswer : Status
        data object Unreadable : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val ids = AtomicLong(System.currentTimeMillis())
    @Volatile private var awaiting: Long? = null
    private val main = Handler(Looper.getMainLooper())
    private val noAnswer = Runnable {
        if (awaiting != null) {
            awaiting = null
            _status.value = Status.NoAnswer
        }
    }

    /** Sends the photo at [uri] to be read as [kind]; the photo file is deleted after. */
    suspend fun ask(context: Context, kind: String, uri: Uri) {
        if (LinkServer.state.value !is LinkState.Connected) return
        _status.value = Status.Asking
        val jpeg = withContext(Dispatchers.IO) { runCatching { shrink(context, uri) }.getOrNull().also { photoFile(context).delete() } }
        if (jpeg == null) {
            _status.value = Status.Unreadable
            return
        }
        val id = ids.incrementAndGet()
        awaiting = id
        LinkServer.send(PhotoQuestion(id, kind, Base64.getEncoder().encodeToString(jpeg)))
        main.removeCallbacks(noAnswer)
        main.postDelayed(noAnswer, ANSWER_MS)
    }

    fun answered(answer: PhotoAnswer) {
        main.post {
            if (awaiting != answer.id) return@post
            awaiting = null
            main.removeCallbacks(noAnswer)
            _status.value = Status.Answered(answer)
        }
    }

    fun clear() {
        awaiting = null
        main.removeCallbacks(noAnswer)
        _status.value = Status.Idle
    }

    /** The photo as a JPEG small enough for one link message, lowering the quality until it is. */
    private fun shrink(context: Context, uri: Uri): ByteArray? {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > SIDE) {
                val scale = SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        try {
            for (quality in listOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= PhotoQuestion.MAX_BYTES) return out.toByteArray()
            }
            return null
        } finally {
            bitmap.recycle()
        }
    }
}

@Composable
internal fun PhotoAskCard(connected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by PhotoQuestions.status.collectAsState()
    var kind by rememberSaveable { mutableStateOf(PhotoQuestion.LIGHT) }

    val read: (Uri) -> Unit = { uri -> scope.launch { PhotoQuestions.ask(context, kind, uri) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) read(photoUri(context))
    }
    val cameraAllowed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) camera.launch(photoUri(context))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri)
    }
    val openCamera = {
        // The QR scanner declares the camera, so the camera app may only be used once it is allowed.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(photoUri(context))
        } else {
            cameraAllowed.launch(Manifest.permission.CAMERA)
        }
    }

    val current = status
    val line = when {
        current == PhotoQuestions.Status.Asking -> stringResource(R.string.photo_asking)
        current == PhotoQuestions.Status.NoAnswer -> stringResource(R.string.photo_no_answer)
        current == PhotoQuestions.Status.Unreadable -> stringResource(R.string.photo_unreadable)
        current is PhotoQuestions.Status.Answered && !current.answer.ok -> current.answer.error
        !connected -> stringResource(R.string.photo_offline)
        else -> stringResource(R.string.photo_detail)
    }
    val tint = when {
        current is PhotoQuestions.Status.Answered && current.answer.ok -> CompanionColors.Teal
        current == PhotoQuestions.Status.NoAnswer || current == PhotoQuestions.Status.Unreadable ||
            (current is PhotoQuestions.Status.Answered && !current.answer.ok) -> CompanionColors.Amber
        else -> CompanionColors.Blue
    }
    Panel {
        Column(Modifier.padding(18.dp)) {
            CardHeading(Icons.Filled.PhotoCamera, stringResource(R.string.photo_title), line, tint)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == PhotoQuestion.LIGHT, onClick = { kind = PhotoQuestion.LIGHT }, label = { Text(stringResource(R.string.photo_kind_light)) })
                FilterChip(selected = kind == PhotoQuestion.BOOKLET, onClick = { kind = PhotoQuestion.BOOKLET }, label = { Text(stringResource(R.string.photo_kind_booklet)) })
            }
            val asking = current == PhotoQuestions.Status.Asking
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = openCamera, enabled = connected && !asking) { Text(stringResource(R.string.photo_take)) }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = connected && !asking
                ) { Text(stringResource(R.string.photo_choose)) }
                if (asking) {
                    Spacer(Modifier.width(4.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CompanionColors.Blue)
                }
            }
            if (current is PhotoQuestions.Status.Answered && current.answer.ok) {
                if (current.answer.kind == PhotoQuestion.BOOKLET) BookletLines(current.answer.services, connected)
                else LightAnswer(current.answer)
            }
            Text(
                stringResource(R.string.photo_privacy),
                color = CompanionColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** What the light is, how serious, and what to do. */
@Composable
private fun LightAnswer(a: PhotoAnswer) {
    val (word, color) = when (a.severity) {
        PhotoAnswer.SEVERITY_STOP -> stringResource(R.string.photo_severity_stop) to CompanionColors.Red
        PhotoAnswer.SEVERITY_OK -> stringResource(R.string.photo_severity_ok) to CompanionColors.Teal
        else -> stringResource(R.string.photo_severity_soon) to CompanionColors.Amber
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(word, color = color, fontWeight = FontWeight.SemiBold)
        if (a.text.isNotBlank()) Text(a.text, style = MaterialTheme.typography.bodyMedium)
        a.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The booklet's lines, each ticked to go to the car's servicing planner. */
@Composable
private fun BookletLines(services: List<ServiceEntry>, connected: Boolean) {
    var ticked by remember(services) { mutableStateOf(services.indices.toSet()) }
    var sent by remember(services) { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp)) {
        services.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i in ticked, onCheckedChange = { on -> ticked = if (on) ticked + i else ticked - i; sent = false })
                Column {
                    Text(s.label.ifBlank { s.kind }, fontWeight = FontWeight.SemiBold)
                    serviceLine(s)?.let { Text(it, color = CompanionColors.Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Button(
            onClick = {
                LinkServer.send(ServiceEntries(services.filterIndexed { i, _ -> i in ticked }))
                sent = true
            },
            enabled = connected && ticked.isNotEmpty() && !sent,
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(stringResource(if (sent) R.string.photo_booklet_sent else R.string.photo_booklet_send)) }
    }
}

/** "Every 20000 km or 24 months · last done 2025-03-14 at 182000 km". */
@Composable
private fun serviceLine(s: ServiceEntry): String? {
    val every = when {
        s.everyKm != null && s.everyMonths != null -> stringResource(R.string.photo_every_km_months, s.everyKm!!, s.everyMonths!!)
        s.everyKm != null -> stringResource(R.string.photo_every_km, s.everyKm!!)
        s.everyMonths != null -> stringResource(R.string.photo_every_months, s.everyMonths!!)
        else -> null
    }
    val done = when {
        s.doneDate != null && s.doneKm != null -> stringResource(R.string.photo_done_date_km, s.doneDate!!, s.doneKm!!)
        s.doneDate != null -> stringResource(R.string.photo_done_date, s.doneDate!!)
        s.doneKm != null -> stringResource(R.string.photo_done_km, s.doneKm!!)
        else -> null
    }
    return listOfNotNull(every, done).joinToString("  ·  ").ifEmpty { null }
}

/** Where the camera app puts the photo; deleted once sent. */
private fun photoFile(context: Context): File = File(File(context.cacheDir, "photo-question").apply { mkdirs() }, "photo.jpg")

private fun photoUri(context: Context): Uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", photoFile(context))
