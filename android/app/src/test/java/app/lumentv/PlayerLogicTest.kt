package app.lumentv

import app.lumentv.data.Health
import app.lumentv.data.HealthPolicy
import app.lumentv.ui.player.ErrorPolicy
import app.lumentv.ui.player.QualityLogic
import app.lumentv.ui.player.TrackOption
import app.lumentv.ui.player.VideoCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {
    @Test fun variantsWithResolutionAreSortedHighestFirst() {
        val opts = QualityLogic.options(
            listOf(
                VideoCandidate("0:0", 360, 800_000, false, "a"),
                VideoCandidate("0:1", 1080, 5_000_000, false, "b"),
                VideoCandidate("0:2", 720, 2_500_000, true, "c"),
            ),
            emptyMap(),
            playingFormatId = "c",
        )
        assertEquals(listOf("1080p", "720p", "360p"), opts.map { it.label })
        assertEquals("2.5 Mbps · Playing now", opts[1].detail)
    }

    @Test fun variantsWithoutResolutionAreKeptAndLabelledByBitrate() {
        // Many IPTV manifests only declare BANDWIDTH; these used to be dropped, leaving just "Auto".
        val opts = QualityLogic.options(
            listOf(
                VideoCandidate("0:0", -1, 1_200_000, false, "lo"),
                VideoCandidate("0:1", -1, 4_000_000, false, "hi"),
            ),
            emptyMap(),
            playingFormatId = null,
        )
        assertEquals(listOf("4.0 Mbps", "1.2 Mbps"), opts.map { it.label })
    }

    @Test fun heightsLearnedFromDecodedVideoAreUsed() {
        val opts = QualityLogic.options(
            listOf(VideoCandidate("0:0", -1, 1_200_000, false, "lo"), VideoCandidate("0:1", -1, 4_000_000, false, "hi")),
            learnedHeights = mapOf("hi" to 1080),
            playingFormatId = "hi",
        )
        assertEquals("1080p", opts[0].label)
        assertEquals("4.0 Mbps · Playing now", opts[0].detail)
        assertEquals("1.2 Mbps", opts[1].label)
    }

    @Test fun chipLabel() {
        val two = listOf(TrackOption("a", "1080p", null, false), TrackOption("b", "720p", null, true))
        assertEquals("Auto · 720p", QualityLogic.chipLabel(true, two, 720, "1080p"))
        assertEquals("Auto · 1080p", QualityLogic.chipLabel(true, two, null, "1080p")) // before the first frame
        assertEquals("Auto", QualityLogic.chipLabel(true, two, null, null))
        assertEquals("720p", QualityLogic.chipLabel(false, two, 720, null))
        assertEquals("576p", QualityLogic.chipLabel(true, emptyList(), 576, "720p")) // single quality: no "Auto"
        assertEquals("720p", QualityLogic.chipLabel(true, emptyList(), null, "720p"))
    }

    @Test fun onlyDefiniteErrorsCount() {
        assertTrue(ErrorPolicy.isDefinite(404, false))
        assertTrue(ErrorPolicy.isDefinite(403, false))
        assertTrue(ErrorPolicy.isDefinite(null, unsupportedFormat = true))
        assertFalse("timeouts are not definite", ErrorPolicy.isDefinite(null, false))
        assertFalse(ErrorPolicy.isDefinite(503, false))
        assertFalse(ErrorPolicy.isDefinite(408, false))
        assertFalse(ErrorPolicy.isDefinite(429, false))
    }

    @Test fun offlineNeedsRepeatedFailuresAndExpires() {
        val t = 1_000_000_000L
        val once = HealthPolicy.recordFailure(null, t)
        assertFalse("one failure is not enough", HealthPolicy.isOffline(once, t))
        val twice = HealthPolicy.recordFailure(once, t + 60_000)
        assertTrue(HealthPolicy.isOffline(twice, t + 120_000))
        assertFalse("marks expire", HealthPolicy.isOffline(twice, t + HealthPolicy.EXPIRY_MS + 120_000))
        // An old failure doesn't stack with a new one.
        val later = HealthPolicy.recordFailure(once, t + HealthPolicy.EXPIRY_MS + 1)
        assertEquals(1, later.fails)
        assertFalse(HealthPolicy.isOffline(null, t))
    }
}
