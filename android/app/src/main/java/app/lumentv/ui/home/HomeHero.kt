package app.lumentv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumentv.data.Channel
import app.lumentv.data.EpgData
import app.lumentv.data.countryName
import app.lumentv.data.nowAndNext
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand
import kotlinx.coroutines.delay

private fun hue(name: String) = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()

/**
 * Top of Home when nothing is playing: a full-width banner for the top picks (swipe, or it moves on by itself),
 * like the home screen of a streaming app, with the app name and search on top.
 */
@Composable
fun HomeHero(
    picks: List<Channel>,
    epg: EpgData,
    clock: Long,
    favorites: List<String>,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit,
    onSearch: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val height = if (wide) 400.dp else 500.dp
        if (picks.isEmpty()) {
            TopBar(onSearch)
            return@BoxWithConstraints
        }
        val pager = rememberPagerState { picks.size }
        // Move on to the next pick every few seconds (stops while you're swiping).
        LaunchedEffect(pager.currentPage, pager.isScrollInProgress, picks.size) {
            if (pager.isScrollInProgress || picks.size < 2) return@LaunchedEffect
            delay(8_000)
            pager.animateScrollToPage((pager.currentPage + 1) % picks.size)
        }
        Column {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(height), key = { picks[it].id }) { page ->
            val c = picks[page]
            val h = hue(c.name)
            val (now, next) = epg[c.epgKey].nowAndNext(clock)
            Box(Modifier.fillMaxSize()) {
                // Backdrop: the channel's colours with its logo large.
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.hsv(h, 0.6f, 0.42f), Color.hsv((h + 50) % 360, 0.6f, 0.16f)))),
                )
                Box(Modifier.align(Alignment.TopCenter).padding(top = if (wide) 70.dp else 104.dp)) { ChannelLogo(c, if (wide) 120.dp else 150.dp) }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.55f to bg.copy(alpha = 0.55f), 1f to bg),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (now != null) LiveBadge()
                        val meta = listOfNotNull(
                            c.groups.firstOrNull()?.takeIf { it != "Other" },
                            c.country.takeIf { it.isNotEmpty() }?.let { countryName(it) },
                            c.quality,
                        )
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                    Text(
                        c.name,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (now != null) {
                        Text(now.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fmtTime(now.start)} – ${fmtTime(now.stop)} · ${fmtDuration(now.stop - clock)} left" + (next?.let { " · Next: ${it.title}" } ?: ""),
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        GradientProgress(now.progress(clock), Modifier.padding(top = 8.dp).widthIn(max = 260.dp).fillMaxWidth(), height = 3.dp)
                    } else {
                        Text(c.groups.joinToString(" · ").ifEmpty { "Live TV" }, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onPlay(c) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Watch", fontWeight = FontWeight.Bold)
                        }
                        val fav = c.id in favorites
                        FilledTonalButton(onClick = { onFavorite(c) }) {
                            Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (fav) "In favorites" else "Favorite")
                        }
                    }
                }
            }
        }
        // Page dots.
        if (picks.size > 1) {
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                picks.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Brand.Cyan else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        }
        TopBar(onSearch)
    }
}

@Composable
private fun TopBar(onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append("Lumen")
                withStyle(SpanStyle(color = Brand.Cyan)) { append(" TV") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search channels", tint = Color.White) }
    }
}
