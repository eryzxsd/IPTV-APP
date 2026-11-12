package app.lumentv.data

/** A titled row of channels on the Home screen. */
data class HomeRow(val id: String, val title: String, val subtitle: String?, val channels: List<Channel>)

/**
 * On-device recommendations. Nothing leaves the phone: the profile is built from
 * recently watched channels and favorites (categories and countries), with the
 * device's country as a starting point for new users.
 */
object Recommender {
    private const val ROW_SIZE = 20
    private val STARTER_GROUPS = mapOf("News" to 1.0, "Movies" to 0.8, "Entertainment" to 0.7, "Sports" to 0.7, "Music" to 0.5, "Kids" to 0.4)
    private val TRAILING_NUMBER = Regex("""\s*\d+$""")

    data class Profile(val groups: Map<String, Double>, val countries: Map<String, Double>) {
        val isEmpty get() = groups.isEmpty() && countries.isEmpty()
    }

    fun profile(recent: List<Channel>, favorites: List<Channel>, deviceCountry: String): Profile {
        val groups = HashMap<String, Double>()
        val countries = HashMap<String, Double>()
        fun add(c: Channel, w: Double) {
            c.groups.filter { it != "Other" }.forEach { groups.merge(it, w, Double::plus) }
            if (c.country.isNotEmpty()) countries.merge(c.country, w, Double::plus)
        }
        recent.forEachIndexed { i, c -> add(c, 1.0 / (1 + i * 0.35)) } // recent first counts most
        favorites.forEach { add(it, 1.5) }
        if (groups.isEmpty()) groups.putAll(STARTER_GROUPS)
        if (countries.isEmpty() && deviceCountry.isNotEmpty()) countries[deviceCountry] = 1.0
        return Profile(groups, countries)
    }

    /** Collapses numbered feeds of the same channel ("ABC News Live 3" -> "abc news live"). */
    fun family(c: Channel) = c.name.lowercase().replace(TRAILING_NUMBER, "").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Regional or numbered versions of one channel ("cnn" / "cnn international latin america"). */
    fun sameFamily(a: String, b: String): Boolean {
        if (a == b) return true
        val wa = a.split(' ')
        val wb = b.split(' ')
        val common = wa.zip(wb).takeWhile { (x, y) -> x == y }.size
        val shorter = minOf(wa.size, wb.size)
        // "cnn" / "cnn international"; or regional siblings like "cnn international latin america" /
        // "cnn international north america" (2+ shared leading words covering half the shorter name).
        return (common == shorter && a.length >= 2) || (common >= 2 && common * 2 >= shorter)
    }

    fun score(c: Channel, p: Profile, hasGuideNow: Boolean, playedOk: Boolean): Double {
        val maxG = p.groups.values.maxOrNull() ?: 1.0
        val maxC = p.countries.values.maxOrNull() ?: 1.0
        var s = c.groups.sumOf { (p.groups[it] ?: 0.0) / maxG }
        s += 0.9 * ((p.countries[c.country] ?: 0.0) / maxC)
        if (hasGuideNow) s += 0.45
        if (c.logo.isNotBlank()) s += 0.2
        if (playedOk) s += 0.35
        if (c.geoBlocked) s -= 0.4
        if (c.notAlways) s -= 0.25
        return s
    }

    /**
     * Builds the Home rows. [hasGuideNow] says whether a channel has a show airing now;
     * [isOffline]/[playedOk] come from stream health.
     */
    fun homeRows(
        all: List<Channel>,
        recent: List<Channel>,
        favorites: List<Channel>,
        deviceCountry: String,
        hasGuideNow: (Channel) -> Boolean,
        isOffline: (Channel) -> Boolean,
        playedOk: (Channel) -> Boolean,
        exclude: String? = null,
    ): List<HomeRow> {
        if (all.isEmpty()) return emptyList()
        val p = profile(recent, favorites, deviceCountry)
        val seen = (recent + favorites).map { it.id }.toHashSet()
        val seenFamilies = (recent + favorites).map(::family)
        val pool = all.filter { !isOffline(it) && it.id != exclude }
        val scored = pool.associateWith { score(it, p, hasGuideNow(it), playedOk(it)) }

        // Channels (and channel families) already used by rows above, so each row brings something new.
        val shown = HashSet<String>()
        val shownFamilies = ArrayList<String>()

        fun pick(candidates: Sequence<Channel>, limit: Int = ROW_SIZE, perGroup: Int = Int.MAX_VALUE): List<Channel> {
            val list = candidates.toList()
            val families = ArrayList<String>()
            val groupCount = HashMap<String, Int>()
            val out = ArrayList<Channel>()
            fun take(allowShown: Boolean) {
                for (c in list) {
                    if (out.size >= limit) return
                    if (c in out || (!allowShown && c.id in shown)) continue
                    val f = family(c)
                    if (families.any { sameFamily(it, f) }) continue
                    if (!allowShown && shownFamilies.any { sameFamily(it, f) }) continue
                    val g = c.groups.firstOrNull() ?: "Other"
                    if ((groupCount[g] ?: 0) >= perGroup) continue
                    families += f
                    groupCount.merge(g, 1, Int::plus)
                    out += c
                }
            }
            take(allowShown = false)
            if (out.size < 8) take(allowShown = true) // too few new ones: allow repeats
            shown += out.map { it.id }
            shownFamilies += families
            return out
        }

        val ranked = pool.sortedByDescending { scored[it] ?: 0.0 }.asSequence() // sorted once, reused by every row
        val rows = ArrayList<HomeRow>()

        val visibleRecent = recent.filter { !isOffline(it) && it.id != exclude }
        if (visibleRecent.isNotEmpty()) {
            rows += HomeRow("continue", "Continue watching", null, visibleRecent.take(ROW_SIZE))
            shown += visibleRecent.map { it.id }
        }

        // Personal picks: new channels and families only, and a mix of categories.
        val forYou = pick(ranked.filter { c -> c.id !in seen && seenFamilies.none { sameFamily(it, family(c)) } }, perGroup = 6)
        if (forYou.isNotEmpty()) {
            val why = if (recent.isEmpty() && favorites.isEmpty()) "Popular picks to get you started" else "Based on what you watch"
            rows += HomeRow("for-you", "Recommended for you", why, forYou)
        }

        recent.firstOrNull()?.let { last ->
            val similar = pick(
                ranked.filter { c -> c.id !in seen && c.groups.any { it in last.groups && it != "Other" } && (last.country.isEmpty() || c.country == last.country || c.country.isEmpty()) },
            )
            if (similar.size >= 3) rows += HomeRow("because", "Because you watched ${last.name}", null, similar)
        }

        val onNow = pick(ranked.filter { hasGuideNow(it) })
        if (onNow.isNotEmpty()) rows += HomeRow("on-now", "On now", "Shows airing right now", onNow)

        val favs = favorites.filter { it.id != exclude }
        if (favs.isNotEmpty()) rows += HomeRow("favorites", "Your favorites", null, favs.take(ROW_SIZE))

        // Category rows for the viewer's top categories.
        p.groups.entries.sortedByDescending { it.value }.map { it.key }.take(4).forEach { g ->
            val row = pick(ranked.filter { g in it.groups })
            if (row.size >= 4) rows += HomeRow("cat:$g", g, null, row)
        }
        return rows
    }

    /** Best single pick to feature when nothing is playing. */
    fun featured(rows: List<HomeRow>): Channel? =
        rows.firstOrNull { it.id == "for-you" }?.channels?.firstOrNull() ?: rows.firstOrNull()?.channels?.firstOrNull()
}
