package app.lumentv.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.lumentv.ui.theme.Brand
import app.lumentv.ui.theme.Eyebrow
import kotlinx.coroutines.delay

enum class PlayerSheet { Subtitles, Audio, Quality, More, Aspect, Sleep, Info }

/** Buttons on the player's bottom bar. */
@Composable
fun ControlButtons(playback: PlaybackState, fullscreen: Boolean, onSheet: (PlayerSheet) -> Unit, onPip: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ControlIcon(
            if (playback.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
            "Subtitles",
            highlighted = playback.subtitlesOn,
        ) { onSheet(PlayerSheet.Subtitles) }
        ControlIcon(Icons.Rounded.Audiotrack, "Audio track") { onSheet(PlayerSheet.Audio) }
        QualityChip(playback) { onSheet(PlayerSheet.Quality) }
        if (fullscreen) {
            ControlIcon(Icons.Rounded.AspectRatio, "Aspect ratio") { onSheet(PlayerSheet.Aspect) }
            if (onPip != null) ControlIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onClick = onPip)
        }
        ControlIcon(Icons.Rounded.MoreVert, "More options", badge = playback.sleepAt != null) { onSheet(PlayerSheet.More) }
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, label: String, highlighted: Boolean = false, badge: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Box {
            Icon(icon, label, tint = if (highlighted) Brand.Cyan else Color.White, modifier = Modifier.size(24.dp))
            if (badge) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(Brand.Cyan))
        }
    }
}

@Composable
private fun QualityChip(playback: PlaybackState, onClick: () -> Unit) {
    val label = when {
        !playback.autoQuality -> playback.video.firstOrNull { it.selected }?.label ?: "Manual"
        playback.currentHeight != null -> "Auto · ${playback.currentHeight}p"
        else -> "Auto"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/** "LIVE" when at the live edge; "Go live" (tap to jump) when paused or behind. */
@OptIn(UnstableApi::class)
@Composable
fun LiveIndicator(controller: PlayerController) {
    val behind by produceState(0L, controller) {
        while (true) {
            value = controller.liveOffsetSeconds()
            delay(1_000)
        }
    }
    val atEdge = behind < 20
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (atEdge) Brand.Live else Color.White.copy(alpha = 0.22f))
            .clickable(enabled = !atEdge, onClick = controller::goLive)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (atEdge) Color.White else Brand.Live))
        Text(if (atEdge) "LIVE" else "GO LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

/** Material 3 bottom sheets for every player option. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun PlayerSheetHost(sheet: PlayerSheet?, controller: PlayerController, playback: PlaybackState, onSheet: (PlayerSheet?) -> Unit) {
    if (sheet == null) return
    val activity = LocalContext.current as? Activity
    val pip = rememberPip(activity)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onSheet(null) }, sheetState = state) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            when (sheet) {
                PlayerSheet.Subtitles -> {
                    SheetTitle("Subtitles", "Closed captions and subtitle tracks in this stream")
                    Choice("Off", null, selected = !playback.subtitlesOn) { controller.selectSubtitle(null); onSheet(null) }
                    if (playback.text.isEmpty()) Empty("This channel doesn't include subtitles.")
                    Options(playback.text, selected = { playback.subtitlesOn && it.selected }) { controller.selectSubtitle(it.id); onSheet(null) }
                }

                PlayerSheet.Audio -> {
                    SheetTitle("Audio", "Language and audio tracks")
                    if (playback.audio.isEmpty()) Empty("No audio tracks reported yet.")
                    Options(playback.audio, selected = { it.selected }) { controller.selectAudio(it.id); onSheet(null) }
                }

                PlayerSheet.Quality -> {
                    SheetTitle("Quality", "Auto adjusts to your connection")
                    Choice(
                        "Auto",
                        playback.currentHeight?.let { "Currently ${it}p" },
                        selected = playback.autoQuality,
                    ) { controller.selectQuality(null); onSheet(null) }
                    if (playback.video.isEmpty()) Empty("This stream has a single quality.")
                    Options(playback.video, selected = { !playback.autoQuality && it.selected }) { controller.selectQuality(it.id); onSheet(null) }
                }

                PlayerSheet.More -> {
                    SheetTitle("More", null)
                    MenuRow(Icons.Rounded.AspectRatio, "Aspect ratio", playback.aspect.label) { onSheet(PlayerSheet.Aspect) }
                    MenuRow(
                        Icons.Rounded.Bedtime,
                        "Sleep timer",
                        playback.sleepAt?.let { "Stops in ${((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min" } ?: "Off",
                    ) { onSheet(PlayerSheet.Sleep) }
                    if (pip != null) MenuRow(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture", "Keep watching in a small window") {
                        onSheet(null)
                        pip()
                    }
                    MenuRow(Icons.Rounded.Info, "Stream info", "Resolution, codecs and bitrate") { onSheet(PlayerSheet.Info) }
                }

                PlayerSheet.Aspect -> {
                    SheetTitle("Aspect ratio", null)
                    AspectMode.entries.forEach { m ->
                        Choice(m.label, null, selected = playback.aspect == m) { controller.setAspect(m); onSheet(null) }
                    }
                }

                PlayerSheet.Sleep -> {
                    SheetTitle("Sleep timer", "Pause playback after a while")
                    Choice("Off", null, selected = playback.sleepAt == null) { controller.setSleepTimer(null); onSheet(null) }
                    listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                        Choice(if (m < 60) "$m minutes" else if (m % 60 == 0) "${m / 60} hour${if (m > 60) "s" else ""}" else "${m / 60}h ${m % 60}m", null, selected = false) {
                            controller.setSleepTimer(m); onSheet(null)
                        }
                    }
                }

                PlayerSheet.Info -> {
                    SheetTitle("Stream info", null)
                    val info = remember(playback) { controller.streamInfo() }
                    InfoRow("Resolution", info.resolution)
                    InfoRow("Frame rate", info.frameRate)
                    InfoRow("Video bitrate", info.bitrate)
                    InfoRow("Video codec", info.videoCodec)
                    InfoRow("Audio codec", info.audioCodec)
                    InfoRow("Audio channels", info.audioChannels)
                    InfoRow("Audio tracks", playback.audio.size.toString())
                    InfoRow("Subtitle tracks", playback.text.size.toString())
                    InfoRow("Server", info.host)
                }
            }
        }
    }
}

/** Returns a function that enters picture-in-picture, or null when the device can't. */
@Composable
fun rememberPip(activity: Activity?): (() -> Unit)? {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return null
    return remember(activity) {
        {
            runCatching {
                activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
            Unit
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun Options(options: List<TrackOption>, selected: (TrackOption) -> Boolean, onPick: (TrackOption) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 4.dp)) {
        items(options, key = { it.id }) { o -> Choice(o.label, o.detail, selected(o)) { onPick(o) } }
    }
}

@Composable
private fun Choice(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        supportingContent = detail?.let { { Text(it) } },
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
    )
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 9.dp)) {
        Text(label, style = Eyebrow.copy(letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
