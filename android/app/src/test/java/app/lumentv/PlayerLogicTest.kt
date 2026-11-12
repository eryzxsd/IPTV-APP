package app.lumentv

import app.lumentv.data.DecoderMode
import app.lumentv.data.HealthPolicy
import app.lumentv.ui.player.DecoderPolicy
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

class LivePolicyTest {
    @Test fun behindIsMeasuredFromTheNormalLivePosition() {
        // Players sit ~18 s behind the edge on purpose (3 x 6 s segments): that is "live".
        assertEquals(0, app.lumentv.ui.player.LivePolicy.secondsBehind(18_000, 18_000))
        assertTrue(app.lumentv.ui.player.LivePolicy.isAtLiveEdge(app.lumentv.ui.player.LivePolicy.secondsBehind(22_000, 18_000)))
        // Paused for a minute: 60 s behind.
        assertEquals(60, app.lumentv.ui.player.LivePolicy.secondsBehind(78_000, 18_000))
        // Unknown target offset: measure from the edge.
        assertEquals(30, app.lumentv.ui.player.LivePolicy.secondsBehind(30_000, -1))
    }

    @Test fun resumeJumpsToLiveOnlyWhenBehindAndEnabled() {
        val p = app.lumentv.ui.player.LivePolicy
        assertTrue(p.shouldJumpToLive(enabled = true, isLive = true, secondsBehind = 60))
        assertFalse("a brief pause just continues", p.shouldJumpToLive(true, true, 3))
        assertFalse("setting off: continue where you paused", p.shouldJumpToLive(false, true, 60))
        assertFalse("not a live stream", p.shouldJumpToLive(true, false, 60))
    }
}

class DecoderPolicyTest {
    private val hw = DecoderPolicy.Codec("c2.qti.avc.decoder", hardware = true, softwareOnly = false)
    private val sw = DecoderPolicy.Codec("c2.android.avc.decoder", hardware = false, softwareOnly = true)
    private val video = listOf(hw, sw)
    private fun pick(mode: DecoderMode, mime: String = "video/avc", list: List<DecoderPolicy.Codec> = video) =
        DecoderPolicy.filter(mode, mime, list) { it }.map { it.name }

    @Test fun modes() {
        assertEquals(listOf(hw.name, sw.name), pick(DecoderMode.Auto))
        assertEquals(listOf(hw.name), pick(DecoderMode.Hw))
        assertEquals(listOf(hw.name), pick(DecoderMode.HwPlus))
        assertEquals(listOf(sw.name), pick(DecoderMode.Sw))
    }

    @Test fun audioIsNeverFiltered() {
        val aac = listOf(DecoderPolicy.Codec("c2.android.aac.decoder", hardware = false, softwareOnly = true))
        assertEquals(listOf("c2.android.aac.decoder"), pick(DecoderMode.Hw, "audio/mp4a-latm", aac))
    }
}
