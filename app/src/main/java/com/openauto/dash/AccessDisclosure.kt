package com.openauto.dash

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Play edition's prominent disclosure before a system permission page:
 * what Dashwheel does with the accessibility service, or with notification
 * access, said in the app before Android's own page opens. Google Play asks
 * for it, and asks that the service is never turned on by the app itself.
 * A small dialog activity, so it can come from anywhere: a tile, Settings,
 * or the phone's keyboard card asking over the link, with no activity at hand.
 * The GitHub edition opens the system page straight away, as before.
 *
 * Either way, a page Android has locked for Dashwheel ([RestrictedSettings])
 * is unlocked through the shell when there is one, else explained.
 */
object AccessDisclosure {
    enum class Kind { ACCESSIBILITY, NOTIFICATIONS }

    /** Shows the disclosure for [kind]; only its "Open settings" opens the system page. */
    fun show(context: Context, kind: Kind) {
        context.startActivity(
            Intent(context, AccessDisclosureActivity::class.java)
                .putExtra(EXTRA_KIND, kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The system page where the driver turns the access of [kind] on. Locked
     * by Android, the switch there would only be grey: the lock is lifted
     * first, or the way to lift it is shown instead.
     */
    fun openSystemPage(context: Context, kind: Kind) {
        val app = context.applicationContext
        if (!RestrictedSettings.blocked(app)) {
            systemPage(app, kind)
            return
        }
        scope.launch {
            if (RestrictedSettings.lift(app)) systemPage(app, kind)
            else app.startActivity(
                Intent(app, AccessDisclosureActivity::class.java)
                    .putExtra(EXTRA_KIND, kind.name)
                    .putExtra(EXTRA_RESTRICTED, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    internal fun systemPage(context: Context, kind: Kind) {
        val action = when (kind) {
            Kind.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            Kind.NOTIFICATIONS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        }
        context.launchSafely(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun appInfo(context: Context) {
        context.launchSafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal const val EXTRA_KIND = "kind"
    internal const val EXTRA_RESTRICTED = "restricted"
}

/**
 * Android 13's "restricted settings": an app installed from a file, not from
 * a store, can't be given accessibility or notification access; its switch
 * stays grey until the driver taps it once, then picks "Allow restricted
 * settings" in its App info menu. Behind it is one app op, which a shell
 * (root or the unit's ADB) can set itself.
 */
internal object RestrictedSettings {
    private const val TAG = "RestrictedSettings"
    private const val OP = "android:access_restricted_settings"

    /** Whether Android keeps Dashwheel's access switches grey. */
    fun blocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            ops.unsafeCheckOpNoThrow(OP, Process.myUid(), context.packageName) == AppOpsManager.MODE_ERRORED
        }.getOrDefault(false)
    }

    /** Lifts the lock through the shell; true once it's gone. Never in the Play edition, never needed there. */
    suspend fun lift(context: Context): Boolean {
        if (Edition.play || !PrivilegedShell.access.value.shell) return false
        runCatching { DockShell.shell(context, "appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow") }
            .onFailure { Log.w(TAG, "could not lift the restricted settings: ${it.message}") }
        return !blocked(context)
    }
}

/** See [AccessDisclosure]: a see-through activity holding the one dialog. */
class AccessDisclosureActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = runCatching { AccessDisclosure.Kind.valueOf(intent.getStringExtra(AccessDisclosure.EXTRA_KIND).orEmpty()) }
            .getOrDefault(AccessDisclosure.Kind.ACCESSIBILITY)
        if (intent.getBooleanExtra(AccessDisclosure.EXTRA_RESTRICTED, false)) {
            showRestricted(kind)
            return
        }
        val title = when (kind) {
            AccessDisclosure.Kind.ACCESSIBILITY -> R.string.access_a11y_title
            AccessDisclosure.Kind.NOTIFICATIONS -> R.string.access_notif_title
        }
        val body = when (kind) {
            AccessDisclosure.Kind.ACCESSIBILITY -> R.string.access_a11y_body
            AccessDisclosure.Kind.NOTIFICATIONS -> R.string.access_notif_body
        }
        setContent {
            OpenAutoDashTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    containerColor = DashColors.Card,
                    title = { Text(getString(title), color = DashColors.TextPrimary) },
                    text = { Text(getString(body), color = DashColors.Muted, style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = {
                            AccessDisclosure.openSystemPage(this, kind)
                            finish()
                        }) { Text(getString(R.string.access_open_settings), color = DashColors.TextPrimary) }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text(getString(R.string.access_not_now), color = DashColors.Muted) }
                    }
                )
            }
        }
    }

    /** Android keeps the switch grey ([RestrictedSettings]): the two steps to unlock it, each one tap away. */
    private fun showRestricted(kind: AccessDisclosure.Kind) {
        setContent {
            OpenAutoDashTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    containerColor = DashColors.Card,
                    title = { Text(getString(R.string.access_restricted_title), color = DashColors.TextPrimary) },
                    text = { Text(getString(R.string.access_restricted_body), color = DashColors.Muted, style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = {
                            AccessDisclosure.appInfo(this)
                            finish()
                        }) { Text(getString(R.string.access_app_info), color = DashColors.TextPrimary) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            AccessDisclosure.systemPage(this, kind)
                            finish()
                        }) { Text(getString(R.string.access_open_settings), color = DashColors.Muted) }
                    }
                )
            }
        }
    }
}
