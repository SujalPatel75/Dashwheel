package com.openauto.dash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Dashwheel as the Home screen, or handed back to the unit's own launcher
 * (Settings, Head unit). Lending the car to someone used to the stock screen
 * took a driver a long time: Dashwheel set itself back as Android's Home at
 * every start ([SystemUpkeep]), pointed the firmware's own launcher choice
 * at itself ([UnitLauncher]) and came back in front at boot and at each
 * ignition ([EmbeddedApp]). Handed back, all of that waits until the switch
 * is turned on again; the settings stay as they are.
 */
internal object HomeHandBack {
    private const val TAG = "HomeHandBack"
    private const val PREFS = "home_hand_back"
    private const val KEY_RELEASED = "released"
    /** The firmware's launcher before Dashwheel took its place, to give it back. */
    private const val KEY_PREVIOUS = "previous_unit_launcher"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _released = MutableStateFlow<Boolean?>(null)
    val releasedFlow: StateFlow<Boolean?> = _released

    /** Whether Dashwheel was handed back and must not take Home or the screen by itself. */
    fun released(context: Context): Boolean =
        _released.value ?: prefs(context).getBoolean(KEY_RELEASED, false).also { _released.value = it }

    /** The firmware's launcher [pkg] that Dashwheel is about to replace ([UnitLauncher]). */
    fun rememberUnitLauncher(context: Context, pkg: String) {
        if (pkg != context.packageName) prefs(context).edit().putString(KEY_PREVIOUS, pkg).apply()
    }

    /** The unit's own launcher back as Home, Dashwheel left alone in the app list. */
    fun handBack(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean(KEY_RELEASED, true).apply()
        _released.value = true
        val stock = stockHome(app)
        DebugLog.note(app, "Home handed back to ${stock?.packageName ?: "the unit"}")
        scope.launch {
            stock?.let { UnitPlatform.adapter.launcher?.handTo(app, it.packageName) }
            if (stock != null && setHome(app, stock)) {
                runCatching {
                    app.startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(stock)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } else {
                // No shell to choose for the driver: Android's own Home picker.
                openHomeSettings(app)
            }
        }
    }

    /** Dashwheel the Home screen again. */
    fun takeBack(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean(KEY_RELEASED, false).apply()
        _released.value = false
        DebugLog.note(app, "Dashwheel is the Home screen again")
        scope.launch {
            if (!setHome(app, ComponentName(app, MainActivity::class.java))) openHomeSettings(app)
            UnitPlatform.adapter.launcher?.claim(app)
        }
    }

    /** The launcher Dashwheel replaced: the firmware's one it remembers, else any other Home app. */
    private fun stockHome(context: Context): ComponentName? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homes = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName != "com.android.settings" }
        val previous = prefs(context).getString(KEY_PREVIOUS, null)
            ?: UnitPlatform.adapter.launcher?.ownChoice()?.takeIf { it != context.packageName }
        val pick = homes.firstOrNull { it.packageName == previous } ?: homes.firstOrNull() ?: return null
        return ComponentName(pick.packageName, pick.name)
    }

    private suspend fun setHome(context: Context, component: ComponentName): Boolean {
        if (Edition.play || !PrivilegedShell.access.value.shell) return false
        val out = runCatching { DockShell.shell(context, "cmd package set-home-activity ${component.flattenToString()}") }
            .getOrElse { return false }
        Log.i(TAG, "Home set to ${component.flattenToShortString()}: ${out.trim()}")
        return !out.contains("Error", ignoreCase = true) && !out.contains("Exception")
    }

    private fun openHomeSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
