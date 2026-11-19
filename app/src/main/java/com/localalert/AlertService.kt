package com.localalert

import android.app.*
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket

class AlertService : Service() {
    companion object { const val PORT = 8765 }
    private var server: ServerSocket? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("listen", "Listening for alerts", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("alert", "Incoming alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        startForeground(1, Notification.Builder(this, "listen")
            .setContentTitle("LocalAlert is listening")
            .setContentText("Waiting for alerts on your Wi-Fi")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).build())
        if (server == null) Thread { runServer() }.start()
        return START_STICKY
    }

    private fun runServer() {
        try {
            val s = ServerSocket(PORT); server = s
            while (!s.isClosed) { val c = s.accept(); Thread { handle(c) }.start() }
        } catch (_: Exception) { server = null }
    }

    private fun handle(c: Socket) {
        try {
            c.use {
                if (!Net.sameSubnet(this, it.inetAddress)) return          // Wi-Fi subnet only
                it.soTimeout = 3000
                val line = it.getInputStream().bufferedReader().readLine() ?: return
                val j = JSONObject(line)
                val p = getSharedPreferences("p", MODE_PRIVATE)
                val mine = p.getString("code", "") ?: ""
                if (mine.isEmpty() || j.optString("code") != mine) return   // wrong pairing code
                if (j.optBoolean("ping")) {
                    val name = p.getString("name", Build.MODEL) ?: Build.MODEL
                    it.getOutputStream().write((JSONObject().put("pong", name).toString() + "\n").toByteArray())
                    it.getOutputStream().flush(); return
                }
                if (j.optString("msg").isBlank()) return
                ui.post { ring(j) }
            }
        } catch (_: Exception) {}
    }

    /** Wake the phone and show the full-screen alert, like an incoming call. */
    @Suppress("DEPRECATION")
    private fun ring(j: JSONObject) {
        val pm = getSystemService(PowerManager::class.java)
        pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "localalert:wake").acquire(10_000L)
        val i = AlertActivity.intent(this, j)
        val pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(j.optString("title", "LocalAlert"))
            .setContentText(j.optString("msg"))
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
        try { startActivity(i) } catch (_: Exception) {}   // allowed because of "Display over other apps"
    }

    override fun onDestroy() { server?.close(); super.onDestroy() }
}
