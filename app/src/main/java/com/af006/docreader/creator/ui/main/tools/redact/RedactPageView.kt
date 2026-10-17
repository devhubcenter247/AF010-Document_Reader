package com.af010.creator.docreader.ui.main.tools.redact

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shows one page with the boxes to black out, and lets the user drag out a new box.
 * Boxes are fractions (0..1) of the page, so they hold for any page size.
 */
class RedactPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private var page: Bitmap? = null
    private var boxes: List<RectF> = emptyList()
    private val pageRect = RectF()

    /** The box being dragged out, in view coordinates. */
    private var dragging: RectF? = null
    private var downX = 0f
    private var downY = 0f

    private val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paperPaint = Paint().apply { color = Color.WHITE }
    private val boxPaint = Paint().apply { color = Color.BLACK }
    private val dragPaint = Paint().apply { color = DRAG_COLOR }

    /** Called with a new box as fractions of the page. */
    var onBoxDrawn: (RectF) -> Unit = {}

    fun setPage(bitmap: Bitmap?, boxes: List<RectF>) {
        page = bitmap
        this.boxes = boxes
        updatePageRect()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePageRect()
    }

    private fun updatePageRect() {
        val bitmap = page
        if (bitmap == null || width == 0 || height == 0) {
            pageRect.setEmpty()
            return
        }
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val shownWidth = bitmap.width * scale
        val shownHeight = bitmap.height * scale
        val left = (width - shownWidth) / 2f
        val top = (height - shownHeight) / 2f
        pageRect.set(left, top, left + shownWidth, top + shownHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = page ?: return
        if (pageRect.isEmpty) return
        canvas.drawRect(pageRect, paperPaint)
        canvas.drawBitmap(bitmap, null, pageRect, pagePaint)
        for (box in boxes) {
            canvas.drawRect(
                pageRect.left + box.left * pageRect.width(),
                pageRect.top + box.top * pageRect.height(),
                pageRect.left + box.right * pageRect.width(),
                pageRect.top + box.bottom * pageRect.height(),
                boxPaint
            )
        }
        dragging?.let { canvas.drawRect(it, dragPaint) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageRect.isEmpty) return false
        val x = event.x.coerceIn(pageRect.left, pageRect.right)
        val y = event.y.coerceIn(pageRect.top, pageRect.bottom)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!pageRect.contains(event.x, event.y)) return false
                downX = x
                downY = y
                dragging = RectF(x, y, x, y)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                dragging?.set(min(downX, x), min(downY, y), max(downX, x), max(downY, y))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val box = dragging
                dragging = null
                // A tap is not a box.
                if (box != null && abs(box.width()) > MIN_BOX_DP * density && abs(box.height()) > MIN_BOX_DP * density) {
                    onBoxDrawn(
                        RectF(
                            (box.left - pageRect.left) / pageRect.width(),
                            (box.top - pageRect.top) / pageRect.height(),
                            (box.right - pageRect.left) / pageRect.width(),
                            (box.bottom - pageRect.top) / pageRect.height()
                        )
                    )
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = null
                invalidate()
            }
        }
        return true
    }

    private companion object {
        const val MIN_BOX_DP = 6f
        val DRAG_COLOR = Color.argb(150, 0, 0, 0)
    }
}
