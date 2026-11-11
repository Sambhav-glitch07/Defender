package `in`.suzuki.defender

import android.content.Context
import android.content.Intent
import android.location.Location
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class NavScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private lateinit var search: EditText
    private lateinit var locText: TextView
    private lateinit var destText: TextView
    private lateinit var routeText: TextView
    private lateinit var recentBox: LinearLayout
    private var lastRecents = ""

    override val root: View by lazy { build() }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL

        // ---- map area
        val map = FrameLayout(ctx)
        map.addView(MapPanelView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(Ui.px(18), 0, Ui.px(8), 0)
        bar.background = Ui.rect(0xE6141618.toInt(), C.LINE)
        bar.addView(Ui.icon(ctx, R.drawable.ic_search, 22f, C.ACCENT))
        search = EditText(ctx)
        search.hint = "Search destination"
        search.setHintTextColor(C.DIM)
        search.setTextColor(C.TEXT)
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(18).toFloat())
        search.typeface = Ui.LIGHT
        search.background = null
        search.maxLines = 1
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        search.setPadding(Ui.px(14), Ui.px(18), Ui.px(14), Ui.px(18))
        search.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                go(search.text.toString())
                true
            } else false
        }
        bar.addView(search, Ui.mm(0, WRAP, 1f))
        bar.addView(Ui.actionIcon(ctx, R.drawable.ic_close, 18f, C.DIM) { search.setText("") })
        val barLp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP)
        barLp.setMargins(Ui.px(24), Ui.px(24), Ui.px(24), 0)
        map.addView(bar, barLp)

        val chip = LinearLayout(ctx)
        chip.orientation = LinearLayout.VERTICAL
        chip.setPadding(Ui.px(18), Ui.px(14), Ui.px(24), Ui.px(14))
        chip.background = Ui.rect(0xE6141618.toInt(), C.LINE)
        chip.addView(Ui.caps(ctx, "Current location", 10f, C.ACCENT))
        locText = Ui.text(ctx, "", 18f, C.TEXT, Ui.LIGHT)
        locText.setPadding(0, Ui.px(6), 0, 0)
        chip.addView(locText)
        val chipLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START)
        chipLp.setMargins(Ui.px(24), 0, 0, Ui.px(24))
        map.addView(chip, chipLp)

        val note = Ui.text(ctx, "Map preview · turn-by-turn guidance opens in your navigation app", 10f, C.DIM)
        val noteLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        noteLp.setMargins(0, 0, Ui.px(24), Ui.px(28))
        map.addView(note, noteLp)

        // ---- route column
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(C.SURFACE)
        col.setPadding(Ui.px(28), Ui.px(28), Ui.px(28), Ui.px(24))
        col.addView(Ui.caps(ctx, "Route", 11f, C.ACCENT))
        destText = Ui.text(ctx, "", 26f, C.TEXT, Ui.LIGHT)
        destText.maxLines = 2
        destText.ellipsize = TextUtils.TruncateAt.END
        destText.setPadding(0, Ui.px(14), 0, 0)
        col.addView(destText)
        routeText = Ui.text(ctx, "", 13f, C.DIM)
        routeText.setPadding(0, Ui.px(10), 0, Ui.px(22))
        col.addView(routeText)
        val startBtn = Ui.button(ctx, "Start navigation", true) { go(search.text.toString().ifBlank { env.prefs.getString("dest", "") ?: "" }) }
        col.addView(startBtn, Ui.mm(MATCH, WRAP))
        val open = Ui.button(ctx, "Open maps app") {
            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))
            env.launch(i)
        }
        val openLp = Ui.mm(MATCH, WRAP)
        openLp.topMargin = Ui.px(10)
        col.addView(open, openLp)

        val rc = Ui.caps(ctx, "Recent destinations", 10f, C.DIM)
        rc.setPadding(0, Ui.px(28), 0, Ui.px(8))
        col.addView(rc)
        recentBox = LinearLayout(ctx)
        recentBox.orientation = LinearLayout.VERTICAL
        col.addView(recentBox, Ui.mm(MATCH, WRAP))

        val sp = View(ctx)
        col.addView(sp, Ui.mm(MATCH, 0, 1f))
        val perm = Ui.button(ctx, "Location settings") {
            if (!env.loc.granted()) env.request(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)) {}
            else env.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        col.addView(perm, Ui.mm(MATCH, WRAP))

        row.addView(map, Ui.mm(0, MATCH, 2.1f))
        row.addView(col, Ui.mm(0, MATCH, 1f))
        refresh()
        return row
    }

    private fun go(q: String) {
        if (q.isBlank()) {
            env.toast("Enter a destination")
            return
        }
        hideKeyboard()
        env.startNavigation(q)
        refresh()
    }

    private fun hideKeyboard() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun refresh() {
        locText.text = env.loc.describe()
        val d = env.prefs.getString("dest", null)
        destText.text = d ?: "No destination"
        val l = env.loc.last
        val dl: Location? = env.destLoc
        routeText.text = when {
            d == null -> "Search a destination to begin."
            l != null && dl != null -> {
                val r = FloatArray(1)
                Location.distanceBetween(l.latitude, l.longitude, dl.latitude, dl.longitude, r)
                String.format(Locale.US, "≈ %.1f km straight-line · route and ETA shown in your navigation app", r[0] / 1000f)
            }
            else -> "Route and ETA are provided by your navigation app."
        }
        val rs = env.recents()
        val key = rs.joinToString("|")
        if (key != lastRecents) {
            lastRecents = key
            recentBox.removeAllViews()
            if (rs.isEmpty()) recentBox.addView(Ui.text(ctx, "None yet", 14f, C.DIM))
            for (r in rs.take(4)) {
                val t = Ui.text(ctx, r, 16f, C.TEXT, Ui.LIGHT)
                t.maxLines = 1
                t.ellipsize = TextUtils.TruncateAt.END
                t.setPadding(0, Ui.px(10), 0, Ui.px(10))
                Ui.pressable(t, false) { search.setText(r); go(r) }
                recentBox.addView(t, Ui.mm(MATCH, WRAP))
            }
        }
    }

    override fun onShow() {
        refresh()
        if (env.focusSearch) {
            env.focusSearch = false
            search.requestFocus()
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onHide() = hideKeyboard()
    override fun onTick() = refresh()
    override fun onPermissionsChanged() = refresh()
}
