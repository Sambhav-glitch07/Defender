package `in`.suzuki.defender

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MediaScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var source: TextView
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var bar: ThinBar
    private lateinit var play: ImageView
    private lateinit var enable: TextView
    private var shownArt: Bitmap? = null
    private var artIsFallback = false

    override val root: View by lazy { build() }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        
        // ---- album artwork
        val left = FrameLayout(ctx)
        left.setBackgroundColor(C.INNER)
        art = ImageView(ctx)
        art.background = Ui.rect(C.SURFACE2, C.LINE)
        showFallback()
        left.addView(art, FrameLayout.LayoutParams(Ui.px(340), Ui.px(340), Gravity.CENTER))

        // ---- details + transport
        val right = LinearLayout(ctx)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.CENTER_VERTICAL
        right.setPadding(Ui.px(56), Ui.px(24), Ui.px(56), Ui.px(24))

        right.addView(Ui.caps(ctx, "Now playing", 11f, C.ACCENT))
        title = Ui.text(ctx, "", 36f, C.TEXT, Ui.LIGHT)
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        title.setPadding(0, Ui.px(16), 0, 0)
        right.addView(title)
        artist = Ui.text(ctx, "", 18f, C.DIM)
        artist.setPadding(0, Ui.px(10), 0, 0)
        right.addView(artist)
        source = Ui.text(ctx, "", 12f, C.DIM)
        source.setPadding(0, Ui.px(14), 0, 0)
        right.addView(source)

        enable = Ui.button(ctx, "Enable now-playing info") {
            env.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        val enLp = Ui.mm(WRAP, WRAP)
        enLp.topMargin = Ui.px(22)
        right.addView(enable, enLp)

        right.addView(View(ctx), Ui.mm(MATCH, 0, 1f))

        bar = ThinBar(ctx)
        val barLp = Ui.mm(MATCH, Ui.px(3))
        right.addView(bar, barLp)
        val times = LinearLayout(ctx)
        times.orientation = LinearLayout.HORIZONTAL
        elapsed = Ui.text(ctx, "0:00", 12f, C.DIM)
        total = Ui.text(ctx, "", 12f, C.DIM)
        times.addView(elapsed, Ui.mm(0, WRAP, 1f))
        times.addView(total)
        val tLp = Ui.mm(MATCH, WRAP)
        tLp.topMargin = Ui.px(10)
        right.addView(times, tLp)

        val controls = LinearLayout(ctx)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.addView(Ui.actionIcon(ctx, R.drawable.ic_prev, 34f) { env.media.prev() })
        play = Ui.icon(ctx, R.drawable.ic_play, 34f, C.TEXT)
        play.setPadding(Ui.px(22), Ui.px(22), Ui.px(22), Ui.px(22))
        play.background = Ui.rect(0, C.ACCENT, 2f, 80f)
        val pl = LinearLayout.LayoutParams(Ui.px(92), Ui.px(92))
        pl.setMargins(Ui.px(36), 0, Ui.px(36), 0)
        play.layoutParams = pl
        Ui.pressable(play) { env.media.playPause(); refresh() }
        controls.addView(play)
        controls.addView(Ui.actionIcon(ctx, R.drawable.ic_next, 34f) { env.media.next() })
        val cLp = Ui.mm(MATCH, WRAP)
        cLp.topMargin = Ui.px(20)
        right.addView(controls, cLp)

        row.addView(left, Ui.mm(0, MATCH, 1f))
        row.addView(right, Ui.mm(0, MATCH, 1.3f))
        refresh()
        return row
    }

    private fun showFallback() {
        if (artIsFallback) return
        artIsFallback = true
        shownArt = null
        art.setImageResource(R.drawable.ic_suzuki_s)
        art.setColorFilter(C.LINE)
        art.scaleType = ImageView.ScaleType.FIT_CENTER
        art.setPadding(Ui.px(90), Ui.px(90), Ui.px(90), Ui.px(90))
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    private fun refresh() {
        val m = env.media
        if (m.session) {
            title.text = if (m.title.isNotEmpty()) m.title else "Playing"
            artist.text = m.artist
            source.text = if (m.playing) "Playing" else "Paused"
            val b = m.art
            if (b != null) {
                if (b !== shownArt) {
                    shownArt = b
                    artIsFallback = false
                    art.clearColorFilter()
                    art.setPadding(0, 0, 0, 0)
                    art.scaleType = ImageView.ScaleType.CENTER_CROP
                    art.setImageBitmap(b)
                }
            } else showFallback()
            bar.fraction = if (m.duration > 0) m.position().toFloat() / m.duration else 0f
            elapsed.text = fmt(m.position())
            total.text = if (m.duration > 0) fmt(m.duration) else ""
        } else {
            showFallback()
            title.text = if (m.access) "Nothing playing" else "Now-playing info is off"
            artist.text = if (m.access) "Start music on your phone or a media app" else "Allow Suzuki OS to read the active media session."
            source.text = "Play, pause and skip still work with connected Bluetooth audio."
            bar.fraction = 0f
            elapsed.text = "0:00"
            total.text = ""
        }
        enable.visibility = if (m.access) View.GONE else View.VISIBLE
        play.setImageResource(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    override fun onShow() = refresh()
    override fun onTick() = refresh()
}