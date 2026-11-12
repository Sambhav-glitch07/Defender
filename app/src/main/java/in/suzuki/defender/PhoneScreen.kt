package `in`.suzuki.defender

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PhoneScreen(env: Env) : Screen(env) {
    private val ctx: Context = env.activity
    private lateinit var devName: TextView
    private lateinit var devSub: TextView
    private lateinit var grant: TextView
    private lateinit var muteBtn: TextView
    private lateinit var display: TextView
    private val tabs = ArrayList<TextView>()
    private val panes = ArrayList<View>()
    private lateinit var recentsBox: LinearLayout
    private lateinit var contactsBox: LinearLayout
    private var tab = 0
    private val number = StringBuilder()

    override val root: View by lazy { build() }

    private val dialPerms: Array<String>
        get() {
            val p = arrayListOf(
                Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE
            )
            if (Build.VERSION.SDK_INT >= 26) p.add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
            return p.toTypedArray()
        }

    private fun build(): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL

        // ---- connection column
        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.setBackgroundColor(C.GLASS)
        left.setPadding(Ui.px(28), Ui.px(28), Ui.px(28), Ui.px(24))
        left.addView(Ui.caps(ctx, "Connected phone", 11f, C.ACCENT))
        devName = Ui.text(ctx, "", 26f, C.TEXT, Ui.LIGHT)
        devName.maxLines = 2
        devName.ellipsize = TextUtils.TruncateAt.END
        devName.setPadding(0, Ui.px(14), 0, 0)
        left.addView(devName)
        devSub = Ui.text(ctx, "", 13f, C.DIM)
        devSub.setPadding(0, Ui.px(10), 0, Ui.px(22))
        left.addView(devSub)
        left.addView(Ui.button(ctx, "Bluetooth settings", true) {
            env.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }, Ui.mm(MATCH, WRAP))
        grant = Ui.button(ctx, "Allow contacts & calls") {
            env.request(dialPerms) { reload() }
        }
        val gLp = Ui.mm(MATCH, WRAP)
        gLp.topMargin = Ui.px(10)
        left.addView(grant, gLp)
        left.addView(View(ctx), Ui.mm(MATCH, 0, 1f))
        left.addView(Ui.caps(ctx, "Call controls", 10f, C.DIM))
        muteBtn = Ui.button(ctx, "Mute microphone") { toggleMute() }
        val mLp = Ui.mm(MATCH, WRAP)
        mLp.topMargin = Ui.px(10)
        left.addView(muteBtn, mLp)
        val endBtn = Ui.button(ctx, "End call", false, true) { endCall() }
        val eLp = Ui.mm(MATCH, WRAP)
        eLp.topMargin = Ui.px(10)
        left.addView(endBtn, eLp)

        // ---- right: tabs + panes
        val right = LinearLayout(ctx)
        right.orientation = LinearLayout.VERTICAL
        val tabRow = LinearLayout(ctx)
        tabRow.orientation = LinearLayout.HORIZONTAL
        val names = listOf("Recents", "Contacts", "Dial pad")
        names.forEachIndexed { i, n ->
            val t = Ui.text(ctx, n.uppercase(), 13f, C.DIM, Ui.MED, 0.18f)
            t.gravity = Gravity.CENTER
            t.setPadding(0, Ui.px(22), 0, Ui.px(18))
            Ui.pressable(t, false) { select(i) }
            tabs.add(t)
            tabRow.addView(t, Ui.mm(0, WRAP, 1f))
        }
        right.addView(tabRow, Ui.mm(MATCH, WRAP))
        right.addView(Ui.hline(ctx))

        val frame = FrameLayout(ctx)
        recentsBox = LinearLayout(ctx)
        recentsBox.orientation = LinearLayout.VERTICAL
        contactsBox = LinearLayout(ctx)
        contactsBox.orientation = LinearLayout.VERTICAL
        val s1 = ScrollView(ctx)
        s1.addView(recentsBox)
        val s2 = ScrollView(ctx)
        s2.addView(contactsBox)
        val dial = buildDial()
        panes.add(s1); panes.add(s2); panes.add(dial)
        for (p in panes) frame.addView(p, FrameLayout.LayoutParams(MATCH, MATCH))
        right.addView(frame, Ui.mm(MATCH, 0, 1f))

        row.addView(left, Ui.mm(0, MATCH, 1f))
        row.addView(right, Ui.mm(0, MATCH, 2f))
        select(0)
        return row
    }

    private fun buildDial(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(0, Ui.px(8), 0, Ui.px(8))

        val dr = LinearLayout(ctx)
        dr.orientation = LinearLayout.HORIZONTAL
        dr.gravity = Gravity.CENTER_VERTICAL
        display = Ui.text(ctx, "", 38f, C.TEXT, Ui.LIGHT)
        display.gravity = Gravity.CENTER
        display.maxLines = 1
        display.ellipsize = TextUtils.TruncateAt.START
        dr.addView(display, Ui.mm(Ui.px(300), Ui.px(52)))
        dr.addView(Ui.actionIcon(ctx, R.drawable.ic_backspace, 24f, C.DIM) {
            if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
            updateDisplay()
        })
        col.addView(dr)

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        for (r in 0 until 4) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 3) {
                val k = keys[r * 3 + c]
                val t = Ui.text(ctx, k, 24f, C.TEXT, Ui.LIGHT)
                t.gravity = Gravity.CENTER
                t.background = Ui.rect(C.GLASS, C.LINE)
                Ui.pressable(t) { number.append(k); updateDisplay() }
                val lp = Ui.mm(Ui.px(92), Ui.px(50))
                lp.setMargins(Ui.px(5), Ui.px(4), Ui.px(5), Ui.px(4))
                line.addView(t, lp)
            }
            col.addView(line)
        }
        val call = Ui.button(ctx, "Call", true) { call(number.toString()) }
        val cLp = Ui.mm(Ui.px(300), WRAP)
        cLp.topMargin = Ui.px(8)
        col.addView(call, cLp)
        updateDisplay()
        return col
    }

    private fun updateDisplay() {
        display.text = if (number.isEmpty()) "" else number.toString()
        display.hint = "Enter number"
        display.setHintTextColor(C.DIM)
    }

    private fun select(i: Int) {
        tab = i
        for (k in tabs.indices) {
            val sel = k == i
            tabs[k].setTextColor(if (sel) C.ACCENT else C.DIM)
            tabs[k].setBackgroundColor(if (sel) C.GLASS2 else 0)
            panes[k].visibility = if (sel) View.VISIBLE else View.GONE
        }
    }

    // ---- actions
    private fun call(n: String) {
        if (n.isEmpty()) {
            env.toast("Enter a number")
            return
        }
        val uri = Uri.fromParts("tel", n, null)
        if (env.has(Manifest.permission.CALL_PHONE)) env.launch(Intent(Intent.ACTION_CALL, uri))
        else env.launch(Intent(Intent.ACTION_DIAL, uri))
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun endCall() {
        if (Build.VERSION.SDK_INT >= 28 && env.has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.endCall()
                return
            } catch (e: Exception) {
            }
        }
        env.toast("Allow calls access, or end the call on your phone")
    }

    private fun toggleMute() {
        val am = env.audio
        am.isMicrophoneMute = !am.isMicrophoneMute
        updateMute()
    }

    private fun updateMute() {
        muteBtn.text = if (env.audio.isMicrophoneMute) "MICROPHONE MUTED" else "MUTE MICROPHONE"
        muteBtn.setTextColor(if (env.audio.isMicrophoneMute) C.ACCENT else C.TEXT)
    }

    private fun message(box: LinearLayout, text: String) {
        box.removeAllViews()
        val t = Ui.text(ctx, text, 15f, C.DIM)
        t.setPadding(Ui.px(32), Ui.px(32), Ui.px(32), 0)
        box.addView(t)
    }

    private fun addRow(box: LinearLayout, name: String, sub: String, num: String, missed: Boolean) {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(Ui.px(32), Ui.px(14), Ui.px(32), Ui.px(14))
        val n = Ui.text(ctx, name, 18f, if (missed) C.DANGER else C.TEXT, Ui.LIGHT)
        n.maxLines = 1
        n.ellipsize = TextUtils.TruncateAt.END
        val s = Ui.text(ctx, sub, 12f, C.DIM)
        s.setPadding(0, Ui.px(4), 0, 0)
        s.maxLines = 1
        r.addView(n)
        r.addView(s)
        Ui.pressable(r, false) { call(num) }
        box.addView(r, Ui.mm(MATCH, WRAP))
        box.addView(Ui.hline(ctx))
    }

    // ---- data
    private fun reload() {
        val connected = env.bt.connectedNames()
        devName.text = when {
            !env.bt.available() -> "No Bluetooth"
            !env.bt.isOn() -> "Bluetooth off"
            connected.isNotEmpty() -> connected[0]
            else -> "No phone connected"
        }
        devSub.text = when {
            !env.bt.permitted() -> "Bluetooth access needed to show the connected phone."
            connected.isNotEmpty() -> "Hands-free connected"
            else -> "Pair a phone in Bluetooth settings."
        }
        val full = env.has(Manifest.permission.READ_CONTACTS) && env.has(Manifest.permission.READ_CALL_LOG)
        grant.visibility = if (full && env.has(Manifest.permission.CALL_PHONE)) View.GONE else View.VISIBLE
        updateMute()

        if (env.has(Manifest.permission.READ_CALL_LOG)) loadRecents()
        else message(recentsBox, "Allow call-log access to show recent calls.")
        if (env.has(Manifest.permission.READ_CONTACTS)) loadContacts()
        else message(contactsBox, "Allow contacts access to show your contacts.")
    }

    private class Entry(val name: String, val sub: String, val num: String, val missed: Boolean)

    private fun loadRecents() {
        Thread {
            val out = ArrayList<Entry>()
            try {
                ctx.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                    null, null, CallLog.Calls.DATE + " DESC"
                )?.use { c ->
                    while (c.moveToNext() && out.size < 30) {
                        val num = c.getString(0) ?: ""
                        val name = c.getString(1)
                        val type = c.getInt(2)
                        val date = c.getLong(3)
                        val kind = when (type) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            else -> "Call"
                        }
                        val ago = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        out.add(Entry(if (name.isNullOrEmpty()) (if (num.isEmpty()) "Unknown" else num) else name,
                            "$kind · $num · $ago", num, type == CallLog.Calls.MISSED_TYPE))
                    }
                }
            } catch (e: Exception) {
            }
            env.main.post {
                recentsBox.removeAllViews()
                if (out.isEmpty()) message(recentsBox, "No recent calls.")
                for (e in out) addRow(recentsBox, e.name, e.sub, e.num, e.missed)
            }
        }.start()
    }

    private fun loadContacts() {
        Thread {
            val out = ArrayList<Entry>()
            try {
                ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
                )?.use { c ->
                    while (c.moveToNext() && out.size < 150) {
                        val num = c.getString(1) ?: ""
                        out.add(Entry(c.getString(0) ?: num, num, num, false))
                    }
                }
            } catch (e: Exception) {
            }
            env.main.post {
                contactsBox.removeAllViews()
                if (out.isEmpty()) message(contactsBox, "No contacts found.")
                for (e in out) addRow(contactsBox, e.name, e.sub, e.num, false)
            }
        }.start()
    }

    override fun onShow() {
        select(env.phoneTab)
        env.phoneTab = 0
        reload()
    }

    override fun onTick() {
        val c = env.bt.connectedNames()
        devName.text = when {
            !env.bt.available() -> "No Bluetooth"
            !env.bt.isOn() -> "Bluetooth off"
            c.isNotEmpty() -> c[0]
            else -> "No phone connected"
        }
    }

    override fun onPermissionsChanged() = reload()
}