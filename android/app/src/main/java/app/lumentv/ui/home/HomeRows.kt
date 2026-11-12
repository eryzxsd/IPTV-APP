package app.lumentv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumentv.data.Channel
import app.lumentv.data.EpgData
import app.lumentv.data.HomeRow
import app.lumentv.data.Programme
import app.lumentv.data.countryName
import app.lumentv.data.flag
import app.lumentv.data.nowAndNext
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.theme.Brand
import app.lumentv.ui.theme.Eyebrow

/** Adds the Home recommendation rows to a LazyColumn. */
fun LazyListScope.recommendationItems(rows: List<HomeRow>, epg: EpgData, clock: Long, currentId: String?, onOpen: (Channel) -> Unit) {
    items(rows, key = { it.id }) { row ->
        Column(Modifier.padding(top = 20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(row.channels, key = { it.id }) { c ->
                    ChannelCard(c, epg[c.epgKey].nowAndNext(clock).first, clock, active = c.id == currentId) { onOpen(c) }
                }
            }
        }
    }
}

private fun hue(name: String) = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()

@Composable
fun ChannelCard(channel: Channel, now: Programme?, clock: Long, active: Boolean, onClick: () -> Unit) {
    val h = hue(channel.name)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.width(168.dp),
    ) {
        // Bottom padding keeps the text clear of the rounded corner's clip.
        Column(Modifier.padding(bottom = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Brush.linearGradient(listOf(Color.hsv(h, 0.55f, 0.32f), Color.hsv((h + 50) % 360, 0.6f, 0.16f))))
                    .then(if (active) Modifier.border(2.dp, Brand.Cyan, MaterialTheme.shapes.medium) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(channel, 52.dp)
                if (now != null) {
                    Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { LiveBadge() }
                    GradientProgress(now.progress(clock), Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 3.dp)
                }
                if (channel.country.isNotEmpty()) {
                    Text(flag(channel.country), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
            Text(
                channel.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            )
            Text(
                now?.title ?: channel.groups.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/** Big card for the top pick when nothing is playing. */
@Composable
fun FeaturedCard(channel: Channel, now: Programme?, clock: Long, onPlay: () -> Unit) {
    val h = hue(channel.name)
    Surface(
        onClick = onPlay,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(Color.hsv(h, 0.6f, 0.38f), Color(0xFF1A1530), Color.hsv((h + 60) % 360, 0.5f, 0.2f)))),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FEATURED FOR YOU", style = Eyebrow, color = Brand.Cyan)
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        now?.let { "On now: ${it.title}" } ?: listOf(channel.groups.joinToString(" · "), "${flag(channel.country)} ${countryName(channel.country)}").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (now != null) GradientProgress(now.progress(clock), Modifier.padding(top = 10.dp).width(180.dp), height = 4.dp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onPlay, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Watch now")
                    }
                }
                Spacer(Modifier.width(16.dp))
                ChannelLogo(channel, 84.dp)
            }
        }
    }
}
