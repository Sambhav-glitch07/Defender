package `in`.suzuki.defender

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat

enum class Dest(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    NAV("Navigation", R.drawable.ic_nav),
    MEDIA("Media", R.drawable.ic_music),
    PHONE("Phone", R.drawable.ic_phone),
    VEHICLE("Vehicle", R.drawable.ic_car),
    APPS("Apps", R.drawable.ic_apps),
    SETTINGS("Settings", R.drawable.ic_settings)
}

/** Shared services + helpers handed to every screen. */
class Env(val activity: MainActivity) {
    val prefs: SharedPreferences = activity.getSharedPreferences("suzuki_os", Context.MODE_PRIVATE)
    val bt = BtHelper(activity)
    val loc = LocationHelper(activity)
    val media = MediaHelper(activity)
    val sys = SysInfo(activity)
    val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val main = Handler(Looper.getMainLooper())

    var phoneTab = 0
    var focusSearch = false
    var destLoc: Location? = null

    fun navigate(d: Dest) = activity.navigate(d)
    fun has(perm: String) = ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
    fun request(perms: Array<String>, cb: () -> Unit) = activity.requestPerms(perms, cb)
    fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_SHORT).show()

    fun tryStart(i: Intent): Boolean = try {
        activity.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun launch(i: Intent): Boolean {
        val ok = tryStart(i)
        if (!ok) toast("Not available on this unit")
        return ok
    }

    fun volumePct(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    fun recents(): List<String> =
        (prefs.getString("recents", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun addRecent(q: String) {
        val l = (listOf(q) + recents().filter { !it.equals(q, true) }).take(5)
        prefs.edit().putString("recents", l.joinToString("\n")).apply()
    }

    /** Hands the destination to whichever navigation app is installed. */
    fun startNavigation(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        addRecent(q)
        prefs.edit().putString("dest", q).apply()
        destLoc = null
        val ok = tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q)))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
        if (!ok) toast("No navigation app found on this unit")
        loc.geocodeAddress(q) { destLoc = it }
        return ok
    }
}

abstract class Screen(protected val env: Env) {
    abstract val root: View
    open fun onShow() {}
    open fun onHide() {}
    open fun onTick() {}
    open fun onPermissionsChanged() {}
}