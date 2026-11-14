package app.lumentv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.lumentv.tv.TvActivity
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
        rule.mainClock.advanceTimeBy(250)
    }

    /** Back as the system delivers it when the screen doesn't handle the key itself. */
    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(300)
    }

    private fun clickText(t: String) {
        rule.onAllNodesWithText(t).onFirst().performClick()
        rule.mainClock.advanceTimeBy(400)
    }

    @Test fun tv() {
        rule.waitUntil(180_000) { rule.activity.vm.homeRows.value.isNotEmpty() }
        rule.waitUntil(300_000) { rule.activity.vm.epg.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        Thread.sleep(3_000)
        shot("tv-0-home")
        key(Key.DirectionRight, 2)
        shot("tv-1-home-right")
        key(Key.DirectionDown)
        shot("tv-1b-home-down")
        key(Key.DirectionDown, 3)
        shot("tv-1c-home-down-more")
        key(Key.Menu)
        shot("tv-1d-options")
        clickText("Watch")
        shot("tv-2-player-info")
        key(Key.DirectionCenter)
        shot("tv-3-player-controls")
        key(Key.Back)
        key(Key.Two)
        key(Key.One)
        shot("tv-4-number-entry")
        rule.mainClock.advanceTimeBy(2_000)
        key(Key.DirectionLeft)
        rule.mainClock.advanceTimeBy(500)
        shot("tv-5-channel-panel")
        key(Key.DirectionLeft) // into the filter column
        key(Key.DirectionDown, 3) // ... Category
        shot("tv-5b-panel-rail")
        key(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(500)
        shot("tv-5c-panel-categories")
        key(Key.DirectionDown) // the second category
        key(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(500)
        shot("tv-5d-panel-filtered")
        check(rule.activity.vm.filter.value is app.lumentv.ui.Filter.Group) { "picking a category filters the list" }
        rule.runOnUiThread { rule.activity.vm.filter.value = app.lumentv.ui.Filter.All }
        rule.mainClock.advanceTimeBy(300)
        key(Key.Back)
        systemBack()
        rule.mainClock.advanceTimeBy(500)
        shot("tv-6-home-playing")

        key(Key.DirectionLeft)
        shot("tv-7-side-menu")
        clickText("All channels")
        shot("tv-8-browse")
        systemBack()
        key(Key.DirectionLeft)
        clickText("Search")
        shot("tv-9-search")
        systemBack()
        key(Key.DirectionLeft)
        clickText("TV guide")
        shot("tv-10-guide")
        systemBack()
        rule.mainClock.advanceTimeBy(800)
        key(Key.DirectionCenter) // "Now playing" card -> player
        rule.mainClock.advanceTimeBy(500)
        key(Key.DirectionCenter) // controls
        key(Key.DirectionRight, 7) // ... -> Close
        shot("tv-11-close-focused")
        key(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(1_000)
        shot("tv-12-after-close")
        key(Key.DirectionCenter) // play the first recommendation
        rule.mainClock.advanceTimeBy(1_000)
        shot("tv-13-playing-again")
        key(Key.Back) // Back on the player closes it
        rule.mainClock.advanceTimeBy(1_000)
        shot("tv-14-after-back")
    }
}
