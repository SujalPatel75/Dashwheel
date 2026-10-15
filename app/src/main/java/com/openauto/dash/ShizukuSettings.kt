package com.openauto.dash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/*
 * Shizuku in the settings (System → More tools): the shell without root, for a
 * unit or a phone with neither Magisk nor the K706's internal ADB. One row says
 * where it stands (ShizukuShell.state) and a tap takes the one step left: get
 * the app, start it, allow Dashwheel in it. GitHub edition only, and not while
 * root answers, which makes it moot.
 */

/**
 * Whether the row has a place on this unit: not in the Play edition, not while
 * root answers (Shizuku is moot then), and not on a unit with its own ADB
 * unless Shizuku is already in use there.
 */
internal fun shizukuOffered(access: PrivilegedShell.Access, state: ShizukuState): Boolean = when {
    Edition.play -> false
    access.root -> false
    access == PrivilegedShell.Access.ADB -> state == ShizukuState.READY
    else -> true
}

/** The row, where [shizukuOffered]. */
@Composable
internal fun ShizukuRow() {
    val access = shellAccess()
    val state by ShizukuShell.state.collectAsState()
    if (!shizukuOffered(access, state)) return
    var open by remember { mutableStateOf(false) }
    SettingsRow(Icons.Filled.Terminal, stringResource(R.string.shizuku_title), stringResource(state.detailRes)) { open = true }
    if (open) ShizukuDialog { open = false }
}

/** One line on where Shizuku stands, for the row and the card. */
private val ShizukuState.detailRes: Int
    get() = when (this) {
        ShizukuState.NOT_INSTALLED -> R.string.shizuku_detail_none
        ShizukuState.NOT_RUNNING -> R.string.shizuku_detail_off
        ShizukuState.OLD_VERSION -> R.string.shizuku_detail_old
        ShizukuState.NOT_ALLOWED -> R.string.shizuku_detail_ask
        ShizukuState.READY -> R.string.shizuku_detail_on
    }

/**
 * What Shizuku brings, where it stands, and the one button that fits: Get
 * Shizuku (with its download page as a QR code for the phone), Open Shizuku
 * (to start it, or to allow Dashwheel after a refusal for good), Allow.
 */
@Composable
internal fun ShizukuDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by ShizukuShell.state.collectAsState()
    val tap = rememberTapFeedback()
    AlertDialog(
        modifier = Modifier.keepClearOfWindows(),
        onDismissRequest = onDismiss,
        containerColor = DashColors.Card,
        title = { Text(stringResource(R.string.shizuku_title), color = DashColors.TextPrimary) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.shizuku_body), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(state.detailRes),
                        color = if (state == ShizukuState.READY) DashColors.Good else DashColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (state == ShizukuState.NOT_INSTALLED || state == ShizukuState.OLD_VERSION) {
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCode(ShizukuShell.DOWNLOAD_URL, Modifier.size(120.dp).clip(DashShape.Small))
                        Text(
                            stringResource(R.string.about_scan), color = DashColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val (label, action) = when (state) {
                ShizukuState.NOT_INSTALLED, ShizukuState.OLD_VERSION -> R.string.shizuku_get to { ShizukuShell.openDownload(context) }
                ShizukuState.NOT_RUNNING, ShizukuState.READY -> R.string.shizuku_open to { ShizukuShell.openApp(context) }
                ShizukuState.NOT_ALLOWED -> R.string.shizuku_allow to { ShizukuShell.requestPermission(context) }
            }
            TextButton(onClick = { tap(); action(); onDismiss() }) { Text(stringResource(label), color = DashColors.AccentInk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dash_close), color = DashColors.Muted) }
        }
    )
}
