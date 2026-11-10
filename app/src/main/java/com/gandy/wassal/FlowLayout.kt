package com.gandy.wassal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** حاوية بتلف العناصر (الشارات) على أكتر من سطر، ومن اليمين للشمال */
class FlowLayout(ctx: Context) : ViewGroup(ctx) {

    init {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(ctx, 6) }
    }

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(-2, -2)
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            measureChildWithMargins(c, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = c.layoutParams as MarginLayoutParams
            val w = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val h = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x + w > maxW && x > 0) { x = 0; y += rowH; rowH = 0 }
            x += w; rowH = maxOf(rowH, h)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = paddingTop; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            val lp = c.layoutParams as MarginLayoutParams
            val w = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val h = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x + w > maxW && x > 0) { x = 0; y += rowH; rowH = 0 }
            // من اليمين للشمال
            val right = r - l - paddingRight - x - lp.rightMargin
            val left = right - c.measuredWidth
            c.layout(left, y + lp.topMargin, right, y + lp.topMargin + c.measuredHeight)
            x += w; rowH = maxOf(rowH, h)
        }
    }
}
