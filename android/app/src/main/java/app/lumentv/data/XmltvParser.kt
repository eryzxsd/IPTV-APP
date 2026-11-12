package app.lumentv.data

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Streams an XMLTV file and keeps only the programmes for channels in the
 * playlist. epgshare01 uses its own ids (`CNBC.HD.us2`), so channels are
 * matched by name and scored; each playlist channel keeps its best match.
 */
class XmltvParser(
    channels: Collection<Channel>,
    private val newParser: () -> XmlPullParser,
    private val from: Long,
    private val to: Long,
) {
    private class Wanted(val name: String, val loose: String, val country: String)

    data class Match(val score: Int, val epgId: String)

    /** Result for one file: the best match per playlist key and the programmes of those matches. */
    class FileResult(val best: Map<String, Match>, val programmes: Map<String, List<Programme>>)

    private val wanted = HashMap<String, Wanted>()
    private val byName = HashMap<String, MutableList<String>>()

    init {
        for (c in channels) {
            if (wanted.containsKey(c.epgKey)) continue
            wanted[c.epgKey] = Wanted(c.name, loose(c.name.replace(Regex("""\(\d+p\)|\[[^\]]*]"""), "")), c.country)
            val n = if (c.epgKey.startsWith("n:")) c.epgKey.drop(2) else M3uParser.normName(c.name)
            if (n.isNotEmpty()) byName.getOrPut(n) { ArrayList(1) }.let { if (c.epgKey !in it) it += c.epgKey }
        }
    }

    fun parse(input: InputStream): FileResult {
        val p = newParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        val best = HashMap<String, Match>()
        var winners: Map<String, List<String>>? = null // epgId -> keys
        val programmes = HashMap<String, MutableList<Programme>>()

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> readChannel(p, best)
                    "programme" -> {
                        val w = winners ?: best.entries.groupBy({ it.value.epgId }, { it.key }).also { winners = it }
                        val id = p.getAttributeValue(null, "channel").orEmpty()
                        if (id in w) {
                            val start = parseTime(p.getAttributeValue(null, "start"))
                            val stop = parseTime(p.getAttributeValue(null, "stop"))
                            val prog = readProgramme(p, start, stop)
                            if (start > 0 && stop > 0 && stop >= from && start <= to) {
                                programmes.getOrPut(id) { ArrayList() } += prog
                            }
                        } else {
                            skip(p)
                        }
                    }
                }
            }
            event = p.next()
        }
        return FileResult(best, programmes)
    }

    private fun readChannel(p: XmlPullParser, best: HashMap<String, Match>) {
        val id = p.getAttributeValue(null, "id").orEmpty()
        val names = ArrayList<String>(2)
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.depth == depth)) break
            if (e == XmlPullParser.START_TAG && p.name == "display-name") names += p.nextText()
        }
        if (id.isEmpty()) return

        fun consider(key: String, score: Int) {
            if (score < MIN_SCORE) return
            val cur = best[key]
            if (cur == null || score > cur.score) best[key] = Match(score, id)
        }

        val k = M3uParser.normId(id)
        if (wanted.containsKey(k)) consider(k, 100)
        val (idName, epgCountry) = splitEpgId(id)
        names += idName
        for (dn in names) {
            val n = M3uParser.normName(dn)
            val keys = ArrayList(byName[n].orEmpty())
            if (wanted.containsKey("n:$n")) keys += "n:$n"
            for (key in keys) {
                val ch = wanted[key] ?: continue
                var score = 50
                if (loose(dn) == ch.loose) score += 10
                if (epgCountry.isNotEmpty() && ch.country.isNotEmpty()) score += if (epgCountry == ch.country) 15 else -20
                if (ALT_FEED.containsMatchIn(dn) && !ALT_FEED.containsMatchIn(ch.name)) score -= 15
                consider(key, score)
            }
        }
    }

    private fun readProgramme(p: XmlPullParser, start: Long, stop: Long): Programme {
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        var icon: String? = null
        var episode: String? = null
        var subTitle: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.depth == depth)) break
            if (e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> if (title == null) title = p.nextText().trim()
                "desc" -> if (desc == null) desc = p.nextText().trim().let { if (it.length > 600) it.take(600) + "…" else it }
                "category" -> if (category == null) category = p.nextText().trim()
                "sub-title" -> if (subTitle == null) subTitle = p.nextText().trim()
                "icon" -> if (icon == null) icon = p.getAttributeValue(null, "src")
                "episode-num" -> if (p.getAttributeValue(null, "system") == "onscreen") episode = p.nextText().trim()
            }
        }
        return Programme(start, stop, title?.ifEmpty { null } ?: "Untitled", desc?.ifEmpty { null }, category?.ifEmpty { null }, icon, episode ?: subTitle?.ifEmpty { null })
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        const val MIN_SCORE = 40
        private val ALT_FEED = Regex("""\b(pacific|west|mountain|central|east\s*\+|\+\s*1|plus\s*1|timeshift)\b|\+1""", RegexOption.IGNORE_CASE)
        private val TIME = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-])?(\d{2})?(\d{2})?""")
        private val NON_ALNUM = Regex("[^a-z0-9]+")

        fun loose(s: String) = s.lowercase().replace(NON_ALNUM, "")

        /** `CNBC.HD.us2` -> ("CNBC HD", "US"), `TLC.HD.uk` -> ("TLC HD", "GB"). */
        fun splitEpgId(id: String): Pair<String, String> {
            val m = Regex("""^(.*)\.([a-z]{2})\d*$""", RegexOption.IGNORE_CASE).find(id) ?: return id to ""
            val c = m.groupValues[2].uppercase()
            return m.groupValues[1].replace('.', ' ') to if (c == "UK") "GB" else c
        }

        /** XMLTV time: `YYYYMMDDhhmmss +hhmm` -> epoch millis (0 if invalid). */
        fun parseTime(s: String?): Long {
            val m = TIME.find(s ?: return 0) ?: return 0
            val g = m.groupValues
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            }
            val offset = if (g[7].isEmpty()) 0L else (if (g[7] == "-") -1 else 1) *
                ((g[8].ifEmpty { "0" }.toLong() * 60 + g[9].ifEmpty { "0" }.toLong()) * 60_000)
            return cal.timeInMillis - offset
        }

        /** Combines per-file results: for every key, the highest score wins (earlier files win ties). */
        fun merge(results: List<FileResult>): EpgData {
            val chosen = HashMap<String, Pair<Int, Int>>() // key -> (score, file index)
            results.forEachIndexed { i, r ->
                for ((key, m) in r.best) {
                    val cur = chosen[key]
                    if (cur == null || m.score > cur.first) chosen[key] = m.score to i
                }
            }
            val out = HashMap<String, List<Programme>>(chosen.size)
            for ((key, pair) in chosen) {
                val r = results[pair.second]
                val list = r.programmes[r.best.getValue(key).epgId] ?: continue
                val sorted = list.sortedBy { it.start }
                out[key] = sorted.filterIndexed { i, p -> i == 0 || p.start != sorted[i - 1].start }
            }
            return out
        }
    }
}
