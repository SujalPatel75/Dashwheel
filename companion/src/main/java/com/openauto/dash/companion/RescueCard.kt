package com.openauto.dash.companion

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Support
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openauto.dash.link.RescueCommand

/** One way out on the card: its button and what the confirm says it does. */
private data class RescueItem(val action: RescueCommand.Action, @StringRes val label: Int, @StringRes val body: Int)

private val RESCUES = listOf(
    RescueItem(RescueCommand.Action.CLEAR_WHEEL_BUTTONS, R.string.rescue_wheel, R.string.rescue_wheel_body),
    RescueItem(RescueCommand.Action.STANDARD_LOOK, R.string.rescue_look, R.string.rescue_look_body),
    RescueItem(RescueCommand.Action.LEAVE_SPLIT, R.string.rescue_split, R.string.rescue_split_body),
    RescueItem(RescueCommand.Action.HAND_HOME_BACK, R.string.rescue_home_back, R.string.rescue_home_back_body),
    RescueItem(RescueCommand.Action.TAKE_HOME_BACK, R.string.rescue_home_take, R.string.rescue_home_take_body)
)

/**
 * Ways out for a driver locked out of the car's screen ([CarRescue]): each
 * asks once here, then runs on the car with no tap there.
 */
@Composable
internal fun RescueCard(connected: Boolean) {
    val answer by CarRescue.answer.collectAsState()
    var asking by rememberSaveable { mutableStateOf<RescueCommand.Action?>(null) }
    val a = answer
    val line = when {
        !connected -> stringResource(R.string.rescue_offline)
        a == null -> stringResource(R.string.rescue_detail)
        else -> stringResource(
            when (a.status) {
                CarRescue.Status.SENDING -> R.string.rescue_sending
                CarRescue.Status.DONE -> R.string.rescue_done
                CarRescue.Status.FAILED -> R.string.rescue_failed
                CarRescue.Status.NO_ANSWER -> R.string.rescue_no_answer
            }
        )
    }
    val tint = when {
        !connected -> CompanionColors.Muted
        a?.status == CarRescue.Status.DONE -> CompanionColors.Teal
        a?.status == CarRescue.Status.FAILED || a?.status == CarRescue.Status.NO_ANSWER -> CompanionColors.Amber
        else -> CompanionColors.Blue
    }
    Panel {
        Column(Modifier.padding(18.dp)) {
            CardHeading(Icons.Filled.Support, stringResource(R.string.rescue_title), line, tint)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RESCUES.forEach { item ->
                    OutlinedButton(
                        onClick = { asking = item.action },
                        enabled = connected && a?.status != CarRescue.Status.SENDING,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(item.label)) }
                }
            }
        }
    }

    RESCUES.firstOrNull { it.action == asking }?.let { item ->
        AlertDialog(
            onDismissRequest = { asking = null },
            containerColor = CompanionColors.SurfaceHigh,
            icon = { Icon(Icons.Filled.Support, contentDescription = null) },
            title = { Text(stringResource(item.label)) },
            text = { Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = {
                    CarRescue.send(item.action)
                    asking = null
                }) { Text(stringResource(R.string.rescue_send)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
