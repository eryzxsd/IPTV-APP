package app.lumentv.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import app.lumentv.R
import app.lumentv.data.Channel
import app.lumentv.data.Programme
import coil3.load
import java.text.DateFormat
import java.util.Date

/** A channel shown on a card/row, with what's on now. */
data class ChannelItem(val channel: Channel, val now: Programme?, val clock: Long, val playing: Boolean = false)

/** Non-channel cards (Guide, Search, Settings). */
data class ActionItem(val id: String, val title: String, val subtitle: String, val icon: Int)

private val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
fun fmtTime(t: Long): String = timeFmt.format(Date(t))
fun fmtDuration(ms: Long): String {
    val m = (ms / 60000).toInt().coerceAtLeast(0)
    return if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
}

fun progressOf(p: Programme, now: Long) = (((now - p.start).toFloat() / (p.stop - p.start)) * 1000).toInt().coerceIn(0, 1000)

fun initials(name: String): String {
    val words = name.replace(Regex("""\(.*?\)|\[.*?]"""), "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0][0]}${words[1][0]}".uppercase()
    }
}

private fun hue(name: String) = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()

/** Coloured tile with initials for channels without a logo (cached per name). */
private val tileCache = object : android.util.LruCache<String, Bitmap>(64) {}
fun initialsTile(context: Context, name: String, w: Int, h: Int): BitmapDrawable {
    val key = "$name|$w|$h"
    val bmp = tileCache.get(key) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val hu = hue(name)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            Color.HSVToColor(floatArrayOf(hu, 0.65f, 0.6f)), Color.HSVToColor(floatArrayOf((hu + 40) % 360, 0.7f, 0.35f)), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = h * 0.32f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(initials(name), w / 2f, h / 2f - (text.descent() + text.ascent()) / 2, text)
        tileCache.put(key, b)
    }
    return BitmapDrawable(context.resources, bmp)
}

/** Loads a channel logo into [image]; falls back to initials in [fallback] (or a generated tile). */
fun bindLogo(image: ImageView, fallback: TextView?, channel: Channel) {
    fun showInitials() {
        if (fallback != null) {
            image.setImageDrawable(null)
            fallback.text = initials(channel.name)
            fallback.visibility = View.VISIBLE
        } else {
            image.setImageDrawable(initialsTile(image.context, channel.name, 320, 180))
        }
    }
    fallback?.visibility = View.GONE
    if (channel.logo.isBlank()) return showInitials()
    image.load(channel.logo) {
        listener(onError = { _, _ -> showInitials() })
    }
}

/** Leanback card: logo on a light tile, channel name, and what's on now. */
class ChannelCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_W, CARD_H)
            setInfoAreaBackgroundColor(ContextCompat.getColor(context, R.color.tv_surface))
            mainImageView?.apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(28, 20, 28, 20)
            }
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val card = vh.view as ImageCardView
        when (item) {
            is ChannelItem -> {
                val c = item.channel
                card.titleText = c.name
                card.contentText = when {
                    item.playing -> "Playing now"
                    item.now != null -> "${fmtTime(item.now.start)}  ${item.now.title}"
                    else -> c.groups.joinToString(" · ")
                }
                val img = card.mainImageView ?: return
                if (c.logo.isBlank()) {
                    img.setPadding(0, 0, 0, 0)
                    img.background = null
                    img.setImageDrawable(initialsTile(card.context, c.name, CARD_W, CARD_H))
                } else {
                    img.setPadding(28, 20, 28, 20)
                    img.background = ColorDrawable(ContextCompat.getColor(card.context, R.color.tv_logo_tile))
                    img.load(c.logo) {
                        listener(onError = { _, _ ->
                            img.setPadding(0, 0, 0, 0)
                            img.background = null
                            img.setImageDrawable(initialsTile(card.context, c.name, CARD_W, CARD_H))
                        })
                    }
                }
            }
            is ActionItem -> {
                card.titleText = item.title
                card.contentText = item.subtitle
                card.badgeImage = null
                card.mainImageView?.apply {
                    setPadding(80, 40, 80, 40)
                    background = ColorDrawable(ContextCompat.getColor(card.context, R.color.tv_surface_high))
                    setImageResource(item.icon)
                }
            }
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        val card = vh.view as ImageCardView
        card.badgeImage = null
        card.mainImage = null
    }

    companion object {
        const val CARD_W = 320
        const val CARD_H = 180
    }
}

/** Row in the player's channel list and the guide's channel column. */
class ChannelRowPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tv_channel_row, parent, false))

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val it = item as ChannelItem
        val v = vh.view
        bindLogo(v.findViewById(R.id.row_logo), v.findViewById(R.id.row_initials), it.channel)
        v.findViewById<TextView>(R.id.row_name).text = it.channel.name
        v.findViewById<TextView>(R.id.row_sub).text = it.now?.title ?: it.channel.groups.joinToString(" · ")
        v.findViewById<ProgressBar>(R.id.row_progress).apply {
            visibility = if (it.now != null) View.VISIBLE else View.GONE
            if (it.now != null) progress = progressOf(it.now, it.clock)
        }
        v.findViewById<View>(R.id.row_badge).visibility = if (it.playing) View.VISIBLE else View.GONE
        v.isActivated = it.playing
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {}
}

/** Row in the guide's schedule column. */
class ProgrammePresenter : Presenter() {
    data class Item(val programme: Programme, val clock: Long)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tv_programme_row, parent, false))

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val (p, clock) = item as Item
        val v = vh.view
        val live = p.isLive(clock)
        v.findViewById<TextView>(R.id.prog_time).text = fmtTime(p.start)
        v.findViewById<TextView>(R.id.prog_title).text = p.title
        v.findViewById<View>(R.id.prog_now).visibility = if (live) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.prog_desc).apply {
            text = p.desc
            visibility = if (p.desc.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.prog_dur).text = fmtDuration(p.stop - p.start)
        v.isActivated = live
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {}
}
