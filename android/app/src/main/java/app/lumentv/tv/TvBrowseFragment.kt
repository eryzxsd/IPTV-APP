package app.lumentv.tv

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lumentv.R
import app.lumentv.data.LoadState
import app.lumentv.data.nowAndNext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Home: the playing channel behind rows of recommendations, plus Guide, Search and Settings. */
class TvBrowseFragment : BrowseSupportFragment() {
    private val activity get() = requireActivity() as TvActivity
    private val vm get() = activity.vm
    private val cards = ChannelCardPresenter()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply { shadowEnabled = false })
    private val rowsById = HashMap<Long, ListRow>()
    private var startedOnContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Lumen TV"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = ContextCompat.getColor(requireContext(), R.color.tv_bg)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.tv_accent)
        adapter = rowsAdapter
        setOnSearchClickedListener { activity.open(TvSearchFragment(), "search") }
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is ChannelItem -> activity.play(item.channel)
                is ActionItem -> when (item.id) {
                    "guide" -> activity.open(TvGuideFragment(), "guide")
                    "search" -> activity.open(TvSearchFragment(), "search")
                    "settings" -> activity.open(TvSettingsFragment(), "settings")
                    "reload" -> vm.reloadChannels()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.homeRows, vm.player.state, vm.epg, vm.clock, vm.channelState) { rows, ps, epg, clock, load ->
                    val out = ArrayList<Pair<Row, List<Any>>>()
                    ps.channel?.let { c ->
                        out += Row(0, "Now playing", null) to listOf(ChannelItem(c, epg[c.epgKey].nowAndNext(clock).first, clock, playing = true))
                    }
                    rows.forEach { r ->
                        out += Row(r.id.hashCode().toLong() or 1L.shl(40), r.title, r.subtitle) to
                            r.channels.map { ChannelItem(it, epg[it.epgKey].nowAndNext(clock).first, clock, it.id == ps.channel?.id) }
                    }
                    val more = mutableListOf<Any>(
                        ActionItem("guide", "TV guide", "What's on now and next", R.drawable.tv_ic_guide),
                        ActionItem("search", "Search", "Channels and programmes", R.drawable.tv_ic_search),
                        ActionItem("settings", "Settings", "Playback, guide, playlists", R.drawable.tv_ic_settings),
                    )
                    if (load is LoadState.Failed) more.add(0, ActionItem("reload", "Couldn't load channels", "Select to try again", R.drawable.tv_ic_search))
                    out += Row(1, "More", null) to more
                    Triple(out, load, rows.isEmpty())
                }.collect { (rows, load, empty) ->
                    render(rows)
                    // Start on the first row once recommendations arrive (not on "More").
                    if (!empty && !startedOnContent) {
                        startedOnContent = true
                        setSelectedPosition(0, false)
                    }
                    if (load is LoadState.Loading && empty) progressBarManager.show() else progressBarManager.hide()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity.setVideoVisibility(0.22f)
    }

    private data class Row(val id: Long, val title: String, val subtitle: String?)

    private fun render(rows: List<Pair<Row, List<Any>>>) {
        val list = rows.map { (row, items) ->
            val existing = rowsById[row.id]
            val listRow = if (existing != null && existing.headerItem.name == row.title) existing
            else ListRow(HeaderItem(row.id, row.title).apply { description = row.subtitle }, ArrayObjectAdapter(cards)).also { rowsById[row.id] = it }
            (listRow.adapter as ArrayObjectAdapter).setItems(items, itemDiff)
            listRow
        }
        rowsAdapter.setItems(list, rowDiff)
    }

    companion object {
        private val rowDiff = object : DiffCallback<ListRow>() {
            override fun areItemsTheSame(a: ListRow, b: ListRow) = a.headerItem.id == b.headerItem.id
            override fun areContentsTheSame(a: ListRow, b: ListRow) = a === b
        }
        /** Same channel, same programme: keep the card as is (no logo reload every clock tick). */
        val itemDiff = object : DiffCallback<Any>() {
            override fun areItemsTheSame(a: Any, b: Any) = when {
                a is ChannelItem && b is ChannelItem -> a.channel.id == b.channel.id
                a is ActionItem && b is ActionItem -> a.id == b.id
                else -> false
            }
            override fun areContentsTheSame(a: Any, b: Any) = when {
                a is ChannelItem && b is ChannelItem -> a.channel == b.channel && a.now == b.now && a.playing == b.playing
                else -> a == b
            }
        }
    }
}
