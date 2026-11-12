package app.lumentv.tv

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import app.lumentv.BuildConfig
import app.lumentv.R
import app.lumentv.data.AppPrefs
import app.lumentv.data.BufferMode
import app.lumentv.data.ClockMode
import app.lumentv.data.DecoderMode
import app.lumentv.data.LoadState
import app.lumentv.data.Sources
import app.lumentv.data.UserSettings
import app.lumentv.ui.player.AspectMode
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Settings for the remote: a Leanback guided step per section. Every option the phone app has.
 * Choices open inline lists; switches are check boxes; guide regions and playlists are saved
 * with Save (or discarded with Back).
 */
class TvSettingsFragment : GuidedStepSupportFragment() {
    private val activity get() = requireActivity() as TvActivity
    private val vm get() = activity.vm
    private val page get() = arguments?.getString(PAGE) ?: ROOT

    /** Unsaved guide/playlist edits. */
    private var draft: UserSettings? = null

    override fun onProvideTheme() = R.style.Theme_LumenTV_Tv_GuidedStep

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.tv_ic_settings)
        return when (page) {
            PLAYBACK -> GuidanceStylist.Guidance("Playback", "Quality, decoder, buffering and what happens when you leave the app.", "Settings", icon)
            CHANNELS -> GuidanceStylist.Guidance("Channels", "How the channel list is sorted and filtered.", "Settings", icon)
            GUIDE -> GuidanceStylist.Guidance("TV guide", "Programme information from epgshare01.", "Settings", icon)
            REGIONS -> GuidanceStylist.Guidance("Guide regions", "Pick the regions and services to download. Larger files take longer.\n\nSelect Save when done.", "TV guide", icon)
            PLAYLIST -> GuidanceStylist.Guidance("Playlist", "Where channels come from. The default is the iptv-org list.\n\nSelect Save when done.", "Settings", icon)
            DATA -> GuidanceStylist.Guidance("Data", "Refresh, clear saved data or reset settings.", "Settings", icon)
            else -> GuidanceStylist.Guidance("Settings", "Lumen TV ${BuildConfig.VERSION_NAME}", null, icon)
        }
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += build()
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        if (page == REGIONS || page == PLAYLIST) {
            actions += GuidedAction.Builder(requireContext()).id(SAVE).title("Save").build()
            actions += GuidedAction.Builder(requireContext()).id(CANCEL).title("Cancel").build()
        }
    }

    override fun onResume() {
        super.onResume()
        activity.setVideoVisibility(0f)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Keep descriptions (current values, load progress) up to date.
        if (page != REGIONS && page != PLAYLIST) {
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.flow.merge(vm.appPrefs.drop(1), vm.settings.drop(1), vm.library.drop(1), vm.epgState.drop(1), vm.epgFailures.drop(1), vm.channelState.drop(1))
                    .collect { refresh() }
            }
        }
    }

    private fun refresh() {
        val fresh = build()
        fresh.forEach { a ->
            val old = findActionById(a.id) ?: return@forEach
            old.description = a.description
            old.isChecked = a.isChecked
            old.subActions = a.subActions
            notifyActionChanged(findActionPositionById(a.id))
        }
    }

    // ---------------------------------------------------------------- pages

    private fun build(): List<GuidedAction> {
        val app = vm.appPrefs.value
        val s = draft ?: vm.settings.value
        return when (page) {
            PLAYBACK -> listOf(
                choice(1, "Maximum quality", MAX_QUALITY, app.maxQuality, "Auto never picks a higher resolution"),
                choice(2, "Decoder", DecoderMode.entries.map { it to it.label }, app.decoder, app.decoder.detail),
                choice(3, "Buffer", BufferMode.entries.map { it to it.label }, app.buffer, app.buffer.detail),
                check(4, "Play in background", app.playInBackground, "Keep playing with the Home button or another app open"),
                choice(5, "Show clock", ClockMode.entries.map { it to it.label }, app.clock),
                check(6, "Subtitles on by default", app.subtitlesByDefault, "Turn on subtitles when a channel has them"),
                choice(7, "Picture size", AspectMode.entries.map { it.name to it.label }, app.defaultAspect),
                check(8, "Jump to live when resuming", app.jumpToLiveOnResume, "Play goes back to the live broadcast after a pause"),
                check(9, "Look up real resolutions", app.lookUpResolutions, "Find the size of quality options the stream doesn't label"),
                check(10, "Start with the last channel", app.resumeLastChannel, "Open the player on the channel you watched last"),
            )
            CHANNELS -> listOf(
                check(20, "Sort A–Z", app.sortAlphabetically, "Otherwise channels keep the playlist's order"),
                check(21, "Hide offline channels", vm.library.value.hideOffline, "Channels that failed to play several times"),
                action(22, "Clear offline marks", "Give every channel another chance"),
                action(23, "Clear favorites", "${vm.library.value.favorites.size} favorites"),
                action(24, "Clear watch history", "Also resets recommendations"),
            )
            GUIDE -> listOf(
                check(30, "Download the TV guide", s.epgEnabled),
                action(31, "Regions and services", "${s.epgCodes.size} selected"),
                choice(32, "Refresh every", REFRESH, app.guideRefreshHours),
                action(33, "Refresh guide now", epgStatus()),
            ) + (vm.epgFailures.value.takeIf { it.isNotEmpty() }?.let { f ->
                listOf(action(34, "Retry failed downloads", "${f.size} failed: " + f.joinToString { it.name }))
            } ?: emptyList())
            REGIONS -> Sources.EPG_SOURCES.mapIndexed { i, src ->
                check(1000L + i, src.label, src.code in s.epgCodes, if (src.isService) "Service" else null)
            }
            PLAYLIST -> Sources.PLAYLISTS.mapIndexed { i, p ->
                GuidedAction.Builder(requireContext()).id(100L + i).title(p.label).description(p.url)
                    .checkSetId(PLAYLIST_SET).checked(s.playlists == listOf(p.url)).build()
            } + GuidedAction.Builder(requireContext()).id(110).title("Custom playlist URL")
                .description(s.playlists.firstOrNull { u -> Sources.PLAYLISTS.none { it.url == u } } ?: "Enter an M3U address")
                .editDescription(s.playlists.firstOrNull { u -> Sources.PLAYLISTS.none { it.url == u } } ?: "https://")
                .descriptionEditable(true).build()
            DATA -> listOf(
                action(40, "Reload channels", (vm.channelState.value as? LoadState.Loading)?.label ?: "${vm.channels.value.size} channels"),
                action(41, "Clear cache", "Downloaded playlists and guide files"),
                action(42, "Reset settings", "Back to the defaults"),
            )
            else -> listOf(
                action(50, "Playback", "${maxLabel(app.maxQuality)} · ${app.decoder.label} decoder · ${app.buffer.label}", sub = true),
                action(51, "Channels", "${vm.channels.value.size} channels", sub = true),
                action(52, "TV guide", if (s.epgEnabled) "${s.epgCodes.size} regions · ${epgStatus()}" else "Off", sub = true),
                action(53, "Playlist", Sources.PLAYLISTS.firstOrNull { listOf(it.url) == s.playlists }?.label ?: "Custom", sub = true),
                action(54, "Data", "Reload, clear cache, reset", sub = true),
                GuidedAction.Builder(requireContext()).id(55).title("About").description("Lumen TV ${BuildConfig.VERSION_NAME} · channels from iptv-org, guide from epgshare01")
                    .infoOnly(true).focusable(false).build(),
            )
        }
    }

    private fun epgStatus() = when (val st = vm.epgState.value) {
        is LoadState.Loading -> st.label
        is LoadState.Failed -> st.message
        is LoadState.Done -> st.message ?: "Up to date"
        LoadState.Idle -> "Not loaded"
    }

    private fun action(id: Long, title: String, desc: String? = null, sub: Boolean = false) =
        GuidedAction.Builder(requireContext()).id(id).title(title).description(desc).hasNext(sub).build()

    private fun check(id: Long, title: String, on: Boolean, desc: String? = null) =
        GuidedAction.Builder(requireContext()).id(id).title(title).description(desc)
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID).checked(on).build()

    private fun <T> choice(id: Long, title: String, options: List<Pair<T, String>>, current: T, detail: String? = null): GuidedAction {
        val label = options.firstOrNull { it.first == current }?.second ?: current.toString()
        val subs = options.mapIndexed { i, (v, l) ->
            GuidedAction.Builder(requireContext()).id(id * 100 + i).title(l)
                .checkSetId(id.toInt()).checked(v == current).build()
        }
        return GuidedAction.Builder(requireContext()).id(id).title(title)
            .description(if (detail != null) "$label · $detail" else label).subActions(subs).build()
    }

    // ---------------------------------------------------------------- clicks

    private fun open(p: String) =
        add(parentFragmentManager, TvSettingsFragment().apply { arguments = bundleOf(PAGE to p) }, TvActivity.ROOT)

    private fun setApp(f: (AppPrefs) -> AppPrefs) = vm.saveApp(f(vm.appPrefs.value))

    override fun onGuidedActionClicked(a: GuidedAction) {
        when (a.id) {
            50L -> open(PLAYBACK)
            51L -> open(CHANNELS)
            52L -> open(GUIDE)
            53L -> open(PLAYLIST)
            54L -> open(DATA)
            4L -> setApp { it.copy(playInBackground = a.isChecked) }
            6L -> setApp { it.copy(subtitlesByDefault = a.isChecked) }
            8L -> setApp { it.copy(jumpToLiveOnResume = a.isChecked) }
            9L -> setApp { it.copy(lookUpResolutions = a.isChecked) }
            10L -> setApp { it.copy(resumeLastChannel = a.isChecked) }
            20L -> setApp { it.copy(sortAlphabetically = a.isChecked) }
            21L -> vm.setHideOffline(a.isChecked)
            22L -> { vm.clearOfflineMarks(); toast("Offline marks cleared") }
            23L -> confirm("Clear all favorites?") { vm.clearFavorites() }
            24L -> confirm("Clear watch history?") { vm.clearHistory() }
            30L -> vm.saveSettings(vm.settings.value.copy(epgEnabled = a.isChecked))
            31L -> open(REGIONS)
            33L -> vm.reloadEpg()
            34L -> vm.retryFailedEpg()
            40L -> vm.reloadChannels()
            41L -> { vm.clearCache(); toast("Cache cleared") }
            42L -> confirm("Reset all settings to the defaults?") { vm.resetSettings() }
            SAVE -> {
                draft?.let(vm::saveSettings)
                parentFragmentManager.popBackStack()
            }
            CANCEL -> parentFragmentManager.popBackStack()
            in 100L..109L -> edit { it.copy(playlists = listOf(Sources.PLAYLISTS[(a.id - 100).toInt()].url)) }
            in 1000L..1999L -> {
                val code = Sources.EPG_SOURCES[(a.id - 1000).toInt()].code
                edit { it.copy(epgCodes = if (a.isChecked) it.epgCodes + code else it.epgCodes - code) }
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(a: GuidedAction): Long {
        if (a.id == 110L) {
            val url = a.editDescription?.toString()?.trim().orEmpty()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                a.description = url
                edit { it.copy(playlists = listOf(url)) }
                // The custom URL replaces the preset.
                actions.filter { it.checkSetId == PLAYLIST_SET }.forEach { p ->
                    p.isChecked = false
                    notifyActionChanged(findActionPositionById(p.id))
                }
            } else toast("Enter an address starting with http:// or https://")
        }
        return GuidedAction.ACTION_ID_CURRENT
    }

    override fun onSubGuidedActionClicked(a: GuidedAction): Boolean {
        val parent = a.id / 100
        val i = (a.id % 100).toInt()
        when (parent) {
            1L -> setApp { it.copy(maxQuality = MAX_QUALITY[i].first) }
            2L -> setApp { it.copy(decoder = DecoderMode.entries[i]) }
            3L -> setApp { it.copy(buffer = BufferMode.entries[i]) }
            5L -> setApp { it.copy(clock = ClockMode.entries[i]) }
            7L -> setApp { it.copy(defaultAspect = AspectMode.entries[i].name) }.also { vm.player.setAspect(AspectMode.entries[i]) }
            32L -> setApp { it.copy(guideRefreshHours = REFRESH[i].first) }
        }
        return true // close the list
    }

    private fun edit(f: (UserSettings) -> UserSettings) {
        draft = f(draft ?: vm.settings.value)
    }

    private fun toast(text: String) = android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show()

    private fun confirm(text: String, yes: () -> Unit) {
        android.app.AlertDialog.Builder(requireContext(), R.style.Theme_LumenTV_Tv_Dialog)
            .setMessage(text)
            .setPositiveButton("Clear") { _, _ -> yes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val PAGE = "page"
        private const val ROOT = "root"
        private const val PLAYBACK = "playback"
        private const val CHANNELS = "channels"
        private const val GUIDE = "guide"
        private const val REGIONS = "regions"
        private const val PLAYLIST = "playlist"
        private const val DATA = "data"
        private const val SAVE = 900L
        private const val CANCEL = 901L
        private const val PLAYLIST_SET = 77
        private val MAX_QUALITY = listOf(0 to "No limit", 1080 to "1080p", 720 to "720p", 480 to "480p (data saver)")
        private val REFRESH = listOf(2 to "2 hours", 4 to "4 hours", 12 to "12 hours", 24 to "24 hours")
        private fun maxLabel(q: Int) = MAX_QUALITY.firstOrNull { it.first == q }?.second ?: "${q}p"
    }
}
