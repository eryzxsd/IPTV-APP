package app.lumentv.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("lumen")

data class UserSettings(
    val playlists: List<String> = Sources.DEFAULT_PLAYLISTS,
    val epgCodes: Set<String> = Sources.DEFAULT_EPG_CODES,
    val customEpgUrls: List<String> = emptyList(),
    val epgEnabled: Boolean = true,
) {
    val epgUrls: List<String>
        get() = Sources.EPG_SOURCES.filter { it.code in epgCodes }.map { it.url } + customEpgUrls
}

data class Library(
    val favorites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    /** channel id -> recent definite failures (a successful play removes the entry) */
    val health: Map<String, Health> = emptyMap(),
    val lastChannel: String? = null,
    val hideOffline: Boolean = false,
) {
    /** Offline only after repeated definite failures; see [HealthPolicy]. */
    fun isOffline(id: String, now: Long = System.currentTimeMillis()) = HealthPolicy.isOffline(health[id], now)
}

enum class BufferMode(val label: String, val detail: String) {
    Fast("Faster start", "Starts quickly, may pause on slow connections"),
    Balanced("Balanced", "Recommended"),
    Smooth("Smoother playback", "Takes longer to start, fewer pauses"),
}

enum class DecoderMode(val label: String, val detail: String) {
    Auto("Auto", "Hardware, falling back to software when needed (recommended)"),
    Hw("HW", "Hardware decoders only"),
    HwPlus("HW+", "Hardware with tunnelled playback and async decoding: lowest CPU use, best on Android TV"),
    Sw("SW", "Software decoders only: slower, but works around buggy hardware decoders"),
}

enum class ClockMode(val label: String) {
    Off("Off"),
    WithControls("With player controls"),
    Always("Always on the video"),
}

/** App options from Settings (playback, channels, guide, appearance). */
data class AppPrefs(
    /** Highest resolution Auto may pick; 0 = no limit. */
    val maxQuality: Int = 0,
    val buffer: BufferMode = BufferMode.Balanced,
    val decoder: DecoderMode = DecoderMode.Auto,
    /** Keep playing (with a media notification) when leaving the app or locking the screen. */
    val playInBackground: Boolean = false,
    val clock: ClockMode = ClockMode.Off,
    val subtitlesByDefault: Boolean = false,
    /** AspectMode name. */
    val defaultAspect: String = "Fit",
    val autoPip: Boolean = true,
    val resumeLastChannel: Boolean = false,
    val lookUpResolutions: Boolean = true,
    /** Pressing play after a pause returns to the live broadcast instead of where you paused. */
    val jumpToLiveOnResume: Boolean = true,
    val sortAlphabetically: Boolean = false,
    val guideRefreshHours: Int = 4,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
)

