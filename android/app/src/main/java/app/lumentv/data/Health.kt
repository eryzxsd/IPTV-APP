package app.lumentv.data

import kotlinx.serialization.Serializable

/** Failures recorded for a channel. A success clears the entry. */
@Serializable
data class Health(val fails: Int, val lastFail: Long)

object HealthPolicy {
    const val FAILS_FOR_OFFLINE = 2
    const val EXPIRY_MS = 3L * 24 * 3_600_000

    fun isOffline(h: Health?, now: Long): Boolean =
        h != null && h.fails >= FAILS_FOR_OFFLINE && now - h.lastFail < EXPIRY_MS

    fun recordFailure(h: Health?, now: Long): Health =
        if (h == null || now - h.lastFail >= EXPIRY_MS) Health(1, now) else Health(h.fails + 1, now)
}
