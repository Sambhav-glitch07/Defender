package `in`.suzuki.defender

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Lightweight installed-app drawer for the head unit. */
class AppsScreen(env: Env) : Screen(env) {
    private val ctx = env.activity
    override val root: View by lazy { build() }

    private fun build(): View {
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.px(30), Ui.px(24), Ui.px(30), Ui.px(20))
        }
        outer.addView(Ui.caps(ctx, "Installed applications", 11f, C.ACCENT))
        val subtitle = Ui.text(ctx, "Select an application to launch", 14f, C.DIM, Ui.LIGHT)
        subtitle.setPadding(0, Ui.px(8), 0, Ui.px(20))
        outer.addView(subtitle)

        val scroll = ScrollView(ctx)
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        if (apps.isEmpty()) {
            grid.addView(Ui.text(ctx, "No launchable applications found", 18f, C.DIM, Ui.LIGHT))
        } else {
            apps.chunked(4).forEach { chunk ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { app -> row.addView(appTile(app), LinearLayout.LayoutParams(0, Ui.px(132), 1f)) }
                repeat(4 - chunk.size) { row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f)) }
                grid.addView(row)
            }
        }
        scroll.addView(grid)
        outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun appTile(app: ApplicationInfo): View {
        val pm = ctx.packageManager
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.px(8), Ui.px(10), Ui.px(8), Ui.px(10))
            background = Ui.rect(C.SURFACE, C.LINE, 1f, 8f)
        }
        val icon = ImageView(ctx).apply {
            setImageDrawable(pm.getApplicationIcon(app))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tile.addView(icon, LinearLayout.LayoutParams(Ui.px(42), Ui.px(42)))
        val name = Ui.text(ctx, pm.getApplicationLabel(app), 13f, C.TEXT, Ui.MED)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        name.setPadding(0, Ui.px(9), 0, 0)
        tile.addView(name)
        Ui.pressable(tile) {
            val launch = pm.getLaunchIntentForPackage(app.packageName)
            if (launch != null) env.launch(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return tile
    }
}
