package `in`.suzuki.defender

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random

/**
 * Cinematic scenery for panels and the global wallpaper, drawn once with Canvas:
 * sky + glow + stars/sun, three fractal or rolling ridge layers with atmospheric haze,
 * fog band, optional road and SUV silhouette, then scrim, vignette and optional dim.
 * Static and cached: no image files, no animation.
 */
class SceneryView(
    ctx: Context,
    private val kind: Int,
    private val car: Boolean = false,
    private val seed: Long = 1L,
    private val dim: Int = 0,
    startLive: Boolean = false
) : View(ctx) {

    companion object {
        const val ROAD = 0
        const val DESERT = 1
        const val HIGHLAND = 2
    }

    // palette: sky top, sky horizon, glow, far, mid, near
    private val pal: IntArray = when (kind) {
        ROAD -> intArrayOf(c("#141A29"), c("#8A5A48"), c("#F2B27A"), c("#4A4656"), c("#2C2D3A"), c("#14161C"))
        DESERT -> intArrayOf(c("#2B3040"), c("#B9825C"), c("#F6C48F"), c("#8A604A"), c("#58402F"), c("#2A1F19"))
        else -> intArrayOf(c("#0A101A"), c("#34506A"), c("#8FB4D0"), c("#233649"), c("#172433"), c("#0D141D"))
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layers = Array(3) { Path() }
    private val shaders = arrayOfNulls<Shader>(3)
    private val road = Path()
    private val body = Path()
    private val glass = Path()
    private var stars = FloatArray(0)
    private var horizon = 0f
    private var sky: Shader? = null
    private var glow: Shader? = null
    private var fog: Shader? = null
    private var scrim: Shader? = null
    private var vignette: Shader? = null

    /** Slow ambient drift (parallax layers, drifting glow, twinkling stars) at ~30 fps. */
    var live: Boolean = startLive
        set(v) {
            field = v
            restart()
        }
    private var running = true
    private val frame = object : Runnable {
        override fun run() {
            if (live && running) {
                invalidate()
                postDelayed(this, 33L)
            }
        }
    }

    private fun restart() {
        removeCallbacks(frame)
        if (live && running) post(frame)
        else invalidate()
    }

    fun setRunning(r: Boolean) {
        running = r
        restart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        running = visibility == VISIBLE
        restart()
    }

    private fun sw(t: Float, period: Float, amp: Float): Float =
        if (live) Math.sin((t * 6.2831855f / period).toDouble()).toFloat() * amp else 0f

    private fun c(s: String) = Color.parseColor(s)

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        horizon = hf * 0.5f
        val clear = pal[2] and 0x00FFFFFF
        sky = LinearGradient(0f, 0f, 0f, horizon, pal[0], pal[1], Shader.TileMode.CLAMP)
        glow = RadialGradient(
            wf * 0.62f, horizon, wf * 0.8f,
            intArrayOf(clear or 0x99000000.toInt(), clear),
            null, Shader.TileMode.CLAMP
        )
        val fc = pal[1] and 0x00FFFFFF
        fog = LinearGradient(
            0f, horizon - hf * 0.06f, 0f, horizon + hf * 0.12f,
            intArrayOf(fc, fc or 0x59000000, fc), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        scrim = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(0x66000000, 0x4D000000, 0xE6070809.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        vignette = RadialGradient(
            wf / 2f, hf / 2f, maxOf(wf, hf) * 0.78f,
            intArrayOf(0x00000000, 0x8C000000.toInt()), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP
        )

        val r = Random(seed * 31L + kind)
        val bases: FloatArray
        val amps: FloatArray
        if (kind == DESERT) {
            bases = floatArrayOf(horizon + hf * 0.01f, horizon + hf * 0.1f, horizon + hf * 0.24f)
            amps = floatArrayOf(hf * 0.05f, hf * 0.08f, hf * 0.1f)
            for (i in 0..2) rolling(layers[i], wf, hf, bases[i], amps[i], r)
        } else {
            bases = floatArrayOf(horizon - hf * 0.03f, horizon + hf * 0.07f, horizon + hf * 0.2f)
            amps = floatArrayOf(hf * 0.1f, hf * 0.07f, hf * 0.05f)
            fractal(layers[0], wf, hf, bases[0], amps[0], r, 0.56f)
            fractal(layers[1], wf, hf, bases[1], amps[1], r, 0.5f)
            rolling(layers[2], wf, hf, bases[2], amps[2], r)
        }
        val cols = intArrayOf(pal[3], pal[4], pal[5])
        val haze = floatArrayOf(0.5f, 0.3f, 0.12f)
        for (i in 0..2) {
            shaders[i] = LinearGradient(
                0f, bases[i] - amps[i], 0f, bases[i] + hf * 0.3f,
                mix(cols[i], pal[1], haze[i]), cols[i], Shader.TileMode.CLAMP
            )
        }

        stars = FloatArray(0)
        if (kind == HIGHLAND) {
            stars = FloatArray(40 * 3)
            for (i in 0 until 40) {
                stars[i * 3] = r.nextFloat() * wf
                stars[i * 3 + 1] = r.nextFloat() * horizon * 0.8f
                stars[i * 3 + 2] = 70f + r.nextFloat() * 130f
            }
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

    /** Midpoint-displacement ridge: natural-looking mountains instead of straight triangles. */
    private fun fractal(path: Path, w: Float, h: Float, base: Float, amp: Float, r: Random, rough: Float) {
        val n = 64
        val ys = FloatArray(n + 1)
        ys[0] = base + (r.nextFloat() - 0.5f) * amp
        ys[n] = base + (r.nextFloat() - 0.5f) * amp
        var step = n
        var scale = amp
        while (step > 1) {
            val half = step / 2
            var i = half
            while (i < n) {
                ys[i] = (ys[i - half] + ys[i + half]) / 2f + (r.nextFloat() - 0.5f) * scale
                i += step
            }
            step = half
            scale *= rough
        }
        path.reset()
        path.moveTo(-w * 0.06f, h)
        for (i in 0..n) path.lineTo(w * (-0.06f + 1.12f * i / n), ys[i])
        path.lineTo(w * 1.06f, h)
        path.close()
    }

    private fun rolling(path: Path, w: Float, h: Float, base: Float, amp: Float, r: Random) {
        path.reset()
        path.moveTo(-w * 0.06f, h)
        path.lineTo(-w * 0.06f, base)
        val steps = 5
        for (i in 1..steps) {
            val cx = w * (-0.06f + 1.12f * (i - 0.5f) / steps)
            path.quadTo(cx, base - amp * (0.3f + r.nextFloat()), w * (-0.06f + 1.12f * i / steps), base + (r.nextFloat() - 0.5f) * amp * 0.6f)
        }
        path.lineTo(w * 1.06f, h)
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
        val t = if (live) (SystemClock.uptimeMillis() % 600000L) / 1000f else 0f

        canvas.save()
        canvas.translate(sw(t, 50f, w * 0.08f), 0f)
        p.shader = glow
        canvas.drawRect(-w * 0.2f, 0f, w * 1.2f, horizon + 2f, p)
        p.shader = null
        if (kind != HIGHLAND) {
            p.color = (pal[2] and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawCircle(w * 0.62f, horizon - h * 0.015f, w * 0.055f, p)
        }
        canvas.restore()

        p.color = pal[5]
        canvas.drawRect(0f, horizon, w, h, p)

        for (i in 0 until stars.size / 3) {
            val tw = if (live) 0.65f + 0.35f * Math.sin((t * 1.3f + i).toDouble()).toFloat() else 1f
            p.color = Color.argb((stars[i * 3 + 2] * tw).toInt(), 255, 255, 255)
            canvas.drawCircle(stars[i * 3], stars[i * 3 + 1], Ui.px(0.9f).toFloat(), p)
        }

        canvas.save(); canvas.translate(sw(t, 40f, w * 0.012f), 0f)
        p.shader = shaders[0]; canvas.drawPath(layers[0], p)
        canvas.restore()
        canvas.save(); canvas.translate(sw(t, 28f, w * 0.02f), 0f)
        p.shader = shaders[1]; canvas.drawPath(layers[1], p)
        canvas.restore()
        p.shader = fog; canvas.drawRect(0f, horizon - h * 0.06f, w, horizon + h * 0.12f, p)
        canvas.save(); canvas.translate(sw(t, 20f, w * 0.03f), 0f)
        p.shader = shaders[2]; canvas.drawPath(layers[2], p)
        canvas.restore()
        p.shader = null

        if (kind == ROAD) {
            p.color = Color.parseColor("#0E1014")
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
        p.shader = vignette
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        if (dim > 0) {
            p.color = Color.argb(dim, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, p)
        }
    }
}