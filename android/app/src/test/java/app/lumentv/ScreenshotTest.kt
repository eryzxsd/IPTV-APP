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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
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

    /** Taps the first on-screen node with this exact text (lazy lists keep some off-screen nodes around). */
    private fun clickVisible(text: String) {
        val nodes = rule.onAllNodesWithText(text)
        val i = nodes.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        check(i >= 0) { "no visible \"$text\"" }
        nodes[i].performClick()
    }

    @Test fun phone() {
        waitForData()
        rule.runOnUiThread { rule.activity.vm.player.stop() }
        rule.mainClock.advanceTimeBy(1_000)
        shot("0a-home-idle")
        rule.onRoot().performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1_000)
        shot("0b-home-idle-scrolled")
        rule.onRoot().performTouchInput { swipeDown(); swipeDown() }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Search channels").performClick()
        rule.mainClock.advanceTimeBy(800)
        shot("0c-search-empty")
        rule.onNode(hasSetTextAction()).performTextInput("news")
        rule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(500)
        rule.mainClock.advanceTimeBy(500)
        shot("0d-search-results")
        androidx.test.espresso.Espresso.pressBack() // keyboard / search
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().performTouchInput { swipeDown(); swipeDown() }
        rule.mainClock.advanceTimeBy(500)
        shot("0-home")
        rule.onAllNodesWithText("Channels").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("1-channels")
        rule.onAllNodesWithText(rule.activity.vm.filtered.value.first().name).onFirst().performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(1_000)
        shot("1b-channel-options")
        androidx.test.espresso.Espresso.pressBack()
        rule.mainClock.advanceTimeBy(800)

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
        rule.onAllNodesWithText(ch.name).onFirst().performClick() // plays it on Home
        rule.mainClock.advanceTimeBy(2_000)
        shot("5-home-player")
        rule.onNodeWithContentDescription("Minimize player").performClick()
        rule.mainClock.advanceTimeBy(800)
        shot("5c-player-minimized")
        check(rule.activity.vm.player.state.value.channel != null) { "minimizing keeps playing" }
        // Swipe the mini player up to open the full player again.
        rule.onNodeWithText(rule.activity.vm.player.state.value.channel!!.name, useUnmergedTree = true)
            .performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f, durationMillis = 200) }
        rule.mainClock.advanceTimeBy(800)
        shot("5d-player-restored")
        check(!rule.activity.vm.minimized.value) { "swiping the mini player up opens the player" }
        rule.onNodeWithContentDescription("Minimize player").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Close player").performClick() // the mini player's ✕
        rule.mainClock.advanceTimeBy(800)
        shot("5e-mini-closed")
        check(rule.activity.vm.player.state.value.channel == null) { "the mini player's close stops playback" }
        rule.runOnUiThread { rule.activity.vm.play(rule.activity.vm.lastChannel.value!!) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
        rule.mainClock.advanceTimeBy(1_500)
        shot("5b-home-recommendations")
        rule.onRoot().performTouchInput { swipeDown(startY = bottom * 0.4f, endY = bottom * 0.85f) }
        rule.mainClock.advanceTimeBy(1_000)

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
        rule.onAllNodesWithText("Channels").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("6-favorites-miniplayer")
    }

    private fun playNewsChannel(): app.lumentv.data.Channel {
        val vm = rule.activity.vm
        val ch = vm.channels.value.first { !vm.epg.value[it.epgKey].isNullOrEmpty() && it.name.contains("News") }
        rule.activity.runOnUiThread { vm.play(ch) }
        rule.mainClock.advanceTimeBy(2_000)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.mainClock.advanceTimeBy(1_000)
        return ch
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeBrowse() {
        waitForData()
        playNewsChannel() // phones go fullscreen in landscape
        shot("11-fullscreen")
        rule.activity.runOnUiThread { rule.activity.vm.browsing.value = true }
        rule.mainClock.advanceTimeBy(1_500)
        shot("12-fullscreen-browse")
        val newsCount = "%,d".format(rule.activity.vm.groups.value.first { it.first == "News" }.second)
        fun pickerShown() = rule.onAllNodesWithText(newsCount, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        repeat(2) {
            if (!pickerShown()) {
                clickVisible("Category")
                rule.mainClock.advanceTimeBy(800)
                runCatching { rule.waitUntil(3_000) { pickerShown() } }
            }
        }
        shot("12b-fullscreen-categories")
        // Choosing a category is checked by key presses in TvScreenshotTest (and was tapped here when run alone);
        // in a full run this test sees a stale semantics tree, so set the filter directly for the screenshot.
        rule.runOnUiThread { rule.activity.vm.filter.value = app.lumentv.ui.Filter.Group("News") }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } // leave the picker
        rule.mainClock.advanceTimeBy(800)
        shot("12c-fullscreen-news")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletHome() {
        waitForData()
        shot("13a-tablet-idle")
        playNewsChannel() // tablets/TV keep the Home layout in landscape
        shot("13b-tablet-home")
    }

    @Test fun guideFailuresAndUnsavedBar() {
        waitForData()
        val vm = rule.activity.vm
        // A guide file that doesn't exist, so it fails and shows the Retry card.
        val bad = "https://epgshare01.online/epgshare01/epg_ripper_DOES_NOT_EXIST.xml.gz"
        rule.activity.runOnUiThread { vm.saveSettings(vm.settings.value.copy(customEpgUrls = listOf(bad))) }
        rule.waitUntil(240_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.epgFailures.value.isNotEmpty() && vm.epgState.value !is app.lumentv.data.LoadState.Loading
        }
        rule.onAllNodesWithText("Guide").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("14-guide-failure")
        rule.onAllNodesWithText("Settings").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        // Toggle a guide region: the unsaved-changes bar must show without scrolling.
        rule.onAllNodesWithText("Japan 1").onFirst().performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("15-settings-unsaved-and-failure")
    }

    @Test fun clockAndPlaybackSettings() {
        waitForData()
        val vm = rule.activity.vm
        rule.onAllNodesWithText("Settings").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        shot("16-settings-playback")
        rule.activity.runOnUiThread { vm.saveApp(vm.appPrefs.value.copy(clock = app.lumentv.data.ClockMode.Always)) }
        rule.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.appPrefs.value.clock == app.lumentv.data.ClockMode.Always
        }
        rule.onAllNodesWithText("Home").onFirst().performClick()
        playNewsChannel()
        rule.mainClock.advanceTimeBy(5_000) // let the controls hide
        shot("17-clock-always")
    }
}
