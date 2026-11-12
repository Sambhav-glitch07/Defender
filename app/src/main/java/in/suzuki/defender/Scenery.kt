package `in`.suzuki.defender

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import java.util.Random

/**
 * Full-bleed "photo-like" scenery for Pivi-Pro-style panels, drawn procedurally with Canvas
 * (sky gradient, glow, layered ridges/dunes, optional road, optional SUV silhouette, dark scrim).
 * Static and cached: no image files, no animation, negligible memory.
 */
class SceneryView(
    ctx: Context,
    private val kind: Int,
    private val car: Boolean = false,
    private val seed: Long = 1L
) : View(ctx) {

    companion object {
        const val ROAD = 0
        const val DESERT = 1
        const val HIGHLAND = 2
    }

    // palette: sky top, sky horizon, glow, far, mid, near
    private val pal: IntArray = when (kind) {
        ROAD -> intArrayOf(c("#1E2330"), c("#7A5C4A"), c("#E0A878"), c("#3A3D48"), c("#262932"), c("#15171C"))
        DESERT -> intArrayOf(c("#3B3F4A"), c("#B08968"), c("#F0C090"), c("#7A5C48"), c("#4F3C30"), c("#2B211B"))
        else -> intArrayOf(c("#131A24"), c("#3E5568"), c("#9FB8C8"), c("#28384A"), c("#1A2634"), c("#0F1620"))
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val far = Path()
    private val mid = Path()
    private val near = Path()
    private val road = Path()
    private val body = Path()
    private val glass = Path()
    private var horizon = 0f
    private var sky: Shader? = null
    private var glow: Shader? = null
    private var scrim: Shader? = null

    private fun c(s: String) = Color.parseColor(s)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        horizon = hf * 0.5f
        sky = LinearGradient(0f, 0f, 0f, horizon, pal[0], pal[1], Shader.TileMode.CLAMP)
        glow = RadialGradient(
            wf * 0.62f, horizon, wf * 0.75f,
            intArrayOf((pal[2] and 0x00FFFFFF) or 0x99000000.toInt(), pal[2] and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP
        )
        scrim = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(0x73000000, 0x40000000, 0xD90A0B0C.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        val r = Random(seed * 31L + kind)
        if (kind == DESERT) {
            dunes(far, wf, hf, horizon + hf * 0.02f, hf * 0.05f, r)
            dunes(mid, wf, hf, horizon + hf * 0.12f, hf * 0.08f, r)
            dunes(near, wf, hf, horizon + hf * 0.26f, hf * 0.1f, r)
        } else {
            ridge(far, wf, hf, horizon - hf * 0.02f, hf * 0.07f, r, 14)
            ridge(mid, wf, hf, horizon + hf * 0.06f, hf * 0.06f, r, 10)
            ridge(near, wf, hf, horizon + hf * 0.2f, hf * 0.05f, r, 7)
        }
        road.reset()
        if (kind == ROAD) {
            road.moveTo(-wf * 0.05f, hf)
            road.quadTo(wf * 0.5f, hf * 0.78f, wf * 0.62f, horizon + hf * 0.1f)
            road.lineTo(wf * 0.66f, horizon + hf * 0.1f)
            road.quadTo(wf * 0.64f, hf * 0.82f, wf * 0.52f, hf)
            road.close()
        }
        buildCar()
    }

    private fun ridge(path: Path, w: Float, h: Float, base: Float, amp: Float, r: Random, steps: Int) {
        path.reset()
        path.moveTo(0f, h)
        path.lineTo(0f, base + (r.nextFloat() - 0.5f) * amp * 2f)
        for (i in 1..steps) path.lineTo(w * i / steps, base + (r.nextFloat() - 0.5f) * amp * 2f)
        path.lineTo(w, h)
        path.close()
    }

    private fun dunes(path: Path, w: Float, h: Float, base: Float, amp: Float, r: Random) {
        path.reset()
        path.moveTo(0f, h)
        path.lineTo(0f, base)
        val steps = 4
        for (i in 1..steps) {
            val cx = w * (i - 0.5f) / steps
            path.quadTo(cx, base - amp * (0.4f + r.nextFloat()), w * i / steps, base + (r.nextFloat() - 0.5f) * amp)
        }
        path.lineTo(w, h)
        path.close()
    }

    /** Boxy, upright 4x4 silhouette in a 100 x 40 box (original shape). */
    private fun buildCar() {
        body.reset(); glass.reset()
        body.moveTo(4f, 30f); body.lineTo(4f, 18f); body.lineTo(16f, 16f); body.lineTo(21f, 6f)
        body.lineTo(75f, 6f); body.lineTo(79f, 16f); body.lineTo(96f, 18f); body.lineTo(96f, 30f); body.close()
        glass.moveTo(24f, 8.5f); glass.lineTo(72f, 8.5f); glass.lineTo(75f, 15.5f); glass.lineTo(20f, 15.5f); glass.close()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = sky
        canvas.drawRect(0f, 0f, w, horizon + 2f, p)
        p.shader = glow
        canvas.drawRect(0f, 0f, w, horizon + 2f, p)
        p.shader = null
        p.color = pal[5]
        canvas.drawRect(0f, horizon, w, h, p)
        p.color = pal[3]; canvas.drawPath(far, p)
        p.color = pal[4]; canvas.drawPath(mid, p)
        p.color = pal[5]; canvas.drawPath(near, p)
        if (kind == ROAD) {
            p.color = Color.parseColor("#101216")
            canvas.drawPath(road, p)
        }
        if (car) {
            val s = w * 0.84f / 100f
            canvas.save()
            canvas.translate(w * 0.08f, h * 0.8f - 40f * s)
            canvas.scale(s, s)
            p.color = Color.parseColor("#0A0B0D")
            canvas.drawPath(body, p)
            p.color = Color.parseColor("#1B2430")
            canvas.drawPath(glass, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.7f
            p.color = (C.ACCENT and 0x00FFFFFF) or 0x90000000.toInt()
            canvas.drawLine(21f, 6f, 75f, 6f, p)
            p.style = Paint.Style.FILL
            for (cx in floatArrayOf(22f, 78f)) {
                p.color = Color.parseColor("#050505"); canvas.drawCircle(cx, 31f, 7.5f, p)
                p.color = Color.parseColor("#22262A"); canvas.drawCircle(cx, 31f, 3.4f, p)
            }
            canvas.restore()
        }
        p.shader = scrim
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }
}