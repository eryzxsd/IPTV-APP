package app.lumentv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.LoadState
import app.lumentv.data.Programme
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.GuideFailuresCard
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand
import kotlinx.coroutines.launch
import java.util.Calendar

private val DP_PER_MIN = 4.dp
private val ROW_H = 68.dp
private val CHANNEL_W = 128.dp
private const val SPAN_HOURS = 26

@Composable
fun GuideScreen(vm: MainViewModel, onPlay: (Channel) -> Unit, contentPadding: PaddingValues) {
    val channels by vm.filtered.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val epgState by vm.epgState.collectAsStateWithLifecycle()
    val failures by vm.epgFailures.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playing by vm.player.state.collectAsStateWithLifecycle()
    var onlyWithGuide by rememberSaveable { mutableStateOf(true) }

    val start = remember(clock / 3_600_000) {
        Calendar.getInstance().apply {
            timeInMillis = clock
            set(Calendar.MINUTE, if (get(Calendar.MINUTE) < 30) 0 else 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis - 3_600_000
    }
    val end = start + SPAN_HOURS * 3_600_000L
    val totalWidth = DP_PER_MIN * ((end - start) / 60_000f)
    fun x(t: Long) = DP_PER_MIN * ((t - start) / 60_000f)

    val rows = remember(channels, epg, onlyWithGuide) { if (onlyWithGuide) channels.filter { !epg[it.epgKey].isNullOrEmpty() } else channels }
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val jumpToNow: () -> Unit = {
        scope.launch { hScroll.animateScrollTo(with(density) { (x(System.currentTimeMillis()) - 40.dp).roundToPx().coerceAtLeast(0) }) }
    }
    LaunchedEffect(start, rows.isNotEmpty()) {
        hScroll.scrollTo(with(density) { (x(System.currentTimeMillis()) - 40.dp).roundToPx().coerceAtLeast(0) })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TV Guide", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (val s = epgState) {
                        is LoadState.Loading -> s.label
                        else -> "${"%,d".format(rows.size)} channels"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilterChip(selected = onlyWithGuide, onClick = { onlyWithGuide = !onlyWithGuide }, label = { Text("With guide") }, shape = RoundedCornerShape(50))
            IconButton(onClick = jumpToNow) { Icon(Icons.Rounded.MyLocation, "Jump to now") }
        }

        GuideFailuresCard(failures, epgState, onRetry = { vm.retryFailedEpg() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

        if (epg.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (epgState is LoadState.Loading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = Brand.Cyan)
                        Text("Downloading the TV guide from epgshare01…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    EmptyState(Icons.Rounded.CalendarViewDay, "No guide data", "Pick TV guide regions in Settings that match your channels.")
                }
            }
            return@Column
        }

        // Time ruler
        Row(Modifier.fillMaxWidth().height(36.dp).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Box(Modifier.width(CHANNEL_W).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(dayLabel(clock), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
            }
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    var t = start
                    while (t < end) {
                        Text(
                            fmtTime(t),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = x(t)).align(Alignment.CenterStart).padding(start = 8.dp),
                        )
                        t += 30 * 60_000L
                    }
                    Box(Modifier.offset(x = x(clock)).width(2.dp).fillMaxHeight().background(Brand.Live))
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(rows, key = { it.id }) { c ->
                GuideRow(
                    channel = c,
                    programmes = epg[c.epgKey].orEmpty(),
                    clock = clock,
                    start = start,
                    end = end,
                    active = playing.channel?.id == c.id,
                    totalWidth = totalWidth,
                    x = ::x,
                    hScroll = hScroll,
                    onPlay = { onPlay(c) },
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>,
    clock: Long,
    start: Long,
    end: Long,
    active: Boolean,
    totalWidth: androidx.compose.ui.unit.Dp,
    x: (Long) -> androidx.compose.ui.unit.Dp,
    hScroll: androidx.compose.foundation.ScrollState,
    onPlay: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scrolledDp = with(LocalDensity.current) { hScroll.value.toDp() }
    Row(Modifier.fillMaxWidth().height(ROW_H)) {
        Row(
            Modifier
                .width(CHANNEL_W)
                .fillMaxHeight()
                .background(if (active) colors.primaryContainer.copy(alpha = 0.5f) else colors.surfaceContainerLow)
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelLogo(channel, 34.dp)
            Text(channel.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                for (p in programmes) {
                    if (p.stop <= start || p.start >= end) continue
                    val left = x(maxOf(p.start, start))
                    val width = x(minOf(p.stop, end)) - left
                    val live = p.isLive(clock)
                    val past = p.stop <= clock
                    // Keep the title readable when the show started before the visible area.
                    val pinned = (scrolledDp - left).coerceIn(0.dp, (width - 90.dp).coerceAtLeast(0.dp))
                    Column(
                        Modifier
                            .offset(x = left + 2.dp)
                            .width((width - 4.dp).coerceAtLeast(2.dp))
                            .fillMaxHeight()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (live) colors.primaryContainer else colors.surfaceContainerHigh.copy(alpha = if (past) 0.5f else 1f))
                            .then(if (live) Modifier.border(1.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
                            .clickable(onClick = onPlay)
                            .padding(start = 10.dp + pinned, end = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            p.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (past) colors.onSurfaceVariant else colors.onSurface,
                        )
                        Text(
                            if (width - pinned > 170.dp) "${fmtTime(p.start)} – ${fmtTime(p.stop)}" else fmtTime(p.start),
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box(Modifier.offset(x = x(clock)).width(2.dp).fillMaxHeight().background(Brand.Live.copy(alpha = 0.9f)))
                Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.05f)))
            }
        }
    }
}

private fun dayLabel(t: Long): String = java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).format(java.util.Date(t))
