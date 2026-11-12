package app.lumentv.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import app.lumentv.LumenApp
import app.lumentv.data.Channel
import app.lumentv.data.EpgData
import app.lumentv.data.Library
import app.lumentv.data.LoadState
import app.lumentv.data.UserSettings
import app.lumentv.data.nowAndNext
import app.lumentv.ui.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Filter {
    data object All : Filter
    data object Favorites : Filter
    data object Recent : Filter
    data class Group(val name: String) : Filter
    data class Country(val code: String) : Filter
}

@OptIn(UnstableApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val lumen = app as LumenApp
    private val repo = lumen.repository
    private val prefs = lumen.prefs

    val settings: StateFlow<UserSettings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val library: StateFlow<Library> = prefs.library.stateIn(viewModelScope, SharingStarted.Eagerly, Library())

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels
    private val _channelState = MutableStateFlow<LoadState>(LoadState.Loading("Loading channels…"))
    val channelState: StateFlow<LoadState> = _channelState

    private val _epg = MutableStateFlow<EpgData>(emptyMap())
    val epg: StateFlow<EpgData> = _epg
    val epgState: StateFlow<LoadState> = repo.epgState

    val query = MutableStateFlow("")
    val filter = MutableStateFlow<Filter>(Filter.All)

    /** Ticks every 30 s so "now playing" and progress bars stay current. */
    val clock: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val player = PlayerController(app, lumen.http, viewModelScope) { ch, ok ->
        viewModelScope.launch { prefs.setHealth(ch.id, ok) }
    }

    val groups: StateFlow<List<Pair<String, Int>>> = channels.map { list ->
        list.flatMap { it.groups }.groupingBy { it }.eachCount().toList()
            .sortedWith(compareBy({ it.first == "Other" }, { it.first }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val countries: StateFlow<List<Pair<String, Int>>> = channels.map { list ->
        list.filter { it.country.isNotEmpty() }.groupingBy { it.country }.eachCount().toList().sortedByDescending { it.second }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filtered: StateFlow<List<Channel>> = combine(channels, filter, query, library, epg) { all, f, q, lib, epg ->
        val byId = if (f is Filter.Favorites || f is Filter.Recent) all.associateBy { it.id } else emptyMap()
        var list = when (f) {
            Filter.All -> all
            Filter.Favorites -> lib.favorites.mapNotNull(byId::get)
            Filter.Recent -> lib.recent.mapNotNull(byId::get)
            is Filter.Group -> all.filter { f.name in it.groups }
            is Filter.Country -> all.filter { it.country == f.code }
        }
        if (lib.hideOffline) list = list.filter { lib.health[it.id] != false }
        val needle = q.trim().lowercase()
        if (needle.isNotEmpty()) {
            val now = System.currentTimeMillis()
            list = list.filter { c ->
                c.name.lowercase().contains(needle) || c.groups.any { it.lowercase().contains(needle) } ||
                    epg[c.epgKey].nowAndNext(now).first?.title?.lowercase()?.contains(needle) == true
            }
        }
        list
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Last watched channel, shown in the mini player until the user picks something. */
    val lastChannel = MutableStateFlow<Channel?>(null)

    private var loadedPlaylists: List<String>? = null
    private var loadedEpg: Pair<List<String>, Int>? = null

    init {
        viewModelScope.launch {
            settings.map { it.playlists }.distinctUntilChanged().collect { loadChannels(it, force = false) }
        }
        viewModelScope.launch {
            combine(channels, settings) { ch, s -> ch to s }.collect { (ch, s) ->
                val urls = if (s.epgEnabled) s.epgUrls else emptyList()
                val key = urls to ch.size
                if (ch.isNotEmpty() && key != loadedEpg) {
                    loadedEpg = key
                    loadEpg(force = false)
                }
            }
        }
    }

    private suspend fun loadChannels(urls: List<String>, force: Boolean) {
        loadedPlaylists = urls
        _channelState.value = LoadState.Loading("Loading channels…")
        runCatching { repo.loadChannels(urls, force) }
            .onSuccess {
                _channels.value = it
                _channelState.value = LoadState.Done()
                restoreLast(it)
            }
            .onFailure { _channelState.value = LoadState.Failed(it.message ?: "Couldn't load the playlist") }
    }

    private fun loadEpg(force: Boolean) = viewModelScope.launch {
        val s = settings.value
        val urls = if (s.epgEnabled) s.epgUrls else emptyList()
        _epg.value = runCatching { repo.loadEpg(channels.value, urls, force) }.getOrDefault(emptyMap())
    }

    private var restored = false
    private suspend fun restoreLast(list: List<Channel>) {
        if (restored) return
        restored = true
        val last = prefs.library.first().lastChannel ?: return
        lastChannel.value = list.firstOrNull { it.id == last }
    }

    fun reloadChannels() = viewModelScope.launch { loadChannels(settings.value.playlists, force = true) }
    fun reloadEpg() = loadEpg(force = true)

    fun play(c: Channel) {
        player.play(c)
        lastChannel.value = c
        viewModelScope.launch { prefs.markWatched(c.id) }
    }

    /** Next/previous channel within the current list. */
    fun zap(direction: Int) {
        val list = filtered.value.ifEmpty { channels.value }
        if (list.isEmpty()) return
        val cur = player.state.value.channel
        val i = list.indexOfFirst { it.id == cur?.id }
        play(list[Math.floorMod(i + direction, list.size)])
    }

    fun toggleFavorite(c: Channel) = viewModelScope.launch { prefs.toggleFavorite(c.id) }
    fun setHideOffline(v: Boolean) = viewModelScope.launch { prefs.setHideOffline(v) }
    fun saveSettings(s: UserSettings) = viewModelScope.launch { prefs.saveSettings(s) }

    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        repo.clearCache()
        loadChannels(settings.value.playlists, force = true)
        loadEpg(force = true)
    }

    override fun onCleared() {
        player.release()
    }
}
