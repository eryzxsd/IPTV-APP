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
    private val _epgFailures = MutableStateFlow<List<EpgFailure>>(emptyList())
    /** Guide files that couldn't be downloaded (shown with a Retry button). */
    val epgFailures: StateFlow<List<EpgFailure>> = _epgFailures

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

    /** Last parsed result per guide file (this session), so a retry only re-downloads failed files. */
    private val fileResults = java.util.concurrent.ConcurrentHashMap<String, XmltvParser.FileResult>()
    private var resultsKey: String? = null

    @Serializable
    private class EpgCache(val data: Map<String, List<Programme>>, val failures: List<EpgFailure>)

    private fun epgKey(channels: List<Channel>, urls: List<String>) = "v2|" + urls.joinToString("|") + "|" + channels.size

    suspend fun loadEpg(channels: List<Channel>, urls: List<String>, force: Boolean, ttlHours: Int = 4): EpgData = withContext(Dispatchers.IO) {
        if (urls.isEmpty() || channels.isEmpty()) {
            _epgState.value = LoadState.Idle
            _epgFailures.value = emptyList()
            return@withContext emptyMap()
        }
        val key = epgKey(channels, urls)
        if (!force) readCache<EpgCache>("epg.json", key, ttlHours * HOUR)?.let {
            _epgFailures.value = it.failures
            _epgState.value = LoadState.Done()
            return@withContext it.data
        }
        fileResults.clear()
        resultsKey = key
        download(channels, urls, urls, base = null, key = key, label = "Guide")
    }

    /** Downloads only the guide files that failed last time and merges them in. */
    suspend fun retryFailedEpg(channels: List<Channel>, urls: List<String>, current: EpgData): EpgData = withContext(Dispatchers.IO) {
        val failed = _epgFailures.value.map { it.url }.filter { it in urls }
        if (failed.isEmpty() || channels.isEmpty()) return@withContext current
        val key = epgKey(channels, urls)
        // Same session: proper merge from parsed results. After a restart: fill in what's missing.
        val haveAll = resultsKey == key && urls.filter { it !in failed }.all { fileResults.containsKey(it) }
        if (!haveAll) {
            fileResults.clear()
            resultsKey = key
        }
        download(channels, urls, failed, base = if (haveAll) null else current, key = key, label = "Retrying guide")
    }

    private suspend fun download(
        channels: List<Channel>,
        urls: List<String>,
        toFetch: List<String>,
        base: EpgData?,
        key: String,
        label: String,
    ): EpgData = coroutineScope {
        val now = System.currentTimeMillis()
        val parser = XmltvParser(channels, { Xml.newPullParser() }, now - 3 * HOUR, now + 48 * HOUR)
        val bytes = AtomicLong()
        val done = AtomicLong()
        val failures = java.util.Collections.synchronizedList(ArrayList<EpgFailure>())
        fun report() {
            _epgState.value = LoadState.Loading(
                "$label ${done.get()}/${toFetch.size} · ${"%.1f".format(bytes.get() / 1048576f)} MB",
                done.get().toFloat() / toFetch.size,
            )
        }
        report()
        val gate = Semaphore(3)
        toFetch.map { url ->
            async {
                gate.withPermit {
                    try {
                        fileResults[url] = fetchGuideWithRetry(url, parser, bytes)
                    } catch (e: Exception) {
                        failures += EpgFailure(url, guideName(url), friendlyError(e))
                    } finally {
                        done.incrementAndGet()
                        report()
                    }
                }
            }
        }.awaitAll()

        val fresh = urls.mapNotNull { fileResults[it] }
        val data = if (base == null) XmltvParser.merge(fresh) else XmltvParser.fillMissing(base, toFetch.mapNotNull { fileResults[it] })
        val failed = failures.sortedBy { urls.indexOf(it.url) }
        _epgFailures.value = failed
        _epgState.value = if (data.isEmpty() && failed.isNotEmpty()) LoadState.Failed(failed.joinToString("\n") { "${it.name}: ${it.reason}" })
        else LoadState.Done()
        if (data.isNotEmpty()) writeCache("epg.json", key, EpgCache(data, failed))
        data
    }

    /**
     * Downloads and parses one guide file. Network problems, timeouts and server errors are
     * retried (big files like "Everything" are the most likely to be interrupted); a missing
     * file (404) isn't.
     */
    private suspend fun fetchGuideWithRetry(url: String, parser: XmltvParser, bytes: AtomicLong): XmltvParser.FileResult {
        var attempt = 0
        while (true) {
            var counted = 0L
            try {
                return get(url).use { raw ->
                    parser.parse(maybeGunzip(CountingStream(raw) { n -> counted += n; bytes.addAndGet(n) }))
                }
            } catch (e: Exception) {
                bytes.addAndGet(-counted) // the retry downloads it again
                attempt++
                if (attempt >= GUIDE_ATTEMPTS || !isRetryable(e)) throw e
                kotlinx.coroutines.delay(if (attempt == 1) 2_000 else 5_000)
            }
        }
    }

    private fun isRetryable(e: Exception): Boolean {
        val code = (e as? HttpException)?.code ?: return true // network, timeout, truncated download, bad data
        return code == 408 || code == 429 || code >= 500
    }

    private fun friendlyError(e: Exception): String = when {
        e is HttpException && e.code == 404 -> "File not found (404)"
        e is HttpException -> "Server error (HTTP ${e.code})"
        e is java.net.SocketTimeoutException -> "The server took too long to respond"
        e is java.net.UnknownHostException -> "No internet connection"
        e is java.io.EOFException || e.message?.contains("unexpected end", true) == true -> "The download was cut off"
        else -> e.message?.take(80) ?: "Download failed"
    }

    private fun guideName(url: String) = Sources.EPG_SOURCES.firstOrNull { it.url == url }?.label ?: url.substringAfterLast('/')

    class HttpException(val code: Int) : IOException("HTTP $code")

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun get(url: String): InputStream {
        val res = http.newCall(Request.Builder().url(url).build()).execute()
        if (!res.isSuccessful) {
            res.close()
            throw HttpException(res.code)
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
        private const val GUIDE_ATTEMPTS = 3
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    }
}