/** Settings and the user's library (favorites, recents, stream health) in DataStore. */
class Prefs(private val context: Context) {
    private object K {
        val playlists = stringPreferencesKey("playlists")
        val epgCodes = stringSetPreferencesKey("epg_codes")
        val customEpg = stringPreferencesKey("custom_epg")
        val epgEnabled = booleanPreferencesKey("epg_enabled")
        val favorites = stringPreferencesKey("favorites")
        val recent = stringPreferencesKey("recent")
        // v2: failure counts. The old key held one-strike "offline" marks and is dropped.
        val oldHealth = stringPreferencesKey("health")
        val health = stringPreferencesKey("health2")
        val last = stringPreferencesKey("last_channel")
        val hideOffline = booleanPreferencesKey("hide_offline")
        val maxQuality = intPreferencesKey("max_quality")
        val buffer = stringPreferencesKey("buffer")
        val decoder = stringPreferencesKey("decoder")
        val background = booleanPreferencesKey("play_in_background")
        val clock = stringPreferencesKey("clock")
        val subsDefault = booleanPreferencesKey("subs_default")
        val aspect = stringPreferencesKey("default_aspect")
        val autoPip = booleanPreferencesKey("auto_pip")
        val resumeLast = booleanPreferencesKey("resume_last")
        val lookUp = booleanPreferencesKey("look_up_resolutions")
        val jumpLive = booleanPreferencesKey("jump_to_live")
        val sortAz = booleanPreferencesKey("sort_az")
        val guideHours = intPreferencesKey("guide_refresh_hours")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val black = booleanPreferencesKey("pure_black")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private fun list(p: Preferences, k: Preferences.Key<String>): List<String>? =
        p[k]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            playlists = list(p, K.playlists)?.takeIf { it.isNotEmpty() } ?: Sources.DEFAULT_PLAYLISTS,
            epgCodes = p[K.epgCodes] ?: Sources.DEFAULT_EPG_CODES,
            customEpgUrls = list(p, K.customEpg).orEmpty(),
            epgEnabled = p[K.epgEnabled] ?: true,
        )
    }

    val library: Flow<Library> = context.dataStore.data.map { p ->
        Library(
            favorites = list(p, K.favorites).orEmpty(),
            recent = list(p, K.recent).orEmpty(),
            health = decodeHealth(p[K.health]),
            lastChannel = p[K.last],
            hideOffline = p[K.hideOffline] ?: false,
        )
    }

    val app: Flow<AppPrefs> = context.dataStore.data.map { p ->
        val d = AppPrefs()
        AppPrefs(
            maxQuality = p[K.maxQuality] ?: d.maxQuality,
            buffer = p[K.buffer]?.let { b -> BufferMode.entries.firstOrNull { it.name == b } } ?: d.buffer,
            decoder = p[K.decoder]?.let { v -> DecoderMode.entries.firstOrNull { it.name == v } } ?: d.decoder,
            playInBackground = p[K.background] ?: d.playInBackground,
            clock = p[K.clock]?.let { v -> ClockMode.entries.firstOrNull { it.name == v } } ?: d.clock,
            subtitlesByDefault = p[K.subsDefault] ?: d.subtitlesByDefault,
            defaultAspect = p[K.aspect] ?: d.defaultAspect,
            autoPip = p[K.autoPip] ?: d.autoPip,
            resumeLastChannel = p[K.resumeLast] ?: d.resumeLastChannel,
            lookUpResolutions = p[K.lookUp] ?: d.lookUpResolutions,
            jumpToLiveOnResume = p[K.jumpLive] ?: d.jumpToLiveOnResume,
            sortAlphabetically = p[K.sortAz] ?: d.sortAlphabetically,
            guideRefreshHours = p[K.guideHours] ?: d.guideRefreshHours,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            pureBlack = p[K.black] ?: d.pureBlack,
        )
    }

    suspend fun saveApp(a: AppPrefs) = context.dataStore.edit {
        it[K.maxQuality] = a.maxQuality
        it[K.buffer] = a.buffer.name
        it[K.decoder] = a.decoder.name
        it[K.background] = a.playInBackground
        it[K.clock] = a.clock.name
        it[K.subsDefault] = a.subtitlesByDefault
        it[K.aspect] = a.defaultAspect
        it[K.autoPip] = a.autoPip
        it[K.resumeLast] = a.resumeLastChannel
        it[K.lookUp] = a.lookUpResolutions
        it[K.jumpLive] = a.jumpToLiveOnResume
        it[K.sortAz] = a.sortAlphabetically
        it[K.guideHours] = a.guideRefreshHours
        it[K.dynamic] = a.dynamicColor
        it[K.black] = a.pureBlack
    }

    suspend fun clearHistory() = context.dataStore.edit { it.remove(K.recent); it.remove(K.last) }
    suspend fun clearFavorites() = context.dataStore.edit { it.remove(K.favorites) }
    suspend fun clearOfflineMarks() = context.dataStore.edit { it.remove(K.health); it.remove(K.oldHealth) }

    /** Restores every setting to its default; favorites and history are kept. */
    suspend fun resetSettings() = context.dataStore.edit { p ->
        listOf(K.playlists, K.epgCodes, K.customEpg, K.epgEnabled, K.hideOffline, K.maxQuality, K.buffer, K.decoder, K.background, K.clock, K.subsDefault, K.aspect,
            K.autoPip, K.resumeLast, K.lookUp, K.jumpLive, K.sortAz, K.guideHours, K.dynamic, K.black).forEach { p.remove(it) }
    }

    suspend fun saveSettings(s: UserSettings) = context.dataStore.edit {
        it[K.playlists] = json.encodeToString(s.playlists)
        it[K.epgCodes] = s.epgCodes
        it[K.customEpg] = json.encodeToString(s.customEpgUrls)
        it[K.epgEnabled] = s.epgEnabled
    }

    suspend fun toggleFavorite(id: String) = context.dataStore.edit {
        val cur = list(it, K.favorites).orEmpty()
        it[K.favorites] = json.encodeToString(if (id in cur) cur - id else cur + id)
    }

    suspend fun markWatched(id: String) = context.dataStore.edit {
        val cur = list(it, K.recent).orEmpty()
        it[K.recent] = json.encodeToString((listOf(id) + (cur - id)).take(30))
        it[K.last] = id
    }

    private fun decodeHealth(s: String?): Map<String, Health> =
        s?.let { runCatching { json.decodeFromString<Map<String, Health>>(it) }.getOrNull() }.orEmpty()

    /** ok = the stream played (clears failures); otherwise records a definite failure. */
    suspend fun setHealth(id: String, ok: Boolean) = context.dataStore.edit {
        it.remove(K.oldHealth)
        val cur = decodeHealth(it[K.health])
        val now = System.currentTimeMillis()
        val next = LinkedHashMap(cur)
        if (ok) {
            if (next.remove(id) == null) return@edit
        } else {
            next.remove(id)
            next[id] = HealthPolicy.recordFailure(cur[id], now)
        }
        while (next.size > 3000) next.remove(next.keys.first())
        it[K.health] = json.encodeToString(next as Map<String, Health>)
    }

    suspend fun setHideOffline(v: Boolean) = context.dataStore.edit { it[K.hideOffline] = v }
}
