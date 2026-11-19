package com.localalert

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

object Ui {
    val KEYS = listOf("red", "orange", "blue", "green")
    private val A = mapOf("red" to "#FF3B4E", "orange" to "#FF9020", "blue" to "#3D8BFF", "green" to "#25D17C")
    private val B = mapOf("red" to "#B0102B", "orange" to "#C2410C", "blue" to "#4C3FD1", "green" to "#0A7F5A")
    fun accent(k: String) = Color.parseColor(A[k] ?: A["red"]!!)
    fun accent2(k: String) = Color.parseColor(B[k] ?: B["red"]!!)
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, fill: Int, radius: Int, stroke: Int = 0, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c, radius).toFloat()
        if (stroke != 0) setStroke(dp(c, strokeDp), stroke)
    }

    fun gradient(c: Context, a: Int, b: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply { cornerRadius = dp(c, radius).toFloat() }
}

/** Saved phones (name + IP). */
object Store {
    fun contacts(ctx: Context): MutableList<Pair<String, String>> {
        val a = JSONArray(ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getString("contacts", "[]"))
        return MutableList(a.length()) { val o = a.getJSONObject(it); Pair(o.getString("n"), o.getString("ip")) }
    }
    fun save(ctx: Context, l: List<Pair<String, String>>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("n", it.first).put("ip", it.second)) }
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("contacts", a.toString()).apply()
    }
}

/** Black OLED page with a scrolling column; returns the column. */
fun Activity.darkPage(): LinearLayout {
    window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
    val side = Ui.dp(this, 18); val top = Ui.dp(this, 52); val bottom = Ui.dp(this, 40)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(side, top, side, bottom) }
    setContentView(ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.BLACK, Color.parseColor("#0A0618"), Color.BLACK))
        addView(root)
    })
    return root
}

class DarkAdapter(ctx: Context, items: List<String>) :
    ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items.toMutableList()) {
    init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    private val d = ctx.resources.displayMetrics.density
    override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
        val v = super.getView(pos, cv, parent) as TextView
        v.setTextColor(Color.WHITE); v.textSize = 16f
        v.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
        v.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#8A8FA3"))
        return v
    }
    override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup): View {
        val v = super.getDropDownView(pos, cv, parent) as TextView
        v.setTextColor(Color.WHITE); v.textSize = 15f; v.setBackgroundColor(Color.parseColor("#14141B"))
        v.setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
        return v
    }
}

/** Dark-themed widget factory. */
class Kit(private val a: Activity, private val root: LinearLayout) {
    val brand = Color.parseColor("#FF4D5E"); val brand2 = Color.parseColor("#FF8A3D"); val muted = Color.parseColor("#8A8FA3")
    fun dp(n: Int) = Ui.dp(a, n)

    fun put(parent: LinearLayout, v: View, top: Int = 8) =
        parent.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })

    fun card(title: String): LinearLayout {
        val c = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
            background = Ui.rounded(a, Color.parseColor("#0C0C12"), 20, Color.parseColor("#1FFFFFFF"))
        }
        c.addView(TextView(a).apply { text = title; textSize = 12f; letterSpacing = 0.15f; setTextColor(brand); setTypeface(null, Typeface.BOLD) })
        put(root, c, 14); return c
    }

    fun small(t: String) = TextView(a).apply { text = t; textSize = 12f; setTextColor(muted) }

    fun edit(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(a).apply {
        this.hint = hint; setText(value); inputType = type; textSize = 15f
        setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#6B6F80"))
        background = Ui.rounded(a, Color.parseColor("#14141C"), 12, Color.parseColor("#26FFFFFF"))
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    fun btn(t: String, primary: Boolean = false, onClick: () -> Unit) = Button(a).apply {
        text = t; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE); stateListAnimator = null
        minHeight = dp(48); minimumHeight = dp(48)
        setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        background = if (primary) Ui.gradient(a, brand, brand2, 24) else Ui.rounded(a, Color.TRANSPARENT, 24, Color.parseColor("#33FFFFFF"))
        setOnClickListener { onClick() }
    }

    fun spinner(items: List<String>, sel: Int = 0) = Spinner(a).apply {
        adapter = DarkAdapter(a, items); if (items.isNotEmpty()) setSelection(sel)
        background = Ui.rounded(a, Color.parseColor("#14141C"), 12, Color.parseColor("#26FFFFFF"))
        setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#14141B")))
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    fun check(t: String, on: Boolean) = CheckBox(a).apply {
        text = t; isChecked = on; setTextColor(Color.WHITE); buttonTintList = ColorStateList.valueOf(brand)
    }
}
