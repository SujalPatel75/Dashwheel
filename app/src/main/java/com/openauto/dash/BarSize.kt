package com.openauto.dash

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How big the bottom bar is drawn: for a screen set deep in a thick bezel, or
 * big fingers. Every look's bar grows the same way, buttons, text and height
 * together ([Scaled]).
 */
internal enum class BarSize(val scale: Float, val title: Int) {
    NORMAL(1f, R.string.dash_bar_size_normal),
    LARGE(1.2f, R.string.dash_bar_size_large),
    LARGER(1.4f, R.string.dash_bar_size_larger);

    companion object {
        private const val PREFS = "dash_bar"
        private const val KEY = "size"

        private val _chosen = MutableStateFlow(NORMAL)
        val chosen: StateFlow<BarSize> = _chosen.asStateFlow()
        private var loaded = false

        @Synchronized
        fun load(context: Context) {
            if (loaded) return
            loaded = true
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _chosen.value = entries.firstOrNull { it.name == saved } ?: NORMAL
        }

        fun save(context: Context, size: BarSize) {
            _chosen.value = size
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, size.name).apply()
        }
    }
}

/** [content] (a bar) at the size the driver chose: the whole of it, as if the screen were denser. */
@Composable
internal fun BarSize.Companion.Scaled(content: @Composable () -> Unit) {
    load(LocalContext.current)
    val size by chosen.collectAsState()
    if (size == BarSize.NORMAL) return content()
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * size.scale, d.fontScale), content = content)
}

/** Settings, Look and the bar's own settings: Normal, Large, Larger. */
@Composable
internal fun BarSizeSetting() {
    val context = LocalContext.current
    BarSize.load(context)
    val size by BarSize.chosen.collectAsState()
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.dash_bar_size),
        color = DashColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
    )
    SegmentedSwitch(
        options = BarSize.entries,
        chosen = size,
        icon = { Icons.Filled.FormatSize },
        title = { stringResource(it.title) },
        onChoose = { BarSize.save(context, it) }
    )
}
