package app.lumentv.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import app.lumentv.ui.player.HomeScreen
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lumentv.ui.components.ChannelOptionsSheet
import app.lumentv.ui.search.SearchScreen
import app.lumentv.ui.components.LocalChannelOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.nowAndNext
import app.lumentv.ui.channels.ChannelsScreen
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.guide.GuideScreen
import app.lumentv.ui.player.PlaybackState
import app.lumentv.ui.settings.SettingsScreen
import app.lumentv.ui.theme.Brand

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Channels("Channels", Icons.Rounded.LiveTv),
    Guide("Guide", Icons.Rounded.CalendarViewDay),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun LumenNav(vm: MainViewModel, inPip: Boolean, onPlayerVisible: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val userFullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val playing = playback.channel != null
    val minimized by vm.minimized.collectAsStateWithLifecycle()
    val playerShown = tab == Tab.Home && playing && !minimized
    LaunchedEffect(playerShown) { onPlayerVisible(playerShown) }
    // Stopped (the mini player's ✕, or elsewhere): next time a channel opens in the full player.
    LaunchedEffect(playing) { if (!playing) vm.minimized.value = false }

    // Picking a channel anywhere plays it on Home, in the full player.
    val open: (Channel) -> Unit = { c ->
        if (vm.player.state.value.channel?.id != c.id || vm.player.state.value.status == PlaybackState.Status.Error) vm.play(c)
        vm.minimized.value = false
        tab = Tab.Home
    }
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    // Category tiles and "More … channels": show that list in Channels.
    val browse: (String?) -> Unit = { g ->
        vm.query.value = ""
        vm.filter.value = when (g) {
            null -> Filter.All
            app.lumentv.ui.home.FAVORITES -> Filter.Favorites
            else -> Filter.Group(g)
        }
        tab = Tab.Channels
    }
    // Search from Home's search button.
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != Tab.Home) searching = false }
    BackHandler(enabled = searching) { searching = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { searching = false }
    // Long-press on any channel.
    var options by remember { mutableStateOf<Channel?>(null) }
    // A search isn't kept: coming back to the app starts without the old results.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.query.value = "" }

    val config = LocalConfiguration.current
    val phoneLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.smallestScreenWidthDp < 600
    val videoFullscreen = playerShown && (inPip || userFullscreen || phoneLandscape)
    val adaptive = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    CompositionLocalProvider(LocalChannelOptions provides { c: Channel -> options = c }) {
    // Surface provides the theme's content colour to every screen.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Tab.entries.forEach { t ->
                    item(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            },
            layoutType = if (videoFullscreen) NavigationSuiteType.None else adaptive,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            // Idle Home's banner runs under the status bar (it pads its own top bar).
            val edgeToEdge = videoFullscreen || (tab == Tab.Home && !playerShown && !searching)
            Column(Modifier.fillMaxSize().then(if (edgeToEdge) Modifier else Modifier.statusBarsPadding())) {
                Box(Modifier.weight(1f)) {
                    val pad = PaddingValues(bottom = 12.dp)
                    when (tab) {
                        Tab.Home -> if (searching) {
                            SearchScreen(vm, onPlay = { c -> searching = false; open(c) }, onBrowse = { g -> searching = false; browse(g) }, onClose = { searching = false })
                        } else {
                            HomeScreen(vm, inPip = inPip, onOpen = open, onBrowse = browse, onSearch = { searching = true })
                        }
                        Tab.Channels -> ChannelsScreen(vm, onOpenPlayer = open, contentPadding = pad)
                        Tab.Guide -> GuideScreen(vm, onPlay = open, contentPadding = pad)
                        Tab.Settings -> SettingsScreen(vm, contentPadding = pad)
                    }
                }
                // On other tabs, and on Home while the player is minimized.
                if (tab != Tab.Home || (playing && minimized)) {
                    MiniPlayer(vm, onOpen = { vm.minimized.value = false; tab = Tab.Home }, onResume = open)
                }
            }
        }
    }
    options?.let { c -> ChannelOptionsSheet(vm, c, onPlay = open, onBrowse = browse, onDismiss = { options = null }) }
    }
}

/** Shows what's playing (or the last channel) while browsing; tap to open the player. */
@Composable
private fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit, onResume: (Channel) -> Unit) {
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val last by vm.lastChannel.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val channel = playback.channel ?: last
    AnimatedVisibility(channel != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val c = channel ?: return@AnimatedVisibility
        val now = epg[c.epgKey].nowAndNext(clock).first
        val expand = { if (playback.channel == null) onResume(c) else onOpen() }
        // Swipe up opens the full player; the card follows the finger and springs back otherwise.
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val threshold = with(LocalDensity.current) { 48.dp.toPx() }
        Surface(
            onClick = expand,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                .graphicsLayer { translationY = drag.value }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy -> scope.launch { drag.snapTo((drag.value + dy).coerceIn(-threshold * 2, 0f)) } },
                    onDragStopped = { velocity ->
                        if (drag.value < -threshold || velocity < -1500f) expand()
                        drag.animateTo(0f)
                    },
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
        ) {
            Column {
                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(c, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                playback.channel == null -> "Tap to resume"
                                playback.status == PlaybackState.Status.Error -> "Can't play this channel"
                                playback.reconnecting -> "Reconnecting…"
                                playback.slow -> "Still connecting…"
                                now != null -> now.title
                                else -> "Live"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (playback.status == PlaybackState.Status.Error) Brand.Live else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        playback.status == PlaybackState.Status.Loading ->
                            CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp, color = Brand.Cyan)
                        playback.channel != null -> IconButton(onClick = vm.player::togglePlay) {
                            Icon(if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause")
                        }
                        else -> IconButton(onClick = { onResume(c) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                    }
                    if (playback.channel != null) {
                        IconButton(onClick = vm.player::stop) { Icon(Icons.Rounded.Close, "Close player") }
                    } else Spacer(Modifier.width(4.dp))
                }
                if (now != null && playback.channel != null) GradientProgress(now.progress(clock), Modifier.fillMaxWidth(), height = 2.dp)
            }
        }
    }
}
