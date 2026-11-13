package app.lumentv.tv

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.lumentv.PlaybackService
import app.lumentv.ui.MainViewModel
import app.lumentv.ui.theme.LumenTheme
import app.lumentv.ui.tv.TvApp
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android TV entry point: the Compose + Material 3 interface built for the remote ([TvApp]).
 * Shares the ViewModel, data and player with the phone app.
 */
class TvActivity : ComponentActivity() {
    val vm: MainViewModel by viewModels()
    private var session: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            vm.appPrefs.map { it.playInBackground }.distinctUntilChanged().collect { on ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) connectSession(on)
            }
        }
        setContent {
            val app by vm.appPrefs.collectAsState()
            LumenTheme(dynamicColor = false, pureBlack = app.pureBlack) { TvApp(vm) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Remote buttons that work on every screen.
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.player.togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!vm.player.player.isPlaying) vm.player.togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.player.pause(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.playPrevious(); return true }
        }
        return super.onKeyDown(keyCode, event)
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
}
