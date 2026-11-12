package app.lumentv.data

object M3uParser {
    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val QUALITY = Regex("""\((\d{3,4}[pi])\)""", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("""\(\d{3,4}[pi]\)|\[(geo-?blocked|not 24/7)]""", RegexOption.IGNORE_CASE)

    fun normId(id: String) = id.trim().lowercase().substringBefore('@')

    private val NAME_NOISE = Regex("""\b(hd|fhd|uhd|sd|4k|hevc|h265|backup|geo-?blocked|not 24/7)\b""")
    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Loose channel-name key: "BBC One HD (UK)" -> "bbcone". */
    fun normName(name: String): String =
        name.lowercase().replace(BRACKETS, " ").replace(NAME_NOISE, " ").replace(NON_ALNUM, "")

    /** iptv-org tvg-ids look like `CNN.us@SD` — the suffix is the country. */
    fun countryFromTvgId(tvgId: String): String {
        val m = Regex("""\.([a-z]{2})$""", RegexOption.IGNORE_CASE).find(tvgId.substringBefore('@')) ?: return ""
        val c = m.groupValues[1].uppercase()
        return if (c == "UK") "GB" else c
    }

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>(12000)
        var attrs: Map<String, String>? = null
        var title = ""
        var ua: String? = null
        var ref: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                val comma = titleComma(line)
                attrs = ATTR.findAll(line.substring(0, comma)).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                title = line.substring((comma + 1).coerceAtMost(line.length)).trim()
                ua = attrs["http-user-agent"]
                ref = attrs["http-referrer"]
                continue
            }
            if (line.startsWith("#EXTVLCOPT:")) {
                val opt = line.removePrefix("#EXTVLCOPT:")
                when {
                    opt.startsWith("http-user-agent=") -> ua = opt.substringAfter('=')
                    opt.startsWith("http-referrer=") -> ref = opt.substringAfter('=')
                }
                continue
            }
            if (line.startsWith("#")) continue
            val a = attrs ?: continue

            val rawName = title.ifEmpty { a["tvg-name"] ?: "Unknown" }
            val name = rawName.replace(TAGS, "").replace(Regex("\\s+"), " ").trim().ifEmpty { rawName }
            val tvgId = a["tvg-id"].orEmpty().trim()
            val groups = a["group-title"].orEmpty().split(';').map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("undefined", true) }
            out += Channel(
                id = hash("${tvgId.ifEmpty { rawName }}|$line"),
                name = name,
                logo = a["tvg-logo"].orEmpty(),
                groups = groups.ifEmpty { listOf("Other") },
                tvgId = tvgId,
                epgKey = if (tvgId.isNotEmpty()) normId(tvgId) else "n:${normName(name)}",
                country = countryFromTvgId(tvgId),
                url = line,
                quality = QUALITY.find(rawName)?.groupValues?.get(1),
                geoBlocked = rawName.contains(Regex("""\[geo-?blocked]""", RegexOption.IGNORE_CASE)),
                notAlways = rawName.contains("[Not 24/7]", ignoreCase = true),
                userAgent = ua?.takeIf { it.isNotBlank() },
                referrer = ref?.takeIf { it.isNotBlank() },
            )
            attrs = null
            ua = null
            ref = null
        }
        // Drop exact duplicates (same name and stream).
        val seen = HashSet<String>()
        return out.filter { seen.add("${it.name}|${it.url}") }
    }

    /** The title follows the first comma that is not inside a quoted attribute. */
    private fun titleComma(line: String): Int {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return line.length
    }

    private fun hash(s: String): String {
        var h = 5381
        for (c in s) h = (h shl 5) + h + c.code
        return Integer.toUnsignedString(h, 36)
    }
}
