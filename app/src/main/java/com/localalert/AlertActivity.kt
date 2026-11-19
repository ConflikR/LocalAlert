package com.localalert

import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class AlertActivity : Activity() {
    companion object {
        fun intent(ctx: Context, j: JSONObject): Intent =
            Intent(ctx, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("title", j.optString("title", "LocalAlert"))
                .putExtra("msg", j.optString("msg"))
                .putExtra("color", j.optString("color", "red"))
                .putExtra("sound", j.optBoolean("sound", true))
                .putExtra("vibrate", j.optBoolean("vibrate", true))
                .putExtra("secs", j.optInt("secs", 0))
                .putExtra("from", j.optString("from"))
    }

    private var player: Ringtone? = null
    private var anim: ValueAnimator? = null
    private var oldVol = -1
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(n: Int) = Ui.dp(this, n)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        render(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i); stopEffects(); render(i)
    }

    private fun render(i: Intent) {
        val key = i.getStringExtra("color") ?: "red"
        val accent = Ui.accent(key); val accent2 = Ui.accent2(key)
        val glow = (accent and 0x00FFFFFF) or (0x59 shl 24)

        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = resources.displayMetrics.widthPixels * 1.1f
                setGradientCenter(0.5f, 0.28f)
                colors = intArrayOf(glow, Color.BLACK)
            }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(28), dp(56), dp(28), dp(40)) }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        fun spacer() = View(this).also { col.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }

        spacer()
        val size = dp(190)
        val holder = FrameLayout(this)
        fun ring() = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(3), accent) }
        }
        val r1 = ring(); val r2 = ring()
        holder.addView(r1, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        holder.addView(r2, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        holder.addView(TextView(this).apply {
            text = "!"; textSize = 52f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accent, accent2)).apply { shape = GradientDrawable.OVAL }
        }, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        col.addView(holder, LinearLayout.LayoutParams(size, size))
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float; val g = (f + 0.5f) % 1f
                r1.scaleX = 1f + 0.7f * f; r1.scaleY = 1f + 0.7f * f; r1.alpha = 0.9f * (1f - f)
                r2.scaleX = 1f + 0.7f * g; r2.scaleY = 1f + 0.7f * g; r2.alpha = 0.9f * (1f - g)
            }
            start()
        }

        col.addView(TextView(this).apply {
            text = (i.getStringExtra("title") ?: "LocalAlert").uppercase(); textSize = 15f; letterSpacing = 0.25f
            setTypeface(null, Typeface.BOLD); setTextColor(accent); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        col.addView(TextView(this).apply {
            text = i.getStringExtra("msg"); textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; maxLines = 9; setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val from = i.getStringExtra("from") ?: ""
        if (from.isNotBlank()) col.addView(TextView(this).apply {
            text = "from $from"; textSize = 15f; setTextColor(Color.parseColor("#8A8FA3")); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        spacer()
        col.addView(Button(this).apply {
            text = "Dismiss"; isAllCaps = false; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            stateListAnimator = null; background = Ui.gradient(this@AlertActivity, accent, accent2, 30)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(58)))
        setContentView(root)
        startEffects(i.getBooleanExtra("sound", true), i.getBooleanExtra("vibrate", true), i.getIntExtra("secs", 0))
    }

    private fun startEffects(sound: Boolean, vibrate: Boolean, secs: Int) {
        val silent = getSharedPreferences("p", MODE_PRIVATE).getBoolean("silent", false)
        if (sound && !silent) try {
            val am = getSystemService(AudioManager::class.java)
            if (oldVol < 0) oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true; play()
            }
        } catch (_: Exception) {}
        if (vibrate) getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0))
        if (secs > 0) handler.postDelayed({ finish() }, secs * 1000L)
    }

    private fun stopEffects() {
        handler.removeCallbacksAndMessages(null)
        anim?.cancel(); anim = null
        try { player?.stop() } catch (_: Exception) {}; player = null
        getSystemService(Vibrator::class.java).cancel()
        if (oldVol >= 0) {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0); oldVol = -1
        }
    }

    override fun onDestroy() {
        stopEffects()
        getSystemService(NotificationManager::class.java).cancel(2)
        super.onDestroy()
    }
}
