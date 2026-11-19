package com.localalert

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

object Net {
    /** Our Wi-Fi IPv4 address + prefix length, or null if not on Wi-Fi. */
    fun local(ctx: Context): Pair<Inet4Address, Int>? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(n) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val la = cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        return Pair(la.address as Inet4Address, la.prefixLength)
    }

    /** True only if [other] is in the same Wi-Fi subnet as this phone. */
    fun sameSubnet(ctx: Context, other: InetAddress): Boolean {
        val (me, prefix) = local(ctx) ?: return false
        val a = me.address
        val b = other.address
        if (b.size != 4) return false
        var bits = prefix
        for (i in 0..3) {
            val mask = if (bits >= 8) 0xFF else if (bits <= 0) 0 else (0xFF shl (8 - bits)) and 0xFF
            bits -= 8
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
        }
        return true
    }
}
