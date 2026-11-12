package `in`.suzuki.defender

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Four tall Pivi-Pro-style panels: Navigation | Media | Phone | Vehicle. */
class HomeScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private lateinit var navDest: TextView
    private lateinit var navSub: TextView
    private lateinit var mTitle: TextView
    private lateinit var mArtist: TextView
    private lateinit var mBar: ThinBar
    private lateinit var mPlay: ImageView
    private lateinit var pName: TextView
    private lateinit var pSub: TextView
    private val vRows = ArrayList<Pair<TextView, () -> String>>()

    override val root: View by lazy { build() }

    private fun centered(t: TextView): TextView {
        t.gravity = Gravity.CENTER
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        return t
    }

    private fun gap(h: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, Ui.px(h))
        return v
    }

    private fun underline(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.LINE)
        val lp = LinearLayout.LayoutParams(Ui.px(200), Ui.px(2))
        lp.topMargin = Ui.px(16)
        lp.bottomMargin = Ui.px(14)
        v.layoutParams = lp
        return v
    }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL

        // ---- NAVIGATION
        val nav = Ui.panel(ctx, "Navigation", Color.parseColor("#1A1D1F"))
        navDest = centered(Ui.text(ctx, "Where to?", 26f, C.TEXT, Ui.LIGHT))
        navSub = centered(Ui.text(ctx, "", 14f, C.DIM))
        nav.body.addView(navDest, Ui.mm(MATCH, WRAP))
        nav.body.addView(underline())
        nav.body.addView(navSub, Ui.mm(MATCH, WRAP))
        nav.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_search) { env.focusSearch = true; env.navigate(Dest.NAV) })
        nav.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_history) { env.navigate(Dest.NAV) })
        nav.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_nav) {
            val d = env.prefs.getString("dest", null)
            if (d != null) env.startNavigation(d) else { env.focusSearch = true; env.navigate(Dest.NAV) }
        })
        Ui.pressable(nav.frame, false) { env.navigate(Dest.NAV) }

        // ---- MEDIA
        val med = Ui.panel(ctx, "Media", Color.parseColor("#1B1D20"))
        mTitle = centered(Ui.text(ctx, "", 24f, C.TEXT, Ui.LIGHT))
        mBar = ThinBar(ctx)
        val barLp = Ui.mm(Ui.px(200), Ui.px(3))
        barLp.topMargin = Ui.px(16)
        barLp.bottomMargin = Ui.px(14)
        mArtist = centered(Ui.text(ctx, "", 14f, C.DIM))
        med.body.addView(mTitle, Ui.mm(MATCH, WRAP))
        med.body.addView(mBar, barLp)
        med.body.addView(mArtist, Ui.mm(MATCH, WRAP))
        med.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_prev) { env.media.prev() })
        mPlay = Ui.actionIcon(ctx, R.drawable.ic_play, 28f) { env.media.playPause(); refresh() }
        med.actions.addView(mPlay)
        med.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_next) { env.media.next() })
        Ui.pressable(med.frame, false) { env.navigate(Dest.MEDIA) }

        // ---- PHONE
        val ph = Ui.panel(ctx, "Phone", Color.parseColor("#181B1E"))
        pName = centered(Ui.text(ctx, "", 24f, C.TEXT, Ui.LIGHT))
        pSub = centered(Ui.text(ctx, "", 14f, C.DIM))
        ph.body.addView(pName, Ui.mm(MATCH, WRAP))
        ph.body.addView(underline())
        ph.body.addView(pSub, Ui.mm(MATCH, WRAP))
        ph.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_phone) { env.phoneTab = 2; env.navigate(Dest.PHONE) })
        ph.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_history) { env.phoneTab = 0; env.navigate(Dest.PHONE) })
        ph.actions.addView(Ui.actionIcon(ctx, R.drawable.ic_person) { env.phoneTab = 1; env.navigate(Dest.PHONE) })
        Ui.pressable(ph.frame, false) { env.navigate(Dest.PHONE) }

        // ---- VEHICLE
        val veh = Ui.panel(ctx, "Vehicle", Color.parseColor("#1A1C1F"))
        val mark = Ui.icon(ctx, R.drawable.ic_suzuki_s, 34f, C.ACCENT)
        val markLp = Ui.mm(Ui.px(34), Ui.px(34))
        markLp.gravity = Gravity.CENTER_HORIZONTAL
        veh.body.addView(mark, markLp)
        veh.body.addView(gap(12))
        veh.body.addView(centered(Ui.text(ctx, "Defender Edition", 20f, C.TEXT, Ui.LIGHT)), Ui.mm(MATCH, WRAP))
        veh.body.addView(underline())
        addRow(veh.body, "Bluetooth") { if (env.bt.isOn()) "On" else "Off" }
        addRow(veh.body, "Wi-Fi") { if (env.sys.wifiConnected()) "Connected" else "Not connected" }
        addRow(veh.body, "GPS speed") { env.loc.speedKmh()?.let { "${it.toInt()} km/h" } ?: "—" }
        addRow(veh.body, "Fuel") { "Not connected" }
        Ui.pressable(veh.frame, false) { env.navigate(Dest.VEHICLE) }

        scene(nav, 0)
        scene(med, 1)
        scene(ph, 2)
        scene(veh, 3, true)

        row.addView(nav.frame, Ui.mm(0, MATCH, 1.5f))
        row.addView(sep()); row.addView(med.frame, Ui.mm(0, MATCH, 1f))
        row.addView(sep()); row.addView(ph.frame, Ui.mm(0, MATCH, 1f))
        row.addView(sep()); row.addView(veh.frame, Ui.mm(0, MATCH, 1f))
        refresh()
        return row
    }

    /** Scenery mode (Settings → Theme): 0 mixed, 1 road, 2 desert, 3 highland, 4 off. */
    private fun scene(panel: Panel, idx: Int, car: Boolean = false) {
        val mode = env.prefs.getInt("scene", 0)
        if (mode == 4) return
        val mixed = intArrayOf(SceneryView.ROAD, SceneryView.DESERT, SceneryView.HIGHLAND, SceneryView.HIGHLAND)
        val kind = if (mode == 0) mixed[idx] else mode - 1
        panel.frame.addView(SceneryView(ctx, kind, car, 7L + idx), 0, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun sep(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.LINE)
        v.layoutParams = LinearLayout.LayoutParams(1, MATCH)
        return v
    }

    private fun addRow(parent: LinearLayout, label: String, value: () -> String) {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(0, Ui.px(5), 0, Ui.px(5))
        r.addView(Ui.text(ctx, label, 13f, C.DIM), Ui.mm(0, WRAP, 1f))
        val v = Ui.text(ctx, value(), 13f, C.TEXT)
        r.addView(v)
        parent.addView(r, Ui.mm(MATCH, WRAP))
        vRows.add(Pair(v, value))
    }

    private fun refresh() {
        val dest = env.prefs.getString("dest", null)
        navDest.text = dest ?: "Where to?"
        navSub.text = env.loc.describe()

        val m = env.media
        mTitle.text = when {
            m.session && m.title.isNotEmpty() -> m.title
            m.session -> "Playing"
            m.access -> "Nothing playing"
            else -> "Media access off"
        }
        mArtist.text = when {
            m.session -> m.artist
            m.access -> "Open a music app"
            else -> "Tap to enable now playing"
        }
        mBar.fraction = if (m.session && m.duration > 0) m.position().toFloat() / m.duration else 0f
        mPlay.setImageResource(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play)

        val names = env.bt.connectedNames()
        pName.text = when {
            !env.bt.available() -> "No Bluetooth"
            !env.bt.isOn() -> "Bluetooth off"
            names.isNotEmpty() -> names[0]
            else -> "No phone connected"
        }
        pSub.text = when {
            !env.bt.permitted() -> "Bluetooth access needed"
            names.isNotEmpty() -> "Connected"
            else -> "Pair a phone in Bluetooth settings"
        }
        for ((tv, f) in vRows) tv.text = f()
    }

    override fun onShow() = refresh()
    override fun onTick() = refresh()
    override fun onPermissionsChanged() = refresh()
}