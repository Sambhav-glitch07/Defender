package `in`.suzuki.defender

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Defender-inspired palette: graphite surfaces, warm white type, restrained champagne accent. */
object C {
    val BG = Color.parseColor("#0A0B0C")
    val SURFACE = Color.parseColor("#141618")
    val SURFACE2 = Color.parseColor("#1D2023")
    val GLASS = Color.parseColor("#B3101214")
    val GLASS2 = Color.parseColor("#B31D2023")
    val INNER = Color.parseColor("#0FFFFFFF")
    val INNER2 = Color.parseColor("#22FFFFFF")
    val LINE = Color.parseColor("#2C3034")
    val TEXT = Color.parseColor("#EDE8DF")
    val DIM = Color.parseColor("#8E8C86")
    val DANGER = Color.parseColor("#B8574C")
    var ACCENT = Color.parseColor("#C9B68E")
}

object Themes {
    val options = listOf(
        "Champagne" to "#C9B68E",
        "Steel" to "#9FB3C4",
        "Bronze" to "#B08650",
        "Pearl" to "#D9D6D0"
    )

    fun hex(name: String?): String = options.firstOrNull { it.first == name }?.second ?: options[0].second
}

/** Frosted-glass surface: translucent dark base, soft sheen, light-catching edge and top highlight. */
class GlassDrawable(private val radius: Float, private val base: Boolean = true) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        fill.style = Paint.Style.FILL
        fill.color = 0x730C0E10
        border.style = Paint.Style.STROKE
        border.strokeWidth = Ui.px(1.2f).coerceAtLeast(1).toFloat()
        spec.style = Paint.Style.STROKE
        spec.strokeWidth = Ui.px(1f).coerceAtLeast(1).toFloat()
        spec.color = 0x55FFFFFF
    }

    override fun onBoundsChange(b: Rect) {
        r.set(b)
        r.inset(0.75f, 0.75f)
        val t = b.top.toFloat()
        val bt = b.bottom.toFloat()
        sheen.shader = LinearGradient(0f, t, 0f, bt,
            intArrayOf(0x2BFFFFFF, 0x0AFFFFFF, 0x14FFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        border.shader = LinearGradient(0f, t, 0f, bt,
            intArrayOf(0x66FFFFFF, 0x1AFFFFFF, 0x33FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (base) c.drawRoundRect(r, radius, radius, fill)
        c.drawRoundRect(r, radius, radius, sheen)
        c.drawRoundRect(r, radius, radius, border)
        c.drawLine(r.left + radius, r.top + 1f, r.right - radius, r.top + 1f, spec)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class Panel(val frame: FrameLayout, val body: LinearLayout, val actions: LinearLayout)

object Ui {
    private var factor = 1f
    private var density = 1f
    var animations = true

    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val REG: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Scales every size relative to a 960x540dp reference so the UI adapts to any head-unit resolution. */
    fun init(ctx: Context) {
        val dm = ctx.resources.displayMetrics
        density = dm.density
        val wDp = dm.widthPixels / dm.density
        val hDp = dm.heightPixels / dm.density
        factor = minOf(wDp / 960f, hDp / 540f).coerceIn(0.6f, 1.6f)
    }

    fun px(v: Float): Int = (v * factor * density).toInt()
    fun px(v: Int): Int = px(v.toFloat())
    fun slide(): Float = 36f * factor * density

    fun text(
        ctx: Context, s: CharSequence, size: Float, color: Int = C.TEXT,
        face: Typeface = REG, spacing: Float = 0f
    ): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * factor * density)
        t.setTextColor(color)
        t.typeface = face
        t.letterSpacing = spacing
        t.includeFontPadding = false
        return t
    }

    fun caps(ctx: Context, s: String, size: Float = 11f, color: Int = C.DIM): TextView =
        text(ctx, s.uppercase(), size, color, MED, 0.18f)

    fun icon(ctx: Context, res: Int, size: Float, tint: Int = C.TEXT): ImageView {
        val v = ImageView(ctx)
        v.setImageResource(res)
        v.setColorFilter(tint)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.layoutParams = LinearLayout.LayoutParams(px(size), px(size))
        return v
    }

    fun rect(fill: Int, stroke: Int = 0, strokeW: Float = 1f, radius: Float = 0f): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        if (stroke != 0) g.setStroke(px(strokeW).coerceAtLeast(1), stroke)
        g.cornerRadius = px(radius).toFloat()
        return g
    }

    fun hline(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(C.LINE)
        v.layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        return v
    }

    fun glass(radiusDp: Float, base: Boolean = true): GlassDrawable = GlassDrawable(px(radiusDp).toFloat(), base)

    fun roundClip(v: View, radiusDp: Float) {
        val r = px(radiusDp).toFloat()
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    /** Staggered "rise + settle" entrance (expo-out). Cheap: only view properties, no layout. */
    fun enter(views: List<View>, baseDelay: Long = 0L, step: Long = 55L) {
        if (!animations) return
        val ip = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = px(28f).toFloat()
            v.scaleX = 0.95f
            v.scaleY = 0.95f
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(baseDelay + i * step).setDuration(520L).setInterpolator(ip).start()
        }
    }

    /** Spring-style touch feedback: quick press-in, overshoot release. */
    fun pressable(v: View, scale: Boolean = true, onClick: () -> Unit) {
        v.isClickable = true
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (scale) view.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.8f).setDuration(90L)
                        .setInterpolator(DecelerateInterpolator()).start()
                    else view.animate().alpha(0.88f).setDuration(90L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260L)
                        .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
        v.setOnClickListener { onClick() }
    }

    fun actionIcon(ctx: Context, res: Int, size: Float = 24f, tint: Int = C.TEXT, onClick: () -> Unit): ImageView {
        val v = icon(ctx, res, size, tint)
        v.setPadding(px(14), px(14), px(14), px(14))
        v.layoutParams = LinearLayout.LayoutParams(px(size + 28f), px(size + 28f))
        pressable(v, true, onClick)
        return v
    }

    fun button(
        ctx: Context, label: String, primary: Boolean = false, danger: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val fg = if (primary) C.BG else if (danger) C.DANGER else C.TEXT
        val t = caps(ctx, label, 12f, fg)
        t.gravity = Gravity.CENTER
        t.setPadding(px(22), px(15), px(22), px(15))
        t.background = if (primary) rect(C.ACCENT, C.ACCENT)
        else rect(0, if (danger) C.DANGER else C.LINE)
        pressable(t, true, onClick)
        return t
    }

    fun toggle(ctx: Context, on: Boolean, onChange: (Boolean) -> Unit): TextView {
        val t = text(ctx, "", 12f, C.TEXT, MED, 0.15f)
        t.gravity = Gravity.CENTER
        t.setPadding(px(18), px(9), px(18), px(9))
        var state = on
        fun render() {
            t.text = if (state) "ON" else "OFF"
            t.setTextColor(if (state) C.BG else C.DIM)
            t.background = rect(if (state) C.ACCENT else 0, if (state) C.ACCENT else C.LINE)
        }
        render()
        pressable(t, true) {
            state = !state
            render()
            onChange(state)
        }
        return t
    }

    fun seek(
        ctx: Context, max: Int, value: Int,
        onChange: (Int, Boolean) -> Unit, onStop: () -> Unit = {}
    ): SeekBar {
        val s = SeekBar(ctx)
        s.max = max
        s.progress = value
        s.progressTintList = ColorStateList.valueOf(C.ACCENT)
        s.thumbTintList = ColorStateList.valueOf(C.ACCENT)
        s.progressBackgroundTintList = ColorStateList.valueOf(C.LINE)
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p, fromUser)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) = onStop()
        })
        return s
    }

    /** Tall Pivi-Pro-style panel: caption on top, content centred, action row at the bottom. */
    fun panel(ctx: Context, caption: String, topColor: Int): Panel {
        val frame = FrameLayout(ctx)
        frame.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, C.BG))
        val cap = caps(ctx, caption, 11f, C.ACCENT)
        val capLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        capLp.topMargin = px(22)
        frame.addView(cap, capLp)

        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.setPadding(px(22), 0, px(22), 0)
        frame.addView(body, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))

        val actions = LinearLayout(ctx)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val aLp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
        aLp.bottomMargin = px(26)
        frame.addView(actions, aLp)
        return Panel(frame, body, actions)
    }

    fun mm(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
}

