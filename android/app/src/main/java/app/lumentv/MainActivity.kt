package app.lumentv

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.lumentv.ui.LumenNav
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    val vm: MainViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    /** Set by the UI while the player screen is showing. */
    var playerVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // TVs get the Leanback interface, even when a box's launcher opens the phone entry point.
        val ui = getSystemService(android.app.UiModeManager::class.java)
        if (ui?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(android.content.Intent(this, app.lumentv.tv.TvActivity::class.java).putExtras(intent))
            finish()
            return
        }
        // Follow the Play in background setting while the app is open.
        lifecycleScope.launch {
            vm.appPrefs.map { it.playInBackground }.distinctUntilChanged().collect { on ->
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) connectSession(on)
            }
        }
        addOnPictureInPictureModeChangedListener(Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode })
        setContent {
            val app by vm.appPrefs.collectAsState()
            LumenTheme(dynamicColor = app.dynamicColor, pureBlack = app.pureBlack) {
                LumenNav(vm = vm, inPip = inPip, onPlayerVisible = { playerVisible = it })
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Keep watching in a floating window when leaving the app from the player.
        if (vm.appPrefs.value.autoPip && playerVisible && vm.player.player.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    private var sessionController: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    /**
     * Play in background: connecting to [PlaybackService] starts it; Media3 then shows the media
     * notification and keeps the service in the foreground while playing.
     */
    private fun connectSession(enabled: Boolean) {
        if (enabled && sessionController == null) {
            val token = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
            sessionController = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
        } else if (!enabled) {
            sessionController?.let { androidx.media3.session.MediaController.releaseFuture(it) }
            sessionController = null
            stopService(android.content.Intent(this, PlaybackService::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        connectSession(vm.appPrefs.value.playInBackground)
    }

    override fun onStop() {
        super.onStop()
        // Unbind; if playing in the background the service keeps running on its own.
        sessionController?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        sessionController = null
        // Don't keep streaming audio in the background (unless we're in PiP).
        if (!inPip && !isChangingConfigurations && !vm.appPrefs.value.playInBackground) vm.player.player.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // TV remotes: channel up/down zap through the list.
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.player.togglePlay(); return true }
            // TV remotes: Guide / Menu opens the channel browser over the video.
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> if (playerVisible) {
                val open = !vm.browsing.value
                vm.browsing.value = open
                if (open) vm.fullscreen.value = true // TVs aren't fullscreen just because they're landscape
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
