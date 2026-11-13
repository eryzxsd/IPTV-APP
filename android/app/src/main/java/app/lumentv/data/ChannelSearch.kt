package app.lumentv.data

/** Search used by the phone and TV search screens: name matches first, then what's on now, then categories. */
object ChannelSearch {
    data class Section(val title: String, val channels: List<Channel>)

    fun search(all: List<Channel>, epg: EpgData, query: String, now: Long = System.currentTimeMillis()): List<Section> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val byName = all.filter { it.name.lowercase().contains(needle) }
            .sortedWith(compareBy<Channel>({ !it.name.lowercase().startsWith(needle) }, { it.name.length }))
        val names = byName.map { it.id }.toHashSet()
        val onNow = all.filter { it.id !in names && epg[it.epgKey].nowAndNext(now).first?.title?.lowercase()?.contains(needle) == true }
        val inGroup = all.filter { it.id !in names && it.groups.any { g -> g.lowercase().contains(needle) } }
        return listOf(Section("Channels", byName), Section("On now", onNow), Section("Categories", inGroup)).filter { it.channels.isNotEmpty() }
    }
}
