package com.af010.creator.docreader.ui.main.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.intuit.sdp.R as Sdp

/**
 * Hairlines between the rows of a list that is itself one card (`HomeCard`), from [start] (the name's
 * start) to the card's end. The rows must be flat: no background, elevation or margin of their own.
 */
class RowDivider(@param:DimenRes private val start: Int = Sdp.dimen._53sdp) : RecyclerView.ItemDecoration() {
    private val paint = Paint()

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val res = parent.resources
        paint.color = ContextCompat.getColor(parent.context, R.color.color_stroke)
        val height = res.getDimension(Sdp.dimen._1sdp)
        val left = res.getDimension(start)
        for (i in 0 until parent.childCount - 1) {
            val child = parent.getChildAt(i)
            val y = child.bottom + child.translationY
            canvas.drawRect(left, y - height, parent.width.toFloat(), y, paint)
        }
    }
}

/**
 * The rows of a long, scrolling list drawn as one card, as the Files tab shows its recent files: the
 * surface, rim and soft shadow of `bg_home_card` behind the rows (between the list's side padding),
 * with [RowDivider]'s hairlines. The card's top and bottom corners show only at the first and last row.
 * [dividerStart] is where the hairlines start, from the card's left edge (the name's start).
 * The list needs `clipToPadding="false"` and side padding wide enough for the shadow.
 */
class CardGroupDecoration(@param:DimenRes private val dividerStart: Int = Sdp.dimen._53sdp) : RecyclerView.ItemDecoration() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.childCount
        val items = parent.adapter?.itemCount ?: 0
        if (count == 0 || items == 0) return
        val context = parent.context
        val res = parent.resources
        val radius = res.getDimension(Sdp.dimen._17sdp)
        val shadow = res.getDimension(Sdp.dimen._8sdp)

        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var first = Int.MAX_VALUE
        var last = -1
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            top = minOf(top, child.top + child.translationY)
            bottom = maxOf(bottom, child.bottom + child.translationY)
            first = minOf(first, position)
            last = maxOf(last, position)
        }
        if (last < 0) return
        // Rows scrolled out above or below: the card runs on past the edge, so no corner shows there.
        if (first > 0) top = -radius - shadow
        if (last < items - 1) bottom = parent.height + radius + shadow
        rect.set(parent.paddingLeft.toFloat(), top, (parent.width - parent.paddingRight).toFloat(), bottom)

        val shadowColor = ContextCompat.getColor(context, R.color.color_home_card_shadow)
        fill.color = ContextCompat.getColor(context, R.color.color_surface_card)
        fill.setShadowLayer(shadow, 0f, shadow / 3, ColorUtils.setAlphaComponent(shadowColor, Color.alpha(shadowColor) / 3))
        canvas.drawRoundRect(rect, radius, radius, fill)
        rim.color = ContextCompat.getColor(context, R.color.color_home_card_stroke)
        rim.strokeWidth = res.displayMetrics.density
        canvas.drawRoundRect(rect, radius, radius, rim)

        line.color = ContextCompat.getColor(context, R.color.color_stroke)
        val height = res.getDimension(Sdp.dimen._1sdp)
        val start = rect.left + res.getDimension(dividerStart)
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position == items - 1) continue
            val y = child.bottom + child.translationY
            canvas.drawRect(start, y - height, rect.right, y, line)
        }
    }
}
