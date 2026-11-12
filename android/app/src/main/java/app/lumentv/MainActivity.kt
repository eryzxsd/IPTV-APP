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

    override fun onStop() {
        super.onStop()
        // Don't keep streaming audio in the background (unless we're in PiP).
        if (!inPip && !isChangingConfigurations) vm.player.player.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // TV remotes: channel up/down zap through the list.
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.player.togglePlay(); return true }
            // TV remotes: Guide / Menu opens the channel browser over the video.
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> if (playerVisible) {
                vm.browsing.value = !vm.browsing.value
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
