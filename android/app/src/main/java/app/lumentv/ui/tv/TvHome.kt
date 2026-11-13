package app.lumentv.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.Channel
import app.lumentv.data.EpgData
import app.lumentv.data.HomeRow
import app.lumentv.data.LoadState
import app.lumentv.data.countryName
import app.lumentv.data.nowAndNext
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.components.ChannelLogo
import app.lumentv.ui.components.GradientProgress
import app.lumentv.ui.components.LiveBadge
import app.lumentv.ui.components.fmtDuration
import app.lumentv.ui.components.fmtTime
import app.lumentv.ui.theme.Brand

/** Where a "browse" screen comes from: a category, or one of the special lists. */
sealed interface BrowseTarget {
    data object All : BrowseTarget
    data object Favorites : BrowseTarget
    data class Category(val name: String) : BrowseTarget
}

private fun hue(name: String) = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()

/**
 * Home, laid out like a streaming service: a big banner describing whatever you're on,
 * rows of channels below it, and a slim menu on the left.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TvHome(
    vm: MainViewModel,
    onPlay: (Channel) -> Unit,
    onOpen: (TvScreen) -> Unit,
    onBrowse: (BrowseTarget) -> Unit,
    onOptions: (Channel) -> Unit,
) {
    val rows by vm.homeRows.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val load by vm.channelState.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val current = playback.channel

    // TV order: recommendations first, then what you were watching, then the rest.
    val ordered = remember(rows, current) {
        val order = listOf("for-you", "continue", "on-now", "favorites", "because")
        val sorted = rows.sortedBy { r -> order.indexOf(r.id).let { if (it < 0) order.size else it } }
        if (current != null) listOf(HomeRow("now", "Now playing", null, listOf(current))) + sorted else sorted
    }
    val categories = remember(groups) {
        groups.filter { it.first != "Other" }.sortedByDescending { it.second }.take(14)
    }

    var focused by remember { mutableStateOf<Channel?>(null) }
    val firstId = ordered.firstOrNull()?.channels?.firstOrNull()?.id
    val hero = focused ?: ordered.firstOrNull()?.channels?.firstOrNull()
    val first = remember { FocusRequester() }
    val hasContent = ordered.isNotEmpty()
    // Start on the first card, also when the rows refresh during startup (until you move yourself).
    var moved by remember { mutableStateOf(false) }
    LaunchedEffect(firstId) { if (firstId != null && !moved) first.requestWhenReady() }

    val live = hero != null && hero.id == current?.id
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        // ---------------------------------------------------------- backdrop
        Backdrop(hero, live, bg)

        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(RAIL_WIDTH))
            Column(Modifier.fillMaxSize()) {
                Billboard(hero, live, epg, clock, Modifier.height(BILLBOARD_HEIGHT))
                when {
                    !hasContent && load is LoadState.Failed -> Message(
                        "Couldn't load channels",
                        (load as LoadState.Failed).message,
                        action = "Try again",
                        onAction = { vm.reloadChannels() },
                    )
                    !hasContent -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Brand.Cyan)
                            Spacer(Modifier.height(16.dp))
                            Text((load as? LoadState.Loading)?.label ?: "Loading channels…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        // The focused row moves to the top of the list (rows slide up under the banner).
                        val density = LocalDensity.current
                        val rowSpec = LocalBringIntoViewSpec.current
                        val topAligned = remember(density) {
                            val title = with(density) { 34.dp.toPx() }
                            object : BringIntoViewSpec {
                                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - title
                            }
                        }
                        CompositionLocalProvider(LocalBringIntoViewSpec provides topAligned) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().focusRestorer(),
                                contentPadding = PaddingValues(bottom = 120.dp),
                            ) {
                                itemsIndexed(ordered, key = { _, r -> r.id }) { i, row ->
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                        ChannelRowSection(
                                            row, epg, clock, current?.id, lib.favorites,
                                            onPlay = onPlay, onOptions = onOptions, onFocused = { focused = it; if (it.id != firstId) moved = true },
                                            firstFocus = if (i == 0) first else null,
                                            resetScroll = i == 0 && !moved,
                                        )
                                    }
                                }
                                if (categories.isNotEmpty()) item(key = "categories") {
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                        CategoryRow(categories, lib.favorites.size, onBrowse)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SideMenu(
            selected = TvScreen.Home,
            onOpen = onOpen,
            onBrowseAll = { onBrowse(BrowseTarget.All) },
            modifier = Modifier.zIndex(1f),
        )
    }
}

private val RAIL_WIDTH = 76.dp
private val BILLBOARD_HEIGHT = 286.dp

/** Full-bleed art behind the banner: the live video for the playing channel, otherwise the channel's colours and logo. */
@Composable
private fun Backdrop(hero: Channel?, live: Boolean, bg: Color) {
    Box(Modifier.fillMaxSize()) {
        if (!live) {
            Box(Modifier.fillMaxSize().background(bg))
            if (hero != null) {
                val h = hue(hero.name)
                Box(
                    Modifier.fillMaxWidth().height(BILLBOARD_HEIGHT + 80.dp)
                        .background(Brush.linearGradient(listOf(Color.hsv((h + 50) % 360, 0.55f, 0.10f), Color.hsv(h, 0.6f, 0.38f)))),
                )
                Box(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 120.dp).graphicsLayer { alpha = 0.92f }) {
                    ChannelLogo(hero, 190.dp)
                }
            }
        }
        // Fade the art into the page: from the left (behind the text) and from the bottom (behind the rows).
        Box(
            Modifier.fillMaxWidth().height(BILLBOARD_HEIGHT + 80.dp)
                .background(Brush.horizontalGradient(0f to bg, 0.38f to bg.copy(alpha = 0.85f), 0.7f to bg.copy(alpha = 0.15f), 1f to Color.Transparent)),
        )
        Box(
            Modifier.fillMaxWidth().padding(top = BILLBOARD_HEIGHT - 120.dp).height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg))),
        )
        Box(Modifier.fillMaxSize().padding(top = BILLBOARD_HEIGHT + 80.dp).background(bg))
    }
}

