package app.lumentv.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * Reads the video size of a stream segment without decoding it, so quality
 * variants whose playlist entry has no RESOLUTION can still be labelled.
 */
@OptIn(UnstableApi::class)
object SegmentProbe {
    private const val TS_PACKET = 188
    private const val STREAM_AVC = 0x1B
    private const val STREAM_HEVC = 0x24

    /** Video size from the start of an MPEG-TS segment (via PAT → PMT → video PES → SPS). */
    fun sizeFromTs(b: ByteArray, n: Int = b.size): Pair<Int, Int>? {
        val first = (0 until minOf(TS_PACKET, n)).firstOrNull { b[it] == SYNC && it + TS_PACKET < n && b[it + TS_PACKET] == SYNC } ?: return null
        var pmtPid = -1
        var videoPid = -1
        var streamType = 0
        val es = ByteArrayOutputStream()
        var checkedAt = 0
        var i = first
        while (i + TS_PACKET <= n) {
            if (b[i] != SYNC) { i++; continue }
            val pusi = b[i + 1].toInt() and 0x40 != 0
            val pid = ((b[i + 1].toInt() and 0x1F) shl 8) or (b[i + 2].toInt() and 0xFF)
            val afc = (b[i + 3].toInt() shr 4) and 3
            val end = i + TS_PACKET
            var p = i + 4
            if (afc == 3) p += 1 + (b[p].toInt() and 0xFF)
            if ((afc == 1 || afc == 3) && p < end) {
                when {
                    pid == 0 && pusi -> pmtPid = patProgramMapPid(b, p, end)
                    pid == pmtPid && pusi && videoPid < 0 -> pmtVideo(b, p, end)?.let { (type, vpid) -> streamType = type; videoPid = vpid }
                    pid == videoPid && videoPid >= 0 -> {
                        var q = p
                        if (pusi && q + 9 <= end && b[q].toInt() == 0 && b[q + 1].toInt() == 0 && b[q + 2].toInt() == 1) {
                            q += 9 + (b[q + 8].toInt() and 0xFF) // skip PES header
                        }
                        if (q < end) es.write(b, q, end - q)
                        if (es.size() - checkedAt >= 8192) {
                            checkedAt = es.size()
                            spsSize(es.toByteArray(), streamType)?.let { return it }
                        }
                    }
                }
            }
            i = end
        }
        return if (videoPid >= 0) spsSize(es.toByteArray(), streamType) else null
    }

    private fun patProgramMapPid(b: ByteArray, start: Int, end: Int): Int {
        var p = start + 1 + (b[start].toInt() and 0xFF) // pointer field
        if (p + 8 > end) return -1
        val sectionEnd = minOf(end, p + 3 + (((b[p + 1].toInt() and 0x0F) shl 8) or (b[p + 2].toInt() and 0xFF)) - 4)
        p += 8
        while (p + 4 <= sectionEnd) {
            val program = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
            val pid = ((b[p + 2].toInt() and 0x1F) shl 8) or (b[p + 3].toInt() and 0xFF)
            if (program != 0) return pid
            p += 4
        }
        return -1
    }

    /** First H.264/H.265 stream in the PMT: (stream type, PID). */
    private fun pmtVideo(b: ByteArray, start: Int, end: Int): Pair<Int, Int>? {
        val p = start + 1 + (b[start].toInt() and 0xFF)
        if (p + 12 > end) return null
        val sectionEnd = minOf(end, p + 3 + (((b[p + 1].toInt() and 0x0F) shl 8) or (b[p + 2].toInt() and 0xFF)) - 4)
        var q = p + 12 + (((b[p + 10].toInt() and 0x0F) shl 8) or (b[p + 11].toInt() and 0xFF))
        while (q + 5 <= sectionEnd) {
            val type = b[q].toInt() and 0xFF
            val pid = ((b[q + 1].toInt() and 0x1F) shl 8) or (b[q + 2].toInt() and 0xFF)
            if (type == STREAM_AVC || type == STREAM_HEVC) return type to pid
            q += 5 + (((b[q + 3].toInt() and 0x0F) shl 8) or (b[q + 4].toInt() and 0xFF))
        }
        return null
    }

    /** Finds the SPS NAL unit in an H.264/H.265 elementary stream and reads the picture size. */
    fun spsSize(es: ByteArray, streamType: Int): Pair<Int, Int>? {
        val hevc = streamType == STREAM_HEVC
        var i = 0
        while (i + 3 < es.size) {
            if (es[i].toInt() == 0 && es[i + 1].toInt() == 0 && es[i + 2].toInt() == 1) {
                val nal = i + 3
                val header = es[nal].toInt() and 0xFF
                val isSps = if (hevc) ((header shr 1) and 0x3F) == 33 else (header and 0x1F) == 7
                if (isSps) {
                    var endIdx = es.size
                    var j = nal + 1
                    while (j + 2 < es.size) {
                        if (es[j].toInt() == 0 && es[j + 1].toInt() == 0 && (es[j + 2].toInt() == 1 || es[j + 2].toInt() == 0)) { endIdx = j; break }
                        j++
                    }
                    if (endIdx == es.size && es.size - nal < 8) return null // SPS cut off; need more data
                    return runCatching {
                        if (hevc) NalUnitUtil.parseH265SpsNalUnit(es, nal, endIdx, null).let { it.width to it.height }
                        else NalUnitUtil.parseSpsNalUnit(es, nal, endIdx).let { it.width to it.height }
                    }.getOrNull()?.takeIf { it.first > 0 && it.second > 0 }
                }
                i = nal
            } else {
                i++
            }
        }
        return null
    }

