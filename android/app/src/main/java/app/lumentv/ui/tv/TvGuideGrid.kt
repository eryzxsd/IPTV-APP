package app.lumentv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.LoadState
import app.lumentv.data.Programme
import app.lumentv.ui.Filter
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand

private val MINUTE: Dp = 4.dp // 30 minutes = 120 dp
private val CHANNEL_COL: Dp = 230.dp
private val ROW_HEIGHT: Dp = 76.dp
private const val HALF_HOUR = 30 * 60_000L
private const val HOURS_AHEAD = 12

/**
 * TV guide as a grid: channels down the side, shows along a timeline with a "now" line.
 * The selected show is described at the top. Left/Right moves through the day, Up/Down through channels.
 */
@Composable
fun TvGuide(vm: MainViewModel, onPlay: (Channel) -> Unit, onOptions: (Channel) -> Unit) {
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val all by vm.channels.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val state by vm.epgState.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val base = filtered.ifEmpty { if (filter == Filter.All) all else emptyList() }
    val rows = remember(base, epg) { base.filter { !epg[it.epgKey].isNullOrEmpty() } }

    // The timeline starts at the last half hour; scrolling moves it later (in minutes).
    val windowStart = remember(clock / HALF_HOUR) { clock / HALF_HOUR * HALF_HOUR }
    var scroll by remember { mutableFloatStateOf(0f) }
    var visibleMinutes by remember { mutableFloatStateOf(150f) }
    val maxScroll = HOURS_AHEAD * 60f - visibleMinutes

    var mode by remember { mutableStateOf(PanelMode.Channels) }
    var choosing by remember { mutableStateOf(false) }
    BackHandler(enabled = mode != PanelMode.Channels) { mode = PanelMode.Channels }
    var selected by remember { mutableStateOf<Pair<Channel, Programme>?>(null) }

    val start = remember(rows) { rows.indexOfFirst { it.id == playback.channel?.id }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    val firstFocus = remember { FocusRequester() }
    var refocus by remember { mutableStateOf(0) }
    LaunchedEffect(rows.firstOrNull()?.id, refocus) {
        if (rows.isNotEmpty()) {
            scroll = 0f
            listState.scrollToItem((start - 1).coerceAtLeast(0))
            firstFocus.requestWhenReady()
        }
    }

    if (choosing) {
        val options = listOf("All channels" to Filter.All, "Favorites" to Filter.Favorites, "Recently watched" to Filter.Recent,
            "Choose a category…" to null, "Choose a country…" to null)
        TvChoiceDialog("Show", options.map { it.first }, options.indexOfFirst { it.second == filter },
            onPick = { i ->
                choosing = false
                when (i) {
                    3 -> mode = PanelMode.Categories
                    4 -> mode = PanelMode.Countries
                    else -> { vm.filter.value = options[i].second!!; refocus++ }
                }
            }, onDismiss = { choosing = false })
    }

    if (mode != PanelMode.Channels) {
        Box(Modifier.fillMaxSize().padding(top = 36.dp, start = 24.dp, end = 24.dp)) {
            TvFilterPicker(vm, mode, filter) { mode = PanelMode.Channels; refocus++ }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        // ------------------------------------------------- details of the selected show
        Details(selected, clock, filter, rows.size, state, onFilter = { choosing = true })

        // ----------------------------------------------------------------- time ruler
        Row(Modifier.fillMaxWidth().height(34.dp)) {
            Text(
                fmtDay(windowStart + (scroll * 60_000).toLong()),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(CHANNEL_COL).padding(start = 28.dp, top = 6.dp),
            )
            val minutePx = with(LocalDensity.current) { MINUTE.toPx() }
            // How many minutes of timeline fit on screen.
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().onSizeChanged { visibleMinutes = it.width / minutePx }) {
                val firstMark = (scroll / 30).toInt()
                for (k in firstMark..firstMark + 8) {
                    val x = MINUTE * (k * 30 - scroll)
                    Text(
                        fmtTime(windowStart + k * HALF_HOUR),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(x = x + 6.dp, y = 6.dp),
                    )
                    Box(Modifier.offset(x = x).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                }
            }
        }

        if (rows.isEmpty()) {
            Text(
                when {
                    state is LoadState.Loading -> (state as LoadState.Loading).label
                    filter != Filter.All -> "No guide information for these channels."
                    else -> "No guide data yet. Choose guide regions in Settings."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp),
            )
        }

        // ------------------------------------------------------------------ the grid
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(rows, key = { _, c -> c.id }) { i, c ->
                    GuideRow(
                        channel = c,
                        number = i + 1,
                        programmes = epg[c.epgKey].orEmpty(),
                        windowStart = windowStart,
                        scroll = scroll,
                        visibleMinutes = visibleMinutes,
                        clock = clock,
                        playing = c.id == playback.channel?.id,
                        firstFocus = if (i == start) firstFocus else null,
                        onFocus = { p ->
                            selected = c to p
                            // Keep the selected show in view: scroll so it starts on screen.
                            val startMin = ((p.start - windowStart) / 60_000f).coerceAtLeast(0f)
                            val endMin = (p.stop - windowStart) / 60_000f
                            if (startMin < scroll) scroll = startMin.coerceIn(0f, maxScroll.coerceAtLeast(0f))
                            else if (startMin > scroll + visibleMinutes - 30) scroll = (startMin - 30).coerceIn(0f, maxScroll.coerceAtLeast(0f))
                            else if (endMin < scroll) scroll = startMin
                        },
                        onClick = { onPlay(c) },
                        onLongClick = { onOptions(c) },
                    )
                }
            }
            // "Now" line across the timeline.
            val nowMin = (clock - windowStart) / 60_000f - scroll
            if (nowMin in 0f..visibleMinutes) {
                Box(Modifier.offset(x = CHANNEL_COL + MINUTE * nowMin).width(2.dp).fillMaxHeight().background(Brand.Cyan.copy(alpha = 0.8f)))
            }
        }
    }
}

