package com.openauto.dash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * The Moments screen: the Home a moment shows, large, drawn by the
 * dashboard's own tiles; what starts and ends it; a switch each and one for
 * them all; and the drive as a line of its moments, with a fault apart since
 * it can come at any time. "Change what it shows" gives a moment a copy of
 * one of the driver's dashboards, or its own Home back.
 */

/**
 * @param now the moment Home shows at this minute, if any
 * @param itemsFor what a moment shows on this screen
 * @param custom whether a moment shows a copy of a dashboard rather than its own Home
 * @param dashboards the driver's dashboards, (page, name), that a moment can copy
 * @param onCopy gives a moment a copy of a page; null puts its own Home back
 */
@Composable
internal fun MomentsScreen(
    settings: MomentSettings,
    now: Moment?,
    itemsFor: (Moment) -> List<DashboardItem>,
    custom: (Moment) -> Boolean,
    pageSize: DpSize,
    tile: @Composable (DashboardItem) -> Unit,
    dashboards: List<Pair<Int, String>>,
    appsNote: Boolean,
    onMaster: (Boolean) -> Unit,
    onToggle: (Moment, Boolean) -> Unit,
    onCopy: (Moment, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(now ?: Moment.GUIDING) }
    var changing by remember { mutableStateOf(false) }
    val tap = rememberTapFeedback()
    val shownNow = now.takeIf { settings.master }
    BackHandler { if (changing) changing = false else onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .keepClearOfWindows()
                .background(DashColors.Background.copy(alpha = 1f))
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.moments_title),
                        color = DashColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    Text(
                        stringResource(if (appsNote) R.string.moments_apps_note else R.string.moments_hint),
                        color = if (appsNote) DashColors.Warning else DashColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(DashColors.Card)
                        .border(1.dp, DashColors.Line, RoundedCornerShape(30.dp))
                        .clickable(role = Role.Switch) { tap(); onMaster(!settings.master) }
                        .heightIn(min = DashSize.TouchPrimary)
                        .padding(start = 22.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(stringResource(R.string.moments_master), color = DashColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    MomentSwitch(settings.master) { tap(); onMaster(it) }
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { tap(); onDismiss() },
                    modifier = Modifier.heightIn(min = DashSize.TouchPrimary),
                    colors = ButtonDefaults.buttonColors(containerColor = DashColors.Accent, contentColor = DashColors.OnAccent),
                    shape = DashShape.Small,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp)
                ) { Text(stringResource(R.string.dash_done), style = MaterialTheme.typography.titleMedium) }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
                // The Home this moment shows, as the dashboard draws it.
                BoxWithConstraints(Modifier.weight(0.56f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                    val aspect = (pageSize.width / pageSize.height).coerceIn(0.4f, 3f)
                    val width = min(maxWidth, maxHeight * aspect)
                    PagePreview(
                        items = itemsFor(selected),
                        pageSize = pageSize,
                        width = width,
                        tile = tile,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .border(2.dp, if (selected == shownNow) DashColors.Accent else DashColors.Line, DashShape.Medium)
                    )
                    if (selected == shownNow) NowChip(stringResource(selected.titleRes), Modifier.padding(start = 16.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DashColors.Card)
                        .border(1.dp, DashColors.Line, RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    if (changing) {
                        ChangeList(
                            moment = selected,
                            custom = custom(selected),
                            dashboards = dashboards,
                            onPick = { page -> tap(); onCopy(selected, page); changing = false },
                            onCancel = { tap(); changing = false }
                        )
                    } else {
                        MomentDetail(
                            moment = selected,
                            custom = custom(selected),
                            on = selected in settings.on,
                            onToggle = { tap(); onToggle(selected, it) },
                            onChange = { tap(); changing = true }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(TIMELINE_HEIGHT), verticalAlignment = Alignment.Top) {
                DriveLine(
                    settings = settings,
                    now = shownNow,
                    selected = selected,
                    itemsFor = itemsFor,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { tap(); selected = it; changing = false }
                Spacer(Modifier.width(16.dp))
                AnyTimeCard(
                    moment = Moment.FAULT,
                    items = itemsFor(Moment.FAULT),
                    selected = selected == Moment.FAULT,
                    on = Moment.FAULT in settings.on,
                    modifier = Modifier.width(230.dp).fillMaxHeight()
                ) { tap(); selected = Moment.FAULT; changing = false }
            }
        }
    }
}

private val TIMELINE_HEIGHT = 168.dp
private val THUMB_WIDTH = 124.dp

@Composable
private fun MomentSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = DashColors.Accent,
            checkedThumbColor = DashColors.OnAccent,
            uncheckedTrackColor = DashColors.CardHi,
            uncheckedBorderColor = DashColors.Line
        )
    )
}

/** "NOW Guiding": the moment Home shows at this minute. */
@Composable
private fun NowChip(title: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(DashColors.Card)
            .border(1.5.dp, DashColors.Accent, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(DashColors.Accent))
        Text(stringResource(R.string.moments_now).uppercase(), color = DashColors.AccentInk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
        Text(title, color = DashColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
    }
}

/** What a moment shows, what starts and ends it, that it waits for the finger, its switch and the way to change it. */
@Composable
private fun MomentDetail(moment: Moment, custom: Boolean, on: Boolean, onToggle: (Boolean) -> Unit, onChange: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(moment.titleRes).uppercase(),
            color = DashColors.AccentInk,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            stringResource(if (custom) R.string.moments_custom else moment.showsRes),
            color = DashColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow(Icons.Filled.Bolt, stringResource(R.string.moments_starts), stringResource(moment.startsRes))
            DetailRow(Icons.Filled.Replay, stringResource(R.string.moments_ends), stringResource(moment.endsRes))
            DetailRow(Icons.Filled.PanTool, stringResource(R.string.moments_stays), stringResource(R.string.moments_stays_body))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            MomentSwitch(on, onToggle)
            Text(
                stringResource(if (on) R.string.moments_on else R.string.moments_off),
                color = DashColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp).weight(1f)
            )
            PillButton(stringResource(R.string.moments_change), Icons.Filled.SwapHoriz, onChange)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DashColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = DashColors.Accent, modifier = Modifier.size(22.dp)) }
        Column {
            Text(label, color = DashColors.TextSecondary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            Text(text, color = DashColors.TextPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Its own Home back, or a copy of one of the driver's dashboards. */
@Composable
private fun ChangeList(moment: Moment, custom: Boolean, dashboards: List<Pair<Int, String>>, onPick: (Int?) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.moments_change),
            color = DashColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            stringResource(R.string.moments_change_hint),
            color = DashColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceRow(stringResource(R.string.moments_as_it_came), !custom) { onPick(null) }
            dashboards.forEach { (page, name) ->
                ChoiceRow(stringResource(R.string.moments_copy_of, name), false) { onPick(page) }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = DashSize.Touch)
                .clip(DashShape.Small)
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.dash_cancel), color = DashColors.TextSecondary, style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun ChoiceRow(label: String, current: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DashSize.TouchPrimary)
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) DashColors.Accent.copy(alpha = 0.16f) else DashColors.CardHi.copy(alpha = 0.5f))
            .then(if (current) Modifier.border(1.5.dp, DashColors.Accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = DashColors.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The drive as a line of its moments: each one's Home in small, a node on
 * the line, its name and what starts it. Lit up to the moment showing now;
 * a moment switched off is dimmed.
 */
@Composable
private fun DriveLine(
    settings: MomentSettings,
    now: Moment?,
    selected: Moment,
    itemsFor: (Moment) -> List<DashboardItem>,
    modifier: Modifier,
    onSelect: (Moment) -> Unit
) {
    val stations = Moment.DRIVE
    val lit = now?.let { stations.indexOf(it) } ?: -1
    val accent = DashColors.Accent
    val dim = DashColors.TextSecondary.copy(alpha = 0.3f)
    val nodeY = THUMB_HEIGHT + 22.dp
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / stations.size
            val y = nodeY.toPx()
            val first = step / 2
            val last = step * (stations.size - 0.5f)
            drawLine(dim, Offset(first, y), Offset(last, y), 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())))
            if (lit > 0) {
                val end = step * (lit + 0.5f)
                drawLine(accent.copy(alpha = 0.3f), Offset(first, y), Offset(end, y), 10.dp.toPx(), StrokeCap.Round)
                drawLine(accent, Offset(first, y), Offset(end, y), 4.dp.toPx(), StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxSize()) {
            stations.forEachIndexed { i, moment ->
                val on = moment in settings.on
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab, onClickLabel = stringResource(moment.titleRes)) { onSelect(moment) }
                        .alpha(if (on && settings.master) 1f else 0.45f)
                ) {
                    MomentThumb(itemsFor(moment), moment == selected)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i <= lit) accent else DashColors.Background)
                            .border(2.5.dp, if (i <= lit) accent else DashColors.TextSecondary.copy(alpha = 0.6f), CircleShape)
                    )
                    Text(
                        stringResource(moment.titleRes),
                        color = if (moment == selected) DashColors.AccentInk else DashColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        stringResource(moment.startsRes),
                        color = DashColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

private val THUMB_HEIGHT = 70.dp

/** A moment's Home in small, as coloured blocks. */
@Composable
private fun MomentThumb(items: List<DashboardItem>, selected: Boolean) {
    val colors = categoryColors()
    val pageBg = DashColors.CardHi
    Canvas(
        Modifier
            .size(THUMB_WIDTH, THUMB_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) DashColors.Accent else DashColors.Line, RoundedCornerShape(8.dp))
    ) {
        drawRoundRect(pageBg.copy(alpha = pageBg.alpha * 0.6f), cornerRadius = CornerRadius(8.dp.toPx()))
        items.forEach { drawTile(it, Offset.Zero, size.width, size.height, colors) }
    }
}

/** The moment that has no place on the line: a fault, any time. */
@Composable
private fun AnyTimeCard(moment: Moment, items: List<DashboardItem>, selected: Boolean, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val red = DashColors.Critical
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(red.copy(alpha = if (selected) 0.18f else 0.08f))
            .border(if (selected) 2.dp else 1.5.dp, red.copy(alpha = if (selected) 1f else 0.55f), shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .alpha(if (on) 1f else 0.5f)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(stringResource(R.string.moments_any_time).uppercase(), color = red, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        val colors = categoryColors()
        val pageBg = DashColors.Background
        Canvas(Modifier.size(THUMB_WIDTH, THUMB_HEIGHT).clip(RoundedCornerShape(8.dp))) {
            drawRoundRect(pageBg, cornerRadius = CornerRadius(8.dp.toPx()))
            items.forEach { drawTile(it, Offset.Zero, size.width, size.height, colors) }
        }
        Text(
            stringResource(moment.titleRes),
            color = DashColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(stringResource(moment.startsRes), color = DashColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * On Home while a moment shows it: the moment's name for a few seconds after
 * it comes, then a small icon. A tap brings the driver's own Home back.
 */
@Composable
internal fun MomentChip(moment: Moment, modifier: Modifier, onBackHome: () -> Unit) {
    var named by remember(moment) { mutableStateOf(true) }
    LaunchedEffect(moment) {
        delay(MOMENT_CHIP_NAMED_MS)
        named = false
    }
    val tap = rememberTapFeedback()
    val label = stringResource(R.string.moments_back_home)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DashColors.Card.copy(alpha = 0.92f))
            .border(1.dp, DashColors.Accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = label) { tap(); onBackHome() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(moment.icon, contentDescription = null, tint = DashColors.Accent, modifier = Modifier.size(20.dp))
        AnimatedVisibility(named) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(moment.titleRes), color = DashColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Icon(Icons.Filled.Home, contentDescription = label, tint = DashColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** How long the chip says the moment's name before it shrinks to its icon. */
private const val MOMENT_CHIP_NAMED_MS = 8_000L
