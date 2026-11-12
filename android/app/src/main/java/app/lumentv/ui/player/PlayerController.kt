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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
import kotlinx.coroutines.sync.withPermit
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
    /** Standard name of the size being played, e.g. "720p" (for 1216×684). */
    val currentQuality: String? = null,
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val subtitlesOn: Boolean = false,
    val aspect: AspectMode = AspectMode.Fit,
    /** Wall-clock time when the sleep timer pauses playback, or null. */
    val sleepAt: Long? = null,
    val isLive: Boolean = false,
    /** Still connecting after a while: some channels are just slow to start. */
    val slow: Boolean = false,
    /** Silently reconnecting after a network hiccup. */
    val reconnecting: Boolean = false,
    /** Looking up the resolutions of quality options the stream doesn't label. */
    val probingQualities: Boolean = false,
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
    buffer: app.lumentv.data.BufferMode,
    private val onHealth: (Channel, Boolean) -> Unit,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder().apply {
                when (buffer) {
                    app.lumentv.data.BufferMode.Fast -> setBufferDurationsMs(2_500, 20_000, 1_000, 2_000)
                    app.lumentv.data.BufferMode.Balanced -> setBufferDurationsMs(8_000, 30_000, 1_500, 3_000)
                    app.lumentv.data.BufferMode.Smooth -> setBufferDurationsMs(20_000, 60_000, 3_000, 6_000)
                }
            }.build(),
        )
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
    /** Sizes learned from decoded video for variants whose manifest entry has no RESOLUTION. */
    private val learnedSizes = HashMap<String, Pair<Int, Int>>()
    /** The variant currently being downloaded (from the media source, not the decoder). */
    private var currentVariant: Format? = null
    private var autoRetried = false
    private val probe = app.lumentv.data.ResolutionProbe(http)
    private var probeJob: Job? = null
    /** Settings > Look up resolutions. */
    var probeEnabled = true
    private var appliedPrefs: app.lumentv.data.AppPrefs? = null

    /** Applies Settings. Only changed values are applied, so choices made in the player stick. */
    fun applyPrefs(p: app.lumentv.data.AppPrefs) {
        val old = appliedPrefs
        appliedPrefs = p
        probeEnabled = p.lookUpResolutions
        if (old == null || old.maxQuality != p.maxQuality) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                if (p.maxQuality > 0) setMaxVideoSize(Int.MAX_VALUE, p.maxQuality) else clearVideoSizeConstraints()
            }.build()
        }
        if (old == null || old.subtitlesByDefault != p.subtitlesByDefault) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !p.subtitlesByDefault)
                .build()
            refreshTracks()
        }
        if (old == null || old.defaultAspect != p.defaultAspect) {
            AspectMode.entries.firstOrNull { it.name == p.defaultAspect }?.let { setAspect(it) }
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    watchdog?.cancel()
                    _state.update {
                        it.copy(status = PlaybackState.Status.Playing, error = null, isLive = player.isCurrentMediaItemLive, slow = false, reconnecting = false)
                    }
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
                val definite = isDefinite(error)
                // One silent reconnect for network hiccups before showing an error.
                if (!definite && !autoRetried) {
                    autoRetried = true
                    _state.update { it.copy(status = PlaybackState.Status.Loading, reconnecting = true) }
                    scope.launch {
                        delay(2_000)
                        player.prepare()
                        player.play()
                    }
                    return
                }
                fail(describe(error), definite)
            }

            override fun onTracksChanged(tracks: Tracks) = refreshTracks()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height <= 0) return
                // Remember the real size of the variant that's playing (manifests often omit it).
                currentVariant?.let { learnedSizes[variantKey(it)] = videoSize.width to videoSize.height }
                _state.update { it.copy(currentQuality = QualityLogic.standardName(videoSize.width, videoSize.height)) }
                refreshTracks()
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            // The variant ExoPlayer switched to. Its Format is the same one listed in Tracks,
            // unlike player.videoFormat, which comes from the decoder.
            override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: MediaLoadData) {
                val f = mediaLoadData.trackFormat ?: return
                if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO && mediaLoadData.trackType != C.TRACK_TYPE_DEFAULT) return
                if (f.height <= 0 && f.bitrate <= 0 && f.peakBitrate <= 0) return
                if (f.sampleMimeType != null && !MimeTypes.isVideo(f.sampleMimeType) && f.height <= 0) return
                currentVariant = f
                refreshTracks()
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
        learnedSizes.clear()
        currentVariant = null
        probeJob?.cancel()
        autoRetried = false
        player.setMediaSource(
            DefaultMediaSourceFactory(dataSource)
                // Slow or flaky servers get more attempts before an error is reported.
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
                .createMediaSource(item),
        )
        player.prepare()
        player.play()
        startSlowTimer()
    }

    /** Never gives up on a slow channel; after a while the UI offers to keep waiting or move on. */
    private fun startSlowTimer() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(SLOW_AFTER_MS)
            if (_state.value.status == PlaybackState.Status.Loading) _state.update { it.copy(slow = true) }
        }
    }

    fun retry() = _state.value.channel?.let(::play)

    /** "Keep waiting": hide the slow-loading hint and keep trying. */
    fun keepWaiting() {
        _state.update { it.copy(slow = false) }
        startSlowTimer()
    }

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
        val video = ArrayList<VideoCandidate>()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        groups.forEachIndexed { gi, g ->
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val id = "$gi:$ti"
                when (g.type) {
                    C.TRACK_TYPE_VIDEO -> video += VideoCandidate(id, f.width, f.height, rate(f), g.isTrackSelected(ti), variantKey(f))
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(id, trackName(f, "Audio ${audio.size + 1}"), audioDetail(f), g.isTrackSelected(ti))
                    C.TRACK_TYPE_TEXT -> text += TrackOption(id, textName(f, text.size + 1), textDetail(f), g.isTrackSelected(ti))
                }
            }
        }
        val videoOptions = QualityLogic.options(video, learnedSizes, currentVariant?.let(::variantKey))
        _state.update {
            it.copy(
                video = videoOptions,
                autoQuality = params.overrides.values.none { o -> o.type == C.TRACK_TYPE_VIDEO },
                currentQuality = player.videoSize.takeIf { v -> v.height > 0 }?.let { v -> QualityLogic.standardName(v.width, v.height) }
                    ?: it.currentQuality,
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

    /**
     * Finds the real resolution of quality options the manifest doesn't label, by reading
     * a small part of one segment of each (a few hundred KB each). Runs once per channel.
     */
    fun probeQualities() {
        if (!probeEnabled || probeJob?.isActive == true) return
        val manifest = player.currentManifest as? androidx.media3.exoplayer.hls.HlsManifest ?: return
        val channel = _state.value.channel ?: return
        val todo = manifest.multivariantPlaylist.variants.filter { v ->
            v.format.height <= 0 && !learnedSizes.containsKey(variantKey(v.format))
        }
        if (todo.isEmpty()) return
        val headers = buildMap {
            put("User-Agent", channel.userAgent ?: Repository.USER_AGENT)
            channel.referrer?.let { put("Referer", it) }
        }
        _state.update { it.copy(probingQualities = true) }
        probeJob = scope.launch {
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            kotlinx.coroutines.coroutineScope {
                todo.forEach { v ->
                    launch {
                        val size = gate.withPermit {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { probe.probe(v.url.toString(), headers) }
                        }
                        if (size != null && _state.value.channel?.id == channel.id) {
                            learnedSizes[variantKey(v.format)] = size
                            refreshTracks()
                        }
                    }
                }
            }
            _state.update { it.copy(probingQualities = false) }
        }
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

    /** Shows an error. Only [definite] failures count towards marking the channel offline. */
    private fun fail(message: String, definite: Boolean) {
        watchdog?.cancel()
        player.stop()
        val ch = _state.value.channel
        _state.update { it.copy(status = PlaybackState.Status.Error, error = message, isPlaying = false, slow = false, reconnecting = false) }
        if (definite) ch?.let { onHealth(it, false) }
    }

    private fun httpStatus(e: PlaybackException): Int? =
        (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode

    private fun isDefinite(e: PlaybackException): Boolean = ErrorPolicy.isDefinite(
        httpStatus(e),
        unsupportedFormat = e.errorCode in UNSUPPORTED_CODES,
    )

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

        private const val LOAD_RETRIES = 6
        private const val SLOW_AFTER_MS = 12_000L
        private val UNSUPPORTED_CODES = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        )

        /** HLS gives each variant a unique id; the same id appears in Tracks and in load events. */
        fun variantKey(f: Format): String = f.id ?: "bitrate:${rate(f)}"

        fun rate(f: Format): Int = f.bitrate.takeIf { it > 0 } ?: f.peakBitrate.takeIf { it > 0 } ?: f.averageBitrate

        fun bitrate(f: Format): String? = QualityLogic.formatBitrate(rate(f))

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
