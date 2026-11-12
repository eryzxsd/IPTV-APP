package app.lumentv

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import app.lumentv.tv.TvActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Renders the Android TV (Leanback) UI at 1080p with real data. Opt-in like [ScreenshotTest]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshotTest {
    private val out = System.getenv("LUMEN_SCREENSHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

    @Before fun only() = assumeTrue(out != null)

    private fun idle(ms: Long = 300) {
        ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
        Thread.sleep(50)
        ShadowLooper.idleMainLooper()
    }

    private fun waitUntil(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < end) {
            Thread.sleep(200)
            ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun ActivityScenario<TvActivity>.shot(name: String) {
        idle(500)
        onActivity { it.window.decorView.captureRoboImage(File(out, "$name.png").path) }
    }

    private fun ActivityScenario<TvActivity>.key(code: Int, times: Int = 1) = repeat(times) {
        onActivity {
            it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        idle(250)
    }

    @Test fun tv() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val s = ActivityScenario.launch(TvActivity::class.java)
        lateinit var a: TvActivity
        s.onActivity { a = it }
        waitUntil(180_000) { a.vm.homeRows.value.isNotEmpty() }
        waitUntil(300_000) { a.vm.epg.value.isNotEmpty() }
        Thread.sleep(5_000) // logos
        idle(1_000)
        s.shot("tv-0-home")

        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        Thread.sleep(2_000)
        s.shot("tv-1-home-focus")

        // Play a channel with guide data.
        val ch = a.vm.channels.value.first { !a.vm.epg.value[it.epgKey].isNullOrEmpty() }
        s.onActivity { it.play(ch) }
        idle(1_000)
        s.shot("tv-2-player-info")
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        s.shot("tv-3-player-controls")
        s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(2_000)
        s.shot("tv-4-channel-panel")
        s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_BACK)

        s.onActivity { it.open(app.lumentv.tv.TvGuideFragment(), "guide") }
        repeat(10) { Thread.sleep(500); idle(100) }
        s.shot("tv-5-guide")
        s.key(KeyEvent.KEYCODE_BACK)

        s.onActivity { it.open(app.lumentv.tv.TvSettingsFragment(), "settings") }
        s.shot("tv-6-settings")
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        idle(1_000)
        s.shot("tv-7-settings-playback")
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        idle(1_000)
        s.shot("tv-8-settings-decoder")
    }
}
