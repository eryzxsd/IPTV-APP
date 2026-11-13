package app.lumentv.ui.player

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

@Composable
private fun Panel(vm: MainViewModel, onClose: () -> Unit) {
    val list by vm.filtered.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val currentId = playback.channel?.id

    // Open scrolled to the channel that's playing.
    LaunchedEffect(Unit) {
        val i = list.indexOfFirst { it.id == currentId }
        if (i > 0) state.scrollToItem((i - 2).coerceAtLeast(0))
    }

    Surface(
        Modifier.fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
    ) {
        Column(Modifier.safeDrawingPadding().padding(top = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Channels", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${"%,d".format(list.size)} · ${filterLabel(filter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            }
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search channels or shows", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PanelChip("All", filter == Filter.All) { vm.filter.value = Filter.All } }
                item { PanelChip("Favorites", filter == Filter.Favorites, Icons.Rounded.Star) { vm.filter.value = Filter.Favorites } }
                item { PanelChip("Recent", filter == Filter.Recent, Icons.Rounded.History) { vm.filter.value = Filter.Recent } }
                (filter as? Filter.Group)?.let { g -> item { PanelChip(g.name, true) {} } }
                (filter as? Filter.Country)?.let { c -> item { PanelChip("${flag(c.code)} ${countryName(c.code)}", true) {} } }
            }
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) {
                Text(
                    "No channels match.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(state = state, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                items(list, key = { it.id }) { c ->
                    PanelRow(
                        channel = c,
                        now = epg[c.epgKey].nowAndNext(clock).first,
                        clock = clock,
                        active = c.id == currentId,
                        favorite = c.id in lib.favorites,
                        onClick = {
                            if (c.id != currentId) vm.play(c)
                            onClose()
                        },
                    )
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
private fun PanelChip(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLeadingIconColor = if (icon == Icons.Rounded.Star) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun PanelRow(channel: Channel, now: Programme?, clock: Long, active: Boolean, favorite: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(MaterialTheme.shapes.medium).channelClickable(channel, onClick),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    if (favorite) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC53D), modifier = Modifier.size(14.dp))
                    if (active) Tag("PLAYING", Color.White, Brand.Live)
                }
                if (now != null) {
                    Text(now.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    GradientProgress(now.progress(clock), Modifier.width(140.dp), height = 2.dp)
                } else {
                    Text(channel.groups.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
