package app.lumentv.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.data.countryName
import app.lumentv.data.flag
import app.lumentv.ui.Filter
import app.lumentv.ui.MainViewModel

/** What the right-hand side shows next to the filter column: the channels, or a category/country picker. */
enum class PanelMode { Channels, Categories, Countries }

/**
 * Filter column shared by the channel list, All channels and the TV guide:
 * All channels, Favorites, Recently watched, Category and Country. Coming into it lands on the current filter.
 */
@Composable
fun TvFilterRail(
    title: String,
    filter: Filter,
    count: Int,
    mode: PanelMode,
    onFilter: (Filter) -> Unit,
    onMode: (PanelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember { FocusRequester() }
    Column(
        modifier
            .focusProperties { onEnter = { if (!selected.requestFocus()) Unit } }
            .focusGroup(),
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("${"%,d".format(count)} · ${filterName(filter)}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 14.dp))
        fun Modifier.ifSelected(on: Boolean) = if (on) focusRequester(selected) else this
        TvRailItem(Icons.Rounded.LiveTv, "All channels", filter == Filter.All && mode == PanelMode.Channels,
            modifier = Modifier.ifSelected(filter == Filter.All)) { onFilter(Filter.All) }
        TvRailItem(Icons.Rounded.Star, "Favorites", filter == Filter.Favorites && mode == PanelMode.Channels,
            modifier = Modifier.ifSelected(filter == Filter.Favorites)) { onFilter(Filter.Favorites) }
        TvRailItem(Icons.Rounded.History, "Recently watched", filter == Filter.Recent && mode == PanelMode.Channels,
            modifier = Modifier.ifSelected(filter == Filter.Recent)) { onFilter(Filter.Recent) }
        TvRailItem(Icons.Rounded.Category, (filter as? Filter.Group)?.name ?: "Category",
            mode == PanelMode.Categories || (filter is Filter.Group && mode == PanelMode.Channels), chevron = true,
            modifier = Modifier.ifSelected(filter is Filter.Group)) { onMode(PanelMode.Categories) }
        TvRailItem(Icons.Rounded.Public, (filter as? Filter.Country)?.let { "${flag(it.code)} ${countryName(it.code)}" } ?: "Country",
            mode == PanelMode.Countries || (filter is Filter.Country && mode == PanelMode.Channels), chevron = true,
            modifier = Modifier.ifSelected(filter is Filter.Country)) { onMode(PanelMode.Countries) }
    }
}

/** Category or country list (with counts) that sets the filter. */
@Composable
fun TvFilterPicker(vm: MainViewModel, mode: PanelMode, filter: Filter, onPicked: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val categories = mode == PanelMode.Categories
    val items = if (categories) groups.filter { it.first != "Other" }.sortedByDescending { it.second } else countries
    val selected = when (filter) {
        is Filter.Group -> filter.name.takeIf { categories }
        is Filter.Country -> filter.code.takeIf { !categories }
        else -> null
    }
    Column {
        Text(if (categories) "Categories" else "Countries", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        Text("OK to show · Back to return", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
        val first = remember(mode) { FocusRequester() }
        val startAt = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (startAt - 3).coerceAtLeast(0))
        LaunchedEffect(mode) { first.requestWhenReady() }
        LazyColumn(state = state, contentPadding = PaddingValues(start = 12.dp, end = 16.dp, bottom = 24.dp)) {
            itemsIndexed(items, key = { _, it -> it.first }) { i, (value, count) ->
                TvPickerRow(
                    if (categories) value else "${flag(value)}  ${countryName(value)}", count, value == selected,
                    modifier = if (i == startAt) Modifier.focusRequester(first) else Modifier,
                ) {
                    vm.filter.value = if (categories) Filter.Group(value) else Filter.Country(value)
                    onPicked()
                }
            }
        }
    }
}

fun filterName(f: Filter) = when (f) {
    Filter.All -> "All channels"
    Filter.Favorites -> "Favorites"
    Filter.Recent -> "Recently watched"
    is Filter.Group -> f.name
    is Filter.Country -> countryName(f.code)
}

@Composable
fun TvRailItem(icon: ImageVector, label: String, selected: Boolean, chevron: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(14.dp),
        color = when {
            focused -> TvFocus.fill
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        contentColor = Color.White,
        border = if (focused) BorderStroke(2.dp, TvFocus.outline) else null,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (icon == Icons.Rounded.Star && !focused) Color(0xFFFFC53D) else Color.White)
            Text(label, Modifier.weight(1f).padding(start = 14.dp), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

@Composable
fun TvPickerRow(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> TvFocus.fill
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text("%,d".format(count), fontSize = 15.sp, color = if (focused) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

