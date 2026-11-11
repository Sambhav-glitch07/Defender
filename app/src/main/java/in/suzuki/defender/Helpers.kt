package `in`.suzuki.defender

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.StatFs
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Required so Android lets us read the active media session (now playing). Does nothing else. */
class MediaListenerService : NotificationListenerService()

// ---------------------------------------------------------------- Bluetooth
class BtHelper(private val ctx: Context) {
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var headset: BluetoothProfile? = null
    private var a2dp: BluetoothProfile? = null

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) headset = proxy
            else if (profile == BluetoothProfile.A2DP) a2dp = proxy
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) headset = null
            else if (profile == BluetoothProfile.A2DP) a2dp = null
        }
    }

    fun available(): Boolean = adapter != null

    fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isOn(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (adapter == null || !permitted() || (headset != null && a2dp != null)) return
        try {
            if (headset == null) adapter.getProfileProxy(ctx, listener, BluetoothProfile.HEADSET)
            if (a2dp == null) adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            headset?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        } catch (e: Exception) {
        }
        headset = null
        a2dp = null
    }

    @SuppressLint("MissingPermission")
    fun connectedNames(): List<String> {
        if (!permitted()) return emptyList()
        return try {
            val all = (headset?.connectedDevices ?: emptyList()) + (a2dp?.connectedDevices ?: emptyList())
            all.distinctBy { it.address }.map { it.name ?: it.address }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ---------------------------------------------------------------- Location
class LocationHelper(private val ctx: Context) : LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())
    var last: Location? = null
        private set
    var place: String? = null
        private set
    var sessionMeters = 0f
        private set
    private var geoAt: Location? = null
    private var running = false

    fun granted(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !granted()) return
        try {
            for (p in lm.getProviders(true)) {
                lm.getLastKnownLocation(p)?.let { accept(it, false) }
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 50f, this)
            running = true
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            lm.removeUpdates(this)
        } catch (e: Exception) {
        }
        running = false
    }

    private fun accept(l: Location, count: Boolean) {
        val prev = last
        if (count && prev != null && l.provider == LocationManager.GPS_PROVIDER && l.accuracy < 30f) {
            val d = prev.distanceTo(l)
            if (d in 3f..500f) sessionMeters += d
        }
        if (prev == null || l.time >= prev.time) last = l
        geocode(l)
    }

    @Suppress("DEPRECATION")
    private fun geocode(l: Location) {
        val g = geoAt
        if (g != null && g.distanceTo(l) < 500f) return
        if (!Geocoder.isPresent()) return
        geoAt = l
        Thread {
            try {
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()
                if (a != null) {
                    val name = listOfNotNull(a.thoroughfare ?: a.featureName, a.locality ?: a.subAdminArea)
                        .joinToString(", ")
                    if (name.isNotEmpty()) main.post { place = name }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    fun geocodeAddress(q: String, cb: (Location?) -> Unit) {
        if (!Geocoder.isPresent()) return
        Thread {
            var out: Location? = null
            try {
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocationName(q, 1)?.firstOrNull()
                if (a != null) {
                    out = Location("geocoder")
                    out.latitude = a.latitude
                    out.longitude = a.longitude
                }
            } catch (e: Exception) {
            }
            main.post { cb(out) }
        }.start()
    }

    fun speedKmh(): Float? {
        val l = last ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000L
        return if (l.hasSpeed() && ageMs < 5000L) l.speed * 3.6f else null
    }

    fun describe(): String {
        if (!granted()) return "Location access off"
        place?.let { return it }
        val l = last ?: return "Locating…"
        return String.format(Locale.US, "%.4f, %.4f", l.latitude, l.longitude)
    }

    override fun onLocationChanged(location: Location) = accept(location, true)
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }
}

// ---------------------------------------------------------------- Media
class MediaHelper(private val ctx: Context) {
    var title = ""
    var artist = ""
    var duration = 0L
    var playing = false
    var art: Bitmap? = null
    var session = false
    var access = false

    private var posBase = 0L
    private var posTime = 0L
    private var speed = 1f
    private var ctrl: MediaController? = null
    private var n = 0
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Called once per second by MainActivity. Sessions are re-discovered every 3 s. */
    fun poll() {
        if (n++ % 3 == 0) findSession()
        val c = ctrl
        if (c == null) {
            session = false; title = ""; artist = ""; art = null; playing = false; duration = 0L
            return
        }
        try {
            val md = c.metadata
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val st = c.playbackState
            playing = st?.state == PlaybackState.STATE_PLAYING
            posBase = st?.position ?: 0L
            posTime = st?.lastPositionUpdateTime ?: 0L
            speed = st?.playbackSpeed ?: 1f
            session = true
        } catch (e: Exception) {
            session = false
        }
    }

    private fun findSession() {
        access = hasAccess()
        if (!access) {
            ctrl = null
            return
        }
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
            ctrl = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (e: Exception) {
            ctrl = null
        }
    }

    fun position(): Long {
        val p = if (!playing) posBase else posBase + ((SystemClock.elapsedRealtime() - posTime) * speed).toLong()
        return if (duration > 0) p.coerceIn(0L, duration) else p.coerceAtLeast(0L)
    }

    private fun key(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause() {
        val c = ctrl
        if (c != null) {
            if (playing) c.transportControls.pause() else c.transportControls.play()
            playing = !playing
        } else key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        n = 0
    }

    fun next() {
        val c = ctrl
        if (c != null) c.transportControls.skipToNext() else key(KeyEvent.KEYCODE_MEDIA_NEXT)
        n = 0
    }

    fun prev() {
        val c = ctrl
        if (c != null) c.transportControls.skipToPrevious() else key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        n = 0
    }
}

// ---------------------------------------------------------------- System info
class SysInfo(private val ctx: Context) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun wifiConnected(): Boolean = try {
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (e: Exception) {
        false
    }

    /** Head-unit battery %, or null when the unit has no battery (most do not). */
    fun battery(): Int? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        if (!i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) return null
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (l < 0 || s <= 0) null else l * 100 / s
    }

    fun freeStorage(): String = try {
        val st = StatFs(Environment.getDataDirectory().path)
        String.format(Locale.US, "%.1f GB free", st.availableBytes / 1073741824.0)
    } catch (e: Exception) {
        "—"
    }
}