/** Thin 3dp progress line (media progress). */
class ThinBar(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) {
            val n = v.coerceIn(0f, 1f)
            if (n != field) {
                field = n
                invalidate()
            }
        }
    private val p = Paint()
    override fun onDraw(canvas: Canvas) {
        p.color = C.LINE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.color = C.ACCENT
        canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), p)
    }
}

/** Static, procedurally drawn map backdrop. Real routing is delegated to the installed navigation app. */
class MapPanelView(ctx: Context) : View(ctx) {
    private val minor = Path()
    private val major = Path()
    private val water = Path()
    private val pMinor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pMajor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pMark = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        pMinor.style = Paint.Style.STROKE
        pMinor.color = Color.parseColor("#1C1F22")
        pMinor.strokeWidth = Ui.px(1.5f).toFloat()
        pMajor.style = Paint.Style.STROKE
        pMajor.color = Color.parseColor("#2B2F33")
        pMajor.strokeWidth = Ui.px(5f).toFloat()
        pMajor.strokeCap = Paint.Cap.ROUND
        pFill.style = Paint.Style.FILL
        pFill.color = Color.parseColor("#0E1417")
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = java.util.Random(11)
        val wf = w.toFloat()
        val hf = h.toFloat()
        minor.reset(); major.reset(); water.reset()
        for (i in 0 until 24) {
            val y0 = r.nextFloat() * hf
            val y1 = r.nextFloat() * hf
            minor.moveTo(0f, y0)
            minor.quadTo(wf * (0.3f + r.nextFloat() * 0.4f), (y0 + y1) / 2f + (r.nextFloat() - 0.5f) * hf * 0.4f, wf, y1)
        }
        for (i in 0 until 20) {
            val x0 = r.nextFloat() * wf
            val x1 = r.nextFloat() * wf
            minor.moveTo(x0, 0f)
            minor.quadTo((x0 + x1) / 2f + (r.nextFloat() - 0.5f) * wf * 0.4f, hf * (0.3f + r.nextFloat() * 0.4f), x1, hf)
        }
        major.moveTo(0f, hf * 0.72f)
        major.cubicTo(wf * 0.3f, hf * 0.92f, wf * 0.6f, hf * 0.2f, wf, hf * 0.34f)
        major.moveTo(wf * 0.18f, 0f)
        major.cubicTo(wf * 0.4f, hf * 0.4f, wf * 0.3f, hf * 0.7f, wf * 0.5f, hf)
        water.moveTo(wf * 0.66f, 0f)
        water.cubicTo(wf * 0.54f, hf * 0.3f, wf * 0.84f, hf * 0.6f, wf * 0.72f, hf)
        water.lineTo(wf * 0.88f, hf)
        water.cubicTo(wf * 0.98f, hf * 0.6f, wf * 0.72f, hf * 0.3f, wf * 0.82f, 0f)
        water.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#101214"))
        canvas.drawPath(water, pFill)
        canvas.drawPath(minor, pMinor)
        canvas.drawPath(major, pMajor)
        val cx = width / 2f
        val cy = height / 2f
        pMark.style = Paint.Style.FILL
        pMark.color = C.ACCENT
        pMark.alpha = 50
        canvas.drawCircle(cx, cy, Ui.px(26f).toFloat(), pMark)
        pMark.alpha = 255
        canvas.drawCircle(cx, cy, Ui.px(7f).toFloat(), pMark)
        pMark.style = Paint.Style.STROKE
        pMark.strokeWidth = Ui.px(2f).toFloat()
        canvas.drawCircle(cx, cy, Ui.px(14f).toFloat(), pMark)
    }
}