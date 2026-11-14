package app.lumentv.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Starting the app again uses the saved channel list without touching the network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupCacheTest {
    private val server = MockWebServer()
    private val hits = AtomicInteger()

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                hits.incrementAndGet()
                val m3u = "#EXTM3U\n" + (1..50).joinToString("\n") {
                    "#EXTINF:-1 tvg-id=\"c$it.us\" group-title=\"News\",Channel $it\nhttps://e/$it.m3u8"
                }
                return MockResponse.Builder().code(200).body(m3u).build()
            }
        }
        server.start()
    }

    @After fun stop() = server.close()

    @Test fun secondStartUsesSavedList() = runBlocking {
        val urls = listOf(server.url("/index.m3u").toString())
        val ctx = RuntimeEnvironment.getApplication()
        assertNull("nothing saved yet", Repository(ctx, OkHttpClient()).cachedChannels(urls))

        val first = Repository(ctx, OkHttpClient()).loadChannels(urls, force = false)
        assertEquals(50, first.size)
        assertEquals(1, hits.get())

        // A new app start (new Repository): the saved list comes back at once, fresh, with no download.
        val saved = Repository(ctx, OkHttpClient()).cachedChannels(urls)
        assertNotNull(saved)
        assertEquals(first, saved!!.first)
        assertFalse("just saved, so not stale", saved.second)
        assertEquals(first, Repository(ctx, OkHttpClient()).loadChannels(urls, force = false))
        assertEquals("no second download", 1, hits.get())
    }
}
