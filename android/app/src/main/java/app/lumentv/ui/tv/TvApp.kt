package app.lumentv.ui.tv

import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lumentv.ui.Filter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.lumentv.data.Channel
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.player.PlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class TvScreen { Home, Player, Guide, Search, Settings, Browse }

/** Navigation for the TV interface. */
class TvNav(private val stack: MutableList<TvScreen>) {
    val current get() = stack.last()
    val canGoBack get() = stack.size > 1
    fun open(s: TvScreen) {
        if (current == s) return
        stack.remove(s)
        stack.add(s)
    }
    fun back() { if (canGoBack) stack.removeAt(stack.lastIndex) }

    /** Side-menu screens are top level: Back from them goes straight to Home (and Back twice there exits). */
    fun openTop(s: TvScreen) {
        stack.clear()
        stack.add(TvScreen.Home)
        if (s != TvScreen.Home) stack.add(s)
    }
}

/**
 * The Android TV interface, in Compose + Material 3. One video surface sits behind every screen,
 * so Home shows the playing channel and opening the player never re-attaches the surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvApp(vm: MainViewModel) {
    val saved = rememberSaveable { arrayListOf(TvScreen.Home.name) }
    val stack = remember { mutableStateListOf<TvScreen>().apply { addAll(saved.map(TvScreen::valueOf)) } }
    // Search isn't kept: coming back to the app starts without the old search.
    LaunchedEffect(stack.toList()) { saved.clear(); saved.addAll(stack.filter { it != TvScreen.Search && it != TvScreen.Browse }.map { it.name }) }
    val nav = remember { TvNav(stack) }
    val screen = stack.last()
    // The player goes back to where you came from; every other screen exits on Back twice (below).
    BackHandler(enabled = screen == TvScreen.Player && nav.canGoBack) { nav.back() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (TvScreen.Search in stack) {
            stack.remove(TvScreen.Search)
            if (stack.isEmpty()) stack.add(TvScreen.Home)
        }
    }

    // Back twice exits the app from any screen (a single press is easy to hit by accident).
    // Pickers, dialogs and the player handle their own Back first.
    val context = LocalContext.current
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = screen != TvScreen.Player) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2_500) (context as? Activity)?.finish()
        else {
            lastBack = now
            Toast.makeText(context, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    var browse by remember { mutableStateOf<BrowseTarget>(BrowseTarget.All) }
    var options by remember { mutableStateOf<Channel?>(null) }
    val openBrowse: (BrowseTarget) -> Unit = { browse = it; nav.open(TvScreen.Browse) }

    val playback by vm.player.state.collectAsStateWithLifecycle()
    val exo by vm.player.playerFlow.collectAsStateWithLifecycle()

    // Settings > Start with the last channel: go straight to the player when it starts.
    LaunchedEffect(Unit) {
        if (vm.appPrefs.value.resumeLastChannel) {
            vm.player.state.map { it.channel != null }.distinctUntilChanged().collect { if (it) nav.open(TvScreen.Player) }
        }
    }

    val play: (Channel) -> Unit = { c ->
        val s = vm.player.state.value
        if (s.channel?.id != c.id || s.status == PlaybackState.Status.Error) vm.play(c)
        nav.open(TvScreen.Player)
    }

    // A clearly visible focus highlight for plain Material components (Settings, dialogs).
    val ripple = RippleConfiguration(color = Color.White, rippleAlpha = RippleAlpha(0.16f, 0.24f, 0.08f, 0.24f))
    CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = MaterialTheme.colorScheme.onBackground) { Box(Modifier.fillMaxSize()) {
            if (playback.channel != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            isFocusable = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setKeepContentOnPlayerReset(true)
                            subtitleView?.apply {
                                setStyle(
                                    CaptionStyleCompat(
                                        android.graphics.Color.WHITE, 0xA0000000.toInt(), android.graphics.Color.TRANSPARENT,
                                        CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, null,
                                    ),
                                )
                                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
                                setApplyEmbeddedStyles(false)
                            }
                        }
                    },
                    update = { v ->
                        if (v.player !== exo) v.player = exo
                        v.resizeMode = playback.aspect.resizeMode
                        v.keepScreenOn = playback.isPlaying
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dim the video behind the browsing screens.
            val scrim = when (screen) {
                TvScreen.Player, TvScreen.Home -> 0f // Home draws its own backdrop over the video
                else -> 1f
            }
            if (scrim > 0f) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = scrim)))

            // Every screen but the player has the side menu; Home lays out its own room for it.
            val withMenu = screen != TvScreen.Player
            Box(Modifier.fillMaxSize().padding(start = if (withMenu && screen != TvScreen.Home) RAIL_WIDTH else 0.dp)) {
            when (screen) {
                TvScreen.Home -> TvHome(vm, onPlay = play, onOpen = nav::open, onBrowse = openBrowse, onOptions = { options = it })
                TvScreen.Player -> TvPlayer(vm, onOpen = nav::open, onExit = nav::back)
                TvScreen.Guide -> TvGuide(vm, onPlay = play, onOptions = { options = it })
                TvScreen.Search -> TvSearch(vm, onPlay = play, onOptions = { options = it })
                TvScreen.Settings -> TvSettings(vm)
                // Browse sets the list filter itself, so channel up/down follows what you browsed.
                TvScreen.Browse -> TvBrowse(vm, browse, onPlay = play, onOptions = { options = it })
            }
            }
            if (withMenu) {
                SideMenu(
                    selected = screen,
                    onOpen = nav::openTop,
                    onBrowseAll = { browse = BrowseTarget.All; nav.openTop(TvScreen.Browse) },
                    modifier = Modifier.zIndex(1f),
                )
            }
            options?.let { c -> ChannelOptions(vm, c, onPlay = play, onBrowse = openBrowse, onDismiss = { options = null }) }
        } }
    }
}
