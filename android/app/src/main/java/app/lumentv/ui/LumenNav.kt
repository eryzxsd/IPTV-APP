package app.lumentv.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lumentv.data.Channel
import app.lumentv.data.nowAndNext
import app.lumentv.ui.channels.ChannelsScreen
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.guide.GuideScreen
import app.lumentv.ui.player.PlaybackState
import app.lumentv.ui.player.PlayerScreen
import app.lumentv.ui.settings.SettingsScreen
import app.lumentv.ui.theme.Brand

private enum class Tab(val label: String, val icon: ImageVector) {
    Channels("Channels", Icons.Rounded.LiveTv),
    Guide("Guide", Icons.Rounded.CalendarViewDay),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun LumenNav(vm: MainViewModel, inPip: Boolean, onPlayerVisible: (Boolean) -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val onPlayer = entry?.destination?.route == "player"
    LaunchedEffect(onPlayer) { onPlayerVisible(onPlayer) }

    val open: (Channel) -> Unit = { c ->
        if (vm.player.state.value.channel?.id != c.id || vm.player.state.value.status == PlaybackState.Status.Error) vm.play(c)
        if (!onPlayer) nav.navigate("player") { launchSingleTop = true }
    }

    // Surface provides the theme's content colour to every screen (incl. the player).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") { Home(vm, open, onOpenPlayer = { nav.navigate("player") { launchSingleTop = true } }) }
            composable("player") { PlayerScreen(vm, inPip = inPip, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun Home(vm: MainViewModel, open: (Channel) -> Unit, onOpenPlayer: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Channels) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                val pad = PaddingValues(bottom = 12.dp)
                when (tab) {
                    Tab.Channels -> ChannelsScreen(vm, onOpenPlayer = open, contentPadding = pad)
                    Tab.Guide -> GuideScreen(vm, onPlay = open, contentPadding = pad)
                    Tab.Settings -> SettingsScreen(vm, contentPadding = pad)
                }
            }
            MiniPlayer(vm, onOpen = onOpenPlayer, onResume = open)
        }
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
        Surface(
            onClick = { if (playback.channel == null) onResume(c) else onOpen() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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
                        IconButton(onClick = vm.player::stop) { Icon(Icons.Rounded.Close, "Stop") }
                    } else Spacer(Modifier.width(4.dp))
                }
                if (now != null && playback.channel != null) GradientProgress(now.progress(clock), Modifier.fillMaxWidth(), height = 2.dp)
            }
        }
    }
}
