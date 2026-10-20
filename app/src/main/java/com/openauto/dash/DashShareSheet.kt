package com.openauto.dash

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The edit bar's Share: this dashboard as a code to pass on (a QR code for
 * the phone, the code itself, a file), with its look if wanted; and a shared
 * one to put in its place, from a pasted code, a file or the phone
 * ([incoming]), or one made from the driver's words ([DashDesigner]), shown
 * first so what comes is what was seen.
 */
@Composable
internal fun DashShareSheet(
    items: List<DashboardItem>,
    name: String,
    installed: Set<String>,
    pageSize: DpSize,
    tile: @Composable (DashboardItem) -> Unit,
    incoming: String?,
    onImport: (List<DashboardItem>, SharedLook?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withLook by remember { mutableStateOf(true) }
    val look = remember { DashShare.currentLook(context) }
    val code = remember(items, withLook) {
        DashShare.encode(SharedDashboard(name, ScreenShape.vertical, items, if (withLook) look else null))
    }
    val dropped = items.count { it is DashboardItem.SystemWidget }

    var typed by remember { mutableStateOf(incoming.orEmpty()) }
    val shared = remember(typed) { DashShare.decode(typed) }
    var takeLook by remember { mutableStateOf(true) }

    // Described in words, typed or said: the result comes in as a code, like a pasted one.
    val design by DashDesigner.state.collectAsState()
    var words by remember { mutableStateOf("") }
    LaunchedEffect(design) {
        (design as? DashDesigner.State.Ready)?.let {
            typed = it.code
            DashDesigner.taken()
        }
    }
    DisposableEffect(Unit) { onDispose { DashDesigner.cancel() } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) DashDesigner.listen(context) else Toast.makeText(context, context.getString(R.string.ai_ask_mic_denied), Toast.LENGTH_SHORT).show()
    }

    val failed = stringResource(R.string.settings_backup_failed)
    val saved = stringResource(R.string.settings_backup_saved)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write("$code\n".toByteArray()) } != null }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                // A share file is one line; a big file is not one.
                runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readNBytesCompat(64 * 1024)) } }.getOrNull()
            }
            if (text != null && DashShare.decode(text) != null) typed = text else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
        }
    }

    SettingsSheet(
        title = stringResource(R.string.share_title, name),
        onDismiss = onDismiss,
        actions = { SheetButton(stringResource(R.string.ai_done), onClick = onDismiss) }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Passing this one on.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(stringResource(R.string.share_this))
                SettingsToggle(Icons.Filled.Palette, stringResource(R.string.share_with_look), stringResource(R.string.share_with_look_detail), withLook) { withLook = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(code, Modifier.size(200.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.share_scan), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text(code, color = DashColors.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 4)
                        TextButton(onClick = { runCatching { save.launch("dashwheel-${fileName(name)}.txt") }.onFailure { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() } }) {
                            Icon(Icons.Filled.Save, contentDescription = null, tint = DashColors.AccentInk)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_save_file), color = DashColors.AccentInk)
                        }
                    }
                }
                if (dropped > 0) Text(stringResource(R.string.share_widgets_left_out), color = DashColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(20.dp))
            // Putting a shared one here.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(stringResource(R.string.design_title))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = words,
                        onValueChange = { words = it },
                        placeholder = { Text(stringResource(R.string.design_hint), color = DashColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors()
                    )
                    IconButton(onClick = { mic.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Icon(
                            Icons.Filled.Mic, contentDescription = stringResource(R.string.design_say),
                            tint = if (design == DashDesigner.State.Listening) DashColors.Critical else DashColors.AccentInk
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val busy = design == DashDesigner.State.Thinking || design == DashDesigner.State.Listening
                    TextButton(onClick = { DashDesigner.fromText(context, words) }, enabled = words.isNotBlank() && !busy) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = DashColors.AccentInk)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.design_make), color = DashColors.AccentInk)
                    }
                    if (design == DashDesigner.State.Thinking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DashColors.AccentInk)
                }
                val line = when (val d = design) {
                    DashDesigner.State.Listening -> stringResource(R.string.design_listening)
                    DashDesigner.State.Thinking -> stringResource(R.string.design_thinking)
                    is DashDesigner.State.Failed -> d.reason
                    else -> null
                }
                line?.let {
                    Text(it, color = if (design is DashDesigner.State.Failed) DashColors.Warning else DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Label(stringResource(R.string.share_use))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(stringResource(R.string.share_paste_hint), color = DashColors.Muted) },
                    singleLine = true,
                    isError = typed.isNotBlank() && shared == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                TextButton(onClick = { runCatching { open.launch(arrayOf("text/*", "application/octet-stream")) }.onFailure { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() } }) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, tint = DashColors.AccentInk)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_open_file), color = DashColors.AccentInk)
                }
                if (shared == null) {
                    Text(stringResource(R.string.share_use_hint), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    val here = remember(shared) { DashShare.itemsFor(shared, ScreenShape.vertical) }
                    PagePreview(items = here, pageSize = pageSize, width = 280.dp, tile = tile)
                    shared.name?.let { Text(it, color = DashColors.TextPrimary, style = MaterialTheme.typography.titleSmall) }
                    val missing = remember(shared, installed) { DashShare.missingApps(here, installed) }
                    if (missing.isNotEmpty()) {
                        Text(stringResource(R.string.share_missing_apps, missing.size), color = DashColors.Warning, style = MaterialTheme.typography.bodySmall)
                    }
                    if (shared.look != null) {
                        SettingsToggle(
                            Icons.Filled.Palette, stringResource(R.string.share_take_look),
                            stringResource(shared.look.mode.titleRes), takeLook
                        ) { takeLook = it }
                    }
                    SheetButton(stringResource(R.string.share_replace)) {
                        onImport(here, shared.look?.takeIf { takeLook })
                    }
                }
            }
        }
    }
}

/** A name a file can carry. */
private fun fileName(name: String): String =
    name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').ifEmpty { "dashboard" }.take(30)

/** At most [max] bytes of the stream (readNBytes is Java 11+, not on Android 10). */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
