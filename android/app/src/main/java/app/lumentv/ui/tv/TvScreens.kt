package app.lumentv.ui.tv

import app.lumentv.ui.Filter
import androidx.activity.compose.BackHandler
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
import app.lumentv.data.ChannelSearch
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
    val homeRows by vm.homeRows.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    // Before anything is typed: quick searches and rows of channels, so the screen isn't empty.
    val suggestions = remember(homeRows) { ChannelSearch.suggestions(homeRows).map { it.title to it.channels } }
    val quick = remember(groups) { groups.filter { it.first != "Other" }.sortedByDescending { it.second }.take(12).map { it.first } }
    val results by produceState(emptyList<Pair<String, List<Channel>>>(), query, all, epg) {
        delay(250)
        value = withContext(Dispatchers.Default) { ChannelSearch.search(all, epg, query).map { it.title to it.channels } }
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
        val blank = query.isBlank()
        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)) {
            if (blank && quick.isNotEmpty()) item(key = "quick") {
                Column {
                    Text("Quick searches", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(quick, key = { it }) { g -> TvButton(g, { query = g }, icon = Icons.Rounded.Search) }
                    }
                }
            }
            items(if (blank) suggestions else results, key = { (if (blank) "s:" else "r:") + it.first }) { (title, list) ->
                Text(if (blank) title else "$title (${list.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(list.take(60), key = { it.id }) { c ->
                        TvChannelCard(c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == playback.channel?.id,
                            onClick = { onPlay(c) }, onLongClick = { onOptions(c) })
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
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val all by vm.channels.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    // Opened from a tile (category, favorites, all): start on that list. The filter column can change it,
    // and channel up/down in the player then moves through the same list.
    LaunchedEffect(target) {
        vm.filter.value = when (target) {
            BrowseTarget.All -> Filter.All
            BrowseTarget.Favorites -> Filter.Favorites
            is BrowseTarget.Category -> Filter.Group(target.name)
        }
    }
    val list = filtered.ifEmpty { if (filter == Filter.All) all else emptyList() }
    var mode by remember { mutableStateOf(PanelMode.Channels) }
    BackHandler(enabled = mode != PanelMode.Channels) { mode = PanelMode.Channels }
    val favorites = remember(lib.favorites) { lib.favorites.toHashSet() }
    val first = remember { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var focusGrid by remember { mutableStateOf(0) }
    LaunchedEffect(list.firstOrNull()?.id, focusGrid) {
        if (list.isNotEmpty()) {
            gridState.scrollToItem(0)
            first.requestWhenReady()
        }
    }

    Row(Modifier.fillMaxSize()) {
        TvFilterRail("Channels", filter, list.size, mode,
            onFilter = { vm.filter.value = it; mode = PanelMode.Channels },
            onMode = { mode = it },
            modifier = Modifier.width(260.dp).fillMaxHeight().padding(start = 16.dp, end = 12.dp, top = 36.dp))
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 36.dp)) {
            if (mode != PanelMode.Channels) {
                TvFilterPicker(vm, mode, filter) { mode = PanelMode.Channels; focusGrid++ }
                return@Column
            }
            Text(filterName(filter), fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
            Text("${"%,d".format(list.size)} channels  ·  OK to watch  ·  hold OK for options  ·  Left for filters",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 4.dp))
            if (list.isEmpty()) {
                Text(if (filter == Filter.Favorites) "No favorites yet. Hold OK on a channel to add one." else "Nothing here yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(190.dp),
                contentPadding = PaddingValues(start = 32.dp, end = 40.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridItemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    TvChannelCard(
                        c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == playback.channel?.id,
                        width = 190.dp,
                        favorite = c.id in favorites,
                        onClick = { onPlay(c) },
                        onLongClick = { onOptions(c) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
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
        val playing = vm.player.state.value.channel?.id == c.id
        add((if (playing) "Return to the video" else "Watch") to { onDismiss(); onPlay(c) })
        if (playing) add("Stop playing" to { vm.player.stop(); onDismiss() })
        add((if (fav) "Remove from favorites" else "Add to favorites") to { vm.toggleFavorite(c); onDismiss() })
        if (group != null) add("More $group channels" to { onDismiss(); onBrowse(BrowseTarget.Category(group)) })
    }
    TvChoiceDialog(c.name, items.map { it.first }, 0, onPick = { items[it].second() }, onDismiss = onDismiss, radio = false,
        subtitle = c.groups.joinToString(" · ").ifEmpty { null })
}
