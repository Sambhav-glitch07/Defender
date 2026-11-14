package `in`.suzuki.defender

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    lateinit var env: Env
    private lateinit var content: FrameLayout
    private lateinit var leftFrame: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var iconBt: ImageView
    private lateinit var iconWifi: ImageView
    private lateinit var iconPhone: ImageView
    private lateinit var iconAudio: ImageView
    private lateinit var volText: TextView
    private lateinit var volMute: ImageView
    private lateinit var pill: View
    private var wallpaper: SceneryView? = null

    private class RailItem(val cell: View, val icon: ImageView, val label: TextView) {
        var tint = C.DIM
    }

    private val rail = HashMap<Dest, RailItem>()
    private val screens = HashMap<Dest, Screen>()
    private var current: Dest? = null

    private val expo = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val handler = Handler(Looper.getMainLooper())
    private val permCallbacks = HashMap<Int, () -> Unit>()
    private var permCode = 100
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private var lastClock = ""

    private val tick = object : Runnable {
        override fun run() {
            env.media.poll()
            updateStatus()
            screens[current]?.onTick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("suzuki_os", MODE_PRIVATE)
        Ui.init(this)
        Ui.animations = prefs.getBoolean("anim", true)
        C.ACCENT = Color.parseColor(Themes.hex(prefs.getString("theme", null)))

        setContentView(R.layout.activity_main)
        env = Env(this)

        // Global wallpaper behind the glass UI.
        val sceneMode = prefs.getInt("scene", 0)
        if (sceneMode != 4) {
            val kind = if (sceneMode in 1..3) sceneMode - 1 else SceneryView.HIGHLAND
            val wp = SceneryView(this, kind, false, 3L, 95, prefs.getBoolean("live", true))
            wallpaper = wp
            findViewById<FrameLayout>(R.id.root).addView(wp, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        content = findViewById(R.id.content)
        leftFrame = findViewById(R.id.leftFrame)
        titleText = findViewById(R.id.titleText)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        iconBt = findViewById(R.id.iconBt)
        iconWifi = findViewById(R.id.iconWifi)
        iconPhone = findViewById(R.id.iconPhone)
        iconAudio = findViewById(R.id.iconAudio)
        volText = findViewById(R.id.volText)
        volMute = findViewById(R.id.volMute)

        // Floating glass chrome.
        findViewById<View>(R.id.topBar).background = Ui.glass(24f)
        leftFrame.background = Ui.glass(28f)
        findViewById<View>(R.id.rightRail).background = Ui.glass(28f)
        Ui.roundClip(content, 28f)

        findViewById<ImageView>(R.id.brandIcon).setColorFilter(C.ACCENT)
        findViewById<ImageView>(R.id.iconApps).apply {
            setColorFilter(C.ACCENT)
            Ui.pressable(this) { navigate(Dest.APPS) }
        }
        setKeepOn(prefs.getBoolean("keepon", true))
        setBrightness(prefs.getFloat("bright", -1f))
        buildRail()
        buildVolumeRail()
        applyImmersive()

        val start = savedInstanceState?.getString("dest")?.let { n -> Dest.values().firstOrNull { it.name == n } }
        navigate(start ?: Dest.HOME)

        if (!prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            val p = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
            content.post { requestPerms(p.toTypedArray()) {} }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("dest", current?.name)
    }

    override fun onResume() {
        super.onResume()
        env.bt.start()
        env.loc.start()
        wallpaper?.setRunning(true)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        wallpaper?.setRunning(false)
        env.loc.stop()
        env.bt.stop()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navigate(Dest.HOME)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (current != Dest.HOME) navigate(Dest.HOME)
    }

    private fun applyImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun setBrightness(f: Float) {
        val lp = window.attributes
        lp.screenBrightness = f
        window.attributes = lp
    }

    fun setKeepOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun setLiveWallpaper(on: Boolean) {
        wallpaper?.live = on
    }

    // ------------------------------------------------------------ permissions
    fun requestPerms(perms: Array<String>, cb: () -> Unit) {
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) {
            cb()
            return
        }
        val code = permCode++
        permCallbacks[code] = cb
        requestPermissions(need.toTypedArray(), code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        env.bt.start()
        env.loc.start()
        permCallbacks.remove(requestCode)?.invoke()
        screens.values.forEach { it.onPermissionsChanged() }
    }

    // ------------------------------------------------------------ chrome
    private fun buildRail() {
        val rl = findViewById<LinearLayout>(R.id.leftRail)

        // Sliding selection pill (sits behind the rail items).
        pill = View(this)
        pill.background = Ui.rect(
            (C.ACCENT and 0x00FFFFFF) or 0x26000000,
            (C.ACCENT and 0x00FFFFFF) or 0x59000000, 1f, 20f
        )
        pill.alpha = 0f
        val pillLp = FrameLayout.LayoutParams(MATCH, Ui.px(10))
        pillLp.setMargins(Ui.px(8), 0, Ui.px(8), 0)
        leftFrame.addView(pill, 0, pillLp)

        for (d in Dest.values()) {
            val cell = FrameLayout(this)
            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            inner.gravity = Gravity.CENTER
            val ic = Ui.icon(this, d.icon, 26f, C.DIM)
            val lb = Ui.text(this, d.label.uppercase(), 9f, C.DIM, Ui.MED, 0.06f)
            lb.maxLines = 1
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, Ui.px(8), 0, 0)
            inner.addView(ic, LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)))
            inner.addView(lb, LinearLayout.LayoutParams(WRAP, WRAP))
            cell.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
            Ui.pressable(cell, false) { navigate(d) }
            rl.addView(cell, LinearLayout.LayoutParams(MATCH, 0, 1f))
            rail[d] = RailItem(cell, ic, lb)
        }
    }

    private fun buildVolumeRail() {
        findViewById<ImageView>(R.id.volUp).apply {
            setColorFilter(C.TEXT)
            Ui.pressable(this) {
                env.audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                updateVolume()
            }
        }
        findViewById<ImageView>(R.id.volDown).apply {
            setColorFilter(C.TEXT)
            Ui.pressable(this) {
                env.audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                updateVolume()
            }
        }
        volMute.setColorFilter(C.TEXT)
        Ui.pressable(volMute) {
            env.audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            updateVolume()
        }
        updateVolume()
    }

    fun updateVolume() {
        val muted = env.audio.isStreamMute(AudioManager.STREAM_MUSIC)
        volText.text = env.volumePct().toString()
        volText.setTextColor(if (muted) C.DIM else C.TEXT)
        volMute.setColorFilter(if (muted) C.DANGER else C.TEXT)
    }

    private fun updateStatus() {
        val now = Date()
        val t = timeFmt.format(now)
        if (t != lastClock) {
            lastClock = t
            clockText.text = t
            dateText.text = dateFmt.format(now).uppercase()
        }
        iconBt.setColorFilter(if (env.bt.isOn()) C.ACCENT else C.DIM)
        iconWifi.setColorFilter(if (env.sys.wifiConnected()) C.ACCENT else C.DIM)
        iconPhone.setColorFilter(if (env.bt.connectedNames().isNotEmpty()) C.ACCENT else C.DIM)
        iconAudio.setColorFilter(if (env.audio.isMusicActive) C.ACCENT else C.DIM)
        updateVolume()
    }

    // ------------------------------------------------------------ navigation
    private fun createScreen(d: Dest): Screen = when (d) {
        Dest.HOME -> HomeScreen(env)
        Dest.NAV -> NavScreen(env)
        Dest.MEDIA -> MediaScreen(env)
        Dest.PHONE -> PhoneScreen(env)
        Dest.VEHICLE -> VehicleScreen(env)
        Dest.APPS -> AppsScreen(env)
        Dest.SETTINGS -> SettingsScreen(env)
    }

    fun navigate(d: Dest) {
        val old = current
        if (d == old) return
        current = d

        val screen = screens.getOrPut(d) { createScreen(d) }
        val v = screen.root
        v.animate().cancel()
        (v.parent as? ViewGroup)?.removeView(v)

        val oldScreen = if (old != null) screens[old] else null
        oldScreen?.onHide()
        val ov = oldScreen?.root

        // Title cross-fade.
        if (Ui.animations) {
            titleText.animate().alpha(0f).setDuration(90L).withEndAction {
                titleText.text = d.label.uppercase()
                titleText.animate().alpha(1f).setDuration(220L).start()
            }.start()
        } else titleText.text = d.label.uppercase()

        // Home = floating cards; every other screen sits on one frosted slab.
        content.background = if (d == Dest.HOME) null else Ui.glass(28f)
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))

        if (Ui.animations && ov != null && old != null) {
            val dir = if (d.ordinal > old.ordinal) 1f else -1f
            v.alpha = 0f
            v.translationX = Ui.slide() * dir
            v.scaleX = 0.965f
            v.scaleY = 0.965f
            v.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                .setDuration(420L).setInterpolator(expo).start()
            ov.animate().cancel()
            ov.animate().alpha(0f).translationX(-Ui.slide() * dir * 0.5f).scaleX(0.985f).scaleY(0.985f)
                .setDuration(220L).withEndAction {
                    if (current != old) content.removeView(ov)
                    ov.alpha = 1f
                    ov.translationX = 0f
                    ov.scaleX = 1f
                    ov.scaleY = 1f
                }.start()
        } else {
            v.alpha = 1f
            v.translationX = 0f
            v.scaleX = 1f
            v.scaleY = 1f
            if (ov != null) content.removeView(ov)
        }
        screen.onShow()
        updateRail()
    }

    private fun tintTo(item: RailItem, to: Int) {
        if (item.tint == to) return
        if (!Ui.animations) {
            item.icon.setColorFilter(to)
            item.label.setTextColor(to)
        } else {
            val a = ValueAnimator.ofArgb(item.tint, to)
            a.duration = 260L
            a.addUpdateListener {
                val c = it.animatedValue as Int
                item.icon.setColorFilter(c)
                item.label.setTextColor(c)
            }
            a.start()
        }
        item.tint = to
    }

    private fun updateRail() {
        val cur = current ?: return
        for ((d, item) in rail) tintTo(item, if (d == cur) C.ACCENT else C.DIM)
        val sel = rail[cur] ?: return
        leftFrame.post {
            val h = sel.cell.height - Ui.px(10)
            if (h > 0) {
                val lp = pill.layoutParams
                if (lp.height != h) {
                    lp.height = h
                    pill.layoutParams = lp
                }
                val y = sel.cell.top + Ui.px(5).toFloat()
                if (pill.alpha == 0f || !Ui.animations) {
                    pill.translationY = y
                    pill.alpha = 1f
                } else {
                    pill.animate().translationY(y).setDuration(460L).setInterpolator(expo).start()
                }
            }
        }
    }
}