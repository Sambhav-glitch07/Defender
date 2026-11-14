package `in`.suzuki.defender

import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Honest vehicle screen: only shows data the Android head unit can really provide.
 * No OEM/CAN data is read, so fuel, range and trip computer are marked as not connected.
 */
class VehicleScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private val live = ArrayList<Pair<TextView, () -> String>>()
    private lateinit var devices: TextView

    override val root: View by lazy { build() }

    private fun card(caption: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(Ui.px(30), Ui.px(30), Ui.px(30), Ui.px(24))
        c.addView(Ui.caps(ctx, caption, 11f, C.ACCENT))
        val sp = View(ctx)
        c.addView(sp, Ui.mm(MATCH, Ui.px(10)))
        return c
    }

    private fun kv(parent: LinearLayout, label: String, value: () -> String) {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(0, Ui.px(12), 0, Ui.px(12))
        r.addView(Ui.text(ctx, label, 11f, C.DIM, Ui.MED, 0.1f))
        val v = Ui.text(ctx, value(), 19f, C.TEXT, Ui.LIGHT)
        v.setPadding(0, Ui.px(5), 0, 0)
        v.maxLines = 1
        v.ellipsize = TextUtils.TruncateAt.END
        r.addView(v)
        parent.addView(r, Ui.mm(MATCH, WRAP))
        parent.addView(Ui.hline(ctx))
        live.add(Pair(v, value))
    }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val dm = ctx.resources.displayMetrics

        val c1 = card("Vehicle")
        c1.setBackgroundColor(C.INNER)
        kv(c1, "EDITION") { "Suzuki Defender Edition" }
        kv(c1, "HEAD UNIT") { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}" }
        kv(c1, "ANDROID") { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
        kv(c1, "DISPLAY") { "${dm.widthPixels} × ${dm.heightPixels}" }
        kv(c1, "SOFTWARE") { "Suzuki OS 1.0.0" }

        val c2 = card("Status")
        kv(c2, "BLUETOOTH") { if (!env.bt.available()) "Not fitted" else if (env.bt.isOn()) "On" else "Off" }
        kv(c2, "WI-FI") { if (env.sys.wifiConnected()) "Connected" else "Not connected" }
        kv(c2, "AUDIO") {
            if (env.audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) "Muted"
            else "Volume ${env.volumePct()}%" + if (env.audio.isMusicActive) " · playing" else ""
        }
        kv(c2, "HEAD-UNIT BATTERY") { env.sys.battery()?.let { "$it%" } ?: "Not reported" }
        kv(c2, "STORAGE") { env.sys.freeStorage() }

        val c3 = card("Trip & fuel")
        c3.setBackgroundColor(C.INNER)
        kv(c3, "GPS SPEED") { env.loc.speedKmh()?.let { "${it.toInt()} km/h" } ?: (if (env.loc.granted()) "—" else "Location access off") }
        kv(c3, "DISTANCE THIS SESSION (GPS)") { String.format(Locale.US, "%.1f km", env.loc.sessionMeters / 1000f) }
        kv(c3, "FUEL LEVEL") { "Not connected" }
        kv(c3, "RANGE") { "Not connected" }
        val note = Ui.text(ctx, "Fuel, range and trip computer need a vehicle interface (CAN / OEM bus). None is connected, so no values are shown.", 11f, C.DIM)
        note.setPadding(0, Ui.px(14), 0, Ui.px(18))
        c3.addView(note)
        c3.addView(Ui.caps(ctx, "Connected devices", 10f, C.ACCENT))
        devices = Ui.text(ctx, "", 15f, C.TEXT, Ui.LIGHT)
        devices.setPadding(0, Ui.px(8), 0, 0)
        c3.addView(devices)

        row.addView(c1, Ui.mm(0, MATCH, 1f))
        row.addView(vsep())
        row.addView(c2, Ui.mm(0, MATCH, 1f))
        row.addView(vsep())
        row.addView(c3, Ui.mm(0, MATCH, 1f))
        refresh()
        return row
    }

    private fun vsep(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.LINE)
        v.layoutParams = LinearLayout.LayoutParams(1, MATCH)
        return v
    }

    private fun refresh() {
        for ((tv, f) in live) {
            val s = f()
            if (tv.text.toString() != s) tv.text = s
        }
        val d = env.bt.connectedNames()
        devices.text = if (d.isEmpty()) "No Bluetooth devices connected" else d.joinToString("\n")
    }

    override fun onShow() = refresh()
    override fun onTick() = refresh()
}