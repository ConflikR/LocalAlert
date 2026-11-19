package com.localalert

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class SetupActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var k: Kit
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var nameEt: EditText
    private lateinit var pinEt: EditText

    private fun notifOk() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun overlayOk() = Settings.canDrawOverlays(this)
    private fun fsiOk() = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    private fun batteryOk() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun pkg() = Uri.parse("package:$packageName")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("p", MODE_PRIVATE)
        val root = darkPage(); k = Kit(this, root)

        root.addView(TextView(this).apply { text = "Welcome to LocalAlert"; textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply {
            text = "Let's set up this phone so alerts can wake it up and pop up over everything, like an incoming call."
            textSize = 14f; setTextColor(k.muted); setPadding(0, k.dp(6), 0, 0)
        })

        val cMe = k.card("THIS PHONE")
        nameEt = k.edit("Phone name (e.g. Mom's phone)", p.getString("name", Build.MODEL) ?: "")
        pinEt = k.edit("Choose a PIN (same on both phones)", p.getString("code", "") ?: "")
        k.put(cMe, nameEt, 12); k.put(cMe, pinEt)

        val cPerm = k.card("PERMISSIONS")
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cPerm.addView(list)

        status = TextView(this).apply { textSize = 14f; setTextColor(k.muted); gravity = Gravity.CENTER }
        k.put(root, k.btn("Finish setup", true) {
            if (pinEt.text.isBlank()) { status.text = "Choose a PIN first (use the same one on both phones)."; return@btn }
            if (!notifOk() || !overlayOk()) { status.text = "Please allow notifications and display over other apps, or tap Skip."; return@btn }
            saveBasics(); p.edit().putBoolean("setupDone", true).putBoolean("listen", true).apply()
            startForegroundService(Intent(this, AlertService::class.java))
            goMain()
        }, 20)
        k.put(root, k.btn("Skip · I only want to send alerts") {
            saveBasics(); p.edit().putBoolean("setupDone", true).apply(); goMain()
        })
        k.put(root, status, 14)
    }

    private fun saveBasics() = p.edit().putString("name", nameEt.text.toString().trim()).putString("code", pinEt.text.toString().trim()).apply()

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onResume() { super.onResume(); render() }

    private fun row(title: String, desc: String, ok: Boolean, action: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(14), k.dp(12), k.dp(10), k.dp(12))
            background = Ui.rounded(context, Color.parseColor("#14141C"), 14, Color.parseColor("#26FFFFFF"))
        }
        r.addView(TextView(this).apply {
            text = if (ok) "✓" else "○"; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(if (ok) Color.parseColor("#25D17C") else k.muted)
        }, LinearLayout.LayoutParams(k.dp(32), -2))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = title; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        col.addView(TextView(this).apply { text = desc; textSize = 12f; setTextColor(k.muted) })
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = k.dp(8) })
        if (!ok) r.addView(k.btn("Allow", true) { action() }.apply {
            minHeight = k.dp(40); minimumHeight = k.dp(40); textSize = 13f; setPadding(k.dp(18), 0, k.dp(18), 0)
        }, LinearLayout.LayoutParams(-2, k.dp(40)).apply { leftMargin = k.dp(8) })
        return r
    }

    private fun render() {
        list.removeAllViews()
        k.put(list, row("Notifications", "Needed so alerts can reach this phone.", notifOk()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }, 12)
        k.put(list, row("Display over other apps", "Lets the alert pop up on top of anything.", overlayOk()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg()))
        })
        k.put(list, row("Full-screen alerts", "Lets alerts wake the screen over the lock screen.", fsiOk()) {
            if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg()))
        })
        k.put(list, row("Run in background", "Set battery to Unrestricted so alerts never get missed.", batteryOk()) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg())) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })
    }
}
