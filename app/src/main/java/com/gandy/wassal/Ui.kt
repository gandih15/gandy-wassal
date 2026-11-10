package com.gandy.wassal

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * عدّة التصميم: كل الألوان والكروت والزراير من مكان واحد،
 * وبتدعم الوضع الليلي أوتوماتيك.
 */
object Ui {

    // ألوان البراند وألوان الصفحات
    val BRAND = Color.parseColor("#0F6E56")
    val BRAND_DARK = Color.parseColor("#085041")
    val BRAND_SOFT = Color.parseColor("#9FE1CB")
    val PAGE_COLORS = listOf(
        Color.parseColor("#0F6E56"), Color.parseColor("#993556"), Color.parseColor("#185FA5"),
        Color.parseColor("#854F0B"), Color.parseColor("#534AB7"), Color.parseColor("#993C1D")
    )
    val GREEN = Color.parseColor("#3B6D11")
    val AMBER = Color.parseColor("#854F0B")
    val RED = Color.parseColor("#A32D2D")
    val BLUE = Color.parseColor("#185FA5")

    data class Pal(val bg: Int, val surface: Int, val surface2: Int, val text: Int, val sub: Int, val border: Int)

    fun isDark(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun pal(ctx: Context) = if (isDark(ctx))
        Pal(Color.parseColor("#141413"), Color.parseColor("#1F1E1D"), Color.parseColor("#2A2927"),
            Color.parseColor("#F5F4EF"), Color.parseColor("#A8A69E"), Color.parseColor("#3A3936"))
    else
        Pal(Color.parseColor("#F5F4EF"), Color.WHITE, Color.parseColor("#F1EFE8"),
            Color.parseColor("#1F1E1D"), Color.parseColor("#6B6A64"), Color.parseColor("#E3E1D9"))

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** لون فاتح من نفس اللون (للخلفيات الناعمة) */
    fun soft(ctx: Context, color: Int): Int =
        if (isDark(ctx)) Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
        else Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))

    /** لون النص فوق الخلفية الناعمة */
    fun onSoft(ctx: Context, color: Int): Int =
        if (isDark(ctx)) lighten(color) else color

    private fun lighten(c: Int): Int {
        fun l(v: Int) = (v + (255 - v) * 0.55).toInt()
        return Color.rgb(l(Color.red(c)), l(Color.green(c)), l(Color.blue(c)))
    }

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, stroke)
    }

    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = top; bottomMargin = bottom }

    // ------------------------------------------------------------------
    // هيكل الشاشة
    // ------------------------------------------------------------------

    /**
     * شاشة كاملة: هيدر ملوّن + محتوى بيسكرول. بترجع العمود اللي هتحط فيه المحتوى.
     */
    fun screen(
        a: Activity, title: String, subtitle: String = "", color: Int = BRAND,
        back: Boolean = true, action: Pair<String, () -> Unit>? = null
    ): LinearLayout {
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pal(a).bg)
        }
        root.addView(header(a, title, subtitle, color, if (back) ({ a.finish() }) else null, action))
        val body = column(a)
        root.addView(ScrollView(a).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        a.setContentView(root)
        a.window.statusBarColor = color
        return body
    }

    /** العمود الأساسي للمحتوى */
    fun column(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val p = dp(ctx, 16); setPadding(p, p, p, dp(ctx, 28))
    }

    fun header(
        ctx: Context, title: String, subtitle: String, color: Int,
        onBack: (() -> Unit)?, action: Pair<String, () -> Unit>? = null, extraBottom: Int = 0
    ): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(color)
            setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 18) + extraBottom)
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (onBack != null) {
            row.addView(TextView(ctx).apply {
                text = "→"; textSize = 22f; setTextColor(Color.WHITE)
                setPadding(0, 0, dp(ctx, 12), 0)
                setOnClickListener { onBack() }
            })
        }
        row.addView(TextView(ctx).apply {
            text = title; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) {
            row.addView(TextView(ctx).apply {
                text = action.first; textSize = 14f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD
                background = rounded(Color.WHITE, dp(ctx, 18).toFloat())
                setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
                setOnClickListener { action.second() }
            })
        }
        box.addView(row)
        if (subtitle.isNotBlank()) {
            box.addView(TextView(ctx).apply {
                text = subtitle; textSize = 13f; setTextColor(Color.argb(210, 255, 255, 255))
                setPadding(0, dp(ctx, 4), 0, 0)
            })
        }
        return box
    }

    // ------------------------------------------------------------------
    // عناصر
    // ------------------------------------------------------------------

    fun card(ctx: Context, vertical: Boolean = false, onClick: (() -> Unit)? = null) = LinearLayout(ctx).apply {
        val p = pal(ctx)
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = if (vertical) Gravity.NO_GRAVITY else Gravity.CENTER_VERTICAL
        background = rounded(p.surface, dp(ctx, 16).toFloat(), p.border, dp(ctx, 1).coerceAtLeast(1) / 2 + 1)
        val pad = dp(ctx, 14); setPadding(pad, pad, pad, pad)
        layoutParams = lp(bottom = dp(ctx, 10))
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
    }

    fun avatar(ctx: Context, name: String, color: Int, size: Int = 42) = TextView(ctx).apply {
        text = name.trim().take(1).ifBlank { "•" }
        textSize = (size / 2.6f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(onSoft(ctx, color))
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(soft(ctx, color)) }
        layoutParams = LinearLayout.LayoutParams(dp(ctx, size), dp(ctx, size)).apply { marginEnd = dp(ctx, 12) }
    }

    fun chip(ctx: Context, s: String, color: Int, filled: Boolean = false, onClick: (() -> Unit)? = null) = TextView(ctx).apply {
        text = s; textSize = 12f
        setTextColor(if (filled) Color.WHITE else onSoft(ctx, color))
        background = rounded(if (filled) color else soft(ctx, color), dp(ctx, 14).toFloat())
        setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(ctx, 6); bottomMargin = dp(ctx, 6) }
        if (onClick != null) setOnClickListener { onClick() }
    }

    fun title(ctx: Context, s: String, size: Float = 16f) = TextView(ctx).apply {
        text = s; textSize = size; typeface = Typeface.DEFAULT_BOLD; setTextColor(pal(ctx).text)
    }

    fun text(ctx: Context, s: String, size: Float = 14f) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(pal(ctx).text); setLineSpacing(0f, 1.15f)
    }

    fun sub(ctx: Context, s: String, size: Float = 12.5f) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(pal(ctx).sub)
    }

    fun section(ctx: Context, s: String) = TextView(ctx).apply {
        text = s; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(pal(ctx).sub)
        layoutParams = lp(top = dp(ctx, 10), bottom = dp(ctx, 8))
    }

    /** عمود فيه عنوان وتحته وصف، بياخد المساحة الفاضية في الصف */
    fun titleBlock(ctx: Context, t: String, s: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(title(ctx, t, 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (s.isNotBlank()) addView(sub(ctx, s).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    /** صف قائمة جاهز: أفاتار + عنوان + وصف + شارة */
    fun listRow(
        ctx: Context, name: String, color: Int, t: String, s: String,
        badge: Pair<String, Int>? = null, onClick: (() -> Unit)? = null
    ) = card(ctx, onClick = onClick).apply {
        addView(avatar(ctx, name, color))
        addView(titleBlock(ctx, t, s))
        if (badge != null) addView(chip(ctx, badge.first, badge.second).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
        })
    }

    fun primary(ctx: Context, s: String, color: Int = BRAND, onClick: () -> Unit) = TextView(ctx).apply {
        text = s; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(color, dp(ctx, 14).toFloat())
        setPadding(0, dp(ctx, 14), 0, dp(ctx, 14))
        layoutParams = lp(top = dp(ctx, 8), bottom = dp(ctx, 6))
        setOnClickListener { onClick() }
    }

    fun secondary(ctx: Context, s: String, color: Int = BRAND, onClick: () -> Unit) = TextView(ctx).apply {
        text = s; textSize = 14.5f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSoft(ctx, color))
        background = rounded(soft(ctx, color), dp(ctx, 14).toFloat())
        setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12))
        layoutParams = lp(top = dp(ctx, 4), bottom = dp(ctx, 4))
        setOnClickListener { onClick() }
    }

    /** صف فيه أكتر من عنصر بنفس العرض */
    fun row(ctx: Context, vararg views: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) p.marginStart = dp(ctx, 8)
            addView(v, p)
        }
    }

    /** مربعات الأرقام (2 في كل صف) */
    fun stats(ctx: Context, items: List<Pair<String, String>>): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(2).forEach { pair ->
            val tiles = pair.map { (value, label) ->
                card(ctx, vertical = true).apply {
                    addView(TextView(ctx).apply { text = value; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(pal(ctx).text) })
                    addView(sub(ctx, label))
                }
            }
            col.addView(row(ctx, *tiles.toTypedArray()))
        }
        return col
    }

    fun note(ctx: Context, s: String, color: Int) = TextView(ctx).apply {
        text = s; textSize = 13f; setTextColor(onSoft(ctx, color)); setLineSpacing(0f, 1.2f)
        background = rounded(soft(ctx, color), dp(ctx, 12).toFloat())
        val p = dp(ctx, 12); setPadding(p, p, p, p)
        layoutParams = lp(top = dp(ctx, 4), bottom = dp(ctx, 10))
    }

    fun empty(ctx: Context, icon: String, t: String, s: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(ctx, 36), 0, dp(ctx, 24))
        addView(TextView(ctx).apply { text = icon; textSize = 40f; gravity = Gravity.CENTER })
        addView(title(ctx, t).apply { gravity = Gravity.CENTER; setPadding(0, dp(ctx, 8), 0, dp(ctx, 4)) })
        addView(sub(ctx, s, 13f).apply { gravity = Gravity.CENTER })
    }

    fun input(ctx: Context, hint: String, value: String = "", number: Boolean = false, lines: Int = 1) = EditText(ctx).apply {
        val p = pal(ctx)
        this.hint = hint; setText(value); textSize = 15f
        setTextColor(p.text); setHintTextColor(p.sub)
        background = rounded(p.surface, dp(ctx, 12).toFloat(), p.border, dp(ctx, 1))
        setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        inputType = when {
            number -> InputType.TYPE_CLASS_PHONE
            lines > 1 -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (lines > 1) { minLines = lines; gravity = Gravity.TOP or Gravity.START }
        layoutParams = lp(bottom = dp(ctx, 10))
    }

    fun label(ctx: Context, s: String) = sub(ctx, s, 13f).apply { setPadding(dp(ctx, 4), 0, dp(ctx, 4), dp(ctx, 6)) }

    /** خانات اختيار شكلها كروت (اختيار واحد) */
    fun choiceCards(
        ctx: Context, options: List<Triple<String, String, Int>>, selected: Int, onSelect: (Int) -> Unit
    ): LinearLayout {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun render(sel: Int) {
            box.removeAllViews()
            options.forEachIndexed { i, (t, s, color) ->
                val p = pal(ctx)
                box.addView(card(ctx, onClick = { render(i); onSelect(i) }).apply {
                    background = if (i == sel) rounded(soft(ctx, color), dp(ctx, 16).toFloat(), color, dp(ctx, 2))
                    else rounded(p.surface, dp(ctx, 16).toFloat(), p.border, dp(ctx, 1))
                    addView(TextView(ctx).apply {
                        text = if (i == sel) "◉" else "○"; textSize = 18f; setTextColor(if (i == sel) color else p.sub)
                        setPadding(0, 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(ctx, 12) }
                    })
                    addView(titleBlock(ctx, t, s))
                })
            }
        }
        render(selected)
        return box
    }

    /** مكان فاضي لملء الشاشة */
    fun spacer(ctx: Context, h: Int) = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, h)) }
}
