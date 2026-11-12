package app.lumentv.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.lumentv.PlaybackService
import app.lumentv.R
import androidx.media3.ui.PlayerView
import app.lumentv.data.Channel
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.player.PlaybackState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Fragments that want the remote's keys before the default focus handling. */
interface TvKeyHandler {
    fun onKey(event: KeyEvent): Boolean
}

/**
 * Android TV entry point: a View-based (Leanback) UI, so TVs never load the Compose UI.
 * Shares the ViewModel, data and player with the phone app.
 */
class TvActivity : FragmentActivity() {
    val vm: MainViewModel by viewModels()
    private var session: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One video surface for every screen: Home shows the playing channel behind its rows,
        // and opening the player never re-attaches the surface (cheap on low-end TVs).
        val video = PlayerView(this).apply {
            useController = false
            isFocusable = false
            setShutterBackgroundColor(Color.BLACK)
            setKeepContentOnPlayerReset(true)
        }
        videoView = video
        scrim = View(this).apply { setBackgroundColor(getColor(R.color.tv_bg)) }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(video, FrameLayout.LayoutParams(-1, -1))
            addView(scrim, FrameLayout.LayoutParams(-1, -1))
            addView(FrameLayout(this@TvActivity).apply { id = ROOT }, FrameLayout.LayoutParams(-1, -1))
        })
        lifecycleScope.launch { vm.player.playerFlow.collect { video.player = it } }
        lifecycleScope.launch {
            vm.player.state.map { it.aspect }.distinctUntilChanged().collect { video.resizeMode = it.resizeMode }
        }
        lifecycleScope.launch {
            vm.player.state.map { it.isPlaying }.distinctUntilChanged().collect { video.keepScreenOn = it }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(ROOT, TvBrowseFragment()) }
            // Settings > Resume last channel: go straight to the player once it starts.
            if (vm.appPrefs.value.resumeLastChannel) {
                lifecycleScope.launch {
                    vm.player.state.map { it.channel != null }.distinctUntilChanged().collect { playing ->
                        if (playing && supportFragmentManager.findFragmentByTag(PLAYER) == null) showPlayer()
                    }
                }
            }
        }
        lifecycleScope.launch {
            vm.appPrefs.map { it.playInBackground }.distinctUntilChanged().collect { on ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) connectSession(on)
            }
        }
    }

    private lateinit var videoView: PlayerView
    private lateinit var scrim: View

    /** How much of the video shows through behind the current screen (0 = none, 1 = all). */
    fun setVideoVisibility(amount: Float) {
        val hasVideo = vm.player.state.value.channel != null
        scrim.animate().alpha(if (hasVideo) 1f - amount else 1f).setDuration(200).start()
    }

    fun play(c: Channel) {
        val s = vm.player.state.value
        if (s.channel?.id != c.id || s.status == PlaybackState.Status.Error) vm.play(c)
        showPlayer()
    }

    fun showPlayer() {
        if (supportFragmentManager.findFragmentByTag(PLAYER) != null) return
        open(TvPlayerFragment(), PLAYER)
    }

    fun open(fragment: Fragment, tag: String) = supportFragmentManager.commit {
        replace(ROOT, fragment, tag)
        addToBackStack(tag)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val f = supportFragmentManager.findFragmentById(ROOT)
        if (f is TvKeyHandler && f.onKey(event)) return true
        // Channel up/down and play/pause work everywhere.
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.player.togglePlay(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Settings > Play in background (same as the phone app). */
    private fun connectSession(enabled: Boolean) {
        if (enabled && session == null) {
            session = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        } else if (!enabled) {
            session?.let { MediaController.releaseFuture(it) }
            session = null
            stopService(Intent(this, PlaybackService::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        connectSession(vm.appPrefs.value.playInBackground)
    }

    override fun onStop() {
        super.onStop()
        session?.let { MediaController.releaseFuture(it) }
        session = null
        if (!isChangingConfigurations && !vm.appPrefs.value.playInBackground) vm.player.player.pause()
    }

    companion object {
        const val ROOT = 0x7f0a_1001
        const val PLAYER = "player"
    }
}
