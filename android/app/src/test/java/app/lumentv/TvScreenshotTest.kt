package app.lumentv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.lumentv.tv.TvActivity
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the Android TV (Compose) UI at 1080p with real data and drives it with remote keys. Opt-in like [ScreenshotTest]. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshotTest {
    /** TVs aren't in touch mode, so the remote's focus shows from the start. */
    @get:Rule(order = 0) val keyboardMode = object : org.junit.rules.ExternalResource() {
        override fun before() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<TvActivity>()
    private val out = System.getenv("LUMEN_SCREENSHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

    @Before fun only() = assumeTrue(out != null)

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    private fun key(k: Key, times: Int = 1) = repeat(times) {
        rule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(k) }
        rule.mainClock.advanceTimeBy(200)
    }

    /** Back as the system delivers it when the screen doesn't handle the key itself. */
    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(300)
    }

    @Test fun tv() {
        rule.waitUntil(180_000) { rule.activity.vm.homeRows.value.isNotEmpty() }
        rule.waitUntil(300_000) { rule.activity.vm.epg.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        Thread.sleep(3_000)
        shot("tv-0-home")
        key(Key.DirectionRight, 2)
        key(Key.DirectionDown)
        shot("tv-1-home-focus")

        // Play a channel with guide data.
        val vm = rule.activity.vm
        val ch = vm.channels.value.first { !vm.epg.value[it.epgKey].isNullOrEmpty() }
        rule.runOnUiThread { vm.play(ch) }
        rule.mainClock.advanceTimeBy(500)
        shot("tv-2-home-playing")
        key(Key.DirectionCenter) // "Now playing" has focus
        shot("tv-3-player-info")
        key(Key.DirectionCenter)
        shot("tv-4-player-controls")
        key(Key.DirectionRight, 4)
        key(Key.DirectionCenter)
        shot("tv-5-player-quality")
        // Dialogs close with Back on a device; the test's key injection doesn't reach the dialog's back handler.
        rule.onAllNodesWithText("OK").onFirst().performClick()
        rule.onAllNodesWithText("Close").let { if (it.fetchSemanticsNodes().isNotEmpty()) it.onFirst().performClick() }
        key(Key.Back)
        key(Key.DirectionLeft)
        rule.mainClock.advanceTimeBy(500)
        shot("tv-6-channel-panel")
        key(Key.DirectionDown, 2)
        shot("tv-6b-channel-panel-moved")
        key(Key.Back) // closes the list
        systemBack() // back to Home
        shot("tv-6c-home-return")
        rule.onAllNodesWithText("Guide").onFirst().performClick()
        rule.mainClock.advanceTimeBy(500)
        shot("tv-7-guide")
        key(Key.DirectionDown)
        shot("tv-7b-guide-moved")
        systemBack()

        rule.onAllNodesWithText("Search").onFirst().performClick()
        shot("tv-8-search")
        systemBack()

        rule.onAllNodesWithText("Settings").onFirst().performClick()
        shot("tv-9-settings")
        key(Key.DirectionDown, 2)
        shot("tv-9b-settings-moved")
    }
}
