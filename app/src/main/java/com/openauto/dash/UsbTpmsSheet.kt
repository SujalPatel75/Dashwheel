package com.openauto.dash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The USB tyre receiver's row in Settings, Car: what it is doing now. */
@Composable
internal fun UsbTpmsRow(onClick: () -> Unit) {
    val state by UsbTpms.state.collectAsState()
    SettingsRow(Icons.Filled.TireRepair, stringResource(R.string.tpms_usb_title), stringResource(state.summaryRes), onClick)
}

/**
 * Which USB serial device is the tyre receiver ([UsbTpms]), whether it
 * talks, and the four readings as decoded, to check them against a gauge.
 */
@Composable
internal fun UsbTpmsSheet(onDismiss: () -> Unit) {
    ParkedOnly(onDismiss)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chosen by UsbTpms.device.collectAsState()
    val state by UsbTpms.state.collectAsState()
    val unit by UsbTpms.unit.collectAsState()
    val tyres by Tyres.tyres.collectAsState()
    val shownUnit by Tyres.unit.collectAsState()
    var looked by remember { mutableIntStateOf(0) }
    var attached by remember { mutableStateOf<List<UsbObdAdapter>?>(null) }
    var denied by remember { mutableStateOf(false) }
    // From the USB service: off the main thread. Looked at again when the state changes (plugged, unplugged).
    LaunchedEffect(looked, state) { attached = withContext(Dispatchers.IO) { UsbObd.attached(context) } }
    fun use(adapter: UsbObdAdapter) = scope.launch {
        denied = !UsbObd.allow(context, adapter)
        if (!denied) UsbTpms.choose(context, adapter)
    }
    SettingsSheet(
        title = stringResource(R.string.tpms_usb_title),
        onDismiss = onDismiss,
        actions = { SheetButton(stringResource(R.string.ai_done), onClick = onDismiss) }
    ) {
        Text(stringResource(R.string.tpms_usb_explanation), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (Tyres.fromApp) Text(stringResource(R.string.tpms_usb_unit_app), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall)

        Label(stringResource(R.string.tpms_usb_devices))
        val list = attached
        if (list != null && list.isEmpty()) Hint(stringResource(R.string.tpms_usb_none_plugged))
        CompositionLocalProvider(LocalSheetInPane provides true) {
            list?.forEach { adapter ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(adapter.label, color = DashColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint(adapter.key)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (adapter.key == chosen) {
                        SheetButton(stringResource(R.string.tpms_usb_forget), primary = false) { UsbTpms.forget(context) }
                    } else {
                        SheetButton(stringResource(R.string.tpms_usb_use)) { use(adapter) }
                    }
                }
            }
            SheetButton(stringResource(R.string.tpms_usb_look_again), primary = false) { looked++ }
        }
        if (denied) Hint(stringResource(R.string.tpms_usb_denied))

        if (chosen == null) return@SettingsSheet
        Label(stringResource(state.summaryRes))
        val receiver = list?.firstOrNull { it.key == chosen }
        if (state == UsbTpmsState.NOT_ALLOWED && receiver != null) {
            CompositionLocalProvider(LocalSheetInPane provides true) {
                SheetButton(stringResource(R.string.tpms_usb_allow)) { use(receiver) }
            }
        }
        if (state == UsbTpmsState.BUSY) Hint(stringResource(R.string.tpms_usb_busy_hint))
        // As decoded, to check against a gauge.
        val u = LocalUnits.current
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(TyrePos.FRONT_LEFT, TyrePos.FRONT_RIGHT, TyrePos.REAR_LEFT, TyrePos.REAR_RIGHT).forEach { pos ->
                val t = tyres[pos]
                val reading = t?.let { "${formatPressure(it.kPa, shownUnit)}, ${u.tempText(it.celsius)}" } ?: "--"
                Text("${stringResource(pos.labelRes)}: $reading", color = DashColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Hint(stringResource(R.string.tpms_usb_repeat_hint))

        if (!Tyres.fromApp) {
            Label(stringResource(R.string.tpms_usb_unit))
            ChoiceRow(PressureUnit.entries, unit, {
                context.getString(
                    when (it) {
                        PressureUnit.BAR -> R.string.tpms_usb_unit_bar
                        PressureUnit.KPA -> R.string.tpms_usb_unit_kpa
                        PressureUnit.PSI -> R.string.tpms_usb_unit_psi
                    }
                )
            }) { UsbTpms.setUnit(context, it) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
}

private val UsbTpmsState.summaryRes: Int
    get() = when (this) {
        UsbTpmsState.OFF -> R.string.tpms_usb_state_off
        UsbTpmsState.UNPLUGGED -> R.string.tpms_usb_state_unplugged
        UsbTpmsState.NOT_ALLOWED -> R.string.tpms_usb_state_not_allowed
        UsbTpmsState.BUSY -> R.string.tpms_usb_state_busy
        UsbTpmsState.LISTENING -> R.string.tpms_usb_state_listening
        UsbTpmsState.RECEIVING -> R.string.tpms_usb_state_receiving
    }
