package app.lumentv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Compact binary cache files for the channel list and the TV guide.
 * Reading these is many times faster than JSON, which matters on low-end (32-bit) TVs
 * where decoding ~11,000 channels from JSON took seconds at every start.
 */
object CacheCodec {
    private const val CHANNELS_MAGIC = 0x4C54_5643 // "LTVC"
    private const val GUIDE_MAGIC = 0x4C54_5645 // "LTVE"
    private const val VERSION = 1

    class Header(val key: String, val time: Long)

    // ------------------------------------------------------------ channels

    fun writeChannels(file: File, key: String, channels: List<Channel>) = write(file) { out ->
        out.writeInt(CHANNELS_MAGIC)
        out.writeInt(VERSION)
        out.str(key)
        out.writeLong(System.currentTimeMillis())
        out.writeInt(channels.size)
        val strings = StringTable()
        for (c in channels) {
            out.str(c.id)
            out.str(c.name)
            strings.write(out, c.logo)
            out.writeInt(c.groups.size)
            c.groups.forEach { strings.write(out, it) }
            out.str(c.tvgId)
            out.str(c.epgKey)
            strings.write(out, c.country)
            out.str(c.url)
            strings.write(out, c.quality)
            out.writeByte((if (c.geoBlocked) 1 else 0) or (if (c.notAlways) 2 else 0))
            strings.write(out, c.userAgent)
            strings.write(out, c.referrer)
        }
    }

    /** Returns null when the file is missing, unreadable or for a different [key]. */
    fun readChannels(file: File, key: String): Pair<Header, List<Channel>>? = read(file) { inp ->
        if (inp.readInt() != CHANNELS_MAGIC || inp.readInt() != VERSION) return@read null
        val header = Header(inp.str()!!, inp.readLong())
        if (header.key != key) return@read null
        val n = inp.readInt()
        val strings = StringTable()
        val list = ArrayList<Channel>(n)
        repeat(n) {
            val id = inp.str()!!
            val name = inp.str()!!
            val logo = strings.read(inp)!!
            val groups = List(inp.readInt()) { strings.read(inp)!! }
            val tvgId = inp.str()!!
            val epgKey = inp.str()!!
            val country = strings.read(inp)!!
            val url = inp.str()!!
            val quality = strings.read(inp)
            val flags = inp.readByte().toInt()
            val ua = strings.read(inp)
            val ref = strings.read(inp)
            list += Channel(id, name, logo, groups, tvgId, epgKey, country, url, quality, flags and 1 != 0, flags and 2 != 0, ua, ref)
        }
        header to list
    }

    // --------------------------------------------------------------- guide

    fun writeGuide(file: File, key: String, data: EpgData, failures: List<EpgFailure>) = write(file) { out ->
        out.writeInt(GUIDE_MAGIC)
        out.writeInt(VERSION)
        out.str(key)
        out.writeLong(System.currentTimeMillis())
        out.writeInt(failures.size)
        failures.forEach { out.str(it.url); out.str(it.name); out.str(it.reason) }
        out.writeInt(data.size)
        val strings = StringTable()
        for ((k, list) in data) {
            out.str(k)
            out.writeInt(list.size)
            for (p in list) {
                out.writeLong(p.start)
                out.writeLong(p.stop)
                strings.write(out, p.title)
                out.str(p.desc)
                strings.write(out, p.category)
                strings.write(out, p.icon)
                out.str(p.episode)
            }
        }
    }

    class Guide(val header: Header, val data: EpgData, val failures: List<EpgFailure>)

    fun readGuide(file: File, key: String): Guide? = read(file) { inp ->
        if (inp.readInt() != GUIDE_MAGIC || inp.readInt() != VERSION) return@read null
        val header = Header(inp.str()!!, inp.readLong())
        if (header.key != key) return@read null
        val failures = List(inp.readInt()) { EpgFailure(inp.str()!!, inp.str()!!, inp.str()!!) }
        val n = inp.readInt()
        val strings = StringTable()
        val map = HashMap<String, List<Programme>>(n * 2)
        repeat(n) {
            val k = inp.str()!!
            val count = inp.readInt()
            map[k] = List(count) {
                Programme(
                    start = inp.readLong(),
                    stop = inp.readLong(),
                    title = strings.read(inp)!!,
                    desc = inp.str(),
                    category = strings.read(inp),
                    icon = strings.read(inp),
                    episode = inp.str(),
                )
            }
        }
        Guide(header, map, failures)
    }

    // ------------------------------------------------------------- helpers

    /** Repeated values (categories, countries, show titles) are written once and then referenced by number. */
    private class StringTable {
        private val ids = HashMap<String, Int>()
        private val values = ArrayList<String>()

        fun write(out: DataOutputStream, s: String?) {
            if (s == null) return out.writeInt(-1)
            val id = ids[s]
            if (id != null) {
                out.writeInt(id)
            } else {
                ids[s] = values.size
                values += s
                out.writeInt(-2)
                out.str(s)
            }
        }

        fun read(inp: DataInputStream): String? = when (val id = inp.readInt()) {
            -1 -> null
            -2 -> inp.str()!!.also { values += it }
            else -> values[id]
        }
    }

    private fun DataOutputStream.str(s: String?) {
        if (s == null) return writeInt(-1)
        val b = s.encodeToByteArray()
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.str(): String? {
        val n = readInt()
        if (n < 0) return null
        if (n > 50_000_000) throw IOException("corrupt cache")
        val b = ByteArray(n)
        readFully(b)
        return b.decodeToString()
    }

    private inline fun write(file: File, block: (DataOutputStream) -> Unit) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use(block)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private inline fun <T> read(file: File, block: (DataInputStream) -> T?): T? {
        if (!file.exists()) return null
        return runCatching { DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use(block) }.getOrNull()
    }
}
