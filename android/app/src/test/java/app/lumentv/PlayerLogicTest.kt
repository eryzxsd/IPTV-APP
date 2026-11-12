package app.lumentv

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
    private fun v(id: String, w: Int, h: Int, rate: Int, selected: Boolean = false) = VideoCandidate(id, w, h, rate, selected, key = id)

    @Test fun variantsWithResolutionUseStandardNames() {
        val opts = QualityLogic.options(
            listOf(v("a", 640, 360, 800_000), v("b", 1920, 1080, 5_000_000), v("c", 1216, 684, 2_500_000, true)),
            emptyMap(),
            playingKey = "c",
        )
        assertEquals(listOf("1080p", "720p", "360p"), opts.map { it.label })
        assertEquals("2.5 Mbps · Playing now", opts[1].detail)
    }

    @Test fun unlabelledVariantsGetRankNamesNotBareBitrates() {
        // The "Anime x HIDIVE" case: five variants with BANDWIDTH only.
        val rates = listOf(3_300_000, 2_300_000, 1_600_000, 1_000_000, 640_000)
        val opts = QualityLogic.options(rates.mapIndexed { i, r -> v("$i", -1, -1, r) }, emptyMap(), playingKey = "1")
        assertEquals(listOf("Highest", "High", "Medium", "Low", "Lowest"), opts.map { it.label })
        assertEquals("3.3 Mbps", opts[0].detail)
        assertEquals("2.3 Mbps · Playing now", opts[1].detail)
        assertEquals("640 kbps", opts[4].detail)
    }

    @Test fun learnedSizeReplacesRankName() {
        val rates = listOf(3_300_000, 2_300_000, 640_000)
        val opts = QualityLogic.options(
            rates.mapIndexed { i, r -> v("$i", -1, -1, r) },
            learnedSizes = mapOf("1" to (1216 to 684)),
            playingKey = "1",
        )
        assertEquals(listOf("Highest", "720p", "Lowest"), opts.map { it.label })
    }

    @Test fun twoUnlabelledVariants() {
        val opts = QualityLogic.options(listOf(v("a", -1, -1, 900_000), v("b", -1, -1, 3_000_000)), emptyMap(), null)
        assertEquals(listOf("Highest", "Lowest"), opts.map { it.label })
    }

    @Test fun standardNames() {
        assertEquals("720p", QualityLogic.standardName(1216, 684))
        assertEquals("1080p", QualityLogic.standardName(1920, 800)) // cinema crop
        assertEquals("1080p", QualityLogic.standardName(1920, 1080))
        assertEquals("576p", QualityLogic.standardName(720, 576))
        assertEquals("480p", QualityLogic.standardName(854, 480))
        assertEquals("360p", QualityLogic.standardName(640, 360))
        assertEquals("2160p", QualityLogic.standardName(3840, 2160))
    }

    @Test fun bitrateUsesADotOnEveryPhone() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("id-ID"))
            assertEquals("3.3 Mbps", QualityLogic.formatBitrate(3_300_000))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test fun chipLabel() {
        val two = listOf(TrackOption("a", "1080p", null, false), TrackOption("b", "720p", null, true))
        assertEquals("Auto · 720p", QualityLogic.chipLabel(true, two, "720p", "1080p"))
        assertEquals("Auto · 1080p", QualityLogic.chipLabel(true, two, null, "1080p"))
        assertEquals("Auto", QualityLogic.chipLabel(true, two, null, null))
        assertEquals("720p", QualityLogic.chipLabel(false, two, "720p", null))
        assertEquals("576p", QualityLogic.chipLabel(true, emptyList(), "576p", "720p"))
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
        assertEquals(1, HealthPolicy.recordFailure(once, t + HealthPolicy.EXPIRY_MS + 1).fails)
        assertFalse(HealthPolicy.isOffline(null, t))
    }
}
