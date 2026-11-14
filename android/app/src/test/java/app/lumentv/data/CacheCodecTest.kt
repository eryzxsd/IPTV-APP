package app.lumentv.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheCodecTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun channels(n: Int) = List(n) { i ->
        Channel(
            id = "id$i", name = "Channel $i", logo = if (i % 3 == 0) "" else "https://i.imgur.com/logo$i.png",
            groups = listOf(listOf("News", "Sports", "Movies", "General")[i % 4]) + if (i % 5 == 0) listOf("Kids") else emptyList(),
            tvgId = "ch$i.us", epgKey = "ch$i.us", country = listOf("US", "GB", "IN", "")[i % 4],
            url = "https://example.com/live/$i/index.m3u8", quality = if (i % 2 == 0) "720p" else null,
            geoBlocked = i % 7 == 0, notAlways = i % 11 == 0, userAgent = if (i % 13 == 0) "UA/1.0" else null, referrer = null,
        )
    }

    private fun guide(channels: Int, perChannel: Int): EpgData = (0 until channels).associate { c ->
        "ch$c.us" to List(perChannel) { p ->
            Programme(1_700_000_000_000 + p * 1_800_000L, 1_700_000_000_000 + (p + 1) * 1_800_000L, "Show ${p % 40}",
                desc = if (p % 2 == 0) "A description of episode $p of the show, with a few words." else null,
                category = if (p % 3 == 0) "News" else null, episode = if (p % 4 == 0) "S1E$p" else null)
        }
    }

    @Test fun channelsRoundTrip() {
        val f = tmp.newFile("p.bin")
        val list = channels(500)
        CacheCodec.writeChannels(f, "k", list)
        assertEquals(list, CacheCodec.readChannels(f, "k")!!.second)
        assertNull("other key", CacheCodec.readChannels(f, "other"))
    }

    @Test fun guideRoundTrip() {
        val f = tmp.newFile("g.bin")
        val data = guide(50, 20)
        val failures = listOf(EpgFailure("u", "UK", "HTTP 503"))
        CacheCodec.writeGuide(f, "k", data, failures)
        val g = CacheCodec.readGuide(f, "k")!!
        assertEquals(data, g.data)
        assertEquals(failures, g.failures)
    }

    @Test fun corruptFileIsIgnored() {
        val f = tmp.newFile("bad.bin")
        f.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertNull(CacheCodec.readChannels(f, "k"))
        assertNull(CacheCodec.readGuide(f, "k"))
    }

    /** Same data as the old JSON cache: how long a cold start spends reading it. */
    @Test fun fasterThanJson() {
        val list = channels(11_500)
        val epg = guide(700, 60)
        val json = Json { ignoreUnknownKeys = true }
        val jf = tmp.newFile("p.json").apply { writeText(json.encodeToString(list)) }
        val ef = tmp.newFile("e.json").apply { writeText(json.encodeToString(epg)) }
        val bf = tmp.newFile("p.bin").also { CacheCodec.writeChannels(it, "k", list) }
        val gf = tmp.newFile("g.bin").also { CacheCodec.writeGuide(it, "k", epg, emptyList()) }
        fun time(block: () -> Unit): Long {
            repeat(3) { block() } // warm up
            val t = System.nanoTime(); repeat(5) { block() }; return (System.nanoTime() - t) / 5_000_000
        }
        val jsonMs = time { json.decodeFromString<List<Channel>>(jf.readText()); json.decodeFromString<Map<String, List<Programme>>>(ef.readText()) }
        val binMs = time { CacheCodec.readChannels(bf, "k"); CacheCodec.readGuide(gf, "k") }
        println("CACHE json=${jsonMs}ms (${(jf.length() + ef.length()) / 1024} KB)  binary=${binMs}ms (${(bf.length() + gf.length()) / 1024} KB)")
    }
}
