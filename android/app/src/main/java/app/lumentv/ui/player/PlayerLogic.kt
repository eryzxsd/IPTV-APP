package app.lumentv.ui.player

/** A video variant as reported by the player (before labelling). */
data class VideoCandidate(
    val id: String,
    /** Height from the manifest, or <= 0 when the stream doesn't say (no RESOLUTION attribute). */
    val height: Int,
    /** Bits per second, or <= 0 when unknown. */
    val bitrate: Int,
    val selected: Boolean,
    /** Format id, used to remember heights learned from decoded video. */
    val formatId: String?,
)

object QualityLogic {
    /**
     * Turns variants into quality options. Variants without a declared height are kept
     * (many IPTV manifests only list bandwidth) and use a height learned from decoded
     * frames when we have one, otherwise their bitrate.
     */
    fun options(
        candidates: List<VideoCandidate>,
        learnedHeights: Map<String, Int>,
        playingFormatId: String?,
    ): List<TrackOption> {
        fun height(c: VideoCandidate) = c.height.takeIf { it > 0 } ?: c.formatId?.let(learnedHeights::get)
        val sorted = candidates
            .sortedWith(compareByDescending<VideoCandidate> { height(it) ?: 0 }.thenByDescending { it.bitrate })
            .distinctBy { (height(it) ?: -1) to it.bitrate }
        return sorted.mapIndexed { i, c ->
            val h = height(c)
            val rate = formatBitrate(c.bitrate)
            val playing = playingFormatId != null && c.formatId == playingFormatId
            TrackOption(
                id = c.id,
                label = h?.let { "${it}p" } ?: rate ?: "Quality ${i + 1}",
                detail = listOfNotNull(if (h != null) rate else null, if (playing) "Playing now" else null)
                    .joinToString(" · ").ifEmpty { null },
                selected = c.selected,
            )
        }
    }

    /** Text for the quality chip on the player. */
    fun chipLabel(autoQuality: Boolean, options: List<TrackOption>, currentHeight: Int?, playlistQuality: String?): String {
        val known = currentHeight?.let { "${it}p" } ?: playlistQuality
        return when {
            !autoQuality -> options.firstOrNull { it.selected }?.label ?: known ?: "Manual"
            options.size <= 1 -> known ?: "Auto"
            known != null -> "Auto · $known"
            else -> "Auto"
        }
    }

    fun formatBitrate(b: Int): String? = when {
        b <= 0 -> null
        b >= 1_000_000 -> "%.1f Mbps".format(b / 1_000_000f)
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
