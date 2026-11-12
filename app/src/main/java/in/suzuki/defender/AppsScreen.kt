package `in`.suzuki.defender

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Apps menu: every launchable app on the head unit, in a clean tile grid. */
class AppsScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private val box = LinearLayout(ctx)
    private lateinit var count: TextView
    private var apps: List<AppEntry> = emptyList()
    private var loading = false
    private var loadedAt = 0L

    private class AppEntry(val label: String, val pkg: String, val icon: Drawable?)

    override val root: View by lazy { build() }

    private fun build(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.px(32), Ui.px(22), Ui.px(32), Ui.px(18))
        head.addView(Ui.caps(ctx, "All apps", 11f, C.ACCENT), Ui.mm(0, WRAP, 1f))
        count = Ui.text(ctx, "", 12f, C.DIM)
        head.addView(count)
        col.addView(head, Ui.mm(MATCH, WRAP))
        col.addView(Ui.hline(ctx))

        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.px(26), Ui.px(18), Ui.px(26), Ui.px(18))
        val sv = ScrollView(ctx)
        sv.addView(box)
        col.addView(sv, Ui.mm(MATCH, 0, 1f))
        return col
    }

    private fun load() {
        if (loading) return
        loading = true
        Thread {
            val pm = ctx.packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list: List<ResolveInfo> = try {
                pm.queryIntentActivities(q, 0)
            } catch (e: Exception) {
                emptyList()
            }
            val out = list
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map {
                    val icon: Drawable? = try {
                        it.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                    AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, icon)
                }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            env.main.post {
                apps = out
                loading = false
                loadedAt = SystemClock.elapsedRealtime()
                render()
            }
        }.start()
    }

    private fun render() {
        box.removeAllViews()
        count.text = "${apps.size} apps"
        if (apps.isEmpty()) {
            box.addView(Ui.text(ctx, "No launchable apps found on this unit.", 15f, C.DIM))
            return
        }
        val cols = 6
        var i = 0
        while (i < apps.size) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until cols) {
                val a = apps.getOrNull(i + c)
                if (a == null) {
                    row.addView(View(ctx), Ui.mm(0, Ui.px(124), 1f))
                } else {
                    row.addView(tile(a), tileLp())
                }
            }
            box.addView(row, Ui.mm(MATCH, WRAP))
            i += cols
        }
    }

    private fun tileLp(): LinearLayout.LayoutParams {
        val lp = Ui.mm(0, Ui.px(124), 1f)
        lp.setMargins(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6))
        return lp
    }

    private fun tile(a: AppEntry): View {
        val t = LinearLayout(ctx)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.setPadding(Ui.px(8), Ui.px(10), Ui.px(8), Ui.px(10))
        t.background = Ui.rect(C.GLASS, C.LINE)
        val iv = ImageView(ctx)
        iv.setImageDrawable(a.icon)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        t.addView(iv, Ui.mm(Ui.px(58), Ui.px(58)))
        val lb = Ui.text(ctx, a.label, 12f, C.TEXT, Ui.LIGHT)
        lb.gravity = Gravity.CENTER
        lb.maxLines = 1
        lb.ellipsize = TextUtils.TruncateAt.END
        lb.setPadding(0, Ui.px(10), 0, 0)
        t.addView(lb, Ui.mm(MATCH, WRAP))
        Ui.pressable(t) {
            val i = ctx.packageManager.getLaunchIntentForPackage(a.pkg)
            if (i != null) env.launch(i) else env.toast("Can't open ${a.label}")
        }
        return t
    }

    override fun onShow() {
        // Reload on first open, and refresh if the list is older than 60 s (new installs).
        if (apps.isEmpty() || SystemClock.elapsedRealtime() - loadedAt > 60_000L) load()
        else render()
    }
}