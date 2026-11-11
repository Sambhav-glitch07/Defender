package `in`.suzuki.defender

import android.Manifest
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    lateinit var env: Env
    private lateinit var content: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var iconBt: ImageView
    private lateinit var iconWifi: ImageView
    private lateinit var iconPhone: ImageView
    private lateinit var iconAudio: ImageView
    private lateinit var volText: TextView
    private lateinit var volMute: ImageView

    private class RailItem(val cell: View, val bar: View, val icon: ImageView, val label: TextView)

    private val rail = HashMap<Dest, RailItem>()
    private val screens = HashMap<Dest, Screen>()
    private var current: Dest? = null

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

        content = findViewById(R.id.content)
        titleText = findViewById(R.id.titleText)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        iconBt = findViewById(R.id.iconBt)
        iconWifi = findViewById(R.id.iconWifi)
        iconPhone = findViewById(R.id.iconPhone)
        iconAudio = findViewById(R.id.iconAudio)
        volText = findViewById(R.id.volText)
        volMute = findViewById(R.id.volMute)

        findViewById<ImageView>(R.id.brandIcon).setColorFilter(C.ACCENT)
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
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
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
        for (d in Dest.values()) {
            val cell = FrameLayout(this)
            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            inner.gravity = Gravity.CENTER
            val ic = Ui.icon(this, d.icon, 26f, C.DIM)
            val lb = Ui.text(this, d.label.uppercase(), 9f, C.DIM, Ui.MED, 0.1f)
            lb.maxLines = 1
            lb.setPadding(0, Ui.px(7), 0, 0)
            inner.addView(ic)
            inner.addView(lb)
            cell.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
            val bar = View(this)
            bar.setBackgroundColor(C.ACCENT)
            bar.visibility = View.INVISIBLE
            val barLp = FrameLayout.LayoutParams(Ui.px(3), MATCH, Gravity.START)
            barLp.topMargin = Ui.px(14)
            barLp.bottomMargin = Ui.px(14)
            cell.addView(bar, barLp)
            Ui.pressable(cell, false) { navigate(d) }
            rl.addView(cell, LinearLayout.LayoutParams(MATCH, 0, 1f))
            rail[d] = RailItem(cell, bar, ic, lb)
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
        val btOn = env.bt.isOn()
        iconBt.setColorFilter(if (btOn) C.ACCENT else C.DIM)
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
        Dest.SETTINGS -> SettingsScreen(env)
        Dest.APPS -> AppsScreen(env)
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

        titleText.text = d.label.uppercase()
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))

        if (Ui.animations && ov != null && old != null) {
            val dir = if (d.ordinal > old.ordinal) 1f else -1f
            v.alpha = 0f
            v.translationX = Ui.slide() * dir
            v.animate().alpha(1f).translationX(0f).setDuration(240L)
                .setInterpolator(DecelerateInterpolator()).start()
            ov.animate().cancel()
            ov.animate().alpha(0f).setDuration(160L).withEndAction {
                if (current != old) content.removeView(ov)
                ov.alpha = 1f
                ov.translationX = 0f
            }.start()
        } else {
            v.alpha = 1f
            v.translationX = 0f
            if (ov != null) content.removeView(ov)
        }
        screen.onShow()
        updateRail()
    }

    private fun updateRail() {
        for ((d, item) in rail) {
            val sel = d == current
            val c = if (sel) C.ACCENT else C.DIM
            item.icon.setColorFilter(c)
            item.label.setTextColor(c)
            item.bar.visibility = if (sel) View.VISIBLE else View.INVISIBLE
            item.cell.setBackgroundColor(if (sel) C.SURFACE2 else 0)
        }
    }
}
