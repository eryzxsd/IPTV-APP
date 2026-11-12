package app.lumentv.tv

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lumentv.R
import app.lumentv.data.ClockMode
import app.lumentv.data.nowAndNext
import app.lumentv.ui.player.AspectMode
import app.lumentv.ui.player.LivePolicy
import app.lumentv.ui.player.PlaybackState
import app.lumentv.ui.player.QualityLogic
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Full-screen player for the remote. Video hidden behind nothing:
 *  - OK shows the info bar and buttons; Up/Down (or CH+/CH−) change channel.
 *  - Left, Guide or Menu opens the channel list; Back closes whatever is open, then goes Home.
 */
class TvPlayerFragment : Fragment(), TvKeyHandler {
    private val activity get() = requireActivity() as TvActivity
    private val vm get() = activity.vm
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { setOverlay(Overlay.None) }
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 10_000)
        }
    }

    private enum class Overlay { None, Info, Controls }
    private var overlay = Overlay.None
    private var dialog: AlertDialog? = null

    private lateinit var root: View
    private lateinit var loading: ProgressBar
    private lateinit var message: View
    private lateinit var clock: TextView
    private lateinit var infoBar: View
    private lateinit var buttons: View
    private lateinit var panel: View
    private lateinit var panelList: VerticalGridView
    private val panelItems = ArrayObjectAdapter(ChannelRowPresenter())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.tv_player, container, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        root = v.findViewById(R.id.tv_player_root)
        loading = v.findViewById(R.id.loading)
        message = v.findViewById(R.id.message)
        clock = v.findViewById(R.id.clock)
        infoBar = v.findViewById(R.id.info_bar)
        buttons = v.findViewById(R.id.buttons)
        panel = v.findViewById(R.id.panel)
        panelList = v.findViewById(R.id.panel_list)

        panelList.adapter = ItemBridgeAdapter(panelItems).apply {
            setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
                override fun onBind(vh: ItemBridgeAdapter.ViewHolder) {
                    vh.itemView.setOnClickListener {
                        val item = vh.item as ChannelItem
                        if (item.playing) setPanel(false) else vm.play(item.channel)
                    }
                }
            })
        }

        v.findViewById<Button>(R.id.btn_play).setOnClickListener { vm.player.togglePlay() }
        v.findViewById<Button>(R.id.btn_channels).setOnClickListener { setPanel(true) }
        v.findViewById<Button>(R.id.btn_subtitles).setOnClickListener { subtitlesDialog() }
        v.findViewById<Button>(R.id.btn_audio).setOnClickListener { audioDialog() }
        v.findViewById<Button>(R.id.btn_quality).setOnClickListener { qualityDialog() }
        v.findViewById<Button>(R.id.btn_more).setOnClickListener { moreDialog() }
        v.findViewById<Button>(R.id.btn_favorite).setOnClickListener { vm.player.state.value.channel?.let(vm::toggleFavorite) }
        v.findViewById<Button>(R.id.message_primary).setOnClickListener {
            if (vm.player.state.value.status == PlaybackState.Status.Error) vm.player.retry() else vm.player.keepWaiting()
        }
        v.findViewById<Button>(R.id.message_next).setOnClickListener { vm.zap(1) }
        // Any focus change inside the controls keeps them on screen a little longer.
        listOf(R.id.btn_play, R.id.btn_channels, R.id.btn_subtitles, R.id.btn_audio, R.id.btn_quality, R.id.btn_more, R.id.btn_favorite).forEach {
            v.findViewById<View>(it).setOnFocusChangeListener { _, has -> if (has) scheduleHide() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.player.state, vm.epg, vm.clock, vm.library, vm.appPrefs) { s, epg, now, lib, app ->
                        render(s, epg, now, lib.favorites, app.clock)
                    }.collect {}
                }
                launch {
                    var lastId: String? = null
                    vm.player.state.collect { s ->
                        // Show the channel name for a moment after zapping.
                        if (s.channel?.id != lastId) {
                            lastId = s.channel?.id
                            if (overlay == Overlay.None && panel.visibility != View.VISIBLE) setOverlay(Overlay.Info)
                        }
                        if (s.status == PlaybackState.Status.Playing && s.video.size > 1) vm.player.probeQualities()
                    }
                }
                launch {
                    combine(vm.filtered, vm.channels, vm.epg, vm.clock, vm.player.state) { f, all, epg, now, s ->
                        val list = f.ifEmpty { all }
                        list.map { ChannelItem(it, epg[it.epgKey].nowAndNext(now).first, now, it.id == s.channel?.id) }
                    }.collect { items ->
                        panelItems.setItems(items, TvBrowseFragment.itemDiff)
                        v.findViewById<TextView>(R.id.panel_subtitle).text = "${items.size} channels · OK to watch"
                    }
                }
            }
        }
        setOverlay(Overlay.Info)
    }

    override fun onResume() {
        super.onResume()
        activity.setVideoVisibility(1f)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        dialog?.dismiss()
    }

    private var clockMode = ClockMode.Off

    private fun render(s: PlaybackState, epg: Map<String, List<app.lumentv.data.Programme>>, now: Long, favorites: List<String>, clockMode: ClockMode) {
        val v = view ?: return
        this.clockMode = clockMode
        updateClock()
        val c = s.channel
        loading.visibility = if ((s.status == PlaybackState.Status.Loading || s.reconnecting) && !s.slow) View.VISIBLE else View.GONE

        // Error or "still connecting" card.
        val showMessage = s.status == PlaybackState.Status.Error || s.slow
        if (showMessage) {
            v.findViewById<TextView>(R.id.message_title).text = if (s.slow && s.status != PlaybackState.Status.Error) "Still connecting…" else "Can't play this channel"
            v.findViewById<TextView>(R.id.message_body).text =
                if (s.status == PlaybackState.Status.Error) s.error ?: "The stream didn't respond." else "Some channels take a while to start."
            v.findViewById<Button>(R.id.message_primary).text = if (s.status == PlaybackState.Status.Error) "Try again" else "Keep waiting"
            v.findViewById<Button>(R.id.message_next).text = "Next channel"
        }
        if (showMessage != (message.visibility == View.VISIBLE)) {
            message.visibility = if (showMessage) View.VISIBLE else View.GONE
            if (showMessage && panel.visibility != View.VISIBLE) v.findViewById<View>(R.id.message_primary).requestFocus()
            else if (!showMessage && overlay != Overlay.Controls && panel.visibility != View.VISIBLE) root.requestFocus()
        }

        if (c == null) return
        v.findViewById<TextView>(R.id.channel_name).text = c.name
        bindLogo(v.findViewById<ImageView>(R.id.logo), v.findViewById(R.id.logo_initials), c)
        v.findViewById<View>(R.id.live).visibility = if (s.isLive) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.quality).apply {
            val label = QualityLogic.chipLabel(s.autoQuality, s.video, s.currentQuality, c.quality)
            text = label
            visibility = if (s.status == PlaybackState.Status.Playing || label != "Auto") View.VISIBLE else View.GONE
        }
        val (cur, next) = epg[c.epgKey].nowAndNext(now)
        v.findViewById<TextView>(R.id.now_title).text = cur?.title ?: c.groups.joinToString(" · ").ifEmpty { "No guide information" }
        v.findViewById<TextView>(R.id.now_time).apply {
            text = cur?.let { "${fmtTime(it.start)} – ${fmtTime(it.stop)}  ·  ${fmtDuration(it.stop - now)} left" } ?: ""
            visibility = if (cur != null) View.VISIBLE else View.GONE
        }
        v.findViewById<ProgressBar>(R.id.now_progress).apply {
            visibility = if (cur != null) View.VISIBLE else View.GONE
            if (cur != null) progress = progressOf(cur, now)
        }
        v.findViewById<TextView>(R.id.next).apply {
            text = next?.let { "Next  ${fmtTime(it.start)}  ${it.title}" } ?: ""
            visibility = if (next != null) View.VISIBLE else View.GONE
        }
        v.findViewById<Button>(R.id.btn_play).text = if (s.isPlaying || s.status == PlaybackState.Status.Loading) "❚❚  Pause" else "▶  Play"
        v.findViewById<Button>(R.id.btn_subtitles).text = if (s.subtitlesOn) "Subtitles: On" else "Subtitles"
        v.findViewById<Button>(R.id.btn_quality).text = "Quality"
        v.findViewById<Button>(R.id.btn_favorite).text = if (c.id in favorites) "★  Favorite" else "☆  Favorite"
    }

    private fun updateClock() {
        if (!::clock.isInitialized) return
        val show = when (clockMode) {
            ClockMode.Off -> false
            ClockMode.WithControls -> overlay != Overlay.None || panel.visibility == View.VISIBLE
            ClockMode.Always -> true
        }
        clock.visibility = if (show) View.VISIBLE else View.GONE
        if (show) clock.text = fmtTime(System.currentTimeMillis())
    }

    // ------------------------------------------------------------ overlays

    private fun setOverlay(o: Overlay) {
        overlay = o
        infoBar.visibility = if (o == Overlay.None) View.GONE else View.VISIBLE
        buttons.visibility = if (o == Overlay.Controls) View.VISIBLE else View.GONE
        when (o) {
            Overlay.Controls -> if (buttons.findFocus() == null) view?.findViewById<View>(R.id.btn_play)?.requestFocus()
            else -> if (message.visibility != View.VISIBLE && panel.visibility != View.VISIBLE) root.requestFocus()
        }
        updateClock()
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideOverlay)
        if (overlay != Overlay.None) handler.postDelayed(hideOverlay, if (overlay == Overlay.Info) 4_000 else 6_000)
    }

    private fun setPanel(open: Boolean) {
        if (open) {
            setOverlay(Overlay.None)
            panel.visibility = View.VISIBLE
            val i = (0 until panelItems.size()).firstOrNull { (panelItems.get(it) as ChannelItem).playing } ?: 0
            panelList.selectedPosition = i
            panelList.requestFocus()
        } else {
            panel.visibility = View.GONE
            if (message.visibility == View.VISIBLE) view?.findViewById<View>(R.id.message_primary)?.requestFocus() else root.requestFocus()
        }
        updateClock()
    }

    override fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        val key = event.keyCode
        if (panel.visibility == View.VISIBLE) {
            return when (key) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (first) setPanel(false); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (first) setPanel(false); true }
                else -> false
            }
        }
        if (overlay == Overlay.Controls) {
            if (down) scheduleHide()
            return when (key) {
                KeyEvent.KEYCODE_BACK -> { if (first) setOverlay(Overlay.None); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { if (first) setOverlay(Overlay.None); true }
                KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (first) setPanel(true); true }
                else -> false
            }
        }
        if (message.visibility == View.VISIBLE) {
            // The message's buttons take Left/Right/OK; Up/Down still change channel.
            return when (key) {
                KeyEvent.KEYCODE_DPAD_UP -> { if (first) vm.zap(-1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (first) vm.zap(1); true }
                KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (first) setPanel(true); true }
                else -> false
            }
        }
        return when (key) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_INFO -> {
                if (first) setOverlay(Overlay.Controls); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> { if (first) vm.zap(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (first) vm.zap(1); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (first) setPanel(true); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (first) setOverlay(Overlay.Controls); true }
            KeyEvent.KEYCODE_BACK -> if (overlay == Overlay.Info) { if (first) setOverlay(Overlay.None); true } else false
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (first && !vm.player.player.isPlaying) vm.player.togglePlay(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (first) vm.player.player.pause(); true }
            else -> false
        }
    }

    // ------------------------------------------------------------- dialogs

    private fun choose(title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        dialog?.dismiss()
        handler.removeCallbacks(hideOverlay)
        dialog = AlertDialog.Builder(requireContext(), R.style.Theme_LumenTV_Tv_Dialog)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { d, which -> d.dismiss(); onPick(which) }
            .setOnDismissListener { dialog = null; scheduleHide() }
            .show()
    }

    private fun subtitlesDialog() {
        val s = vm.player.state.value
        if (s.text.isEmpty()) return info("Subtitles", "This channel has no subtitles.")
        val labels = listOf("Off") + s.text.map { listOfNotNull(it.label, it.detail).joinToString("  ·  ") }
        val sel = if (!s.subtitlesOn) 0 else s.text.indexOfFirst { it.selected } + 1
        choose("Subtitles", labels, sel) { i -> vm.player.selectSubtitle(if (i == 0) null else s.text[i - 1].id) }
    }

    private fun audioDialog() {
        val s = vm.player.state.value
        if (s.audio.size <= 1) return info("Audio", s.audio.firstOrNull()?.let { "Only one audio track: ${it.label}" } ?: "This channel has one audio track.")
        choose("Audio", s.audio.map { listOfNotNull(it.label, it.detail).joinToString("  ·  ") }, s.audio.indexOfFirst { it.selected }) { i ->
            vm.player.selectAudio(s.audio[i].id)
        }
    }

    private fun qualityDialog() {
        val s = vm.player.state.value
        val c = s.channel ?: return
        val known = s.currentQuality ?: c.quality
        val auto = if (known != null && s.autoQuality) "Auto (now $known)" else "Auto"
        if (s.video.size <= 1) {
            return info("Quality", known?.let { "This channel is only available in $it." } ?: "This channel has a single quality.")
        }
        val labels = listOf(auto) + s.video.map { listOfNotNull(it.label, it.detail).joinToString("  ·  ") }
        val sel = if (s.autoQuality) 0 else s.video.indexOfFirst { it.selected } + 1
        choose(if (s.probingQualities) "Quality (checking resolutions…)" else "Quality", labels, sel) { i ->
            vm.player.selectQuality(if (i == 0) null else s.video[i - 1].id)
        }
    }

    private fun moreDialog() {
        val behind = vm.player.liveOffsetSeconds()
        val items = buildList {
            if (!LivePolicy.isAtLiveEdge(behind)) add("Go to live (${behind}s behind)" to { vm.player.goLive() })
            add("Picture: ${vm.player.state.value.aspect.label}" to ::aspectDialog)
            add(sleepLabel() to ::sleepDialog)
            add("Stream info" to ::streamInfo)
            add("Channel list" to { setPanel(true) })
            add("TV guide" to { activity.open(TvGuideFragment(), "guide") })
            add("Reload stream" to { vm.player.retry() })
        }
        dialog?.dismiss()
        handler.removeCallbacks(hideOverlay)
        dialog = AlertDialog.Builder(requireContext(), R.style.Theme_LumenTV_Tv_Dialog)
            .setTitle("More")
            .setItems(items.map { it.first }.toTypedArray()) { d, which -> d.dismiss(); items[which].second() }
            .setOnDismissListener { dialog = null; scheduleHide() }
            .show()
    }

    private fun sleepLabel(): String {
        val at = vm.player.state.value.sleepAt ?: return "Sleep timer: Off"
        return "Sleep timer: ${fmtDuration(at - System.currentTimeMillis())} left"
    }

    private fun aspectDialog() {
        val modes = AspectMode.entries
        choose("Picture", modes.map { it.label }, modes.indexOf(vm.player.state.value.aspect)) { vm.player.setAspect(modes[it]) }
    }

    private fun sleepDialog() {
        val options = listOf<Int?>(null, 15, 30, 60, 90, 120)
        val labels = options.map { m -> if (m == null) "Off" else if (m < 60) "$m minutes" else fmtDuration(m * 60_000L) }
        val at = vm.player.state.value.sleepAt
        choose("Sleep timer", labels, if (at == null) 0 else -1) { vm.player.setSleepTimer(options[it]) }
    }

    private fun streamInfo() {
        val i = vm.player.streamInfo()
        val rows = listOfNotNull(
            i.resolution?.let { "Resolution: $it" },
            i.frameRate?.let { "Frame rate: $it" },
            i.videoCodec?.let { "Video: $it" },
            i.audioCodec?.let { "Audio: $it" + (i.audioChannels?.let { c -> " ($c)" } ?: "") },
            i.bitrate?.let { "Bitrate: $it" },
            "Server: ${i.host}",
        )
        info("Stream info", rows.joinToString("\n"))
    }

    private fun info(title: String, text: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(requireContext(), R.style.Theme_LumenTV_Tv_Dialog)
            .setTitle(title).setMessage(text).setPositiveButton("OK", null)
            .setOnDismissListener { dialog = null; scheduleHide() }
            .show()
    }
}
