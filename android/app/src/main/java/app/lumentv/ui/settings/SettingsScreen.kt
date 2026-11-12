package app.lumentv.ui.settings

import android.os.Build
import app.lumentv.ui.components.GuideFailuresCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.BuildConfig
import app.lumentv.data.AppPrefs
import app.lumentv.data.BufferMode
import app.lumentv.data.LoadState
import app.lumentv.data.Sources
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.player.AspectMode
import app.lumentv.ui.theme.Brand
import app.lumentv.ui.theme.Eyebrow

private data class Confirm(val title: String, val body: String, val action: String, val run: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val app by vm.appPrefs.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val epgState by vm.epgState.collectAsStateWithLifecycle()
    val failures by vm.epgFailures.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { draft = saved }
    var customPlaylist by remember(saved) { mutableStateOf(saved.playlists.filter { u -> Sources.PLAYLISTS.none { it.url == u } }.joinToString("\n")) }
    var customEpg by remember(saved) { mutableStateOf(saved.customEpgUrls.joinToString("\n")) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val uri = LocalUriHandler.current
    fun set(a: AppPrefs) = vm.saveApp(a)

    fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val pending = draft.copy(
        playlists = (draft.playlists.filter { u -> Sources.PLAYLISTS.any { it.url == u } } + lines(customPlaylist)).ifEmpty { Sources.DEFAULT_PLAYLISTS },
        customEpgUrls = lines(customEpg),
    )
    val dirty = pending != saved

    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ------------------------------------------------------------ Playback
        Section("Playback") {
            Choice(
                Icons.Rounded.HighQuality, "Maximum quality", "Highest resolution Auto may use",
                options = listOf(0 to "No limit", 1080 to "1080p", 720 to "720p", 480 to "480p (data saver)"),
                selected = app.maxQuality,
            ) { set(app.copy(maxQuality = it)) }
            Choice(
                Icons.Rounded.WifiTethering, "Buffering", app.buffer.detail + " · applies next time the app starts",
                options = BufferMode.entries.map { it to it.label },
                selected = app.buffer,
            ) { set(app.copy(buffer = it)) }
            Toggle(Icons.Rounded.ClosedCaption, "Subtitles on by default", "Turn on subtitles when a channel has them", app.subtitlesByDefault) {
                set(app.copy(subtitlesByDefault = it))
            }
            Choice(
                Icons.Rounded.AspectRatio, "Default aspect ratio", null,
                options = AspectMode.entries.map { it.name to it.label },
                selected = app.defaultAspect,
            ) { set(app.copy(defaultAspect = it)) }
            Toggle(
                Icons.Rounded.LiveTv, "Jump to live when resuming",
                "Pressing play after a pause takes you back to the live broadcast. Turn off to continue where you paused.",
                app.jumpToLiveOnResume,
            ) { set(app.copy(jumpToLiveOnResume = it)) }
            Toggle(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture when leaving", "Keep watching in a small window when you go home", app.autoPip) {
                set(app.copy(autoPip = it))
            }
            Toggle(Icons.Rounded.PlayCircle, "Resume last channel", "Start playing your last channel when the app opens", app.resumeLastChannel) {
                set(app.copy(resumeLastChannel = it))
            }
            Toggle(
                Icons.Rounded.Search, "Look up resolutions",
                "For streams that don't label their qualities, read a small sample of each when you open Quality (uses a little data)",
                app.lookUpResolutions,
            ) { set(app.copy(lookUpResolutions = it)) }
        }

        // ------------------------------------------------------------ Channels
        Section("Channels") {
            Toggle(Icons.Rounded.SortByAlpha, "Sort A–Z", "Otherwise channels keep the playlist's order", app.sortAlphabetically) {
                set(app.copy(sortAlphabetically = it))
            }
            Toggle(
                Icons.Rounded.VisibilityOff, "Hide offline channels",
                "Channels that failed twice for a definite reason (e.g. removed or geo-blocked)", lib.hideOffline,
            ) { vm.setHideOffline(it) }
        }

        // ------------------------------------------------------------ Guide
        Section("TV guide", "Guide data from epgshare01. Fewer regions load faster.") {
            Toggle(Icons.Rounded.CalendarViewDay, "Load the TV guide", null, draft.epgEnabled) { draft = draft.copy(epgEnabled = it) }
            Choice(
                Icons.Rounded.Timer, "Refresh guide every", null,
                options = listOf(2 to "2 hours", 4 to "4 hours", 12 to "12 hours", 24 to "24 hours"),
                selected = app.guideRefreshHours,
            ) { set(app.copy(guideRefreshHours = it)) }
            (epgState as? LoadState.Loading)?.let { l ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(l.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { l.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            GuideFailuresCard(failures, epgState, onRetry = { vm.retryFailedEpg() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            listOf(false to "COUNTRIES", true to "STREAMING SERVICES & NETWORKS").forEach { (service, label) ->
                Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sources.EPG_SOURCES.filter { it.isService == service }.forEach { src ->
                        val on = src.code in draft.epgCodes
                        FilterChip(
                            selected = on,
                            enabled = draft.epgEnabled,
                            onClick = { draft = draft.copy(epgCodes = if (on) draft.epgCodes - src.code else draft.epgCodes + src.code) },
                            label = { Text(src.label) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = customEpg,
                onValueChange = { customEpg = it },
                enabled = draft.epgEnabled,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Extra XMLTV URLs (.xml or .xml.gz)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
        }

        // ------------------------------------------------------------ Playlist
        Section("Playlist", "Channels come from the public iptv-org playlists, or your own.") {
            Sources.PLAYLISTS.forEach { p ->
                val selected = p.url in draft.playlists
                Row(
                    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                        draft = draft.copy(playlists = listOf(p.url))
                        customPlaylist = ""
                    }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text("iptv-org · ${p.label}", fontWeight = FontWeight.Medium)
                        Text(p.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(
                value = customPlaylist,
                onValueChange = { customPlaylist = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Your own M3U URLs (one per line)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
        }

        // ------------------------------------------------------------ Appearance
        Section("Appearance") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle(Icons.Rounded.Palette, "Material You colors", "Use colors from your wallpaper", app.dynamicColor) { set(app.copy(dynamicColor = it)) }
            }
            Toggle(Icons.Rounded.Contrast, "Pure black background", "Saves battery on OLED screens", app.pureBlack) { set(app.copy(pureBlack = it)) }
        }

        // ------------------------------------------------------------ Data
        Section("Data & storage") {
            Action(Icons.Rounded.Refresh, "Refresh playlist", "Download the channel list again") { vm.reloadChannels() }
            Action(Icons.Rounded.CalendarViewDay, "Refresh TV guide", "Download the guide again") { vm.reloadEpg() }
            Action(Icons.Rounded.DeleteSweep, "Clear cache", "Cached playlist, guide and logos") { vm.clearCache() }
            Action(Icons.Rounded.History, "Clear watch history", "${lib.recent.size} recently watched") {
                confirm = Confirm("Clear watch history?", "Your recently watched list will be emptied.", "Clear") { vm.clearHistory() }
            }
            Action(Icons.Rounded.VisibilityOff, "Reset offline marks", "${lib.health.count { lib.isOffline(it.key) }} channels marked offline") {
                vm.clearOfflineMarks()
            }
            Action(Icons.Rounded.StarOutline, "Clear favorites", "${lib.favorites.size} favorites", destructive = true) {
                confirm = Confirm("Clear all favorites?", "This removes all ${lib.favorites.size} favorites and can't be undone.", "Clear") { vm.clearFavorites() }
            }
            Action(Icons.Rounded.RestartAlt, "Reset all settings", "Favorites and history are kept", destructive = true) {
                confirm = Confirm("Reset all settings?", "Every setting goes back to its default. Favorites and history are kept.", "Reset") { vm.resetSettings() }
            }
        }

        // ------------------------------------------------------------ About
        Section("About") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Lumen TV ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium)
                Text(
                    "Free, public streams from iptv-org and TV guide data from epgshare01. Streams come and go; a channel is marked Offline only after it fails twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { uri.openUri("https://github.com/iptv-org/iptv") }) { Text("iptv-org") }
                TextButton(onClick = { uri.openUri("https://epgshare01.online/") }) { Text("epgshare01") }
                TextButton(onClick = { uri.openUri("https://github.com/eryzxsd/IPTV-APP") }) { Text("Source code") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    // Always visible (not at the bottom of the scroll): unsaved playlist / guide-source changes.
    AnimatedVisibility(dirty, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 6.dp, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unsaved changes", fontWeight = FontWeight.SemiBold)
                    Text("Playlist or TV guide sources", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    draft = saved
                    customPlaylist = saved.playlists.filter { u -> Sources.PLAYLISTS.none { it.url == u } }.joinToString("\n")
                    customEpg = saved.customEpgUrls.joinToString("\n")
                }) { Text("Discard") }
                Button(onClick = { vm.saveSettings(pending) }) { Text("Save") }
            }
        }
    }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.body) },
            confirmButton = { TextButton(onClick = { c.run(); confirm = null }) { Text(c.action, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = Eyebrow, color = Brand.Cyan, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                content()
            }
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun <T> Choice(icon: ImageVector, title: String, subtitle: String?, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    ListItem(
        modifier = Modifier.clickable { open = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(listOfNotNull(current, subtitle).joinToString(" · ")) },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(value == selected, role = Role.RadioButton) { onPick(value); open = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, subtitle: String?, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = color, leadingIconColor = color),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
    )
}
