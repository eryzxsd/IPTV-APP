package app.lumentv.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.lumentv.data.Channel
import app.lumentv.data.Programme
import app.lumentv.data.countryName
import app.lumentv.data.flag
import app.lumentv.data.nowAndNext
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.Tag
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand
import app.lumentv.ui.theme.Eyebrow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel, inPip: Boolean, onBack: () -> Unit) {
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val channel = playback.channel ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val programmes = epg[channel.epgKey]
    val (now, next) = programmes.nowAndNext(clock)

    val activity = LocalContext.current as? Activity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = landscape || inPip

    // Immersive mode while the video fills the screen.
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val toggleFullscreen = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }
    BackHandler(enabled = landscape && !inPip) { toggleFullscreen() }

    val favorite = channel.id in lib.favorites

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoBox(vm, playback, channel, now, clock, fullscreen = true, inPip = inPip, favorite = favorite, onBack = onBack, onFullscreen = toggleFullscreen)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.background(Color.Black).statusBarsPadding().fillMaxWidth().aspectRatio(16f / 9f)) {
            VideoBox(vm, playback, channel, now, clock, fullscreen = false, inPip = false, favorite = favorite, onBack = onBack, onFullscreen = toggleFullscreen)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item { ChannelHeader(channel, favorite, onFavorite = { vm.toggleFavorite(channel) }) }
            item { NowCard(channel, now, clock, noGuide = programmes == null) }
            if (!programmes.isNullOrEmpty()) {
                item {
                    Text("Schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                }
                // Day headers are precomputed: lazy items may compose in any order.
                val upcoming = programmes.filter { it.stop > clock }.take(30)
                val firstDay = upcoming.firstOrNull()?.let { dayFmt.format(Date(it.start)) }
                val rows = upcoming.mapIndexed { i, p ->
                    val day = dayFmt.format(Date(p.start))
                    val prev = upcoming.getOrNull(i - 1)?.let { dayFmt.format(Date(it.start)) } ?: firstDay
                    (if (day != prev) day else null) to p
                }
                items(rows, key = { it.second.start }) { (header, p) ->
                    Column {
                        if (header != null) Text(header.uppercase(), style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        ScheduleRow(p, live = p === now)
                    }
                }
            }
            item { if (next == null && programmes.isNullOrEmpty()) Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val dayFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.VideoBox(
    vm: MainViewModel,
    playback: PlaybackState,
    channel: Channel,
    now: Programme?,
    clock: Long,
    fullscreen: Boolean,
    inPip: Boolean,
    favorite: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
) {
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    val activity = LocalContext.current as? Activity
    val pip = rememberPip(activity)
    LaunchedEffect(controls, poke, playback.isPlaying, sheet) {
        if (controls && playback.isPlaying && sheet == null) {
            delay(3500)
            controls = false
        }
    }
    val show = !inPip && (controls || !playback.isPlaying)

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                // Readable captions: white text on a translucent box, respecting system caption size.
                subtitleView?.apply {
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            0xA0000000.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            android.graphics.Color.BLACK,
                            null,
                        ),
                    )
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.05f)
                    setApplyEmbeddedStyles(false)
                }
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        update = {
            it.player = vm.player.player
            it.resizeMode = playback.aspect.resizeMode
        },
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
                poke++
            },
    )

    when (playback.status) {
        PlaybackState.Status.Loading -> if (playback.slow && !inPip) {
            SlowOverlay(playback.reconnecting, onKeepWaiting = vm.player::keepWaiting, onNext = { vm.zap(1) })
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Brand.Cyan, strokeWidth = 3.dp)
                if (playback.reconnecting && !inPip) {
                    Text("Reconnecting…", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        PlaybackState.Status.Error -> if (!inPip) ErrorOverlay(playback.error, channel, onRetry = vm.player::retry, onNext = { vm.zap(1) })
        else -> {}
    }

    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        LiveIndicator(vm.player)
                    }
                    if (fullscreen && now != null) {
                        Text("${fmtTime(now.start)} – ${fmtTime(now.stop)} · ${now.title}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (fullscreen) {
                    IconButton(onClick = { vm.toggleFavorite(channel) }) {
                        Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Favorite", tint = if (favorite) Color(0xFFFFC53D) else Color.White)
                    }
                }
            }

            // Centre controls
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                RoundIcon(Icons.Rounded.SkipPrevious, "Previous channel") { vm.zap(-1); poke++ }
                if (playback.status != PlaybackState.Status.Loading) {
                    FilledIconButton(
                        onClick = { vm.player.togglePlay(); poke++ },
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Icon(if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", Modifier.size(34.dp)) }
                } else Spacer(Modifier.size(64.dp))
                RoundIcon(Icons.Rounded.SkipNext, "Next channel") { vm.zap(1); poke++ }
            }

            // Bottom bar
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (now != null) GradientProgress(now.progress(clock), Modifier.fillMaxWidth().padding(horizontal = 4.dp), height = 3.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        now?.let { "${it.title} · ${fmtDuration(it.stop - clock)} left" } ?: channel.groups.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    ControlButtons(playback, fullscreen, onSheet = { sheet = it; poke++ }, onPip = pip)
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(42.dp)) {
                        Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }
    PlayerSheetHost(sheet, vm.player, playback, onSheet = { sheet = it; poke++ })
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

/** Shown when a channel is slow to start: it isn't treated as broken, the user decides. */
@Composable
private fun BoxScope.SlowOverlay(reconnecting: Boolean, onKeepWaiting: () -> Unit, onNext: () -> Unit) {
    Column(
        Modifier.matchParentSize().background(Color(0x99000000)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Brand.Cyan, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (reconnecting) "Reconnecting…" else "Still connecting…", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Some channels take a while to start. It'll keep trying.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onKeepWaiting) { Text("Keep waiting", color = Color.White) }
            Button(onClick = onNext) {
                Text("Next channel")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BoxScope.ErrorOverlay(message: String?, channel: Channel, onRetry: () -> Unit, onNext: () -> Unit) {
    Column(
        Modifier.matchParentSize().background(Color(0xE60A080E)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Brand.Live, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text("Can't play this channel", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            (message ?: "") + if (channel.geoBlocked) " It's marked as geo-blocked." else "",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry", color = Color.White)
            }
            Button(onClick = onNext) {
                Text("Next channel")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChannelHeader(channel: Channel, favorite: Boolean, onFavorite: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(channel, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(channel.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("${flag(channel.country)} ${countryName(channel.country)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                channel.quality?.let { Tag(it) }
                if (channel.notAlways) Tag("Part-time")
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NowCard(channel: Channel, now: Programme?, clock: Long, noGuide: Boolean) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(if (now != null) "ON NOW" else "NO GUIDE DATA", style = Eyebrow, color = Brand.Cyan)
            if (now != null) {
                Text(now.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "${fmtTime(now.start)} – ${fmtTime(now.stop)} · ${fmtDuration(now.stop - clock)} left" + (now.episode?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                GradientProgress(now.progress(clock), Modifier.fillMaxWidth().padding(vertical = 12.dp), height = 5.dp)
                now.desc?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis) }
            } else {
                Text(
                    if (noGuide) "There's no TV guide information for this channel." else "Nothing scheduled right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
                channel.groups.forEach { Tag(it, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer) }
            }
        }
    }
}

@Composable
private fun ScheduleRow(p: Programme, live: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (live) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(fmtTime(p.start), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp), fontSize = 13.sp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (live) Tag("NOW", Color.White, Brand.Live)
            }
            p.desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(fmtDuration(p.stop - p.start), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
}
