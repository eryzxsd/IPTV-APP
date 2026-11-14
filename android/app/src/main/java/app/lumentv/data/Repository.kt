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
    /**
     * App storage, not the system cache: Android clears the cache when space runs low (often on TVs),
     * which made every start download the whole playlist again.
     */
    private val cacheDir = File(context.filesDir, "lumen").apply { mkdirs() }

    init {
        // The old JSON cache files (before 1.9.3) are no longer used.
        File(context.cacheDir, "lumen").listFiles()?.filter { it.name.endsWith(".json") }?.forEach { it.delete() }
    }

    private val _epgState = MutableStateFlow<LoadState>(LoadState.Idle)
    val epgState: StateFlow<LoadState> = _epgState
    private val _epgFailures = MutableStateFlow<List<EpgFailure>>(emptyList())
    /** Guide files that couldn't be downloaded (shown with a Retry button). */
    val epgFailures: StateFlow<List<EpgFailure>> = _epgFailures


    private fun playlistKey(urls: List<String>) = "v1|" + urls.joinToString("|")
    private val playlistFile get() = File(cacheDir, "playlist.bin")
    private val guideFile get() = File(cacheDir, "guide.bin")

    /**
     * The saved channel list, whatever its age, so the app can show it straight away.
     * `stale` says it's due a refresh (do that in the background).
     */
    suspend fun cachedChannels(urls: List<String>): Pair<List<Channel>, Boolean>? = withContext(Dispatchers.IO) {
        val (header, list) = CacheCodec.readChannels(playlistFile, playlistKey(urls)) ?: return@withContext null
        if (list.isEmpty()) return@withContext null
        list to (System.currentTimeMillis() - header.time > PLAYLIST_TTL)
    }

    suspend fun loadChannels(urls: List<String>, force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        val key = playlistKey(urls)
        if (!force) cachedChannels(urls)?.let { (list, stale) -> if (!stale) return@withContext list }
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
        CacheCodec.writeChannels(playlistFile, key, all)
        all
    }

    /** Last parsed result per guide file (this session), so a retry only re-downloads failed files. */
    private val fileResults = java.util.concurrent.ConcurrentHashMap<String, XmltvParser.FileResult>()
    private var resultsKey: String? = null

    /** Depends only on the guide files chosen, so a refreshed playlist doesn't throw the guide away. */
    private fun epgKey(urls: List<String>) = "v3|" + urls.joinToString("|")

    class CachedGuide(val data: EpgData, val failures: List<EpgFailure>, val stale: Boolean)

    /** The saved guide (up to a day and a half old, while it still has today's shows), to show straight away. */
    suspend fun cachedGuide(urls: List<String>, ttlHours: Int): CachedGuide? = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext null
        val g = CacheCodec.readGuide(guideFile, epgKey(urls)) ?: return@withContext null
        val age = System.currentTimeMillis() - g.header.time
        if (age > 36 * HOUR || g.data.isEmpty()) return@withContext null
        _epgFailures.value = g.failures
        _epgState.value = LoadState.Done()
        CachedGuide(g.data, g.failures, stale = age > ttlHours * HOUR)
    }

    suspend fun loadEpg(channels: List<Channel>, urls: List<String>, force: Boolean, ttlHours: Int = 4): EpgData = withContext(Dispatchers.IO) {
        if (urls.isEmpty() || channels.isEmpty()) {
            _epgState.value = LoadState.Idle
            _epgFailures.value = emptyList()
            return@withContext emptyMap()
        }
        val key = epgKey(urls)
        if (!force) cachedGuide(urls, ttlHours)?.let { if (!it.stale) return@withContext it.data }
        fileResults.clear()
        resultsKey = key
        download(channels, urls, urls, base = null, key = key, label = "Guide")
    }

    /** Downloads only the guide files that failed last time and merges them in. */
    suspend fun retryFailedEpg(channels: List<Channel>, urls: List<String>, current: EpgData): EpgData = withContext(Dispatchers.IO) {
        val failed = _epgFailures.value.map { it.url }.filter { it in urls }
        if (failed.isEmpty() || channels.isEmpty()) return@withContext current
        val key = epgKey(urls)
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
        if (data.isNotEmpty()) CacheCodec.writeGuide(guideFile, key, data, failed)
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
