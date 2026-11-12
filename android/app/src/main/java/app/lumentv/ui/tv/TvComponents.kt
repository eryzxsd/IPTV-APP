package app.lumentv.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumentv.data.Channel
import app.lumentv.data.Programme
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.theme.Brand
import kotlinx.coroutines.android.awaitFrame

/** Focus colours for the remote: a clear outline and a lifted card, no animations to keep weak TVs fast. */
object TvFocus {
    val outline = Color.White
    val fill = Brand.Violet
}

private fun hue(name: String) = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()

/** Requests focus once the item is laid out (safe to call before it's attached). */
suspend fun FocusRequester.requestWhenReady() {
    repeat(5) {
        awaitFrame()
        if (runCatching { requestFocus() }.isSuccess) return
    }
}

/** Pill button for the player and top bar: violet with a white outline when focused. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = when {
            focused -> TvFocus.fill
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.White.copy(alpha = 0.12f)
        },
        contentColor = Color.White,
        border = if (focused) BorderStroke(2.dp, TvFocus.outline) else null,
        interactionSource = source,
    ) {
        Row(
            Modifier.heightIn(min = 46.dp).padding(horizontal = if (text.isEmpty()) 12.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = text.ifEmpty { null }, modifier = Modifier.size(22.dp))
            if (text.isNotEmpty()) Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Channel card for the Home rows and search results. */
@Composable
fun TvChannelCard(
    channel: Channel,
    now: Programme?,
    clock: Long,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 212.dp,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val h = hue(channel.name)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).graphicsLayer {
            val s = if (focused) 1.07f else 1f
            scaleX = s
            scaleY = s
        },
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        border = when {
            focused -> BorderStroke(3.dp, TvFocus.outline)
            playing -> BorderStroke(2.dp, Brand.Cyan)
            else -> null
        },
        interactionSource = source,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Brush.linearGradient(listOf(Color.hsv(h, 0.55f, 0.34f), Color.hsv((h + 50) % 360, 0.6f, 0.16f)))),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(channel, 60.dp)
                if (playing) {
                    Text(
                        "PLAYING",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Brand.Cyan)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                } else if (now != null) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { LiveBadge() }
                }
                if (now != null) GradientProgress(now.progress(clock), Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 4.dp)
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    now?.title ?: channel.groups.joinToString(" · ").ifEmpty { "Live TV" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Big tile used for actions (Guide, Search, Settings) next to channel cards. */
@Composable
fun TvActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.width(248.dp).graphicsLayer {
            val s = if (focused) 1.07f else 1f
            scaleX = s
            scaleY = s
        },
        shape = RoundedCornerShape(14.dp),
        color = if (focused) TvFocus.fill else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (focused) BorderStroke(3.dp, TvFocus.outline) else null,
        interactionSource = source,
    ) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(28.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(subtitle, fontSize = 14.sp, color = LocalContentColorDim(focused), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LocalContentColorDim(focused: Boolean) =
    if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

/** Row in the channel list and the guide's channel column. */
@Composable
fun TvChannelRow(
    channel: Channel,
    now: Programme?,
    clock: Long,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> TvFocus.fill
            playing -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        contentColor = Color.White,
        interactionSource = source,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel, 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (favorite) "★ " else "") + channel.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    now?.title ?: channel.groups.joinToString(" · ").ifEmpty { "No guide information" },
                    fontSize = 14.sp,
                    color = if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now != null) GradientProgress(now.progress(clock), Modifier.padding(top = 6.dp).width(180.dp), height = 3.dp)
            }
            if (playing) {
                Text(
                    "PLAYING",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brand.Cyan).padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** A list dialog for the remote: the current choice starts focused. */
@Composable
fun TvChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    radio: Boolean = true,
) {
    val first = remember { FocusRequester() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceAtLeast(0))
    LaunchedEffect(Unit) { first.requestWhenReady() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            LazyColumn(state = state, modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(options) { i, label ->
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    Surface(
                        onClick = { onPick(i) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (focused) TvFocus.fill else Color.Transparent,
                        contentColor = if (focused) Color.White else MaterialTheme.colorScheme.onSurface,
                        interactionSource = source,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (i == selected.coerceAtLeast(0)) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (radio) RadioButton(selected = i == selected, onClick = null)
                            Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = if (radio) 12.dp else 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
