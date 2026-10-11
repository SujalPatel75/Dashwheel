package com.openauto.dash

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * Where the map's tiles and the routes come from. By default the free public
 * services: OpenFreeMap's styles (free, no key, no usage cap) and the public
 * Valhalla router of FOSSGIS (a community server meant for light use). Heavy
 * users and resellers point these at their own servers here: any MapLibre
 * style URL, and any Valhalla /route endpoint.
 */
object MapServers {
    const val DEFAULT_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    const val DEFAULT_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
    const val DEFAULT_ROUTING = "https://valhalla1.openstreetmap.de/route"

    data class Servers(
        val styleDark: String = DEFAULT_STYLE_DARK,
        val styleLight: String = DEFAULT_STYLE_LIGHT,
        val routing: String = DEFAULT_ROUTING
    ) {
        val isDefault: Boolean get() = this == Servers()
    }

    private const val PREFS = "map_servers"
    private const val KEY_DARK = "style_dark"
    private const val KEY_LIGHT = "style_light"
    private const val KEY_ROUTING = "routing"

    private val _current = MutableStateFlow(Servers())
    val current: StateFlow<Servers> = _current
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _current.value = Servers(
            styleDark = p.getString(KEY_DARK, null)?.takeIf(::usable) ?: DEFAULT_STYLE_DARK,
            styleLight = p.getString(KEY_LIGHT, null)?.takeIf(::usable) ?: DEFAULT_STYLE_LIGHT,
            routing = p.getString(KEY_ROUTING, null)?.takeIf(::usable) ?: DEFAULT_ROUTING
        )
    }

    /** Keeps [servers], each blank or unusable address falling back to its default. */
    fun save(context: Context, servers: Servers) {
        val s = Servers(
            styleDark = servers.styleDark.trim().takeIf(::usable) ?: DEFAULT_STYLE_DARK,
            styleLight = servers.styleLight.trim().takeIf(::usable) ?: DEFAULT_STYLE_LIGHT,
            routing = servers.routing.trim().takeIf(::usable) ?: DEFAULT_ROUTING
        )
        _current.value = s
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DARK, s.styleDark).putString(KEY_LIGHT, s.styleLight).putString(KEY_ROUTING, s.routing)
            .apply()
    }

    /** A web address the map or the router can be asked: http or https, with a host. */
    internal fun usable(url: String): Boolean {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        return (u.scheme == "https" || u.scheme == "http") && !u.host.isNullOrBlank()
    }
}

/** Settings → System → Map and route servers: the three addresses, and the free defaults back. */
@Composable
internal fun MapServersSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    MapServers.load(context)
    var dark by remember { mutableStateOf(MapServers.current.value.styleDark) }
    var light by remember { mutableStateOf(MapServers.current.value.styleLight) }
    var routing by remember { mutableStateOf(MapServers.current.value.routing) }
    fun close() {
        MapServers.save(context, MapServers.Servers(dark, light, routing))
        onDismiss()
    }
    SettingsSheet(
        title = stringResource(R.string.map_servers_title),
        onDismiss = ::close,
        actions = {
            SheetButton(stringResource(R.string.map_servers_reset), primary = false) {
                val d = MapServers.Servers()
                dark = d.styleDark; light = d.styleLight; routing = d.routing
            }
            SheetButton(stringResource(R.string.ai_done), onClick = ::close)
        }
    ) {
        Text(stringResource(R.string.map_servers_explanation), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Label(stringResource(R.string.map_servers_style_dark))
        UrlField(dark) { dark = it }
        Label(stringResource(R.string.map_servers_style_light))
        UrlField(light) { light = it }
        Label(stringResource(R.string.map_servers_routing))
        UrlField(routing) { routing = it }
    }
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        isError = value.isNotBlank() && !MapServers.usable(value),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
    )
}