/** The big description at the top: whatever card has focus. */
@Composable
private fun Billboard(c: Channel?, live: Boolean, epg: EpgData, clock: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 40.dp, end = 48.dp, top = 30.dp)) {
        Text(
            buildAnnotatedString {
                append("Lumen")
                withStyle(SpanStyle(color = Brand.Cyan)) { append(" TV") }
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.9f),
        )
        if (c == null) return@Column
        val (now, next) = epg[c.epgKey].nowAndNext(clock)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (live) Eyebrow("NOW PLAYING", Brand.Cyan) else if (now != null) LiveBadge()
            val meta = listOfNotNull(
                c.groups.firstOrNull()?.takeIf { it != "Other" },
                c.country.takeIf { it.isNotEmpty() }?.let { countryName(it) },
                c.quality,
            )
            if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
        }
        Text(
            c.name,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.62f),
        )
        Column(Modifier.fillMaxWidth(0.55f)) {
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(now.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Text("  ${fmtTime(now.start)} – ${fmtTime(now.stop)} · ${fmtDuration(now.stop - clock)} left", fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                GradientProgress(now.progress(clock), Modifier.padding(vertical = 8.dp).width(320.dp), height = 4.dp)
                if (!now.desc.isNullOrBlank()) {
                    Text(now.desc, fontSize = 15.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (next != null) {
                    Text("Next  ${fmtTime(next.start)}  ${next.title}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                Text(
                    c.groups.joinToString(" · ").ifEmpty { "Live TV" } + "  ·  No guide information",
                    fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                if (live) "OK to return to the video  ·  hold OK for options" else "OK to watch  ·  hold OK for options",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String, color: Color) {
    Text(
        text,
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelRowSection(
    row: HomeRow,
    epg: EpgData,
    clock: Long,
    currentId: String?,
    favorites: List<String>,
    onPlay: (Channel) -> Unit,
    onOptions: (Channel) -> Unit,
    onFocused: (Channel) -> Unit,
    firstFocus: FocusRequester?,
    resetScroll: Boolean = false,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(row.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 40.dp))
        // Lazy rows keep their scroll anchored to an item when the list changes; start at the front again.
        val state = rememberLazyListState()
        LaunchedEffect(row.channels.firstOrNull()?.id) { if (resetScroll || state.firstVisibleItemIndex <= 1) state.scrollToItem(0) }
        LazyRow(
            state = state,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.channels, key = { _, c -> c.id }) { i, c ->
                TvChannelCard(
                    c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = c.id == currentId,
                    width = 196.dp,
                    favorite = c.id in favorites,
                    onClick = { onPlay(c) },
                    onLongClick = { onOptions(c) },
                    onFocused = { onFocused(c) },
                    modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryRow(categories: List<Pair<String, Int>>, favorites: Int, onBrowse: (BrowseTarget) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text("Browse", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 40.dp))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "all") { CategoryTile("All channels", null, Icons.Rounded.Category) { onBrowse(BrowseTarget.All) } }
            if (favorites > 0) item(key = "fav") { CategoryTile("Favorites", "$favorites", Icons.Rounded.Star) { onBrowse(BrowseTarget.Favorites) } }
            items(categories, key = { it.first }) { (name, count) ->
                CategoryTile(name, "$count channels", null) { onBrowse(BrowseTarget.Category(name)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(name: String, detail: String?, icon: ImageVector?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val h = hue(name)
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = if (focused) androidx.compose.foundation.BorderStroke(3.dp, TvFocus.outline) else null,
        modifier = Modifier.width(196.dp).height(96.dp).graphicsLayer {
            val s = if (focused) 1.07f else 1f
            scaleX = s
            scaleY = s
        },
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.hsv(h, 0.6f, if (focused) 0.6f else 0.42f), Color.hsv((h + 60) % 360, 0.65f, 0.2f))))
                .padding(14.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(26.dp))
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

/** Slim menu on the left; it widens to show labels while it has focus. */
@Composable
fun SideMenu(selected: TvScreen?, onOpen: (TvScreen) -> Unit, onBrowseAll: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val bg = MaterialTheme.colorScheme.background
    val home = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxHeight()
            // Coming into the menu lands on the current page.
            .focusProperties { onEnter = { if (!home.requestFocus()) Unit } }
            .focusGroup()
            .width(if (open) 250.dp else RAIL_WIDTH)
            .background(
                if (open) Brush.horizontalGradient(0f to bg, 0.75f to bg.copy(alpha = 0.96f), 1f to bg.copy(alpha = 0.0f))
                else Brush.horizontalGradient(listOf(bg.copy(alpha = 0.55f), Color.Transparent)),
            )
            .onFocusChanged { open = it.hasFocus }
            .padding(start = 14.dp, top = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MenuItem(Icons.Rounded.Search, "Search", open, selected == TvScreen.Search) { onOpen(TvScreen.Search) }
        MenuItem(Icons.Rounded.Home, "Home", open, selected == TvScreen.Home, Modifier.focusRequester(home)) { onOpen(TvScreen.Home) }
        MenuItem(Icons.Rounded.CalendarViewDay, "TV guide", open, selected == TvScreen.Guide) { onOpen(TvScreen.Guide) }
        MenuItem(Icons.Rounded.Category, "All channels", open, selected == TvScreen.Browse) { onBrowseAll() }
        MenuItem(Icons.Rounded.Settings, "Settings", open, selected == TvScreen.Settings) { onOpen(TvScreen.Settings) }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, expanded: Boolean, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(50),
        color = when {
            focused -> TvFocus.fill
            else -> Color.Transparent
        },
        contentColor = when {
            focused -> Color.White
            selected -> Brand.Cyan
            else -> Color.White.copy(alpha = 0.7f)
        },
        modifier = modifier.then(if (expanded) Modifier.width(210.dp) else Modifier.size(48.dp)),
    ) {
        Row(Modifier.height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, modifier = Modifier.size(24.dp))
            if (expanded) Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp), maxLines = 1)
        }
    }
}

@Composable
fun Message(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}, secondary: String? = null, onSecondary: () -> Unit = {}) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(action) { if (action != null) focus.requestWhenReady() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(560.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (body != null) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (action != null) TvButton(action, onAction, icon = Icons.Rounded.Refresh, modifier = Modifier.focusRequester(focus))
                if (secondary != null) TvButton(secondary, onSecondary)
            }
        }
    }
}
