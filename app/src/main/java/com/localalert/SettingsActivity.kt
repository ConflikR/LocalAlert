package com.localalert

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SettingsActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var status: TextView
    private lateinit var titleEt: EditText
    private lateinit var secsEt: EditText

    private fun say(t: String) = runOnUiThread { status.text = t }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("p", MODE_PRIVATE)
        val root = darkPage(); val k = Kit(this, root)

        // header
        val back = TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = Ui.rounded(context, Color.parseColor("#16FFFFFF"), 26, Color.parseColor("#26FFFFFF"))
            setOnClickListener { finish() }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(back, LinearLayout.LayoutParams(k.dp(52), k.dp(52)))
        head.addView(TextView(this).apply { text = "Settings"; textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = k.dp(16) })
        root.addView(head)

        val wifi = Net.local(this)
        k.put(root, TextView(this).apply {
            textSize = 13f; setTextColor(Color.WHITE); setPadding(k.dp(14), k.dp(8), k.dp(14), k.dp(8))
            text = if (wifi != null) "●  Wi-Fi connected · this phone ${wifi.first.hostAddress}" else "○  Not connected to Wi-Fi"
            background = Ui.rounded(context, Color.parseColor("#14FFFFFF"), 20)
        }, 14)
        status = TextView(this).apply { textSize = 14f; setTextColor(k.muted); gravity = Gravity.CENTER }

        // ---- receiving ----
        val cRx = k.card("RECEIVING ON THIS PHONE")
        k.put(cRx, k.btn("Enable receiving", true) {
            val pin = p.getString("code", "") ?: ""
            if (pin.isBlank()) { say("Set a PIN on the main screen first."); return@btn }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                say("Allow 'Display over other apps', come back, and tap Enable receiving again."); return@btn
            }
            p.edit().putBoolean("listen", true).apply()
            startForegroundService(Intent(this, AlertService::class.java))
            say("Listening for alerts on this Wi-Fi.")
        }, 12)
        k.put(cRx, k.btn("Stop receiving") {
            p.edit().putBoolean("listen", false).apply(); stopService(Intent(this, AlertService::class.java)); say("Stopped.")
        })
        k.put(cRx, k.btn("Permissions & setup") { startActivity(Intent(this, SetupActivity::class.java)) }, 10)
        val silent = k.check("Silent mode (no ringtone on this phone)", p.getBoolean("silent", false))
        silent.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("silent", c).apply() }
        k.put(cRx, silent, 10)
        k.put(cRx, k.small("Message text size on this phone"), 10)
        val sizeSp = k.spinner(listOf("Normal", "Large", "Extra large"), p.getInt("size", 1))
        sizeSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { p.edit().putInt("size", pos).apply() }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        k.put(cRx, sizeSp, 6)
        k.put(cRx, k.small("Tip: set this app's battery usage to “Unrestricted” so alerts always arrive."), 10)

        // ---- phones ----
        val cPh = k.card("PHONES")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun renderList() {
            list.removeAllViews()
            val cs = Store.contacts(this)
            if (cs.isEmpty()) k.put(list, k.small("No phones yet."), 10)
            cs.forEach { c ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(k.dp(14), k.dp(10), k.dp(8), k.dp(10)); background = Ui.rounded(context, Color.parseColor("#14141C"), 12, Color.parseColor("#26FFFFFF")) }
                row.addView(TextView(this).apply { text = "${c.first}\n${c.second}"; textSize = 14f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "✕"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(k.muted)
                    setOnClickListener { Store.save(this@SettingsActivity, Store.contacts(this@SettingsActivity).also { l -> l.removeAll { it.second == c.second } }); renderList() }
                }, LinearLayout.LayoutParams(k.dp(44), k.dp(44)))
                k.put(list, row, 8)
            }
        }
        renderList(); cPh.addView(list)
        val scanBtn = k.btn("Find phones on my Wi-Fi", true) {}
        scanBtn.setOnClickListener {
            val pin = p.getString("code", "") ?: ""
            if (pin.isBlank()) { say("Set a PIN on the main screen first."); return@setOnClickListener }
            scan(pin, scanBtn, ::renderList)
        }
        k.put(cPh, scanBtn, 14)
        k.put(cPh, k.btn("Add phone manually") {
            val n = k.edit("Name", ""); val i = k.edit("IP address", "", InputType.TYPE_CLASS_PHONE)
            val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(k.dp(20), k.dp(10), k.dp(20), 0) }
            k.put(l, n); k.put(l, i)
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Add phone").setView(l)
                .setPositiveButton("Save") { _, _ ->
                    if (i.text.isNotBlank()) {
                        val cs = Store.contacts(this); cs.removeAll { it.second == i.text.toString().trim() }
                        cs.add(Pair(n.text.toString().ifBlank { i.text.toString() }, i.text.toString().trim())); Store.save(this, cs); renderList()
                    }
                }.setNegativeButton("Cancel", null).show()
        })

        // ---- alert options ----
        val cOp = k.card("ALERT OPTIONS")
        titleEt = k.edit("Alert title", p.getString("title", "LocalAlert") ?: "LocalAlert")
        secsEt = k.edit("Auto-close after seconds (0 = until dismissed)", p.getInt("secs", 0).toString(), InputType.TYPE_CLASS_NUMBER)
        val ring = k.check("Play the phone's call ringtone", p.getBoolean("sSound", true))
        val vib = k.check("Vibrate", p.getBoolean("sVib", true))
        ring.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("sSound", c).apply() }
        vib.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("sVib", c).apply() }
        k.put(cOp, titleEt, 12); k.put(cOp, ring, 10); k.put(cOp, vib, 0); k.put(cOp, secsEt, 8)
        k.put(cOp, k.btn("Preview alert on this phone") {
            save()
            val j = JSONObject().put("title", titleEt.text.toString().ifBlank { "LocalAlert" })
                .put("msg", p.getString("lastMsg", "This is a test alert.")).put("color", Ui.KEYS[p.getInt("color", 0).coerceIn(0, 3)])
                .put("sound", ring.isChecked).put("vibrate", vib.isChecked).put("secs", secsEt.text.toString().toIntOrNull() ?: 0)
                .put("from", p.getString("name", ""))
            startActivity(AlertActivity.intent(this, j))
        }, 12)
        k.put(root, status, 16)
    }

    private fun save() = p.edit().putString("title", titleEt.text.toString().ifBlank { "LocalAlert" })
        .putInt("secs", secsEt.text.toString().toIntOrNull() ?: 0).apply()

    override fun onPause() { save(); super.onPause() }

    private fun scan(code: String, btn: Button, done: () -> Unit) {
        val l = Net.local(this) ?: run { say("Connect to Wi-Fi first."); return }
        val base = l.first.address
        btn.isEnabled = false; say("Scanning…")
        val pool = Executors.newFixedThreadPool(64)
        var found = 0
        for (i in 1..254) {
            if (i == (base[3].toInt() and 0xFF)) continue
            val ip = "${base[0].toInt() and 0xFF}.${base[1].toInt() and 0xFF}.${base[2].toInt() and 0xFF}.$i"
            pool.execute {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ip, AlertService.PORT), 400); s.soTimeout = 1500
                        s.getOutputStream().write((JSONObject().put("code", code).put("ping", true).toString() + "\n").toByteArray())
                        s.getOutputStream().flush()
                        val reply = s.getInputStream().bufferedReader().readLine() ?: return@use
                        val n = JSONObject(reply).optString("pong")
                        if (n.isNotEmpty()) synchronized(this) {
                            val cs = Store.contacts(this@SettingsActivity); cs.removeAll { it.second == ip }; cs.add(Pair(n, ip))
                            Store.save(this@SettingsActivity, cs); found++
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        pool.shutdown()
        Thread {
            pool.awaitTermination(40, TimeUnit.SECONDS)
            runOnUiThread {
                btn.isEnabled = true; done()
                status.text = if (found > 0) "Found $found phone(s)." else "No phones found. Check the PIN and that receiving is enabled on the other phone."
            }
        }.start()
    }
}
