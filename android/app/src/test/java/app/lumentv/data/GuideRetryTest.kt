package app.lumentv.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

/** Guide downloads: automatic retries, failures listed, and "Retry" fetching only the failed files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideRetryTest {
    private val server = MockWebServer()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile private var bigFileFixed = false

    private val channels = listOf("Alpha", "Bravo", "Charlie").map {
        Channel("id$it", "$it TV", "", listOf("News"), "$it.us", "${it.lowercase()}.us", "US", "https://e/$it")
    }

    private fun xmltvGz(channel: String, title: String): ByteArray {
        val f = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val xml = """<?xml version="1.0" encoding="UTF-8"?><tv>
            <channel id="$channel.us"><display-name>$channel TV</display-name></channel>
            <programme start="${f.format(Date(now - 600_000))}" stop="${f.format(Date(now + 600_000))}" channel="$channel.us"><title>$title</title></programme>
            </tv>"""
        return ByteArrayOutputStream().also { GZIPOutputStream(it).use { gz -> gz.write(xml.toByteArray()) } }.toByteArray()
    }

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val n = hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                fun ok(bytes: ByteArray) = MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()
                return when (path) {
                    "/ok.xml.gz" -> ok(xmltvGz("Alpha", "Morning News"))
                    // Flaky server: two 503s, then fine -> the automatic retry should succeed.
                    "/flaky.xml.gz" -> if (n <= 2) MockResponse.Builder().code(503).build() else ok(xmltvGz("Bravo", "Evening News"))
                    // Missing until "fixed": not retried automatically, listed as failed.
                    "/big.xml.gz" -> if (bigFileFixed) ok(xmltvGz("Charlie", "Late News")) else MockResponse.Builder().code(404).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun stop() = server.close()

    @Test fun retriesFlakyFilesListsFailuresAndRetriesOnlyThose() = runBlocking {
        val repo = Repository(RuntimeEnvironment.getApplication(), OkHttpClient())
        val urls = listOf("/ok.xml.gz", "/flaky.xml.gz", "/big.xml.gz").map { server.url(it).toString() }

        val data = repo.loadEpg(channels, urls, force = true)
        assertEquals("Morning News", data["alpha.us"]?.single()?.title)
        assertEquals("flaky file recovered by automatic retries", "Evening News", data["bravo.us"]?.single()?.title)
        assertEquals(3, hits["/flaky.xml.gz"]!!.get())
        assertEquals("404 is not retried", 1, hits["/big.xml.gz"]!!.get())
        val failures = repo.epgFailures.value
        assertEquals(listOf(urls[2]), failures.map { it.url })
        assertEquals("File not found (404)", failures.single().reason)

        // The file comes back; Retry downloads only the failed one and merges it in.
        bigFileFixed = true
        val okHitsBefore = hits["/ok.xml.gz"]!!.get()
        val retried = repo.retryFailedEpg(channels, urls, data)
        assertEquals("Late News", retried["charlie.us"]?.single()?.title)
        assertEquals("Morning News", retried["alpha.us"]?.single()?.title)
        assertEquals("working files aren't downloaded again", okHitsBefore, hits["/ok.xml.gz"]!!.get())
        assertTrue(repo.epgFailures.value.isEmpty())
    }
}
