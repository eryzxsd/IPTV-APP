package app.lumentv.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import app.lumentv.LumenApp
import app.lumentv.data.AppPrefs
import app.lumentv.data.Channel
import app.lumentv.data.HomeRow
import app.lumentv.data.Recommender
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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
    val epgFailures: StateFlow<List<app.lumentv.data.EpgFailure>> = repo.epgFailures

    val query = MutableStateFlow("")
    /** Fullscreen channel browser open. */
    val browsing = MutableStateFlow(false)
    /** Fullscreen requested with the button (tablets/TV; phones use rotation). */
    val fullscreen = MutableStateFlow(false)
    val filter = MutableStateFlow<Filter>(Filter.All)

    /** Ticks every 30 s so "now playing" and progress bars stay current. */
    val clock: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    /** Read once up front: the player's buffer size is fixed when it's created. */
    private val initialApp: AppPrefs = runBlocking { withTimeoutOrNull(1_500) { prefs.app.first() } } ?: AppPrefs()
    val appPrefs: StateFlow<AppPrefs> = prefs.app.stateIn(viewModelScope, SharingStarted.Eagerly, initialApp)

    /** Shared with the background playback service (owned by the app, not this screen). */
    val player: PlayerController = lumen.playerController

    val groups: StateFlow<List<Pair<String, Int>>> = channels.map { list ->
        list.flatMap { it.groups }.groupingBy { it }.eachCount().toList()
            .sortedWith(compareBy({ it.first == "Other" }, { it.first }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val countries: StateFlow<List<Pair<String, Int>>> = channels.map { list ->
        list.filter { it.country.isNotEmpty() }.groupingBy { it.country }.eachCount().toList().sortedByDescending { it.second }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filtered: StateFlow<List<Channel>> = combine(channels, filter, query, library, combine(epg, appPrefs, ::Pair)) { all, f, q, lib, (epg, app) ->
        val byId = if (f is Filter.Favorites || f is Filter.Recent) all.associateBy { it.id } else emptyMap()
        var list = when (f) {
            Filter.All -> all
            Filter.Favorites -> lib.favorites.mapNotNull(byId::get)
            Filter.Recent -> lib.recent.mapNotNull(byId::get)
            is Filter.Group -> all.filter { f.name in it.groups }
            is Filter.Country -> all.filter { it.country == f.code }
        }
        if (lib.hideOffline) list = list.filter { !lib.isOffline(it.id) }
        // Favorites and Recent keep their own order.
        if (app.sortAlphabetically && f !is Filter.Favorites && f !is Filter.Recent) {
            list = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
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

    /** Home rows (continue watching, recommendations, on now, categories), built on-device. */
    val homeRows: StateFlow<List<HomeRow>> = combine(
        channels,
        library,
        epg,
        player.state.map { it.channel?.id }.distinctUntilChanged(),
        clock.map { it / 600_000 }.distinctUntilChanged(), // refresh "on now" every 10 minutes
    ) { all, lib, epg, current, _ ->
        val byId = all.associateBy { it.id }
        val now = System.currentTimeMillis()
        val watched = lib.recent.toHashSet()
        Recommender.homeRows(
            all = all,
            recent = lib.recent.mapNotNull(byId::get),
            favorites = lib.favorites.mapNotNull(byId::get),
            deviceCountry = java.util.Locale.getDefault().country,
            hasGuideNow = { epg[it.epgKey].nowAndNext(now).first != null },
            isOffline = { lib.isOffline(it.id) },
            playedOk = { it.id in watched },
            exclude = current,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Last watched channel, shown in the mini player until the user picks something. */
    val lastChannel = MutableStateFlow<Channel?>(null)

    private var loadedPlaylists: List<String>? = null
    private var loadedEpg: Pair<List<String>, Int>? = null

    init {
        viewModelScope.launch { appPrefs.collect(player::applyPrefs) }
        player.onZap = { zap(it) }
        viewModelScope.launch {
            settings.map { it.playlists }.distinctUntilChanged().collect { loadChannels(it, force = false) }
        }
        viewModelScope.launch {
            combine(channels, settings) { ch, s -> ch to s }.collect { (ch, s) ->
                val urls = if (s.epgEnabled) s.epgUrls else emptyList()
                // Not keyed on the channel count: a background playlist refresh shouldn't reload the guide.
                val key = urls to 0
                if (ch.isNotEmpty() && key != loadedEpg) {
                    loadedEpg = key
                    loadEpg(force = false)
                }
            }
        }
    }

    private suspend fun loadChannels(urls: List<String>, force: Boolean) {
        loadedPlaylists = urls
        // Show the saved list straight away, whatever its age; refresh an old one in the background.
        if (!force) {
            val cached = runCatching { repo.cachedChannels(urls) }.getOrNull()
            if (cached != null) {
                val (list, stale) = cached
                _channels.value = list
                _channelState.value = LoadState.Done()
                restoreLast(list)
                if (stale) viewModelScope.launch {
                    runCatching { repo.loadChannels(urls, force = true) }.onSuccess { fresh ->
                        if (loadedPlaylists == urls && fresh.isNotEmpty()) _channels.value = fresh
                    }
                }
                return
            }
        }
        _channelState.value = LoadState.Loading("Loading channels…")
        runCatching { repo.loadChannels(urls, force) }
            .onSuccess {
                _channels.value = it
                _channelState.value = LoadState.Done()
                restoreLast(it)
            }
            .onFailure { _channelState.value = LoadState.Failed(it.message ?: "Couldn't load the playlist") }
    }

    /** The guide load in progress: a newer one (e.g. guide regions changed) replaces it, so an older result can't overwrite it. */
    private var epgJob: kotlinx.coroutines.Job? = null

    private fun loadEpg(force: Boolean): kotlinx.coroutines.Job {
        epgJob?.cancel()
        return viewModelScope.launch { loadEpgNow(force) }.also { epgJob = it }
    }

    private suspend fun loadEpgNow(force: Boolean) {
        val s = settings.value
        val urls = if (s.epgEnabled) s.epgUrls else emptyList()
        if (urls.isEmpty()) {
            _epg.value = runCatching { repo.loadEpg(channels.value, urls, force, appPrefs.value.guideRefreshHours) }.getOrDefault(emptyMap())
            return
        }
        // Show the saved guide straight away; download a new one only when it's due.
        if (!force) {
            val cached = runCatching { repo.cachedGuide(urls, appPrefs.value.guideRefreshHours) }.getOrNull()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (cached != null) {
                _epg.value = cached.data
                if (!cached.stale) return
            }
        }
        val fresh = runCatching { repo.loadEpg(channels.value, urls, force = true, appPrefs.value.guideRefreshHours) }.getOrNull()
        // Replaced by a newer load meanwhile (runCatching above also catches the cancellation): leave it to that one.
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // A failed refresh keeps the guide we already have.
        if (!fresh.isNullOrEmpty() || _epg.value.isEmpty()) _epg.value = fresh.orEmpty()
    }

    private var restored = false
    private suspend fun restoreLast(list: List<Channel>) {
        if (restored) return
        restored = true
        val last = prefs.library.first().lastChannel ?: return
        val ch = list.firstOrNull { it.id == last } ?: return
        lastChannel.value = ch
        // Settings > Resume last channel: start playing it right away.
        if (appPrefs.value.resumeLastChannel && player.state.value.channel == null) play(ch)
    }

    fun reloadChannels() = viewModelScope.launch { loadChannels(settings.value.playlists, force = true) }
    fun reloadEpg() = loadEpg(force = true)

    /** Re-downloads only the guide files that failed. */
    fun retryFailedEpg() = viewModelScope.launch {
        if (epgState.value is LoadState.Loading) return@launch
        val s = settings.value
        val urls = if (s.epgEnabled) s.epgUrls else emptyList()
        _epg.value = runCatching { repo.retryFailedEpg(channels.value, urls, _epg.value) }.getOrDefault(_epg.value)
    }

    /** Phone Home: the player is minimized to the mini player (still playing) and Home shows its normal page. */
    val minimized = MutableStateFlow(false)

    /** The channel before the current one, for the remote's "last channel" button. */
    val previousChannel = MutableStateFlow<Channel?>(null)

    fun play(c: Channel) {
        player.state.value.channel?.takeIf { it.id != c.id }?.let { previousChannel.value = it }
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

    /** Back to the channel watched before this one. */
    fun playPrevious() { previousChannel.value?.let(::play) }

    fun toggleFavorite(c: Channel) = viewModelScope.launch { prefs.toggleFavorite(c.id) }
    fun setHideOffline(v: Boolean) = viewModelScope.launch { prefs.setHideOffline(v) }
    fun saveSettings(s: UserSettings) = viewModelScope.launch { prefs.saveSettings(s) }
    fun saveApp(a: AppPrefs) = viewModelScope.launch { prefs.saveApp(a) }
    fun clearHistory() = viewModelScope.launch { prefs.clearHistory(); lastChannel.value = player.state.value.channel }
    fun clearFavorites() = viewModelScope.launch { prefs.clearFavorites() }
    fun clearOfflineMarks() = viewModelScope.launch { prefs.clearOfflineMarks() }
    fun resetSettings() = viewModelScope.launch { prefs.resetSettings() }

    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        repo.clearCache()
        loadChannels(settings.value.playlists, force = true)
        loadEpg(force = true)
    }

    override fun onCleared() {
        // The player belongs to the app: it may keep playing in the background.
        player.onZap = null
    }
}
