package com.af010.creator.docreader.ui.main.scan.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.main.scan.detection.EraseStroke
import org.opencv.core.Point

/** Shows a scanned page and lets the finger paint over the parts to remove. */
class CleanUpView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** A painted line, in pixels of the bitmap so it stays in place when the view is resized. */
    private class Stroke(val width: Float) {
        val path = Path()
        val points = mutableListOf<PointF>()
    }

    private var bitmap: Bitmap? = null
    private val strokes = mutableListOf<Stroke>()
    private var current: Stroke? = null

    private val imageRect = RectF()
    private var scale = 1f

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.accent)
    }

    /** Width of the brush on screen, in pixels. */
    var brushSize = 0f

    var onStrokesChanged: () -> Unit = {}

    val hasStrokes: Boolean get() = strokes.isNotEmpty()

    fun setImage(image: Bitmap) {
        bitmap = image
        strokes.clear()
        current = null
        layoutImage()
        invalidate()
        onStrokesChanged()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        invalidate()
        onStrokesChanged()
    }

    /** What was painted, as fractions of the image. */
    fun strokes(): List<EraseStroke> {
        val image = bitmap ?: return emptyList()
        val width = image.width.toDouble()
        val height = image.height.toDouble()
        return strokes.map { stroke ->
            EraseStroke(stroke.points.map { Point(it.x / width, it.y / height) }, stroke.width / width)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    /** Fits the image in the centre of the view. */
    private fun layoutImage() {
        val image = bitmap ?: return
        if (width == 0 || height == 0) return
        scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        imageRect.set(left, top, left + drawnWidth, top + drawnHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val image = bitmap ?: return
        canvas.drawBitmap(image, null, imageRect, bitmapPaint)
        // One layer for all strokes, so lines that cross do not get darker.
        val layer = canvas.saveLayerAlpha(imageRect, STROKE_ALPHA)
        canvas.translate(imageRect.left, imageRect.top)
        canvas.scale(scale, scale)
        strokes.forEach { stroke ->
            strokePaint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, strokePaint)
        }
        canvas.restoreToCount(layer)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val image = bitmap ?: return false
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val stroke = Stroke(brushSize / scale)
                val start = toImage(image, event.x, event.y)
                stroke.points += start
                stroke.path.moveTo(start.x, start.y)
                // A tap alone leaves a dot.
                stroke.path.lineTo(start.x + 0.01f, start.y)
                strokes += stroke
                current = stroke
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val stroke = current ?: return true
                for (i in 0 until event.historySize) {
                    stroke.add(toImage(image, event.getHistoricalX(i), event.getHistoricalY(i)))
                }
                stroke.add(toImage(image, event.x, event.y))
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (current != null) {
                    current = null
                    onStrokesChanged()
                }
            }
        }
        return true
    }

    private fun Stroke.add(point: PointF) {
        points += point
        path.lineTo(point.x, point.y)
    }

    private fun toImage(image: Bitmap, x: Float, y: Float) = PointF(
        ((x - imageRect.left) / scale).coerceIn(0f, image.width.toFloat()),
        ((y - imageRect.top) / scale).coerceIn(0f, image.height.toFloat()),
    )

    private companion object {
        const val STROKE_ALPHA = 150
    }
}
