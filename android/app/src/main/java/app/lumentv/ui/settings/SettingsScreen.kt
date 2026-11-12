package app.lumentv.ui.settings

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumentv.BuildConfig
import app.lumentv.data.LoadState
import app.lumentv.data.Sources
import app.lumentv.data.UserSettings
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.theme.Brand
import app.lumentv.ui.theme.Eyebrow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val epgState by vm.epgState.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { draft = saved }
    var customPlaylist by remember(saved) { mutableStateOf(saved.playlists.filter { u -> Sources.PLAYLISTS.none { it.url == u } }.joinToString("\n")) }
    var customEpg by remember(saved) { mutableStateOf(saved.customEpgUrls.joinToString("\n")) }
    val uri = LocalUriHandler.current

    fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val pending = draft.copy(
        playlists = (draft.playlists.filter { u -> Sources.PLAYLISTS.any { it.url == u } } + lines(customPlaylist)).ifEmpty { Sources.DEFAULT_PLAYLISTS },
        customEpgUrls = lines(customEpg),
    )
    val dirty = pending != saved

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Section("Playlist", "Channels come from the public iptv-org playlists.") {
            Sources.PLAYLISTS.forEach { p ->
                val selected = p.url in draft.playlists
                Row(
                    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                        draft = draft.copy(playlists = listOf(p.url))
                        customPlaylist = ""
                    },
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
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("Your own M3U URLs (one per line)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
        }

        Section("TV guide", "Guide data from epgshare01. Fewer regions load faster.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Load the TV guide", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = draft.epgEnabled, onCheckedChange = { draft = draft.copy(epgEnabled = it) })
            }
            if (epgState is LoadState.Done && (epgState as LoadState.Done).message != null) {
                Text((epgState as LoadState.Done).message!!, fontSize = 12.sp, color = Brand.Warn)
            }
            listOf(false to "COUNTRIES", true to "STREAMING SERVICES & NETWORKS").forEach { (service, label) ->
                Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Extra XMLTV URLs (.xml or .xml.gz)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (dirty) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.saveSettings(pending) }) { Text("Save changes") }
                TextButton(onClick = {
                    draft = saved
                    customPlaylist = saved.playlists.filter { u -> Sources.PLAYLISTS.none { it.url == u } }.joinToString("\n")
                    customEpg = saved.customEpgUrls.joinToString("\n")
                }) { Text("Discard") }
            }
        }

        Section("Data", null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.reloadEpg() }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Refresh guide")
                }
                OutlinedButton(onClick = { vm.clearCache() }) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Clear cache")
                }
            }
            TextButton(onClick = { vm.saveSettings(UserSettings()) }) { Text("Reset to defaults") }
        }

        Section("About", null) {
            Text("Lumen TV ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium)
            Text(
                "Free, public streams from iptv-org and TV guide data from epgshare01. Streams come and go; channels that fail are marked Offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { uri.openUri("https://github.com/iptv-org/iptv") }) { Text("iptv-org") }
                TextButton(onClick = { uri.openUri("https://epgshare01.online/") }) { Text("epgshare01") }
                TextButton(onClick = { uri.openUri("https://github.com/eryzxsd/IPTV-APP") }) { Text("Source code") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            } else Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
