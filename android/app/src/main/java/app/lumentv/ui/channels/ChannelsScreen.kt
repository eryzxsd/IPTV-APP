package app.lumentv.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.LoadState
import app.lumentv.data.countryName
import app.lumentv.data.flag
import app.lumentv.data.nowAndNext
import app.lumentv.ui.Filter
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelRow
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.LoadingBar
import app.lumentv.ui.theme.Brand

private enum class Sheet { Groups, Countries }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: MainViewModel, onOpenPlayer: (Channel) -> Unit, contentPadding: PaddingValues) {
    val list by vm.filtered.collectAsStateWithLifecycle()
    val all by vm.channels.collectAsStateWithLifecycle()
    val state by vm.channelState.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val listState = rememberLazyListState()
    val favorites = remember(lib.favorites) { lib.favorites.toSet() }

    LaunchedEffect(filter) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        // Header + search
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title(filter), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text(
                        if (state is LoadState.Loading) "Loading…" else "${"%,d".format(list.size)} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::reloadChannels) { Icon(Icons.Rounded.Refresh, "Reload playlist") }
            }
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                placeholder = { Text("Search channels or shows", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }

        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Chip("All", filter == Filter.All) { vm.filter.value = Filter.All } }
            item { Chip("Favorites", filter == Filter.Favorites, Icons.Rounded.Star) { vm.filter.value = Filter.Favorites } }
            item { Chip("Recent", filter == Filter.Recent, Icons.Rounded.History) { vm.filter.value = Filter.Recent } }
            item {
                Chip((filter as? Filter.Group)?.name ?: "Category", filter is Filter.Group, trailing = true) { sheet = Sheet.Groups }
            }
            item {
                Chip(
                    (filter as? Filter.Country)?.let { "${flag(it.code)} ${countryName(it.code)}" } ?: "Country",
                    filter is Filter.Country,
                    trailing = true,
                ) { sheet = Sheet.Countries }
            }
            item { Chip("Hide offline", lib.hideOffline, Icons.Rounded.VisibilityOff) { vm.setHideOffline(!lib.hideOffline) } }
        }

        if (state is LoadState.Loading) LoadingBar(Modifier.padding(horizontal = 16.dp))

        Box(Modifier.weight(1f)) {
            when {
                state is LoadState.Failed && all.isEmpty() -> EmptyState(
                    Icons.Rounded.CloudOff,
                    "Couldn't load the playlist",
                    (state as LoadState.Failed).message,
                ) { FilledTonalButton(onClick = vm::reloadChannels) { Text("Try again") } }

                list.isEmpty() && state !is LoadState.Loading -> EmptyState(
                    when (filter) {
                        Filter.Favorites -> Icons.Rounded.Star
                        Filter.Recent -> Icons.Rounded.History
                        else -> Icons.Rounded.SearchOff
                    },
                    when (filter) {
                        Filter.Favorites -> "No favorites yet"
                        Filter.Recent -> "Nothing watched yet"
                        else -> "No channels found"
                    },
                    when (filter) {
                        Filter.Favorites -> "Tap the star on any channel to keep it here."
                        Filter.Recent -> "Channels you watch will show up here."
                        else -> "Try a different search or filter."
                    },
                )

                else -> LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }, contentType = { "channel" }) { c ->
                        ChannelRow(
                            channel = c,
                            now = epg[c.epgKey].nowAndNext(clock).first,
                            clock = clock,
                            active = playback.channel?.id == c.id,
                            favorite = c.id in favorites,
                            offline = lib.health[c.id] == false,
                            onClick = { onOpenPlayer(c) },
                            onFavorite = { vm.toggleFavorite(c) },
                        )
                    }
                }
            }
        }
    }

    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            PickerSheet(
                title = if (s == Sheet.Groups) "Categories" else "Countries",
                items = if (s == Sheet.Groups) groups else countries,
                label = { if (s == Sheet.Groups) it else "${flag(it)}  ${countryName(it)}" },
                searchLabel = { if (s == Sheet.Groups) it else countryName(it) },
                selected = when (val f = filter) {
                    is Filter.Group -> f.name
                    is Filter.Country -> f.code
                    else -> null
                },
                onPick = {
                    vm.filter.value = if (s == Sheet.Groups) Filter.Group(it) else Filter.Country(it)
                    sheet = null
                },
            )
        }
    }
}

private fun title(f: Filter) = when (f) {
    Filter.All -> "All channels"
    Filter.Favorites -> "Favorites"
    Filter.Recent -> "Recently watched"
    is Filter.Group -> f.name
    is Filter.Country -> "${flag(f.code)} ${countryName(f.code)}"
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: Boolean = false,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        trailingIcon = if (trailing) ({ Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) }) else null,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = if (icon == Icons.Rounded.Star) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun PickerSheet(
    title: String,
    items: List<Pair<String, Int>>,
    label: (String) -> String,
    searchLabel: (String) -> String,
    selected: String?,
    onPick: (String) -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, q) { if (q.isBlank()) items else items.filter { searchLabel(it.first).contains(q.trim(), true) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.first }) { (value, count) ->
                Surface(
                    onClick = { onPick(value) },
                    shape = MaterialTheme.shapes.small,
                    color = if (value == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(label(value), fontWeight = FontWeight.Medium) },
                        trailingContent = {
                            Text("%,d".format(count), color = if (value == selected) Brand.Cyan else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }
            }
        }
    }
}
