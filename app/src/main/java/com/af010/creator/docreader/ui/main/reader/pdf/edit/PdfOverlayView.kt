package com.af010.creator.docreader.ui.main.reader.pdf.edit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import com.reader.pdfviewer.PDFView

/**
 * A layer over the PDF view for the edit modes (annotating, filling a form, blacking out): the
 * active [mode] gets the touches it wants and draws its frames; the rest goes to the PDF view
 * below, so pages still scroll and zoom. Placed exactly over [pdfView].
 */
@SuppressLint("ViewConstructor")
internal class PdfOverlayView(context: Context, val pdfView: PDFView) : View(context) {

    /** What the overlay does now; null lets every touch through. */
    interface Mode {
        /** A touch starts: true takes the whole gesture. */
        fun onDown(x: Float, y: Float): Boolean
        fun onMove(x: Float, y: Float) {}
        /** The gesture ends; [tap] when the finger hardly moved. */
        fun onUp(x: Float, y: Float, tap: Boolean) {}
        fun onCancel() {}
        fun draw(canvas: Canvas) {}
    }

    var mode: Mode? = null
        set(value) {
            field?.onCancel()
            field = value
            gesture = false
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var gesture = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    // pages move under the overlay: redraw when the view scrolls or zooms
    private var lastX = 0f
    private var lastY = 0f
    private var lastZoom = 0f
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (mode != null && (pdfView.currentXOffset != lastX || pdfView.currentYOffset != lastY || pdfView.zoom != lastZoom)) {
            lastX = pdfView.currentXOffset; lastY = pdfView.currentYOffset; lastZoom = pdfView.zoom
            invalidate()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val m = mode ?: return false
        // the overlay and the PDF view share their bounds, so the points are the same
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = m.onDown(x, y)
                downX = x; downY = y; moved = false
                if (gesture) invalidate()
                return gesture
            }
            MotionEvent.ACTION_MOVE -> if (gesture) {
                if (!moved && Math.hypot((x - downX).toDouble(), (y - downY).toDouble()) > slop) moved = true
                if (moved) m.onMove(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (gesture) {
                gesture = false
                m.onUp(x, y, !moved)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> if (gesture) {
                gesture = false
                m.onCancel()
                invalidate()
            }
        }
        return gesture
    }

    override fun onDraw(canvas: Canvas) {
        mode?.draw(canvas)
    }

    // ---- drawing helpers for the modes ----

    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * density; color = ACCENT
        pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val handleRadius = 7 * density
    val touchRadius = 22 * density

    /** A dashed frame with a round handle at each corner. */
    fun drawFrame(canvas: Canvas, r: RectF, handles: Boolean = true) {
        canvas.drawRect(r, frame)
        if (!handles) return
        for (p in corners(r)) {
            canvas.drawCircle(p.x, p.y, handleRadius, handle)
            canvas.drawCircle(p.x, p.y, handleRadius * 0.55f, handleFill)
        }
    }

    /** Corners in the order top left, top right, bottom left, bottom right. */
    fun corners(r: RectF) = listOf(PointF(r.left, r.top), PointF(r.right, r.top), PointF(r.left, r.bottom), PointF(r.right, r.bottom))

    /**
     * The corner of [r] under the finger (the nearest one), or -1. Inside the frame a corner only
     * reaches a third of its shorter side: the handles of a small frame (a line of text) would
     * cover it whole, and a finger meaning to move it would resize it.
     */
    fun cornerAt(r: RectF, x: Float, y: Float): Int {
        val reach = if (r.contains(x, y)) minOf(touchRadius, minOf(r.width(), r.height()) / 3) else touchRadius
        return corners(r).withIndex()
            .map { (index, p) -> index to Math.hypot((p.x - x).toDouble(), (p.y - y).toDouble()) }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first ?: -1
    }

    fun dp(value: Float) = value * density

    companion object {
        const val ACCENT = 0xFF4850F4.toInt()
    }
}
