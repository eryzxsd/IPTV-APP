package app.lumentv

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app (real playlist + guide from the network) on the JVM and
 * saves screenshots. Opt-in: set LUMEN_SCREENSHOTS to an output folder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = System.getenv("LUMEN_SCREENSHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

    @Before fun only() = assumeTrue(out != null)

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    private fun waitForData() {
        rule.waitUntil(180_000) { rule.activity.vm.channels.value.isNotEmpty() }
        rule.waitUntil(300_000) { rule.activity.vm.epg.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        Thread.sleep(4_000) // let logos arrive
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun phone() {
        waitForData()
        shot("1-channels")

        rule.onAllNodesWithText("Category").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("2-categories")
        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1_000)

        rule.onAllNodesWithText("Guide").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("3-guide")

        rule.onAllNodesWithText("Settings").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("4-settings")
        rule.onAllNodesWithText("TV GUIDE").onFirst().performScrollTo()
        rule.mainClock.advanceTimeBy(500)
        shot("4b-settings-guide")
        rule.onAllNodesWithText("Reset all settings").onFirst().performScrollTo()
        rule.mainClock.advanceTimeBy(500)
        shot("4c-settings-data")

        // Open a channel that has guide data.
        rule.onAllNodesWithText("Channels").onFirst().performClick()
        val vm = rule.activity.vm
        val ch = vm.channels.value.first { !vm.epg.value[it.epgKey].isNullOrEmpty() && it.name.contains("News") }
        // Favorite it and open it from the Favorites filter (the only row there).
        vm.toggleFavorite(ch)
        rule.waitUntil(15_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ch.id in vm.library.value.favorites
        }
        rule.onAllNodesWithText("Favorites").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitUntil(15_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.onAllNodesWithText(ch.name).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(ch.name).onFirst().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        shot("5-player")

        // Player option sheets.
        rule.waitUntil(30_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.player.state.value.text.isNotEmpty() || vm.player.state.value.status == app.lumentv.ui.player.PlaybackState.Status.Error
        }
        fun openSheet(desc: String, name: String) {
            // Controls auto-hide while playing; tap the video to show them.
            if (rule.onAllNodes(hasContentDescription(desc)).fetchSemanticsNodes().isEmpty()) {
                rule.onRoot().performTouchInput { click(Offset(centerX, 200f)) }
                rule.mainClock.advanceTimeBy(500)
            }
            rule.onAllNodes(hasContentDescription(desc)).onFirst().performClick()
            rule.mainClock.advanceTimeBy(1_500)
            shot(name)
            // The sheet is its own window: send Back to the focused window, not the activity.
            androidx.test.espresso.Espresso.pressBack()
            rule.mainClock.advanceTimeBy(1_500)
        }
        openSheet("Subtitles", "7-subtitles")
        openSheet("Audio track", "8-audio")
        openSheet("More options", "9-more")
        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1_000)
        shot("6-favorites-miniplayer")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeBrowse() {
        waitForData()
        val vm = rule.activity.vm
        val ch = vm.channels.value.first { !vm.epg.value[it.epgKey].isNullOrEmpty() && it.name.contains("News") }
        rule.activity.runOnUiThread { vm.play(ch) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitUntil(15_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.onAllNodesWithText(ch.name).fetchSemanticsNodes().isNotEmpty()
        }
        shot("10-landscape-home")
        rule.onAllNodesWithText(ch.name).onFirst().performClick() // mini player -> fullscreen player
        rule.mainClock.advanceTimeBy(1_500)
        shot("11-fullscreen")
        rule.activity.runOnUiThread { vm.browsing.value = true }
        rule.mainClock.advanceTimeBy(1_500)
        shot("12-fullscreen-browse")
    }
}
