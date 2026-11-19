package com.localalert

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var status: TextView
    private lateinit var who: Spinner
    private lateinit var da: DarkAdapter
    private lateinit var noPhones: TextView
    private lateinit var nameEt: EditText
    private lateinit var pinEt: EditText
    private var contacts: List<Pair<String, String>> = emptyList()
    private val defaultPresets = listOf(
        "Your son wants food. Please report to the kitchen and get some food.",
        "Please call me when you see this.",
        "Dinner is ready. Come to the table.")

    private fun say(t: String) = runOnUiThread { status.text = t }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("p", MODE_PRIVATE)
        if (!p.getBoolean("setupDone", false)) {   // first launch -> setup screen
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }
        val root = darkPage(); val k = Kit(this, root)
        var colorIdx = p.getInt("color", 0).coerceIn(0, 3)

        // ---- header: title + settings icon ----
        val titleTv = TextView(this).apply {
            text = "LocalAlert"; textSize = 36f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            post {
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    intArrayOf(k.brand, k.brand2, Color.parseColor("#FFC857")), null, Shader.TileMode.CLAMP); invalidate()
            }
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(titleTv)
            addView(TextView(context).apply { text = "Instant alerts across your Wi-Fi"; textSize = 14f; setTextColor(k.muted) })
        }
        val gear = TextView(this).apply {
            text = "⚙"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = Ui.rounded(context, Color.parseColor("#16FFFFFF"), 26, Color.parseColor("#26FFFFFF"))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(gear, LinearLayout.LayoutParams(k.dp(52), k.dp(52)))
        root.addView(head)

        // ---- this phone ----
        nameEt = k.edit("Phone name (e.g. Mom's phone)", p.getString("name", Build.MODEL) ?: "")
        pinEt = k.edit("PIN (same on both phones)", p.getString("code", "") ?: "")
        val cMe = k.card("THIS PHONE")
        k.put(cMe, nameEt, 12); k.put(cMe, pinEt)

        // ---- send an alert ----
        val cTx = k.card("SEND AN ALERT")
        da = DarkAdapter(this, emptyList())
        who = k.spinner(emptyList()).also { it.adapter = da }
        k.put(cTx, who, 12)
        noPhones = k.small("No phones yet. Open ⚙ Settings and tap “Find phones”.")
        k.put(cTx, noPhones, 6)

        val msg = k.edit("Type your message…", p.getString("lastMsg", defaultPresets[0]) ?: "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            minLines = 6; gravity = Gravity.TOP or Gravity.START; textSize = 22f; setPadding(k.dp(16), k.dp(16), k.dp(16), k.dp(16))
        }
        k.put(cTx, msg, 14)

        // quick message shortcuts
        fun presets(): MutableList<String> {
            val s = p.getString("presets", null) ?: return defaultPresets.toMutableList()
            val a = JSONArray(s); return MutableList(a.length()) { a.getString(it) }
        }
        fun savePresets(l: List<String>) = p.edit().putString("presets", JSONArray(l).toString()).apply()
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(t: String, strong: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
            text = t; textSize = 13f; setTextColor(Color.WHITE); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
            maxWidth = k.dp(220); setPadding(k.dp(14), k.dp(10), k.dp(14), k.dp(10))
            background = if (strong) Ui.gradient(context, k.brand, k.brand2, 20)
                         else Ui.rounded(context, Color.parseColor("#1AFFFFFF"), 20, Color.parseColor("#26FFFFFF"))
            setOnClickListener { onClick() }
        }
        fun renderChips() {
            chipRow.removeAllViews()
            presets().forEach { t ->
                chipRow.addView(chip(t) { msg.setText(t) }.apply {
                    setOnLongClickListener { savePresets(presets().also { it.remove(t) }); renderChips(); true }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = k.dp(8) })
            }
            chipRow.addView(chip("＋ Save this", true) {
                val t = msg.text.toString().trim(); val l = presets()
                if (t.isNotEmpty() && t !in l) { l.add(t); savePresets(l); renderChips() }
            })
        }
        renderChips()
        k.put(cTx, k.small("Shortcuts · tap to use · long-press to delete"), 14)
        k.put(cTx, HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) }, 8)

        // colour swatches (+ colours the message border and the send button)
        k.put(cTx, k.small("Alert colour"), 16)
        val send = k.btn("SEND ALERT", true) {}
        val swatches = Ui.KEYS.map { View(this) }
        fun paint() {
            val key = Ui.KEYS[colorIdx]
            swatches.forEachIndexed { i, v ->
                v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.accent(Ui.KEYS[i]), Ui.accent2(Ui.KEYS[i]))).apply {
                    shape = GradientDrawable.OVAL; setStroke(k.dp(3), if (i == colorIdx) Color.WHITE else Color.TRANSPARENT)
                }
            }
            msg.background = Ui.rounded(this, Color.parseColor("#14141C"), 16, Ui.accent(key), 2)
            send.background = Ui.gradient(this, Ui.accent(key), Ui.accent2(key), 24)
        }
        val swRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swatches.forEachIndexed { i, v ->
            v.setOnClickListener { colorIdx = i; paint() }
            swRow.addView(v, LinearLayout.LayoutParams(k.dp(44), k.dp(44)).apply { rightMargin = k.dp(14) })
        }
        k.put(cTx, swRow, 8)
        paint()

        send.setOnClickListener {
            savePhone()
            val i = who.selectedItemPosition
            if (i !in contacts.indices) { say("Pick a phone first. Add phones in ⚙ Settings."); return@setOnClickListener }
            if (msg.text.isBlank()) { say("Type a message first."); return@setOnClickListener }
            if (pinEt.text.isBlank()) { say("Set a PIN first."); return@setOnClickListener }
            p.edit().putString("lastMsg", msg.text.toString()).putInt("color", colorIdx).apply()
            val j = JSONObject().put("code", pinEt.text.toString().trim())
                .put("title", p.getString("title", "LocalAlert")).put("msg", msg.text.toString().trim())
                .put("color", Ui.KEYS[colorIdx]).put("sound", p.getBoolean("sSound", true))
                .put("vibrate", p.getBoolean("sVib", true)).put("secs", p.getInt("secs", 0)).put("from", nameEt.text.toString().trim())
            say("Sending…")
            Thread { say(post(contacts[i].second, j)) }.start()
        }
        k.put(cTx, send, 20)
        status = TextView(this).apply { textSize = 14f; setTextColor(k.muted); gravity = Gravity.CENTER }
        k.put(root, status, 16)
    }

    private fun savePhone() { if (!::nameEt.isInitialized) return; saveNow() }
    private fun saveNow() = p.edit().putString("name", nameEt.text.toString().trim()).putString("code", pinEt.text.toString().trim()).apply()

    override fun onPause() { savePhone(); super.onPause() }

    override fun onResume() {
        super.onResume()
        if (!::who.isInitialized) return
        val sel = who.selectedItemPosition
        contacts = Store.contacts(this)
        da.clear(); da.addAll(contacts.map { "${it.first}  (${it.second})" }); da.notifyDataSetChanged()
        if (contacts.isNotEmpty()) who.setSelection(sel.coerceIn(0, contacts.size - 1))
        noPhones.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun post(ip: String, j: JSONObject): String = try {
        val addr = InetAddress.getByName(ip)
        if (!Net.sameSubnet(this, addr)) "Blocked: that phone isn't on your Wi-Fi network."
        else Socket().use { s ->
            s.connect(InetSocketAddress(addr, AlertService.PORT), 3000)
            s.getOutputStream().write((j.toString() + "\n").toByteArray()); s.getOutputStream().flush()
            "Alert sent ✓"
        }
    } catch (e: Exception) { "Couldn't reach it. Is receiving enabled on that phone? (${e.message})" }
}