    /** Video size from an fMP4 init segment (first `tkhd` box with a non-zero size). */
    fun sizeFromMp4(b: ByteArray, n: Int = b.size): Pair<Int, Int>? {
        var i = 4
        while (i + 4 <= n) {
            if (b[i] == 't'.code.toByte() && b[i + 1] == 'k'.code.toByte() && b[i + 2] == 'h'.code.toByte() && b[i + 3] == 'd'.code.toByte()) {
                val box = i - 4
                val version = b[i + 4].toInt()
                val off = if (version == 1) 96 else 84
                if (box + off + 8 <= n) {
                    val w = int32(b, box + off) ushr 16
                    val h = int32(b, box + off + 4) ushr 16
                    if (w > 0 && h > 0) return w to h
                }
            }
            i++
        }
        return null
    }

    private fun int32(b: ByteArray, p: Int) =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private const val SYNC = 0x47.toByte()
}

/** Fetches a variant's media playlist and a small part of one segment to read its resolution. */
class ResolutionProbe(private val http: OkHttpClient) {
    /** A segment and, for AES-128 (clear-key HLS, not DRM), how to decrypt it. */
    data class Segment(val uri: String, val keyUri: String?, val iv: ByteArray?)

    /** Returns (width, height), or null when it can't tell (DRM, unreachable, unknown format). */
    fun probe(variantUrl: String, headers: Map<String, String>): Pair<Int, Int>? = runCatching {
        val playlist = fetch(variantUrl, headers, 512 * 1024)?.decodeToString() ?: return null
        if (playlist.contains("#EXT-X-STREAM-INF")) return null
        val base = URI(variantUrl)
        Regex("""#EXT-X-MAP:.*URI="([^"]+)"""").find(playlist)?.let { m ->
            val init = fetch(base.resolve(m.groupValues[1]).toString(), headers, 1024 * 1024) ?: return null
            return SegmentProbe.sizeFromMp4(init)
        }
        // The newest segment is the most likely to still be on the server.
        val seg = parseSegments(playlist).lastOrNull() ?: return null
        var data = fetch(base.resolve(seg.uri).toString(), headers, 768 * 1024, range = true) ?: return null
        if (seg.keyUri != null) {
            val key = fetch(base.resolve(seg.keyUri).toString(), headers, 64) ?: return null
            if (key.size != 16) return null
            data = decryptPrefix(data, key, seg.iv ?: return null)
        }
        SegmentProbe.sizeFromTs(data) ?: SegmentProbe.sizeFromMp4(data)
    }.getOrNull()

    private fun fetch(url: String, headers: Map<String, String>, limit: Int, range: Boolean = false): ByteArray? {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (range) header("Range", "bytes=0-${limit - 1}")
        }.build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            val stream = res.body.source().inputStream()
            while (out.size() < limit) {
                val r = stream.read(buf, 0, minOf(buf.size, limit - out.size()))
                if (r < 0) break
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }

    companion object {
        /** Segments with the AES-128 key and IV that apply to each (null key = not encrypted). */
        fun parseSegments(playlist: String): List<Segment> {
            var seq = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""").find(playlist)?.groupValues?.get(1)?.toLong() ?: 0L
            var keyUri: String? = null
            var keyIv: ByteArray? = null
            var unsupportedKey = false
            val out = ArrayList<Segment>()
            for (raw in playlist.lineSequence()) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXT-X-KEY:") -> {
                        val method = Regex("""METHOD=([A-Z0-9-]+)""").find(line)?.groupValues?.get(1)
                        when (method) {
                            "NONE" -> { keyUri = null; keyIv = null; unsupportedKey = false }
                            "AES-128" -> {
                                keyUri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
                                keyIv = Regex("""IV=0[xX]([0-9a-fA-F]+)""").find(line)?.groupValues?.get(1)?.let(::hexIv)
                                unsupportedKey = keyUri == null
                            }
                            else -> unsupportedKey = true // SAMPLE-AES etc. is real DRM: can't read it
                        }
                    }
                    line.isEmpty() || line.startsWith("#") -> {}
                    else -> {
                        if (!unsupportedKey) out += Segment(line, keyUri, if (keyUri != null) keyIv ?: sequenceIv(seq) else null)
                        seq++
                    }
                }
            }
            return out
        }

        /** Without an IV attribute, HLS uses the segment's media sequence number. */
        fun sequenceIv(seq: Long) = ByteArray(16).also { for (i in 0 until 8) it[15 - i] = (seq ushr (8 * i)).toByte() }

        private fun hexIv(hex: String): ByteArray {
            val h = hex.padStart(32, '0').takeLast(32)
            return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        /** AES-128-CBC decrypt of the first bytes of a segment (whole blocks only). */
        fun decryptPrefix(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
            val n = data.size - data.size % 16
            val c = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
            return c.doFinal(data, 0, n)
        }
    }
}
