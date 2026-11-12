package app.lumentv.ui.player

/** A video variant as reported by the player (before labelling). */
data class VideoCandidate(
    val id: String,
    /** Size from the manifest, or <= 0 when the stream doesn't say (no RESOLUTION attribute). */
    val width: Int,
    val height: Int,
    /** Bits per second, or <= 0 when unknown. */
    val bitrate: Int,
    val selected: Boolean,
    /** Identifies the variant across track updates; used for learned sizes and "playing now". */
    val key: String,
)

object QualityLogic {
    private val TIERS = listOf("Highest", "High", "Medium", "Low", "Lowest")

    /**
     * Turns variants into quality options, highest first. Sizes use standard names
     * (1216×684 -> "720p"). Variants whose manifest entry has no resolution use the
     * size learned once they've played, otherwise a rank name like "High".
     */
    fun options(
        candidates: List<VideoCandidate>,
        learnedSizes: Map<String, Pair<Int, Int>>,
        playingKey: String?,
    ): List<TrackOption> {
        fun size(c: VideoCandidate): Pair<Int, Int>? =
            if (c.height > 0) c.width to c.height else learnedSizes[c.key]
        val allSized = candidates.all { size(it) != null }
        val sorted = candidates
            .sortedWith(
                if (allSized) compareByDescending<VideoCandidate> { size(it)!!.second }.thenByDescending { it.bitrate }
                else compareByDescending { it.bitrate }, // one bitrate ladder: higher bitrate = higher quality
            )
            .distinctBy { (size(it)?.second ?: -1) to it.bitrate }
        val n = sorted.size
        return sorted.mapIndexed { i, c ->
            val sz = size(c)
            val playing = playingKey != null && c.key == playingKey
            val label = sz?.let { standardName(it.first, it.second) }
                ?: TIERS[if (n <= 1) 0 else Math.round(i * (TIERS.size - 1f) / (n - 1))]
            TrackOption(
                id = c.id,
                label = label,
                detail = listOfNotNull(formatBitrate(c.bitrate), if (playing) "Playing now" else null)
                    .joinToString(" · ").ifEmpty { null },
                selected = c.selected,
            )
        }
    }

    /** Familiar name for a video size: 1216×684 -> "720p", 1920×800 (cinema) -> "1080p". */
    fun standardName(width: Int, height: Int): String {
        // Use the 16:9-equivalent height so wide or cropped video gets its usual name.
        val h = maxOf(height, if (width > 0) width * 9 / 16 else 0)
        return when {
            h >= 2000 -> "2160p"
            h >= 1300 -> "1440p"
            h >= 900 -> "1080p"
            h >= 650 -> "720p"
            h >= 530 -> "576p"
            h >= 420 -> "480p"
            h >= 330 -> "360p"
            h >= 220 -> "240p"
            else -> "144p"
        }
    }

    /** Text for the quality chip on the player. [current] is the standard name of what's playing. */
    fun chipLabel(autoQuality: Boolean, options: List<TrackOption>, current: String?, playlistQuality: String?): String {
        val known = current ?: playlistQuality
        return when {
            !autoQuality -> options.firstOrNull { it.selected }?.label ?: known ?: "Manual"
            options.size <= 1 -> known ?: "Auto"
            known != null -> "Auto · $known"
            else -> "Auto"
        }
    }

    fun formatBitrate(b: Int): String? = when {
        b <= 0 -> null
        b >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mbps", b / 1_000_000f)
        else -> "${b / 1000} kbps"
    }
}

object ErrorPolicy {
    /**
     * Whether an error says the stream itself is broken (so it may count towards
     * marking the channel offline), as opposed to a slow or flaky connection.
     */
    fun isDefinite(httpStatus: Int?, unsupportedFormat: Boolean): Boolean = when {
        unsupportedFormat -> true
        httpStatus == null -> false
        httpStatus == 408 || httpStatus == 429 -> false // timeout / rate limited: try again later
        else -> httpStatus in 400..499
    }
}

object LivePolicy {
    /** Within this many seconds of the normal live position counts as "live". */
    const val LIVE_SLACK_S = 8L

    /**
     * Seconds behind the stream's normal live position. Players deliberately stay a few
     * segments behind the very edge ([targetOffsetMs]), so that part doesn't count.
     */
    fun secondsBehind(currentOffsetMs: Long, targetOffsetMs: Long): Long =
        ((currentOffsetMs - maxOf(0L, targetOffsetMs)) / 1000).coerceAtLeast(0)

    fun isAtLiveEdge(secondsBehind: Long) = secondsBehind <= LIVE_SLACK_S

    /** Pressing play after a pause: go back to live unless the user prefers to resume where they paused. */
    fun shouldJumpToLive(enabled: Boolean, isLive: Boolean, secondsBehind: Long) =
        enabled && isLive && !isAtLiveEdge(secondsBehind)
}
