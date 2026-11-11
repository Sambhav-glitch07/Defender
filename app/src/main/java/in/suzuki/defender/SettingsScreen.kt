package `in`.suzuki.defender

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private val pane = FrameLayout(ctx)
    private val cats = listOf("Display", "Theme", "Connectivity", "Sound", "About Suzuki OS")
    private val catViews = ArrayList<TextView>()
    private var sel = 0

    override val root: View by lazy { build() }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL

        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.setBackgroundColor(C.SURFACE)
        list.setPadding(0, Ui.px(20), 0, 0)
        cats.forEachIndexed { i, n ->
            val t = Ui.text(ctx, n.uppercase(), 13f, C.DIM, Ui.MED, 0.16f)
            t.setPadding(Ui.px(32), Ui.px(24), Ui.px(16), Ui.px(24))
            Ui.pressable(t, false) { select(i) }
            catViews.add(t)
            list.addView(t, Ui.mm(MATCH, WRAP))
            list.addView(Ui.hline(ctx))
        }
        row.addView(list, Ui.mm(0, MATCH, 1f))
        row.addView(vsep())
        row.addView(pane, Ui.mm(0, MATCH, 2.6f))
        select(0)
        return row
    }

    private fun vsep(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.LINE)
        v.layoutParams = LinearLayout.LayoutParams(1, MATCH)
        return v
    }

    private fun select(i: Int) {
        sel = i
        for (k in catViews.indices) {
            catViews[k].setTextColor(if (k == i) C.ACCENT else C.DIM)
            catViews[k].setBackgroundColor(if (k == i) C.SURFACE2 else 0)
        }
        pane.removeAllViews()
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.px(40), Ui.px(32), Ui.px(40), Ui.px(24))
        when (i) {
            0 -> display(col)
            1 -> theme(col)
            2 -> connectivity(col)
            3 -> sound(col)
            else -> about(col)
        }
        val sv = ScrollView(ctx)
        sv.addView(col)
        pane.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun header(col: LinearLayout, s: String) {
        col.addView(Ui.text(ctx, s, 28f, C.TEXT, Ui.LIGHT))
        val l = Ui.hline(ctx)
        val lp = Ui.mm(MATCH, 1)
        lp.topMargin = Ui.px(18)
        l.layoutParams = lp
        col.addView(l)
    }

    /** One settings row: title + helper text on the left, control on the right. */
    private fun item(col: LinearLayout, title: String, sub: String, control: View) {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, Ui.px(18), 0, Ui.px(18))
        val tx = LinearLayout(ctx)
        tx.orientation = LinearLayout.VERTICAL
        tx.addView(Ui.text(ctx, title, 18f, C.TEXT, Ui.LIGHT))
        if (sub.isNotEmpty()) {
            val s = Ui.text(ctx, sub, 12f, C.DIM)
            s.setPadding(0, Ui.px(4), 0, 0)
            tx.addView(s)
        }
        r.addView(tx, Ui.mm(0, WRAP, 1f))
        r.addView(control)
        col.addView(r, Ui.mm(MATCH, WRAP))
        col.addView(Ui.hline(ctx))
    }

    private fun display(col: LinearLayout) {
        header(col, "Display")
        val cur = env.prefs.getFloat("bright", -1f)
        lateinit var seek: SeekBar
        seek = Ui.seek(ctx, 90, if (cur < 0f) 90 else ((cur * 100).toInt() - 10).coerceIn(0, 90), { p, _ ->
            env.activity.setBrightness((p + 10) / 100f)
        }, {
            env.prefs.edit().putFloat("bright", (seek.progress + 10) / 100f).apply()
        })
        item(col, "Screen brightness", "Applies to this app only. Your head unit may also control brightness.", seek.also { it.layoutParams = Ui.mm(Ui.px(280), WRAP) })
        item(col, "Keep screen on", "Prevents the display from sleeping while Suzuki OS is open.",
            Ui.toggle(ctx, env.prefs.getBoolean("keepon", true)) {
                env.prefs.edit().putBoolean("keepon", it).apply()
                env.activity.setKeepOn(it)
            })
        item(col, "Screen transitions", "Fade and slide between screens. Turn off on slower hardware.",
            Ui.toggle(ctx, Ui.animations) {
                Ui.animations = it
                env.prefs.edit().putBoolean("anim", it).apply()
            })
    }

    private fun theme(col: LinearLayout) {
        header(col, "Theme")
        val cur = env.prefs.getString("theme", null) ?: Themes.options[0].first
        val sub = Ui.text(ctx, "Accent colour. The interface stays in graphite; only highlights change.", 12f, C.DIM)
        sub.setPadding(0, Ui.px(16), 0, Ui.px(16))
        col.addView(sub)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        for ((name, hex) in Themes.options) {
            val tile = LinearLayout(ctx)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(Ui.px(10), Ui.px(18), Ui.px(10), Ui.px(18))
            tile.background = Ui.rect(C.SURFACE, if (name == cur) Color.parseColor(hex) else C.LINE, if (name == cur) 2f else 1f)
            val sw = View(ctx)
            sw.setBackgroundColor(Color.parseColor(hex))
            tile.addView(sw, Ui.mm(Ui.px(54), Ui.px(26)))
            val lb = Ui.text(ctx, name.uppercase(), 11f, C.TEXT, Ui.MED, 0.14f)
            lb.setPadding(0, Ui.px(12), 0, 0)
            tile.addView(lb)
            Ui.pressable(tile) {
                if (name != cur) {
                    env.prefs.edit().putString("theme", name).apply()
                    env.activity.recreate()
                }
            }
            val lp = Ui.mm(0, WRAP, 1f)
            lp.setMargins(0, 0, Ui.px(12), 0)
            row.addView(tile, lp)
        }
        col.addView(row, Ui.mm(MATCH, WRAP))
    }

    private fun connectivity(col: LinearLayout) {
        header(col, "Connectivity")
        item(col, "Bluetooth",
            if (!env.bt.available()) "Not fitted" else if (env.bt.isOn()) "On" else "Off",
            Ui.button(ctx, "Open") { env.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        item(col, "Wi-Fi", if (env.sys.wifiConnected()) "Connected" else "Not connected",
            Ui.button(ctx, "Open") { env.launch(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        item(col, "Location", if (env.loc.granted()) "Permission granted" else "Permission not granted",
            Ui.button(ctx, "Open") { env.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
        item(col, "Now-playing access", if (env.media.hasAccess()) "Enabled" else "Disabled",
            Ui.button(ctx, "Open") { env.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
    }

    private fun sound(col: LinearLayout) {
        header(col, "Sound")
        val max = env.audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val seek = Ui.seek(ctx, max, env.audio.getStreamVolume(AudioManager.STREAM_MUSIC), { p, user ->
            if (user) {
                env.audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
                env.activity.updateVolume()
            }
        })
        item(col, "Media volume", "", seek.also { it.layoutParams = Ui.mm(Ui.px(280), WRAP) })
        item(col, "Mute", "Silences media output.",
            Ui.toggle(ctx, env.audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                val m = env.audio.isStreamMute(AudioManager.STREAM_MUSIC)
                if (m != it) env.audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                env.activity.updateVolume()
            })
        item(col, "System sound settings", "Equaliser and output options provided by your head unit.",
            Ui.button(ctx, "Open") { env.launch(Intent(Settings.ACTION_SOUND_SETTINGS)) })
    }

    private fun about(col: LinearLayout) {
        header(col, "About Suzuki OS")
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(0, Ui.px(24), 0, Ui.px(10))
        top.addView(Ui.icon(ctx, R.drawable.ic_suzuki_s, 56f, C.ACCENT))
        val tx = LinearLayout(ctx)
        tx.orientation = LinearLayout.VERTICAL
        tx.setPadding(Ui.px(20), 0, 0, 0)
        tx.addView(Ui.text(ctx, "SUZUKI OS", 24f, C.TEXT, Ui.MED, 0.3f))
        val e = Ui.text(ctx, "Defender Edition  ·  Version 1.0.0", 13f, C.DIM)
        e.setPadding(0, Ui.px(6), 0, 0)
        tx.addView(e)
        top.addView(tx)
        col.addView(top)
        val d = Ui.text(
            ctx,
            "A lightweight native Android infotainment launcher.\n\n" +
                "Vehicle data is limited to what the head unit exposes. No vehicle bus is accessed, " +
                "and no values are simulated.\n\n" +
                "Interface concept only. Not affiliated with or endorsed by Suzuki Motor Corporation " +
                "or Land Rover. The S mark in this build is a stand-in; replace it with a licensed asset before distribution.",
            13f, C.DIM
        )
        d.setLineSpacing(0f, 1.25f)
        col.addView(d)
    }

    override fun onShow() = select(sel)
}
