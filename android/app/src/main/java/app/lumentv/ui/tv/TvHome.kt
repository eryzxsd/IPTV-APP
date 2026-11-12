package app.lumentv.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.EpgData
import app.lumentv.data.LoadState
import app.lumentv.data.nowAndNext
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand

/** Home: what's playing, then rows of recommendations, with Search, Guide and Settings at the top. */
@Composable
fun TvHome(vm: MainViewModel, onPlay: (Channel) -> Unit, onOpen: (TvScreen) -> Unit) {
    val rows by vm.homeRows.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val last by vm.lastChannel.collectAsStateWithLifecycle()
    val load by vm.channelState.collectAsStateWithLifecycle()
    val current = playback.channel
    val first = remember { FocusRequester() }
    val hasContent = rows.isNotEmpty() || current != null || last != null
    // Start on "Now playing" (or the first recommendation), also when coming back from the player.
    LaunchedEffect(hasContent, (current ?: last)?.id) { if (hasContent) first.requestWhenReady() }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        TopBar(clock, onOpen, Modifier.padding(horizontal = 48.dp))
        when {
            !hasContent && load is LoadState.Failed -> Message(
                "Couldn't load channels",
                (load as LoadState.Failed).message,
                action = "Try again",
                onAction = { vm.reloadChannels() },
            )
            !hasContent -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Brand.Cyan)
                    Spacer(Modifier.height(16.dp))
                    Text((load as? LoadState.Loading)?.label ?: "Loading channels…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hero = current ?: last
                if (hero != null) item(key = "hero") {
                    val now = epg[hero.epgKey].nowAndNext(clock)
                    Section(if (current != null) "Now playing" else "Continue watching", null) {
                        Row(
                            Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvChannelCard(
                                hero, now.first, clock, playing = current != null, width = 300.dp,
                                onClick = { onPlay(hero) },
                                modifier = Modifier.focusRequester(first),
                            )
                            Column(Modifier.padding(start = 32.dp).width(560.dp)) {
                                Text(hero.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                now.first?.let {
                                    Text(it.title, fontSize = 20.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                                    Text("${fmtTime(it.start)} – ${fmtTime(it.stop)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                now.second?.let {
                                    Text("Next  ${fmtTime(it.start)}  ${it.title}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                TvButton(if (current != null) "Watch" else "Resume", onClick = { onPlay(hero) }, icon = Icons.Rounded.PlayArrow)
                            }
                        }
                    }
                }
                itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
                    Section(row.title, row.subtitle) {
                        CardRow(row.channels, epg, clock, current?.id, onPlay, if (hero == null && i == 0) first else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(clock: Long, onOpen: (TvScreen) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append("Lumen")
                withStyle(SpanStyle(color = Brand.Cyan)) { append(" TV") }
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.weight(1f))
        Text(fmtTime(clock), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        TvButton("Search", { onOpen(TvScreen.Search) }, icon = Icons.Rounded.Search)
        Spacer(Modifier.width(12.dp))
        TvButton("Guide", { onOpen(TvScreen.Guide) }, icon = Icons.Rounded.CalendarViewDay)
        Spacer(Modifier.width(12.dp))
        TvButton("Settings", { onOpen(TvScreen.Settings) }, icon = Icons.Rounded.Settings)
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, bottom = 2.dp))
            }
        }
        content()
    }
}

@Composable
private fun CardRow(channels: List<Channel>, epg: EpgData, clock: Long, currentId: String?, onPlay: (Channel) -> Unit, firstFocus: FocusRequester?) {
    // Lazy rows keep their scroll anchored to an item when the list changes; start at the front again.
    val state = rememberLazyListState()
    LaunchedEffect(channels.firstOrNull()?.id) { if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
            TvChannelCard(
                c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == currentId,
                onClick = { onPlay(c) },
                modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
fun Message(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}, secondary: String? = null, onSecondary: () -> Unit = {}) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(action) { if (action != null) focus.requestWhenReady() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(560.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (body != null) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (action != null) TvButton(action, onAction, icon = Icons.Rounded.Refresh, modifier = Modifier.focusRequester(focus))
                if (secondary != null) TvButton(secondary, onSecondary)
            }
        }
    }
}
