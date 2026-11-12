package app.lumentv.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTrackNameProvider
import app.lumentv.data.Channel
import app.lumentv.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** One selectable track. [id] is "groupIndex:trackIndex" within the current [Tracks]. */
data class TrackOption(val id: String, val label: String, val detail: String?, val selected: Boolean)

enum class AspectMode(val label: String, val resizeMode: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill (crop)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FixedWidth("Fit width", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
}

data class StreamInfo(
    val resolution: String?,
    val frameRate: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioChannels: String?,
    val bitrate: String?,
    val host: String,
)

data class PlaybackState(
    val channel: Channel? = null,
    val status: Status = Status.Idle,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val video: List<TrackOption> = emptyList(),
    /** true when quality is automatic (adaptive). */
    val autoQuality: Boolean = true,
    /** Height currently being played, e.g. 720. */
    val currentHeight: Int? = null,
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val subtitlesOn: Boolean = false,
    val aspect: AspectMode = AspectMode.Fit,
    /** Wall-clock time when the sleep timer pauses playback, or null. */
    val sleepAt: Long? = null,
    val isLive: Boolean = false,
) {
    enum class Status { Idle, Loading, Playing, Error }
}

/**
 * Owns the ExoPlayer instance. Lives in the ViewModel so playback survives
 * rotation and continues while browsing the channel list.
 */
@UnstableApi
class PlayerController(
    context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val onHealth: (Channel, Boolean) -> Unit,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(8_000, 30_000, 1_500, 3_000).build())
        .build()
        .apply {
            playWhenReady = true
            // Subtitles start off; the user turns them on from the CC button.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

    private val names = DefaultTrackNameProvider(context.resources)
    private val UNKNOWN = context.getString(androidx.media3.ui.R.string.exo_track_unknown)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var watchdog: Job? = null
    private var sleepJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    watchdog?.cancel()
                    _state.update { it.copy(status = PlaybackState.Status.Playing, error = null, isLive = player.isCurrentMediaItemLive) }
                    _state.value.channel?.let { onHealth(it, true) }
                } else if (playbackState == Player.STATE_BUFFERING && _state.value.status != PlaybackState.Status.Playing) {
                    _state.update { it.copy(status = PlaybackState.Status.Loading) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                fail(describe(error))
            }

            override fun onTracksChanged(tracks: Tracks) = refreshTracks()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) _state.update { it.copy(currentHeight = videoSize.height) }
            }
        })
    }

    // ---------------------------------------------------------------- playback

    fun play(channel: Channel) {
        _state.update {
            PlaybackState(channel = channel, status = PlaybackState.Status.Loading, aspect = it.aspect, sleepAt = it.sleepAt)
        }
        val headers = buildMap { channel.referrer?.let { put("Referer", it) } }
        val dataSource = OkHttpDataSource.Factory(http)
            .setUserAgent(channel.userAgent ?: Repository.USER_AGENT)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder().setUri(channel.url).apply {
            // Many iptv-org streams are HLS behind URLs without a .m3u8 extension.
            if (!PROGRESSIVE.containsMatchIn(channel.url)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        // Track overrides belong to the previous stream; language preferences and
        // "subtitles off" carry over to the next channel.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverrides().build()
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        player.prepare()
        player.play()
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(25_000)
            if (_state.value.status == PlaybackState.Status.Loading) fail("The stream took too long to respond.")
        }
    }

    fun retry() = _state.value.channel?.let(::play)

    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) retry() else player.play()
        }
    }

    /** Seconds behind the live edge (0 for non-live streams). */
    fun liveOffsetSeconds(): Long {
        if (!player.isCurrentMediaItemLive) return 0
        val off = player.currentLiveOffset
        return if (off == C.TIME_UNSET) 0 else off / 1000
    }

    fun goLive() {
        player.seekToDefaultPosition()
        player.play()
    }

    fun stop() {
        watchdog?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.update { PlaybackState(aspect = it.aspect, sleepAt = it.sleepAt) }
    }

    fun release() {
        watchdog?.cancel()
        sleepJob?.cancel()
        player.release()
    }

    // ------------------------------------------------------------------ tracks

    private fun refreshTracks() {
        val groups = player.currentTracks.groups
        val params = player.trackSelectionParameters
        val video = ArrayList<Pair<Format, TrackOption>>()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        groups.forEachIndexed { gi, g ->
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val id = "$gi:$ti"
                when (g.type) {
                    C.TRACK_TYPE_VIDEO -> if (f.height > 0) video += f to TrackOption(id, "${f.height}p", bitrate(f), g.isTrackSelected(ti))
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(id, trackName(f, "Audio ${audio.size + 1}"), audioDetail(f), g.isTrackSelected(ti))
                    C.TRACK_TYPE_TEXT -> text += TrackOption(id, textName(f, text.size + 1), textDetail(f), g.isTrackSelected(ti))
                }
            }
        }
        val videoOptions = video
            .sortedWith(compareByDescending<Pair<Format, TrackOption>> { it.first.height }.thenByDescending { it.first.bitrate })
            .distinctBy { it.first.height to it.first.bitrate }
            .map { it.second }
        _state.update {
            it.copy(
                video = if (videoOptions.size > 1) videoOptions else emptyList(),
                autoQuality = params.overrides.values.none { o -> o.type == C.TRACK_TYPE_VIDEO },
                currentHeight = player.videoFormat?.height?.takeIf { h -> h > 0 } ?: it.currentHeight,
                audio = audio,
                text = text,
                subtitlesOn = !params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && text.any { t -> t.selected },
            )
        }
    }

    /** Media3's name, unless it only knows "Unknown" (no language or label in the stream). */
    private fun trackName(f: Format, fallback: String): String {
        val n = names.getTrackName(f)
        return if (f.language == null && f.label == null && n.startsWith(UNKNOWN)) n.replaceFirst(UNKNOWN, fallback) else n
    }

    private fun textName(f: Format, n: Int): String {
        val cc = f.sampleMimeType == MimeTypes.APPLICATION_CEA608 || f.sampleMimeType == MimeTypes.APPLICATION_CEA708
        return trackName(f, if (cc) "Closed captions" else "Subtitles $n")
    }

    private fun track(id: String): Pair<Tracks.Group, Int>? {
        val (gi, ti) = id.split(':').map { it.toIntOrNull() ?: return null }
        val g = player.currentTracks.groups.getOrNull(gi) ?: return null
        return if (ti < g.length) g to ti else null
    }

    /** null = automatic quality. */
    fun selectQuality(id: String?) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (id != null) track(id)?.let { (g, ti) -> b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ti)) }
        player.trackSelectionParameters = b.build()
        refreshTracks()
    }

    fun selectAudio(id: String) {
        val (g, ti) = track(id) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ti))
            // Remember the language so the next channel picks it too.
            .setPreferredAudioLanguage(g.getTrackFormat(ti).language)
            .build()
        refreshTracks()
    }

    /** null = subtitles off. */
    fun selectSubtitle(id: String?) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (id == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val (g, ti) = track(id) ?: return
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ti))
                .setPreferredTextLanguage(g.getTrackFormat(ti).language)
        }
        player.trackSelectionParameters = b.build()
        refreshTracks()
    }

    // ------------------------------------------------------------ view options

    fun setAspect(mode: AspectMode) = _state.update { it.copy(aspect = mode) }

    /** Pauses playback after [minutes]; null cancels the timer. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(sleepAt = null) }
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAt = at) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _state.update { it.copy(sleepAt = null) }
        }
    }

    fun streamInfo(): StreamInfo {
        val v = player.videoFormat
        val a = player.audioFormat
        return StreamInfo(
            resolution = v?.takeIf { it.width > 0 }?.let { "${it.width} × ${it.height}" },
            frameRate = v?.frameRate?.takeIf { it > 0 }?.let { "%.0f fps".format(it) },
            videoCodec = v?.codecs ?: v?.sampleMimeType,
            audioCodec = a?.codecs ?: a?.sampleMimeType,
            audioChannels = a?.channelCount?.takeIf { it > 0 }?.let(::channelLabel),
            bitrate = (v?.bitrate?.takeIf { it > 0 } ?: v?.peakBitrate?.takeIf { it > 0 })?.let { "%.1f Mbps".format(it / 1_000_000f) },
            host = _state.value.channel?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() }.orEmpty(),
        )
    }

    // ----------------------------------------------------------------- helpers

    private fun fail(message: String) {
        watchdog?.cancel()
        player.stop()
        val ch = _state.value.channel
        _state.update { it.copy(status = PlaybackState.Status.Error, error = message, isPlaying = false) }
        ch?.let { onHealth(it, false) }
    }

    private fun describe(e: PlaybackException): String {
        val cause = e.cause
        if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            return when (cause.responseCode) {
                403 -> "The stream refused the connection (HTTP 403). It may be geo-blocked."
                404 -> "The stream no longer exists (HTTP 404)."
                else -> "The stream returned HTTP ${cause.responseCode}."
            }
        }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Couldn't connect to the stream."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This stream's format isn't supported."
            else -> "The stream is offline or unavailable."
        }
    }

    companion object {
        private val PROGRESSIVE = Regex("""\.(mp4|webm|mkv|mp3|aac|ogg|ts)(\?|$)""", RegexOption.IGNORE_CASE)

        fun bitrate(f: Format): String? {
            val b = f.bitrate.takeIf { it > 0 } ?: f.peakBitrate.takeIf { it > 0 } ?: f.averageBitrate.takeIf { it > 0 } ?: return null
            return if (b >= 1_000_000) "%.1f Mbps".format(b / 1_000_000f) else "${b / 1000} kbps"
        }

        fun channelLabel(n: Int) = when (n) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 surround"
            8 -> "7.1 surround"
            else -> "$n channels"
        }

        /** The track name already includes language, channels and bitrate; add the codec. */
        private fun audioDetail(f: Format): String? = when {
            f.codecs?.startsWith("mp4a") == true -> "AAC"
            f.codecs?.startsWith("ac-3") == true -> "Dolby Digital"
            f.codecs?.startsWith("ec-3") == true -> "Dolby Digital Plus"
            f.codecs?.startsWith("opus") == true -> "Opus"
            else -> f.codecs?.substringBefore('.')?.uppercase()
        }

        private fun textDetail(f: Format): String? = when (f.sampleMimeType) {
            MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 ->
                if (f.accessibilityChannel != Format.NO_VALUE) "CC${f.accessibilityChannel}" else if (f.language != null) "Closed captions" else null
            MimeTypes.TEXT_VTT -> "WebVTT"
            MimeTypes.APPLICATION_TTML -> "TTML"
            else -> null
        }
    }
}
