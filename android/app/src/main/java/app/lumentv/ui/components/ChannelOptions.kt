package app.lumentv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.ui.MainViewModel

/** Opens the options for a channel (long-press on any channel). Provided by the app's navigation. */
val LocalChannelOptions = staticCompositionLocalOf<((Channel) -> Unit)?> { null }

/** Tap plays; long-press opens the channel's options (with a little haptic tick). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.channelClickable(channel: Channel, onClick: () -> Unit): Modifier {
    val options = LocalChannelOptions.current
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = options?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it(channel) } },
        onLongClickLabel = if (options != null) "Channel options" else null,
    )
}

/** Watch / favorite / more from this category, as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelOptionsSheet(
    vm: MainViewModel,
    channel: Channel,
    onPlay: (Channel) -> Unit,
    onBrowse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val favorite = channel.id in lib.favorites
    val group = channel.groups.firstOrNull { it != "Other" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { ChannelLogo(channel, 44.dp) },
                headlineContent = { Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = channel.groups.takeIf { it.isNotEmpty() }?.let { { Text(it.joinToString(" · "), maxLines = 1) } },
            )
            Option(Icons.Rounded.PlayArrow, "Watch") { onDismiss(); onPlay(channel) }
            Option(
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                if (favorite) "Remove from favorites" else "Add to favorites",
            ) { vm.toggleFavorite(channel); onDismiss() }
            if (group != null) Option(Icons.AutoMirrored.Rounded.List, "More $group channels") { onDismiss(); onBrowse(group) }
        }
    }
}

@Composable
private fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(label) },
    )
}
