package app.lumentv.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

/** Downloads, parses and caches playlists and guide data. */
class Repository(context: Context, private val http: OkHttpClient) {
    private val cacheDir = File(context.cacheDir, "lumen").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private val _epgState = MutableStateFlow<LoadState>(LoadState.Idle)
    val epgState: StateFlow<LoadState> = _epgState

    @Serializable
    private class Cached<T>(val key: String, val time: Long, val data: T)

    suspend fun loadChannels(urls: List<String>, force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        val key = "v1|" + urls.joinToString("|")
        if (!force) readCache<List<Channel>>("playlist.json", key, PLAYLIST_TTL)?.let { return@withContext it }
        val errors = mutableListOf<String>()
        val lists = urls.map { url ->
            async {
                runCatching { M3uParser.parse(get(url).use { it.readBytes().decodeToString() }) }
                    .onFailure { errors += "${url.substringAfterLast('/')}: ${it.message}" }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()
        val seen = HashSet<String>()
        val all = lists.flatten().filter { seen.add("${it.name}|${it.url}") }
        if (all.isEmpty()) throw IOException(errors.joinToString("\n").ifEmpty { "The playlist is empty" })
        writeCache("playlist.json", key, all)
        all
    }

    suspend fun loadEpg(channels: List<Channel>, urls: List<String>, force: Boolean): EpgData = withContext(Dispatchers.IO) {
        if (urls.isEmpty() || channels.isEmpty()) {
            _epgState.value = LoadState.Idle
            return@withContext emptyMap()
        }
        val key = "v1|" + urls.joinToString("|") + "|" + channels.size
        if (!force) readCache<Map<String, List<Programme>>>("epg.json", key, EPG_TTL)?.let {
            _epgState.value = LoadState.Done()
            return@withContext it
        }

        val now = System.currentTimeMillis()
        val parser = XmltvParser(channels, { Xml.newPullParser() }, now - 3 * HOUR, now + 48 * HOUR)
        val bytes = AtomicLong()
        val done = AtomicLong()
        val errors = mutableListOf<String>()
        fun report() {
            _epgState.value = LoadState.Loading(
                "Guide ${done.get()}/${urls.size} · ${"%.1f".format(bytes.get() / 1048576f)} MB",
                done.get().toFloat() / urls.size,
            )
        }
        report()
        val gate = Semaphore(3)
        val results = coroutineScope {
            urls.map { url ->
                async {
                    gate.withPermit {
                        try {
                            get(url).use { raw ->
                                val counted = CountingStream(raw) { n -> bytes.addAndGet(n); }
                                parser.parse(maybeGunzip(counted))
                            }
                        } catch (e: Exception) {
                            synchronized(errors) { errors += "${url.substringAfterLast('/')}: ${e.message}" }
                            null
                        } finally {
                            done.incrementAndGet()
                            report()
                        }
                    }
                }
            }.awaitAll()
        }
        val data = XmltvParser.merge(results.filterNotNull())
        _epgState.value = if (data.isEmpty() && errors.isNotEmpty()) LoadState.Failed(errors.joinToString("\n"))
        else LoadState.Done(errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        if (data.isNotEmpty()) writeCache("epg.json", key, data)
        data
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun get(url: String): InputStream {
        val res = http.newCall(Request.Builder().url(url).build()).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("HTTP ${res.code}")
        }
        return res.body.byteStream()
    }

    private fun maybeGunzip(input: InputStream): InputStream {
        val b = BufferedInputStream(input, 65536)
        b.mark(2)
        val gz = b.read() == 0x1f && b.read() == 0x8b
        b.reset()
        return if (gz) BufferedInputStream(GZIPInputStream(b, 65536), 65536) else b
    }

    private inline fun <reified T> readCache(name: String, key: String, ttl: Long): T? = runCatching {
        val f = File(cacheDir, name)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > ttl) return null
        val c = f.inputStream().buffered().use { json.decodeFromStream<Cached<T>>(it) }
        if (c.key == key) c.data else null
    }.getOrNull()

    private inline fun <reified T> writeCache(name: String, key: String, data: T) {
        runCatching {
            val tmp = File(cacheDir, "$name.tmp")
            tmp.outputStream().buffered().use { json.encodeToStream(Cached(key, System.currentTimeMillis(), data), it) }
            tmp.renameTo(File(cacheDir, name))
        }
    }

    private class CountingStream(private val inner: InputStream, private val onRead: (Long) -> Unit) : InputStream() {
        override fun read(): Int = inner.read().also { if (it >= 0) onRead(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) onRead(it.toLong()) }
        override fun close() = inner.close()
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val PLAYLIST_TTL = 6 * HOUR
        private const val EPG_TTL = 4 * HOUR
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    }
}
