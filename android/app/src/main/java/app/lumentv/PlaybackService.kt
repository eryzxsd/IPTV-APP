package app.lumentv

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.lumentv.ui.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings > Play in background. Wraps the app's player in a media session, which gives a
 * media notification, lock-screen controls and headset buttons, and keeps the stream
 * playing after you leave the app.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var follow: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as LumenApp
        val controller = app.playerController
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, ChannelPlayer(controller.player, controller))
            .setSessionActivity(open)
            .build()
        // The player is rebuilt when the decoder setting changes: follow it.
        follow = app.appScope.launch {
            controller.playerFlow.collect { p -> session?.player = ChannelPlayer(p, controller) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused ends the service; while playing, it keeps going.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        follow?.cancel()
        // Only the session is released: the player belongs to the app.
        session?.release()
        session = null
        super.onDestroy()
    }
}

/** Next/previous in the notification or on a headset change channel; play resumes at live. */
@OptIn(UnstableApi::class)
private class ChannelPlayer(player: Player, private val controller: PlayerController) : ForwardingPlayer(player) {
    private val zapCommands = arrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        if (controller.onZap == null) super.getAvailableCommands()
        else super.getAvailableCommands().buildUpon().addAll(*zapCommands.toIntArray()).build()

    override fun isCommandAvailable(command: Int): Boolean =
        (command in zapCommands && controller.onZap != null) || super.isCommandAvailable(command)

    override fun seekToNext() = zap(1)
    override fun seekToNextMediaItem() = zap(1)
    override fun seekToPrevious() = zap(-1)
    override fun seekToPreviousMediaItem() = zap(-1)

    override fun play() {
        if (!isPlaying) controller.togglePlay() else super.play()
    }

    private fun zap(direction: Int) {
        controller.onZap?.invoke(direction)
    }
}
