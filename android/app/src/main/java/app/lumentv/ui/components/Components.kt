package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumentv.data.Channel
import app.lumentv.data.Programme
import app.lumentv.data.flag
import app.lumentv.ui.theme.Brand
import coil3.compose.SubcomposeAsyncImage
import java.text.DateFormat
import java.util.Date

private val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
fun fmtTime(t: Long): String = timeFmt.format(Date(t))
fun fmtDuration(ms: Long): String {
    val m = (ms / 60000).toInt().coerceAtLeast(0)
    return if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
}

/** Channel logo on a light tile, or coloured initials when there is none. */
@Composable
fun ChannelLogo(channel: Channel, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    var failed by remember(channel.logo) { mutableStateOf(channel.logo.isBlank()) }
    val shape = RoundedCornerShape(size * 0.24f)
    var loaded by remember(channel.logo) { mutableStateOf(false) }
    val hue = (channel.name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                if (loaded) Brush.linearGradient(listOf(Color(0xFFF2F3F7), Color(0xFFF2F3F7)))
                else Brush.linearGradient(listOf(Color.hsv(hue, 0.65f, 0.75f), Color.hsv((hue + 40) % 360, 0.7f, 0.55f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Initials show until (and unless) the logo loads.
        if (!loaded) Text(initials(channel.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
        if (!failed) {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.8f),
                onSuccess = { loaded = true },
                onError = { failed = true },
            )
        }
    }
}

private fun initials(name: String): String {
    val words = name.replace(Regex("""\(.*?\)|\[.*?]"""), "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0][0]}${words[1][0]}".uppercase()
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, background: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(background).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun LiveBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Brand.Live).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun GradientProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(height)).background(Brand.gradient))
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    now: Programme?,
    clock: Long,
    active: Boolean,
    favorite: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(MaterialTheme.shapes.medium)
            .channelClickable(channel, onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (active) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ChannelLogo(channel, modifier = Modifier.alpha(if (offline && !active) 0.45f else 1f))
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        channel.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (offline && !active) colors.onSurfaceVariant else colors.onSurface,
                    )
                    if (channel.country.isNotEmpty()) Text(flag(channel.country), fontSize = 12.sp)
                    when {
                        offline -> Tag("Offline", Brand.Live, Brand.Live.copy(alpha = 0.15f))
                        else -> {
                            channel.quality?.let { Tag(it) }
                            if (channel.geoBlocked) Tag("Geo", Brand.Warn, Brand.Warn.copy(alpha = 0.14f))
                        }
                    }
                }
            },
            supportingContent = {
                Column {
                    if (now != null) {
                        Text(
                            "${fmtTime(now.start)}  ${now.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(5.dp))
                        GradientProgress(now.progress(clock), Modifier.fillMaxWidth(0.9f))
                    } else {
                        Text(channel.groups.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) Color(0xFFFFC53D) else colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            },
        )
    }
}

@Composable
fun LoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(modifier = modifier.fillMaxWidth().height(2.dp), strokeCap = StrokeCap.Round)
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.width(1.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
