package app.lumentv.ui.tv

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.LoadState
import app.lumentv.data.Programme
import app.lumentv.data.nowAndNext
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ guide

/** Two-pane guide: channels with guide data on the left, the focused channel's schedule on the right. */
@Composable
fun TvGuide(vm: MainViewModel, onPlay: (Channel) -> Unit, onOptions: (Channel) -> Unit) {
    val all by vm.channels.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val state by vm.epgState.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val withGuide = remember(all, epg) { all.filter { !epg[it.epgKey].isNullOrEmpty() } }
    var selected by remember { mutableStateOf<Channel?>(null) }
    val start = remember(withGuide.isEmpty()) { withGuide.indexOfFirst { it.id == playback.channel?.id }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(withGuide.isNotEmpty()) { if (withGuide.isNotEmpty()) focus.requestWhenReady() }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(480.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 40.dp)) {
            Text("TV Guide", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
            Text(
                when {
                    state is LoadState.Loading -> (state as LoadState.Loading).label
                    withGuide.isEmpty() -> "No guide data yet. Choose guide regions in Settings."
                    else -> "${withGuide.size} of ${all.size} channels have guide data"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
            )
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
                itemsIndexed(withGuide, key = { _, c -> c.id }) { i, c ->
                    TvChannelRow(
                        c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == playback.channel?.id,
                        onClick = { onPlay(c) },
                        onLongClick = { onOptions(c) },
                        onFocused = { selected = c },
                        modifier = if (i == start) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
        val c = selected
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp, end = 48.dp, top = 44.dp)) {
            if (c == null) return@Column
            val list = remember(c, epg, clock / 60_000) { epg[c.epgKey].orEmpty().filter { it.stop > clock } }
            Text(c.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (list.isEmpty()) "No guide information" else "OK to watch · Right to browse the schedule", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.start }) { p -> ProgrammeRow(p, clock) { onPlay(c) } }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(p: Programme, clock: Long, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val live = p.isLive(clock)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> TvFocus.fill
            live -> Color.White.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        contentColor = Color.White,
        interactionSource = source,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(fmtTime(p.start), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp),
                color = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (live) LiveBadge()
                }
                if (!p.desc.isNullOrBlank()) {
                    Text(p.desc, fontSize = 15.sp, maxLines = if (focused) 4 else 2, overflow = TextOverflow.Ellipsis,
                        color = if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(fmtDuration(p.stop - p.start), fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp),
                color = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ----------------------------------------------------------------- search

/** Search channels by name, category or what's on now; the mic button uses the remote's voice search. */
@Composable
fun TvSearch(vm: MainViewModel, onPlay: (Channel) -> Unit, onOptions: (Channel) -> Unit) {
    // Not saved: a new visit (or coming back to the app) starts with an empty search.
    var query by remember { mutableStateOf("") }
    val all by vm.channels.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestWhenReady() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }
    val results by produceState(emptyList<Pair<String, List<Channel>>>(), query, all, epg) {
        delay(250)
        val needle = query.trim().lowercase()
        value = if (needle.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val byName = all.filter { it.name.lowercase().contains(needle) }
                .sortedWith(compareBy<Channel>({ !it.name.lowercase().startsWith(needle) }, { it.name.length }))
            val names = byName.map { it.id }.toHashSet()
            val onNow = all.filter { it.id !in names && epg[it.epgKey].nowAndNext(now).first?.title?.lowercase()?.contains(needle) == true }
            val inGroup = all.filter { it.id !in names && it.groups.any { g -> g.lowercase().contains(needle) } }
            listOf("Channels" to byName, "On now" to onNow, "Categories" to inGroup).filter { it.second.isNotEmpty() }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search channels, categories and programmes") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
            Spacer(Modifier.width(16.dp))
            TvButton("Voice", {
                runCatching {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                }
            }, icon = Icons.Rounded.Mic)
        }
        if (query.isNotBlank() && results.isEmpty()) {
            Text("No results for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(48.dp))
        }
        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)) {
            items(results, key = { it.first }) { (title, list) ->
                Text("$title (${list.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(list.take(60), key = { it.id }) { c ->
                        TvChannelCard(c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = false, onClick = { onPlay(c) }, onLongClick = { onOptions(c) })
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- settings

/** The phone app's Settings (every option), laid out for a TV screen. */
@Composable
fun TvSettings(vm: MainViewModel) {
    // Start on the first option.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        repeat(5) {
            kotlinx.coroutines.android.awaitFrame()
            if (focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)) return@LaunchedEffect
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 920.dp).fillMaxSize().padding(top = 24.dp)) {
            SettingsScreen(vm, contentPadding = PaddingValues(bottom = 24.dp))
        }
    }
}

// ----------------------------------------------------------------- browse

/** Every channel in a category (or all channels / favorites), as a grid. */
@Composable
fun TvBrowse(vm: MainViewModel, target: BrowseTarget, onPlay: (Channel) -> Unit, onOptions: (Channel) -> Unit) {
    val all by vm.channels.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val list = remember(all, lib.favorites, target) {
        when (target) {
            BrowseTarget.All -> all
            BrowseTarget.Favorites -> all.associateBy { it.id }.let { m -> lib.favorites.mapNotNull(m::get) }
            is BrowseTarget.Category -> all.filter { target.name in it.groups }
        }
    }
    val title = when (target) {
        BrowseTarget.All -> "All channels"
        BrowseTarget.Favorites -> "Favorites"
        is BrowseTarget.Category -> target.name
    }
    val favorites = remember(lib.favorites) { lib.favorites.toHashSet() }
    val first = remember { FocusRequester() }
    LaunchedEffect(list.isNotEmpty()) { if (list.isNotEmpty()) first.requestWhenReady() }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
        Text("${list.size} channels  ·  OK to watch  ·  hold OK for options", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 8.dp))
        if (list.isEmpty()) {
            Text("Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(48.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                TvChannelCard(
                    c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == playback.channel?.id,
                    width = 200.dp,
                    favorite = c.id in favorites,
                    onClick = { onPlay(c) },
                    onLongClick = { onOptions(c) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- options

/** Long-press OK (or Menu) on a channel. */
@Composable
fun ChannelOptions(vm: MainViewModel, c: Channel, onPlay: (Channel) -> Unit, onBrowse: (BrowseTarget) -> Unit, onDismiss: () -> Unit) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val fav = c.id in lib.favorites
    val group = c.groups.firstOrNull { it != "Other" }
    val items = buildList<Pair<String, () -> Unit>> {
        add("Watch" to { onDismiss(); onPlay(c) })
        add((if (fav) "Remove from favorites" else "Add to favorites") to { vm.toggleFavorite(c); onDismiss() })
        if (group != null) add("More $group channels" to { onDismiss(); onBrowse(BrowseTarget.Category(group)) })
    }
    TvChoiceDialog(c.name, items.map { it.first }, 0, onPick = { items[it].second() }, onDismiss = onDismiss, radio = false,
        subtitle = c.groups.joinToString(" · ").ifEmpty { null })
}
