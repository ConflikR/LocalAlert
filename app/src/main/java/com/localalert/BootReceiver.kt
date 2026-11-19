package com.localalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("listen", false))
            c.startForegroundService(Intent(c, AlertService::class.java))
    }
}
