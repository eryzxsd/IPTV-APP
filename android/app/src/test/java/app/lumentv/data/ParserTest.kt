package app.lumentv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.util.zip.GZIPInputStream

class ParserTest {
    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="CNN.us@SD" tvg-logo="https://x/cnn.png" group-title="News",CNN (1080p)
        https://cnn.example/live.m3u8
        #EXTINF:-1 tvg-id="TLC.ru@SD" group-title="Lifestyle;Documentary",TLC (Russia) (720p) [Geo-blocked]
        #EXTVLCOPT:http-user-agent=Special/1.0
        #EXTVLCOPT:http-referrer=https://ref.example/
        http://tlc.example/stream
        #EXTINF:-1 tvg-id="" group-title="Undefined",Starz, the "best" (576p) [Not 24/7]
        https://starz.example/a.m3u8
        #EXTINF:-1 tvg-id="CNN.us@SD" tvg-logo="https://x/cnn.png" group-title="News",CNN (1080p)
        https://cnn.example/live.m3u8
    """.trimIndent()

    @Test fun parsesM3u() {
        val ch = M3uParser.parse(playlist)
        assertEquals("duplicates are dropped", 3, ch.size)
        val cnn = ch[0]
        assertEquals("CNN", cnn.name)
        assertEquals("1080p", cnn.quality)
        assertEquals("cnn.us", cnn.epgKey)
        assertEquals("US", cnn.country)
        assertEquals(listOf("News"), cnn.groups)

        val tlc = ch[1]
        assertEquals("TLC (Russia)", tlc.name)
        assertTrue(tlc.geoBlocked)
        assertEquals("RU", tlc.country)
        assertEquals("Special/1.0", tlc.userAgent)
        assertEquals("https://ref.example/", tlc.referrer)
        assertEquals(listOf("Lifestyle", "Documentary"), tlc.groups)

        val starz = ch[2]
        assertEquals("Starz, the \"best\"", starz.name)
        assertTrue(starz.notAlways)
        assertEquals("n:starzthebest", starz.epgKey)
        assertEquals(listOf("Other"), starz.groups)
        assertNull(starz.userAgent)
    }

    @Test fun xmltvTime() {
        assertEquals(1_762_905_600_000, XmltvParser.parseTime("20251112000000 +0000"))
        assertEquals(1_762_905_600_000 - 3_600_000, XmltvParser.parseTime("20251112000000 +0100"))
        assertEquals(0, XmltvParser.parseTime("garbage"))
    }

    @Test fun splitsEpgIds() {
        assertEquals("CNBC HD" to "US", XmltvParser.splitEpgId("CNBC.HD.us2"))
        assertEquals("TLC HD" to "GB", XmltvParser.splitEpgId("TLC.HD.uk"))
        assertEquals("plex.tv.ION.plex" to "", XmltvParser.splitEpgId("plex.tv.ION.plex"))
    }

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="CNN.HD.us2"><display-name>CNN HD</display-name></channel>
          <channel id="CNN.HD.(Pacific).us2"><display-name>CNN HD (Pacific)</display-name></channel>
          <channel id="TLC.HD.uk"><display-name>TLC HD</display-name></channel>
          <channel id="Starz.the.best.us2"><display-name>Starz, the "best"</display-name></channel>
          <programme start="20300101000000 +0000" stop="20300101010000 +0000" channel="CNN.HD.us2">
            <title>News &amp; More</title><desc>Top stories</desc><category>News</category>
            <episode-num system="onscreen">S1E2</episode-num>
          </programme>
          <programme start="20300101030000 +0000" stop="20300101040000 +0000" channel="CNN.HD.(Pacific).us2"><title>Pacific feed</title></programme>
          <programme start="20300101000000 +0000" stop="20300101010000 +0000" channel="TLC.HD.uk"><title>UK show</title></programme>
          <programme start="20300101000000 +0000" stop="20300101020000 +0000" channel="Starz.the.best.us2"><title>Movie</title></programme>
          <programme start="19990101000000 +0000" stop="19990101010000 +0000" channel="CNN.HD.us2"><title>Too old</title></programme>
        </tv>
    """.trimIndent()

    @Test fun matchesAndFiltersGuide() {
        val channels = M3uParser.parse(playlist)
        val from = XmltvParser.parseTime("20291231000000 +0000")
        val to = XmltvParser.parseTime("20300103000000 +0000")
        val parser = XmltvParser(channels, { KXmlParser() }, from, to)
        val data = XmltvParser.merge(listOf(parser.parse(xml.byteInputStream())))

        val cnn = data["cnn.us"]
        assertNotNull("CNN matches its East feed by name + country", cnn)
        assertEquals(listOf("News & More"), cnn!!.map { it.title })
        assertEquals("S1E2", cnn[0].episode)
        assertEquals("News", cnn[0].category)

        assertFalse("TLC (Russia) must not get the UK schedule", data.containsKey("tlc.ru"))
        assertEquals(listOf("Movie"), data["n:starzthebest"]?.map { it.title })
    }

    @Test fun nowAndNext() {
        val list = listOf(Programme(0, 10, "a"), Programme(10, 20, "b"), Programme(30, 40, "c"))
        assertEquals("b" to "c", list.nowAndNext(15).let { it.first?.title to it.second?.title })
        assertEquals(null to "c", list.nowAndNext(25).let { it.first?.title to it.second?.title })
    }

    /** Runs against real downloads when LUMEN_REAL_DATA points at a folder with index.m3u and *.xml.gz. */
    @Test fun realData() {
        val dir = System.getenv("LUMEN_REAL_DATA")?.let(::File)
        assumeTrue(dir != null && File(dir, "index.m3u").exists())
        val t0 = System.currentTimeMillis()
        val channels = M3uParser.parse(File(dir, "index.m3u").readText())
        println("playlist: ${channels.size} channels in ${System.currentTimeMillis() - t0} ms")
        assertTrue(channels.size > 5000)

        val now = System.currentTimeMillis()
        val parser = XmltvParser(channels, { KXmlParser() }, now - 3 * 3_600_000L, now + 48 * 3_600_000L)
        val t1 = System.currentTimeMillis()
        val results = dir!!.listFiles { f -> f.name.endsWith(".xml.gz") }!!.sorted().map { f ->
            GZIPInputStream(f.inputStream().buffered(), 65536).use { parser.parse(it.buffered()) }
        }
        val data = XmltvParser.merge(results)
        val withGuide = channels.count { !data[it.epgKey].isNullOrEmpty() }
        println("guide: ${data.size} keys, $withGuide channels with guide data in ${System.currentTimeMillis() - t1} ms")
        assertTrue(withGuide > 300)
        data.values.forEach { list -> assertTrue(list.zipWithNext().all { (a, b) -> a.start < b.start }) }

        // Recommendations on the full playlist, as if two news channels were watched recently.
        val recent = channels.filter { "News" in it.groups && it.country == "US" }.take(2)
        val t2 = System.currentTimeMillis()
        val rows = Recommender.homeRows(
            channels, recent, emptyList(), "US",
            hasGuideNow = { !data[it.epgKey].isNullOrEmpty() }, isOffline = { false }, playedOk = { false },
        )
        println("home rows in ${System.currentTimeMillis() - t2} ms (recent: ${recent.map { it.name }})")
        rows.forEach { r -> println("  ${r.title}: ${r.channels.take(6).joinToString { it.name }}") }
        assertTrue(rows.any { it.id == "for-you" })
    }
}
