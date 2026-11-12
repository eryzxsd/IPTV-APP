package app.lumentv.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProbeTest {
    private fun res(name: String) = javaClass.getResourceAsStream("/probe/$name")!!.readBytes()

    @Test fun h264TransportStream() = assertEquals(1216 to 684, SegmentProbe.sizeFromTs(res("avc_1216x684.ts")))

    @Test fun h265TransportStream() = assertEquals(1920 to 800, SegmentProbe.sizeFromTs(res("hevc_1920x800.ts")))

    @Test fun fmp4InitSegment() = assertEquals(1280 to 720, SegmentProbe.sizeFromMp4(res("fmp4_init_1280x720.mp4")))

    @Test fun aes128SegmentsAreDecrypted() {
        // Clear-key HLS (Pluto TV etc.): encrypt a segment, then decrypt only its first part like the probe does.
        val key = ByteArray(16) { (it * 7 + 3).toByte() }
        val iv = ResolutionProbe.sequenceIv(7448)
        val plain = res("avc_1216x684.ts")
        val c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        val enc = c.doFinal(plain)
        assertEquals(null, SegmentProbe.sizeFromTs(enc))
        val prefix = enc.copyOf(40_000)
        assertEquals(1216 to 684, SegmentProbe.sizeFromTs(ResolutionProbe.decryptPrefix(prefix, key, iv)))
    }

    @Test fun playlistSegmentsCarryKeysAndIvs() {
        val segs = ResolutionProbe.parseSegments(
            """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7448
            #EXTINF:5,
            plain0.ts
            #EXT-X-KEY:METHOD=AES-128,URI="k1"
            #EXTINF:5,
            enc1.ts
            #EXT-X-KEY:METHOD=AES-128,URI="k2",IV=0x000102030405060708090A0B0C0D0E0F
            #EXTINF:5,
            enc2.ts
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="drm"
            #EXTINF:5,
            drm3.ts
            """.trimIndent(),
        )
        assertEquals(listOf("plain0.ts", "enc1.ts", "enc2.ts"), segs.map { it.uri }) // SAMPLE-AES (DRM) skipped
        assertEquals(null, segs[0].keyUri)
        assertEquals("k1", segs[1].keyUri)
        assertEquals(ResolutionProbe.sequenceIv(7449).toList(), segs[1].iv!!.toList())
        assertEquals((0..15).map { it.toByte() }, segs[2].iv!!.toList())
    }

    @Test fun garbageIsIgnored() {
        assertEquals(null, SegmentProbe.sizeFromTs(ByteArray(4000) { (it * 31).toByte() }))
        assertEquals(null, SegmentProbe.sizeFromMp4(ByteArray(10)))
    }

    /** Opt-in: LUMEN_PROBE_URL=<multivariant .m3u8> probes every variant of a real stream. */
    @Test fun realStream() {
        val url = System.getenv("LUMEN_PROBE_URL")
        assumeTrue(!url.isNullOrBlank())
        val http = OkHttpClient()
        val master = http.newCall(okhttp3.Request.Builder().url(url!!).build()).execute().use { it.request.url.toString() to it.body.string() }
        val base = java.net.URI(master.first)
        val variants = master.second.lines().zipWithNext().filter { it.first.startsWith("#EXT-X-STREAM-INF") }
        val probe = ResolutionProbe(http)
        for ((inf, uri) in variants) {
            val bw = Regex("BANDWIDTH=(\\d+)").find(inf)?.groupValues?.get(1)
            val t0 = System.currentTimeMillis()
            val size = probe.probe(base.resolve(uri.trim()).toString(), emptyMap())
            println("BANDWIDTH=$bw -> $size (${System.currentTimeMillis() - t0} ms)")
        }
    }
}
