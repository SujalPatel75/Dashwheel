package com.openauto.dash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Settings, Diagnostics, Sensor watch: each reading's usual and its last
 * drives, the ones that drifted with the mechanic's word on them, and Start
 * again on a drifted one once its part is replaced (a new battery has a
 * usual of its own).
 */
@Composable
internal fun SensorWatchSheet(onDismiss: () -> Unit) {
    ParkedOnly(onDismiss)
    val history by SensorWatch.history.collectAsState()
    val findings by SensorWatch.findings.collectAsState()
    val causes by SensorWatch.causes.collectAsState()
    val resources = LocalContext.current.resources
    val units = LocalUnits.current
    val locale = Locale.getDefault()
    val statuses = remember(history, findings) { SensorWatch.statuses() }
    // The particle filter's interval only for a car that has one.
    val shown = statuses.filter { it.signal != WatchSignal.FILTER_KM || CarProfileStore.current.particleFilter || it.learned > 0 }
    SettingsSheet(
        title = stringResource(R.string.watch_title),
        onDismiss = onDismiss,
        actions = { SheetButton(stringResource(R.string.ai_done), onClick = onDismiss) }
    ) {
        Text(stringResource(R.string.watch_explanation), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        findings.forEach { f ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.watch_drifted, stringResource(f.signal.labelRes)),
                    color = DashColors.Warning, fontWeight = FontWeight.SemiBold
                )
                Text(
                    WatchLines.spoken(f, AiLanguage.of(locale), units).text(resources),
                    color = DashColors.TextPrimary, style = MaterialTheme.typography.bodyMedium
                )
                causes[f.signal]?.let { Text(it, color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Label(stringResource(R.string.watch_readings))
        shown.forEach { s ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(s.signal.labelRes), color = DashColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    val detail = when {
                        s.usual == null -> stringResource(R.string.watch_learning, s.learned, DriftRules.MIN_BASE + DriftRules.RECENT)
                        s.recent == null -> stringResource(R.string.watch_usual_only, WatchLines.value(s.signal, s.usual, locale, units))
                        else -> stringResource(
                            R.string.watch_usual_recent,
                            WatchLines.value(s.signal, s.usual, locale, units),
                            WatchLines.value(s.signal, s.recent, locale, units)
                        )
                    }
                    Text(detail, color = DashColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            s.finding != null -> R.string.watch_state_drift
                            s.usual == null -> R.string.watch_state_learning
                            else -> R.string.watch_state_normal
                        }
                    ),
                    color = when {
                        s.finding != null -> DashColors.Warning
                        s.usual == null -> DashColors.Muted
                        else -> DashColors.Good
                    },
                    fontWeight = FontWeight.Bold
                )
                // Only where it drifted: a part just replaced. A slip elsewhere would throw away weeks of drives.
                if (s.finding != null) {
                    TextButton(onClick = { SensorWatch.forget(s.signal) }) { Text(stringResource(R.string.watch_forget), color = DashColors.AccentInk) }
                }
            }
        }
        Text(stringResource(R.string.watch_forget_hint), color = DashColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
