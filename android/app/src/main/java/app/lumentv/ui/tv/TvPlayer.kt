package app.lumentv.ui.tv

import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import app.lumentv.data.flag
import app.lumentv.data.countryName
import app.lumentv.data.Channel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.ClockMode
import app.lumentv.data.nowAndNext
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.Filter
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.player.AspectMode
import app.lumentv.ui.player.LivePolicy
import app.lumentv.ui.player.PlaybackState
import app.lumentv.ui.player.QualityLogic
import app.lumentv.ui.theme.Brand
import kotlinx.coroutines.delay

private enum class Overlay { None, Info, Controls }
private enum class Dialog { Subtitles, Audio, Quality, More, Picture, Sleep, Info }

/**
 * Full-screen player for the remote:
 *  - OK shows the info bar and buttons; Up/Down (or CH+/CH−) change channel.
 *  - Left, Guide or Menu opens the channel list; Back closes whatever is open, then goes Home.
 */
@Composable
fun TvPlayer(vm: MainViewModel, onOpen: (TvScreen) -> Unit, onExit: () -> Unit) {
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val app by vm.appPrefs.collectAsStateWithLifecycle()
    val channel = playback.channel

    var overlay by remember { mutableStateOf(Overlay.Info) }
    var panel by remember { mutableStateOf(false) }
    var panelMode by remember { mutableStateOf(PanelMode.Channels) }
    LaunchedEffect(panel) { if (!panel) panelMode = PanelMode.Channels }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var poke by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val messageFocus = remember { FocusRequester() }

    val error = playback.status == PlaybackState.Status.Error
    val message = error || playback.slow
    val paused = !playback.isPlaying && playback.status == PlaybackState.Status.Playing

    // Channel numbers (position in the current list) for the number keys.
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val allChannels by vm.channels.collectAsStateWithLifecycle()
    val list = filtered.ifEmpty { allChannels }
    val number = remember(list, channel?.id) { list.indexOfFirst { it.id == channel?.id }.takeIf { it >= 0 }?.plus(1) }
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_600)
        digits.toIntOrNull()?.let { n -> list.getOrNull(n - 1)?.let(vm::play) }
        digits = ""
    }

    // Nothing playing (stopped elsewhere): go back Home.
    LaunchedEffect(channel == null) { if (channel == null) onExit() }
    // Show the channel name for a moment after changing channel.
    LaunchedEffect(channel?.id) { if (!panel && overlay == Overlay.None) overlay = Overlay.Info }
    // While paused, the info bar stays up.
    LaunchedEffect(paused) { if (paused && overlay == Overlay.None && !panel) overlay = Overlay.Info }
    LaunchedEffect(overlay, poke, dialog, panel, paused) {
        if (overlay != Overlay.None && dialog == null && !panel && !paused) {
            delay(if (overlay == Overlay.Info) 4_000 else 6_000)
            overlay = Overlay.None
        }
    }
    // Keep focus where the remote expects it.
    LaunchedEffect(overlay, panel, message) {
        when {
            panel -> Unit // the list focuses its playing row
            message && overlay != Overlay.Controls -> messageFocus.requestWhenReady()
            overlay == Overlay.Controls -> playFocus.requestWhenReady()
            else -> rootFocus.requestWhenReady()
        }
    }
    LaunchedEffect(playback.status, playback.video.size) {
        if (playback.status == PlaybackState.Status.Playing && playback.video.size > 1) vm.player.probeQualities()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val k = e.key
                val menu = k == Key.Guide || k == Key.Menu
                val digit = digitOf(e.nativeKeyEvent.keyCode)
                when {
                    // Number keys type a channel number; it switches after a short pause.
                    digit != null && !panel -> { if (digits.length < 5) digits += digit; true }
                    k == Key.LastChannel -> { vm.playPrevious(); true }
                    panel -> when {
                        // Back leaves a category/country picker first, then closes the list.
                        k == Key.Back -> { if (panelMode != PanelMode.Channels) panelMode = PanelMode.Channels else panel = false; true }
                        menu -> { panel = false; true }
                        else -> false
                    }
                    overlay == Overlay.Controls -> {
                        poke++
                        when {
                            k == Key.Back || k == Key.DirectionUp || k == Key.DirectionDown -> { overlay = Overlay.None; true }
                            menu -> { overlay = Overlay.None; panel = true; true }
                            else -> false
                        }
                    }
                    message -> when {
                        k == Key.Back -> { vm.player.stop(); true }
                        k == Key.DirectionUp -> { vm.zap(-1); true }
                        k == Key.DirectionDown -> { vm.zap(1); true }
                        menu -> { panel = true; true }
                        else -> false
                    }
                    else -> when {
                        k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter || k == Key.Info || k == Key.DirectionRight -> {
                            overlay = Overlay.Controls; true
                        }
                        k == Key.DirectionUp -> { vm.zap(-1); true }
                        k == Key.DirectionDown -> { vm.zap(1); true }
                        k == Key.DirectionLeft || menu -> { overlay = Overlay.None; panel = true; true }
                        // Back closes the player: Home goes back to how it looks when the app opens.
                        k == Key.Back -> { vm.player.stop(); true }
                        else -> false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (channel == null) return@Box
        val (now, next) = epg[channel.epgKey].nowAndNext(clock)

        if ((playback.status == PlaybackState.Status.Loading || playback.reconnecting) && !playback.slow) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Brand.Cyan, strokeWidth = 5.dp)
        }

        // Clock (Settings > Show clock).
        val showClock = when (app.clock) {
            ClockMode.Off -> false
            ClockMode.WithControls -> overlay != Overlay.None || panel
            ClockMode.Always -> true
        }
        if (showClock) {
            val time by produceState(System.currentTimeMillis()) {
                while (true) { value = System.currentTimeMillis(); delay(10_000) }
            }
            Text(
                fmtTime(time),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (message) {
            Column(
                Modifier.align(Alignment.Center).width(560.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (error) "Can't play this channel" else "Still connecting…", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (error) playback.error ?: "The stream didn't respond." else "Some channels take a while to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        if (error) "Try again" else "Keep waiting",
                        onClick = { if (error) vm.player.retry() else vm.player.keepWaiting() },
                        modifier = Modifier.focusRequester(messageFocus),
                    )
                    TvButton("Next channel", { vm.zap(1) }, icon = Icons.Rounded.SkipNext)
                }
            }
        }

        if (overlay != Overlay.None && !panel) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.92f))))
                    .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 36.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(channel, 96.dp)
                    Column(Modifier.padding(start = 24.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (number != null) Text("$number", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Brand.Cyan)
                            Text(channel.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (paused) {
                                Text("PAUSED", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brand.Warn).padding(horizontal = 8.dp, vertical = 3.dp))
                            } else if (playback.isLive) LiveBadge()
                            val q = QualityLogic.chipLabel(playback.autoQuality, playback.video, playback.currentQuality, channel.quality)
                            if (playback.status == PlaybackState.Status.Playing || q != "Auto") {
                                Text(q, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Text(
                            now?.title ?: channel.groups.joinToString(" · ").ifEmpty { "No guide information" },
                            fontSize = 21.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (now != null) {
                            Text("${fmtTime(now.start)} – ${fmtTime(now.stop)}  ·  ${fmtDuration(now.stop - clock)} left", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                            GradientProgress(now.progress(clock), Modifier.padding(top = 10.dp).fillMaxWidth(), height = 5.dp)
                        }
                        if (next != null) {
                            Text("Next  ${fmtTime(next.start)}  ${next.title}", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                if (overlay == Overlay.Controls) {
                    // Scrolls if a small screen can't fit every button; focus keeps the current one visible.
                    Row(Modifier.padding(top = 24.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val playing = playback.isPlaying || playback.status == PlaybackState.Status.Loading
                        TvButton(if (playing) "Pause" else "Play", vm.player::togglePlay,
                            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(playFocus))
                        TvButton("Channels", { overlay = Overlay.None; panel = true }, icon = Icons.AutoMirrored.Rounded.List)
                        TvButton(if (playback.subtitlesOn) "Subtitles: On" else "Subtitles", { dialog = Dialog.Subtitles }, icon = Icons.Rounded.ClosedCaption)
                        TvButton("Audio", { dialog = Dialog.Audio }, icon = Icons.Rounded.Audiotrack)
                        TvButton("Quality", { dialog = Dialog.Quality }, icon = Icons.Rounded.HighQuality)
                        TvButton("More", { dialog = Dialog.More }, icon = Icons.Rounded.MoreHoriz)
                        val fav = channel.id in lib.favorites
                        TvButton(if (fav) "Saved" else "Favorite", { vm.toggleFavorite(channel) },
                            icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, selected = fav)
                        // Stop watching and go back to Home as it looks when the app opens.
                        TvButton("Close", { vm.player.stop() }, icon = Icons.Rounded.Close)
                    }
                }
            }
        }

        if (panel) ChannelPanel(vm, panelMode, onMode = { panelMode = it }, onClose = { panel = false })

        // The channel number being typed, with a preview of where it goes.
        if (digits.isNotEmpty()) {
            val target = digits.toIntOrNull()?.let { list.getOrNull(it - 1) }
            Column(
                Modifier.align(Alignment.TopStart).padding(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(digits, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Brand.Cyan)
                Text(target?.name ?: "No channel ${digits}", fontSize = 16.sp, color = Color.White, maxLines = 1)
            }
        }
    }

    PlayerDialogs(vm, dialog, onDialog = { dialog = it; poke++ }, onOpen = onOpen, onPanel = { panel = true })
}

enum class PanelMode { Channels, Categories, Countries }

/**
 * Channel list over the video: filters in a column on the left (Left/Right to move between them),
 * category and country pickers, and the channel list with what's on now and next.
 */
@Composable
private fun BoxScope.ChannelPanel(vm: MainViewModel, mode: PanelMode, onMode: (PanelMode) -> Unit, onClose: () -> Unit) {
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val all by vm.channels.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val list = filtered.ifEmpty { if (filter == Filter.All) all else emptyList() }
    val currentId = playback.channel?.id
    val start = remember(filter, list.size) { list.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val listFocus = remember { FocusRequester() }
    var focusList by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { listFocus.requestWhenReady() }
    // A new list (filter changed): start at the playing channel, or at the top.
    LaunchedEffect(filter, list.firstOrNull()?.id) { state.scrollToItem((start - 3).coerceAtLeast(0)) }
    LaunchedEffect(focusList) {
        if (focusList > 0) {
            state.scrollToItem((start - 3).coerceAtLeast(0))
            listFocus.requestWhenReady()
        }
    }
    // Moving into the filter column lands on the current filter, not whichever item is level with the row.
    val railSelected = remember { FocusRequester() }
    val favorites = remember(lib.favorites) { lib.favorites.toHashSet() }
    var options by remember { mutableStateOf<Channel?>(null) }
    options?.let { c ->
        ChannelOptions(vm, c, onPlay = { vm.play(it) }, onBrowse = { b ->
            if (b is BrowseTarget.Category) vm.filter.value = Filter.Group(b.name)
        }, onDismiss = { options = null })
    }
    val setFilter: (Filter) -> Unit = { f ->
        vm.filter.value = f
        onMode(PanelMode.Channels)
    }

    Row(
        Modifier.align(Alignment.CenterStart).width(860.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.99f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
            ))),
    ) {
        // ------------------------------------------------------------ filters
        Column(
            Modifier.width(270.dp).fillMaxHeight()
                .focusProperties { onEnter = { if (!railSelected.requestFocus()) Unit } }
                .focusGroup()
                .padding(start = 20.dp, end = 12.dp, top = 32.dp),
        ) {
            Text("Channels", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${"%,d".format(list.size)} · ${filterName(filter)}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 14.dp))
            fun Modifier.ifSelected(on: Boolean) = if (on) focusRequester(railSelected) else this
            TvRailItem(Icons.Rounded.LiveTv, "All channels", filter == Filter.All && mode == PanelMode.Channels,
                modifier = Modifier.ifSelected(filter == Filter.All)) { setFilter(Filter.All) }
            TvRailItem(Icons.Rounded.Star, "Favorites", filter == Filter.Favorites && mode == PanelMode.Channels,
                modifier = Modifier.ifSelected(filter == Filter.Favorites)) { setFilter(Filter.Favorites) }
            TvRailItem(Icons.Rounded.History, "Recently watched", filter == Filter.Recent && mode == PanelMode.Channels,
                modifier = Modifier.ifSelected(filter == Filter.Recent)) { setFilter(Filter.Recent) }
            TvRailItem(Icons.Rounded.Category, (filter as? Filter.Group)?.name ?: "Category",
                mode == PanelMode.Categories || (filter is Filter.Group && mode == PanelMode.Channels), chevron = true,
                modifier = Modifier.ifSelected(filter is Filter.Group)) { onMode(PanelMode.Categories) }
            TvRailItem(Icons.Rounded.Public, (filter as? Filter.Country)?.let { "${flag(it.code)} ${countryName(it.code)}" } ?: "Country",
                mode == PanelMode.Countries || (filter is Filter.Country && mode == PanelMode.Channels), chevron = true,
                modifier = Modifier.ifSelected(filter is Filter.Country)) { onMode(PanelMode.Countries) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 28.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))

        // ---------------------------------------------------- list or picker
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 32.dp)) {
            if (mode != PanelMode.Channels) {
                val categories = mode == PanelMode.Categories
                val items = if (categories) groups.filter { it.first != "Other" }.sortedByDescending { it.second } else countries
                val selected = when (val f = filter) {
                    is Filter.Group -> f.name.takeIf { categories }
                    is Filter.Country -> f.code.takeIf { !categories }
                    else -> null
                }
                Text(if (categories) "Categories" else "Countries", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                Text("OK to show · Back to return", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
                val first = remember(mode) { FocusRequester() }
                val startAt = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
                val pickerState = rememberLazyListState(initialFirstVisibleItemIndex = (startAt - 3).coerceAtLeast(0))
                LaunchedEffect(mode) { first.requestWhenReady() }
                LazyColumn(state = pickerState, contentPadding = PaddingValues(start = 12.dp, end = 16.dp, bottom = 24.dp)) {
                    itemsIndexed(items, key = { _, it -> it.first }) { i, (value, count) ->
                        TvPickerRow(
                            if (categories) value else "${flag(value)}  ${countryName(value)}", count, value == selected,
                            modifier = if (i == startAt) Modifier.focusRequester(first) else Modifier,
                        ) {
                            vm.filter.value = if (categories) Filter.Group(value) else Filter.Country(value)
                            onMode(PanelMode.Channels)
                            focusList++
                        }
                    }
                }
            } else {
                Text("OK to watch · hold OK for options · Left for filters", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
                if (list.isEmpty()) {
                    Text(if (filter == Filter.Favorites) "No favorites yet. Hold OK on a channel to add one." else "No channels here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
                LazyColumn(state = state, contentPadding = PaddingValues(start = 12.dp, end = 16.dp, bottom = 24.dp)) {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        val playing = c.id == currentId
                        val (now, next) = epg[c.epgKey].nowAndNext(clock)
                        TvChannelRow(
                            c, now, clock, playing,
                            next = next,
                            favorite = c.id in favorites,
                            number = i + 1,
                            onClick = { if (playing) onClose() else vm.play(c) },
                            onLongClick = { options = c },
                            modifier = if (i == start) Modifier.focusRequester(listFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

private fun filterName(f: Filter) = when (f) {
    Filter.All -> "All channels"
    Filter.Favorites -> "Favorites"
    Filter.Recent -> "Recently watched"
    is Filter.Group -> f.name
    is Filter.Country -> countryName(f.code)
}

@Composable
private fun TvRailItem(icon: ImageVector, label: String, selected: Boolean, chevron: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(14.dp),
        color = when {
            focused -> TvFocus.fill
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        contentColor = Color.White,
        border = if (focused) BorderStroke(2.dp, TvFocus.outline) else null,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (icon == Icons.Rounded.Star && !focused) Color(0xFFFFC53D) else Color.White)
            Text(label, Modifier.weight(1f).padding(start = 14.dp), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TvPickerRow(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> TvFocus.fill
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text("%,d".format(count), fontSize = 15.sp, color = if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerDialogs(vm: MainViewModel, dialog: Dialog?, onDialog: (Dialog?) -> Unit, onOpen: (TvScreen) -> Unit, onPanel: () -> Unit) {
    val s by vm.player.state.collectAsStateWithLifecycle()
    val close = { onDialog(null) }
    fun label(o: app.lumentv.ui.player.TrackOption) = listOfNotNull(o.label, o.detail).joinToString("  ·  ")
    when (dialog) {
        null -> Unit
        Dialog.Subtitles ->
            if (s.text.isEmpty()) InfoDialog("Subtitles", "This channel has no subtitles.", close)
            else TvChoiceDialog("Subtitles", listOf("Off") + s.text.map(::label), if (!s.subtitlesOn) 0 else s.text.indexOfFirst { it.selected } + 1,
                onPick = { i -> vm.player.selectSubtitle(if (i == 0) null else s.text[i - 1].id); close() }, onDismiss = close)
        Dialog.Audio ->
            if (s.audio.size <= 1) InfoDialog("Audio", s.audio.firstOrNull()?.let { "Only one audio track: ${it.label}" } ?: "This channel has one audio track.", close)
            else TvChoiceDialog("Audio", s.audio.map(::label), s.audio.indexOfFirst { it.selected },
                onPick = { i -> vm.player.selectAudio(s.audio[i].id); close() }, onDismiss = close)
        Dialog.Quality -> {
            val known = s.currentQuality ?: s.channel?.quality
            if (s.video.size <= 1) InfoDialog("Quality", known?.let { "This channel is only available in $it." } ?: "This channel has a single quality.", close)
            else TvChoiceDialog(
                "Quality",
                listOf(if (known != null && s.autoQuality) "Auto (now $known)" else "Auto") + s.video.map(::label),
                if (s.autoQuality) 0 else s.video.indexOfFirst { it.selected } + 1,
                subtitle = if (s.probingQualities) "Checking resolutions…" else null,
                onPick = { i -> vm.player.selectQuality(if (i == 0) null else s.video[i - 1].id); close() },
                onDismiss = close,
            )
        }
        Dialog.More -> {
            val behind = remember { vm.player.liveOffsetSeconds() }
            val items = buildList<Pair<String, () -> Unit>> {
                if (!LivePolicy.isAtLiveEdge(behind)) add("Go to live (${behind}s behind)" to { vm.player.goLive(); close() })
                add("Picture: ${s.aspect.label}" to { onDialog(Dialog.Picture) })
                add((s.sleepAt?.let { "Sleep timer: ${fmtDuration(it - System.currentTimeMillis())} left" } ?: "Sleep timer: Off") to { onDialog(Dialog.Sleep) })
                add("Stream info" to { onDialog(Dialog.Info) })
                vm.previousChannel.value?.let { p -> add("Previous channel: ${p.name}" to { vm.playPrevious(); close() }) }
                add("Channel list" to { close(); onPanel() })
                add("TV guide" to { close(); onOpen(TvScreen.Guide) })
                add("Reload stream" to { vm.player.retry(); close() })
            }
            TvChoiceDialog("More", items.map { it.first }, 0, onPick = { items[it].second() }, onDismiss = close, radio = false)
        }
        Dialog.Picture -> {
            val modes = AspectMode.entries
            TvChoiceDialog("Picture", modes.map { it.label }, modes.indexOf(s.aspect), onPick = { vm.player.setAspect(modes[it]); close() }, onDismiss = close)
        }
        Dialog.Sleep -> {
            val options = listOf<Int?>(null, 15, 30, 60, 90, 120)
            TvChoiceDialog(
                "Sleep timer",
                options.map { m -> if (m == null) "Off" else if (m < 60) "$m minutes" else fmtDuration(m * 60_000L) },
                if (s.sleepAt == null) 0 else -1,
                onPick = { vm.player.setSleepTimer(options[it]); close() },
                onDismiss = close,
            )
        }
        Dialog.Info -> {
            val i = remember { vm.player.streamInfo() }
            InfoDialog(
                "Stream info",
                listOfNotNull(
                    i.resolution?.let { "Resolution: $it" },
                    i.frameRate?.let { "Frame rate: $it" },
                    i.videoCodec?.let { "Video: $it" },
                    i.audioCodec?.let { "Audio: $it" + (i.audioChannels?.let { c -> " ($c)" } ?: "") },
                    i.bitrate?.let { "Bitrate: $it" },
                    "Server: ${i.host}",
                ).joinToString("\n"),
                close,
            )
        }
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, fontSize = 16.sp) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text("OK") } },
    )
}

/** 0–9 from the remote's number keys (or a keyboard's number row / keypad). */
private fun digitOf(keyCode: Int): Char? = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (keyCode - android.view.KeyEvent.KEYCODE_0)
    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0)
    else -> null
}
