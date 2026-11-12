package app.lumentv.data

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    /** Stable id derived from tvg-id/name + stream URL. */
    val id: String,
    val name: String,
    val logo: String,
    val groups: List<String>,
    val tvgId: String,
    /** Key used to join with guide data. */
    val epgKey: String,
    /** ISO 3166 alpha-2 (upper case) or "". */
    val country: String,
    val url: String,
    val quality: String? = null,
    val geoBlocked: Boolean = false,
    val notAlways: Boolean = false,
    /** Extra headers some iptv-org streams need (http-user-agent / http-referrer). */
    val userAgent: String? = null,
    val referrer: String? = null,
)

@Serializable
data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val episode: String? = null,
) {
    fun isLive(now: Long) = now in start until stop
    fun progress(now: Long): Float = ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** Guide data keyed by [Channel.epgKey], each list sorted by start time. */
typealias EpgData = Map<String, List<Programme>>

fun List<Programme>?.nowAndNext(now: Long): Pair<Programme?, Programme?> {
    if (this.isNullOrEmpty()) return null to null
    var lo = 0
    var hi = size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val p = this[mid]
        when {
            p.stop <= now -> lo = mid + 1
            p.start > now -> hi = mid - 1
            else -> return p to getOrNull(mid + 1)
        }
    }
    return null to firstOrNull { it.start > now }
}

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val label: String, val progress: Float? = null) : LoadState
    data class Done(val message: String? = null) : LoadState
    data class Failed(val message: String) : LoadState
}
