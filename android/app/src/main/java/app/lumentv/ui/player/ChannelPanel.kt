package app.lumentv.ui.player

import app.lumentv.ui.components.fmtDuration
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch
import app.lumentv.ui.components.fmtTime
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import app.lumentv.ui.components.channelClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.Programme
import app.lumentv.data.countryName
import app.lumentv.data.flag
import app.lumentv.data.nowAndNext
import app.lumentv.ui.Filter
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.Tag
import app.lumentv.ui.theme.Brand

/** Channel browser shown over the video in fullscreen, so you can switch without leaving it. */
@Composable
fun BoxScope.ChannelPanel(vm: MainViewModel, visible: Boolean, onClose: () -> Unit) {
    // Tap outside the panel to close it.
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it },
        modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
    ) {
        Panel(vm, onClose)
    }
}

private enum class PanelMode { Channels, Categories, Countries }

@Composable
private fun Panel(vm: MainViewModel, onClose: () -> Unit) {
    val list by vm.filtered.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val previous by vm.previousChannel.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentId = playback.channel?.id
    val favorites = remember(lib.favorites) { lib.favorites.toHashSet() }
    var mode by remember { mutableStateOf(PanelMode.Channels) }
    var pickerQuery by remember { mutableStateOf("") }
    val backToChannels = { mode = PanelMode.Channels; pickerQuery = "" }
    // Back steps out of a picker first, then closes the panel.
    BackHandler(enabled = mode != PanelMode.Channels) { backToChannels() }

    val currentIndex = list.indexOfFirst { it.id == currentId }
    // Open scrolled to the channel that's playing.
    LaunchedEffect(Unit) { if (currentIndex > 0) state.scrollToItem((currentIndex - 2).coerceAtLeast(0)) }
    val awayFromCurrent by remember(currentIndex) {
        derivedStateOf { currentIndex >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == currentIndex } }
    }
    val pick: (PanelMode) -> Unit = { mode = it; pickerQuery = "" }
    val previousChannel = previous?.takeIf { it.id != currentId }

    val pickerItems = if (mode == PanelMode.Categories) groups.filter { it.first != "Other" }.sortedByDescending { it.second } else countries
    @Composable
    fun PickerContent() = Picker(
        items = pickerItems,
        query = pickerQuery,
        onQuery = { pickerQuery = it },
        label = { if (mode == PanelMode.Categories) it else "${flag(it)}  ${countryName(it)}" },
        searchText = { if (mode == PanelMode.Categories) it else countryName(it) },
        selected = when (val f = filter) {
            is Filter.Group -> f.name.takeIf { mode == PanelMode.Categories }
            is Filter.Country -> f.code.takeIf { mode == PanelMode.Countries }
            else -> null
        },
        onPick = {
            vm.filter.value = if (mode == PanelMode.Categories) Filter.Group(it) else Filter.Country(it)
            backToChannels()
            scope.launch { state.scrollToItem(0) }
        },
    )

    @Composable
    fun ChannelList(modifier: Modifier) = Box(modifier) {
        if (list.isEmpty()) {
            Text(
                if (query.isNotBlank()) "No channels match \"${query.trim()}\"." else "No channels here yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(state = state, contentPadding = PaddingValues(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 12.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                val (now, next) = epg[c.epgKey].nowAndNext(clock)
                PanelRow(
                    channel = c, number = i + 1, now = now, next = next, clock = clock,
                    active = c.id == currentId, favorite = c.id in favorites,
                    onFavorite = { vm.toggleFavorite(c) },
                    onClick = {
                        if (c.id != currentId) vm.play(c)
                        onClose()
                    },
                )
            }
        }
        if (awayFromCurrent) {
            SmallFloatingActionButton(
                onClick = { scope.launch { state.animateScrollToItem((currentIndex - 2).coerceAtLeast(0)) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Rounded.MyLocation, "Go to the channel that's playing") }
        }
    }

    BoxWithConstraints {
        // Landscape phones and tablets: filters in a column beside the list, so more channels fit.
        val wide = maxWidth >= 600.dp
        val bg = MaterialTheme.colorScheme.surfaceContainerLow
        Surface(
            Modifier.fillMaxHeight().then(if (wide) Modifier.widthIn(max = 780.dp).fillMaxWidth(0.66f) else Modifier.widthIn(max = 440.dp).fillMaxWidth()),
            color = Color.Transparent,
            shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg.copy(alpha = 0.99f), bg.copy(alpha = 0.94f))))) {
                if (wide) {
                    Row(Modifier.safeDrawingPadding()) {
                        FilterRail(
                            filter = filter, count = list.size, previous = previousChannel, mode = mode,
                            onFilter = { vm.filter.value = it; backToChannels(); scope.launch { state.scrollToItem(0) } },
                            onPick = pick, onPrevious = vm::playPrevious,
                            modifier = Modifier.width(220.dp).fillMaxHeight(),
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (mode != PanelMode.Channels) {
                                    IconButton(onClick = backToChannels) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to channels") }
                                    Text(if (mode == PanelMode.Categories) "Categories" else "Countries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                } else {
                                    SearchField(query, { vm.query.value = it }, "Search channels or shows", Modifier.weight(1f).padding(start = 4.dp))
                                }
                                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
                            }
                            if (mode != PanelMode.Channels) PickerContent() else ChannelList(Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(Modifier.safeDrawingPadding().padding(top = 8.dp)) {
                        Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (mode != PanelMode.Channels) {
                                IconButton(onClick = backToChannels) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to channels") }
                            } else Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (mode) {
                                        PanelMode.Channels -> "Channels"
                                        PanelMode.Categories -> "Categories"
                                        PanelMode.Countries -> "Countries"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (mode == PanelMode.Channels) {
                                    Text("${"%,d".format(list.size)} · ${filterLabel(filter)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
                        }
                        if (mode != PanelMode.Channels) {
                            PickerContent()
                            return@Column
                        }
                        SearchField(query, { vm.query.value = it }, "Search channels or shows", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { PanelChip("All", filter == Filter.All) { vm.filter.value = Filter.All } }
                            item { PanelChip("Favorites", filter == Filter.Favorites, Icons.Rounded.Star) { vm.filter.value = Filter.Favorites } }
                            item { PanelChip("Recent", filter == Filter.Recent, Icons.Rounded.History) { vm.filter.value = Filter.Recent } }
                            item { PanelChip((filter as? Filter.Group)?.name ?: "Category", filter is Filter.Group, trailing = true) { pick(PanelMode.Categories) } }
                            item {
                                PanelChip((filter as? Filter.Country)?.let { "${flag(it.code)} ${countryName(it.code)}" } ?: "Country", filter is Filter.Country, trailing = true) {
                                    pick(PanelMode.Countries)
                                }
                            }
                        }
                        previousChannel?.let { PreviousCard(it, vm::playPrevious, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) }
                        Spacer(Modifier.height(6.dp))
                        ChannelList(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Filters as a column (wide panel): lists, category, country, and the previous channel. */
@Composable
private fun FilterRail(
    filter: Filter,
    count: Int,
    previous: Channel?,
    mode: PanelMode,
    onFilter: (Filter) -> Unit,
    onPick: (PanelMode) -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 10.dp, top = 14.dp, bottom = 12.dp)) {
        Text("Channels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${"%,d".format(count)} · ${filterLabel(filter)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        RailItem(Icons.Rounded.LiveTv, "All channels", selected = filter == Filter.All && mode == PanelMode.Channels) { onFilter(Filter.All) }
        RailItem(Icons.Rounded.Star, "Favorites", selected = filter == Filter.Favorites && mode == PanelMode.Channels, iconTint = Color(0xFFFFC53D)) { onFilter(Filter.Favorites) }
        RailItem(Icons.Rounded.History, "Recently watched", selected = filter == Filter.Recent && mode == PanelMode.Channels) { onFilter(Filter.Recent) }
        RailItem(
            Icons.Rounded.Category, (filter as? Filter.Group)?.name ?: "Category",
            selected = mode == PanelMode.Categories || (filter is Filter.Group && mode == PanelMode.Channels), chevron = true,
        ) { onPick(PanelMode.Categories) }
        RailItem(
            Icons.Rounded.Public, (filter as? Filter.Country)?.let { "${flag(it.code)} ${countryName(it.code)}" } ?: "Country",
            selected = mode == PanelMode.Countries || (filter is Filter.Country && mode == PanelMode.Channels), chevron = true,
        ) { onPick(PanelMode.Countries) }
        if (previous != null) {
            Spacer(Modifier.height(12.dp))
            PreviousCard(previous, onPrevious)
        }
    }
}

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    chevron: Boolean = false,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint ?: androidx.compose.material3.LocalContentColor.current)
            Text(label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
    }
}

/** "Previous" shortcut: back to the channel you watched before this one. */
@Composable
private fun PreviousCard(p: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(p, 32.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("PREVIOUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Brand.Cyan)
                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = 48.dp),
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

/** Category or country list inside the panel, with its own search. */
@Composable
private fun Picker(
    items: List<Pair<String, Int>>,
    query: String,
    onQuery: (String) -> Unit,
    label: (String) -> String,
    searchText: (String) -> String,
    selected: String?,
    onPick: (String) -> Unit,
) {
    val shown = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { searchText(it.first).lowercase().contains(q) }
    }
    SearchField(query, onQuery, "Filter", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
        items(shown, key = { it.first }) { (value, count) ->
            Surface(
                onClick = { onPick(value) },
                shape = RoundedCornerShape(12.dp),
                color = if (value == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label(value), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%,d".format(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun filterLabel(f: Filter) = when (f) {
    Filter.All -> "All channels"
    Filter.Favorites -> "Favorites"
    Filter.Recent -> "Recently watched"
    is Filter.Group -> f.name
    is Filter.Country -> countryName(f.code)
}

@Composable
private fun PanelChip(
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
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
        trailingIcon = if (trailing) ({ Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) }) else null,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLeadingIconColor = if (icon == Icons.Rounded.Star) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun PanelRow(
    channel: Channel,
    number: Int,
    now: Programme?,
    next: Programme?,
    clock: Long,
    active: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape)
            .then(
                if (active) Modifier.background(Brush.horizontalGradient(listOf(Brand.Violet.copy(alpha = 0.38f), Brand.Cyan.copy(alpha = 0.10f))))
                else Modifier,
            )
            .channelClickable(channel, onClick),
    ) {
        // Accent bar on the channel that's playing.
        if (active) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Brand.gradient))
        Row(Modifier.padding(start = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Brand.Cyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.width(36.dp),
                maxLines = 1,
            )
            ChannelLogo(channel, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false), fontSize = 14.sp)
                    if (active) {
                        Text("PLAYING", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Color.Black,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Brand.Cyan).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                if (now != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(now.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text("  ${fmtDuration(now.stop - clock)} left", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                    }
                    GradientProgress(now.progress(clock), Modifier.padding(top = 4.dp).fillMaxWidth(0.7f), height = 2.dp)
                    if (next != null) {
                        Text("Next ${fmtTime(next.start)} · ${next.title}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                } else {
                    Text(channel.groups.joinToString(" · ").ifEmpty { "No guide information" }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
