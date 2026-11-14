package app.lumentv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in (LUMEN_REAL_DATA): how long each start-up step takes with the real iptv-org playlist. */
class StartupBenchmark {
    @Test fun startup() {
        assumeTrue(!System.getenv("LUMEN_REAL_DATA").isNullOrBlank())
        val text = OkHttpClient().newCall(Request.Builder().url(Sources.DEFAULT_PLAYLISTS[0]).build()).execute().use { it.body.string() }
        fun <T> t(label: String, block: () -> T): T {
            repeat(2) { block() }
            val s = System.nanoTime(); val r = block(); println("BENCH $label ${(System.nanoTime() - s) / 1_000_000} ms"); return r
        }
        val list = t("parse m3u (${text.length / 1024} KB)") { M3uParser.parse(text) }
        val f = File.createTempFile("playlist", ".bin")
        CacheCodec.writeChannels(f, "k", list)
        println("BENCH channels=${list.size} file=${f.length() / 1024} KB")
        t("read channel cache") { CacheCodec.readChannels(f, "k") }
        t("home rows (no history)") {
            Recommender.homeRows(list, emptyList(), emptyList(), "US", hasGuideNow = { false }, isOffline = { false }, playedOk = { false })
        }
        val recent = list.shuffled(java.util.Random(1)).take(20)
        t("home rows (20 watched)") {
            Recommender.homeRows(list, recent, recent.take(5), "US", hasGuideNow = { it.hashCode() % 7 == 0 }, isOffline = { false }, playedOk = { it in recent })
        }
        t("groups") { list.flatMap { it.groups }.groupingBy { it }.eachCount() }
        t("sort A-Z") { list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    }
}