@Composable
private fun Details(sel: Pair<Channel, Programme>?, clock: Long, filter: Filter, count: Int, state: LoadState, onFilter: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(150.dp).padding(start = 28.dp, end = 40.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (sel == null) {
                Text("TV Guide", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(if (state is LoadState.Loading) state.label else "$count channels with guide information",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val (c, p) = sel
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelLogo(c, 28.dp)
                Text(c.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (p.isLive(clock)) LiveBadge()
            }
            Text(p.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fmtDay(p.start)}  ${fmtTime(p.start)} – ${fmtTime(p.stop)}  ·  ${fmtDuration(p.stop - p.start)}" +
                        if (p.isLive(clock)) "  ·  ${fmtDuration(p.stop - clock)} left" else "",
                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.isLive(clock)) GradientProgress(p.progress(clock), Modifier.padding(top = 6.dp).width(300.dp), height = 3.dp)
            if (!p.desc.isNullOrBlank()) {
                Text(p.desc, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.9f))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            TvButton(filterName(filter), onFilter, icon = Icons.Rounded.FilterList)
            Text("OK to watch · hold OK for options", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    number: Int,
    programmes: List<Programme>,
    windowStart: Long,
    scroll: Float,
    visibleMinutes: Float,
    clock: Long,
    playing: Boolean,
    firstFocus: FocusRequester?,
    onFocus: (Programme) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val windowEnd = windowStart + HOURS_AHEAD * 3_600_000L
    // Shows that fall in (or just around) the part of the timeline on screen; neighbours stay so Left/Right can reach them.
    val fromMs = windowStart + ((scroll - 180) * 60_000).toLong()
    val toMs = windowStart + ((scroll + visibleMinutes + 180) * 60_000).toLong()
    val shown = programmes.filter { it.stop > maxOf(fromMs, windowStart) && it.start < minOf(toMs, windowEnd) }
    val current = shown.firstOrNull { it.isLive(clock) } ?: shown.firstOrNull()

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 3.dp)) {
        // Channel column.
        Row(
            Modifier.width(CHANNEL_COL).fillMaxHeight().padding(start = 16.dp, end = 10.dp)
                .background(if (playing) Brand.Violet.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (playing) Brand.Cyan else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp).padding(start = 8.dp))
            ChannelLogo(channel, 40.dp)
            Text(channel.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp))
        }
        // Timeline.
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            for (p in shown) {
                val startMin = ((maxOf(p.start, windowStart) - windowStart) / 60_000f)
                val endMin = ((minOf(p.stop, windowEnd) - windowStart) / 60_000f)
                val x = MINUTE * (startMin - scroll)
                val w = (MINUTE * (endMin - startMin) - 4.dp).coerceAtLeast(24.dp)
                ProgrammeBlock(
                    p, clock, x, w,
                    modifier = if (firstFocus != null && p === current) Modifier.focusRequester(firstFocus) else Modifier,
                    onFocus = { onFocus(p) },
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            }
            if (shown.isEmpty()) {
                Text("No information", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    p: Programme,
    clock: Long,
    x: Dp,
    w: Dp,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val live = p.isLive(clock)
    val ended = p.stop <= clock
    Surface(
        modifier = modifier
            .offset(x = x)
            .width(w)
            .fillMaxHeight()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tvClickable(source, onClick, onLongClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> TvFocus.fill
            live -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (ended && !focused) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
        border = if (focused) BorderStroke(2.dp, TvFocus.outline) else null,
    ) {
        // Slivers (a show that started well before the timeline) stay blank rather than showing a cut-off letter.
        if (w < 56.dp) return@Surface
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) Box(Modifier.size(6.dp).background(Brand.Live, RoundedCornerShape(50)))
                Text(
                    (if (live) " " else "") + fmtTime(p.start),
                    fontSize = 12.sp,
                    color = if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (live && w > 80.dp) {
                Spacer(Modifier.height(4.dp))
                GradientProgress(p.progress(clock), Modifier.fillMaxWidth(), height = 2.dp)
            }
        }
    }
}

private val dayFmt = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
private fun fmtDay(t: Long): String {
    val today = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = t }
    return if (today.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) "Today" else dayFmt.format(java.util.Date(t))
}
