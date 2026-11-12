package app.lumentv.tv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import app.lumentv.data.Channel
import app.lumentv.data.nowAndNext

/** Search channels by name, group or what's on now. Uses the remote's voice button when available. */
class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private val activity get() = requireActivity() as TvActivity
    private val vm get() = activity.vm
    private val rows = ArrayObjectAdapter(ListRowPresenter().apply { shadowEnabled = false })
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            if (item is ChannelItem) activity.play(item.channel)
        })
        // Voice search through the system recogniser (no microphone permission needed).
        @Suppress("DEPRECATION")
        setSpeechRecognitionCallback {
            runCatching { startActivityForResult(recognizerIntent, VOICE) }
        }
    }

    override fun onResume() {
        super.onResume()
        activity.setVideoVisibility(0.08f)
    }

    override fun getResultsAdapter(): ObjectAdapter = rows

    override fun onQueryTextChange(newQuery: String): Boolean {
        pending?.let(handler::removeCallbacks)
        pending = Runnable { search(newQuery) }.also { handler.postDelayed(it, 250) }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        pending?.let(handler::removeCallbacks)
        search(query)
        return true
    }

    private fun search(q: String) {
        val needle = q.trim().lowercase()
        rows.clear()
        if (needle.isEmpty()) return
        val now = vm.clock.value
        val epg = vm.epg.value
        val all = vm.channels.value
        val byName = all.filter { it.name.lowercase().contains(needle) }
            .sortedWith(compareBy<Channel>({ !it.name.lowercase().startsWith(needle) }, { it.name.length }))
        val names = byName.map { it.id }.toHashSet()
        val onNow = all.filter { it.id !in names && epg[it.epgKey].nowAndNext(now).first?.title?.lowercase()?.contains(needle) == true }
        val inGroup = all.filter { it.id !in names && it.groups.any { g -> g.lowercase().contains(needle) } }
        fun row(id: Long, title: String, list: List<Channel>) {
            if (list.isEmpty()) return
            val a = ArrayObjectAdapter(ChannelCardPresenter())
            a.addAll(0, list.take(60).map { ChannelItem(it, epg[it.epgKey].nowAndNext(now).first, now) })
            rows.add(ListRow(HeaderItem(id, "$title (${list.size})"), a))
        }
        row(1, "Channels", byName)
        row(2, "On now", onNow)
        row(3, "Categories", inGroup)
    }

    @Deprecated("Fragment result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE && resultCode == android.app.Activity.RESULT_OK && data != null) setSearchQuery(data, true)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object { const val VOICE = 41 }
}
