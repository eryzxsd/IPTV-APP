package app.lumentv.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
