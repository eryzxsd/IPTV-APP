package app.lumentv.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import app.lumentv.R
import app.lumentv.data.Channel
import app.lumentv.data.LoadState
import app.lumentv.data.nowAndNext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Two-pane guide: channels with guide data on the left, the selected channel's schedule on the right. */
class TvGuideFragment : Fragment() {
    private val activity get() = requireActivity() as TvActivity
    private val vm get() = activity.vm
    private val channelItems = ArrayObjectAdapter(ChannelRowPresenter())
    private val programmeItems = ArrayObjectAdapter(ProgrammePresenter())
    private var selected: Channel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.tv_guide, container, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val channels = v.findViewById<VerticalGridView>(R.id.guide_channels)
        val programmes = v.findViewById<VerticalGridView>(R.id.guide_programmes)
        val count = v.findViewById<TextView>(R.id.guide_count)
        val title = v.findViewById<TextView>(R.id.guide_channel)
        val hint = v.findViewById<TextView>(R.id.guide_hint)

        channels.adapter = ItemBridgeAdapter(channelItems).apply {
            setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
                override fun onBind(vh: ItemBridgeAdapter.ViewHolder) {
                    vh.itemView.setOnClickListener { activity.play((vh.item as ChannelItem).channel) }
                }
            })
        }
        programmes.adapter = ItemBridgeAdapter(programmeItems).apply {
            setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
                override fun onBind(vh: ItemBridgeAdapter.ViewHolder) {
                    vh.itemView.setOnClickListener { selected?.let(activity::play) }
                }
            })
        }

        fun showSchedule() {
            val c = selected ?: return
            val now = vm.clock.value
            val list = vm.epg.value[c.epgKey].orEmpty().filter { it.stop > now }
            title.text = c.name
            hint.text = if (list.isEmpty()) "No guide information" else "OK to watch · Right to see the schedule"
            programmeItems.setItems(list.map { ProgrammePresenter.Item(it, now) }, object : DiffCallback<ProgrammePresenter.Item>() {
                override fun areItemsTheSame(a: ProgrammePresenter.Item, b: ProgrammePresenter.Item) = a.programme.start == b.programme.start
                override fun areContentsTheSame(a: ProgrammePresenter.Item, b: ProgrammePresenter.Item) = a == b
            })
            programmes.selectedPosition = 0
        }

        channels.setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(parent: RecyclerView, child: RecyclerView.ViewHolder?, position: Int, subposition: Int) {
                val item = (child as? ItemBridgeAdapter.ViewHolder)?.item as? ChannelItem ?: return
                if (item.channel.id != selected?.id) {
                    selected = item.channel
                    showSchedule()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var focused = false
                combine(vm.channels, vm.epg, vm.clock, vm.player.state, vm.epgState) { all, epg, now, s, load ->
                    val withGuide = all.filter { !epg[it.epgKey].isNullOrEmpty() }
                    Triple(withGuide.map { ChannelItem(it, epg[it.epgKey].nowAndNext(now).first, now, it.id == s.channel?.id) }, load, all.size)
                }.collect { (items, load, total) ->
                    channelItems.setItems(items, TvBrowseFragment.itemDiff)
                    count.text = when {
                        load is LoadState.Loading -> load.label
                        items.isEmpty() -> "No guide data yet. Choose guide regions in Settings."
                        else -> "${items.size} of $total channels have guide data"
                    }
                    if (!focused && items.isNotEmpty()) {
                        focused = true
                        channels.selectedPosition = items.indexOfFirst { it.playing }.coerceAtLeast(0)
                        channels.requestFocus()
                    }
                    if (selected != null) showSchedule()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity.setVideoVisibility(0.08f)
    }
}
