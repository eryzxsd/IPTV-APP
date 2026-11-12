package app.lumentv.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

data class Quality(val height: Int, val label: String)

data class PlaybackState(
    val channel: Channel? = null,
    val status: Status = Status.Idle,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val qualities: List<Quality> = emptyList(),
    /** null = automatic */
    val selectedHeight: Int? = null,
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
        .apply { playWhenReady = true }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var watchdog: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    watchdog?.cancel()
                    _state.update { it.copy(status = PlaybackState.Status.Playing, error = null) }
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

            override fun onTracksChanged(tracks: Tracks) {
                val heights = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                    .filter { it > 0 }.distinct().sortedDescending()
                _state.update { s -> s.copy(qualities = if (heights.size > 1) heights.map { Quality(it, "${it}p") } else emptyList()) }
            }
        })
    }

    fun play(channel: Channel) {
        _state.value = PlaybackState(channel = channel, status = PlaybackState.Status.Loading)
        val headers = buildMap {
            channel.referrer?.let { put("Referer", it) }
        }
        val dataSource = OkHttpDataSource.Factory(http)
            .setUserAgent(channel.userAgent ?: Repository.USER_AGENT)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder().setUri(channel.url).apply {
            // Many iptv-org streams are HLS behind URLs without a .m3u8 extension.
            if (!PROGRESSIVE.containsMatchIn(channel.url)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
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

    fun setQuality(height: Int?) {
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (height != null) {
            player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.let { g ->
                val idx = (0 until g.length).firstOrNull { g.getTrackFormat(it).height == height }
                if (idx != null) params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, idx))
            }
        }
        player.trackSelectionParameters = params.build()
        _state.update { it.copy(selectedHeight = height) }
    }

    fun stop() {
        watchdog?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState()
    }

    fun release() {
        watchdog?.cancel()
        player.release()
    }

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

    private companion object {
        val PROGRESSIVE = Regex("""\.(mp4|webm|mkv|mp3|aac|ogg|ts)(\?|$)""", RegexOption.IGNORE_CASE)
    }
}
