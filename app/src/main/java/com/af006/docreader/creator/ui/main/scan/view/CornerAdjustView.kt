package com.af010.creator.docreader.ui.main.scan.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import kotlin.math.hypot
import org.opencv.core.Point

/**
 * Shows a photo with a draggable 4-corner crop frame on top of it. While a corner is dragged, a
 * magnifier in the top corner opposite the finger shows the area under it zoomed in.
 *
 * Usage: [setImage] with the photo file, its size and the initial corners (fractions 0..1 of the
 * image), then read the adjusted corners back with [corners].
 */
class CornerAdjustView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fractions = FloatArray(8)
    private var imageWidth = 0
    private var imageHeight = 0
    private val imageRect = RectF()
    private val path = Path()
    private var draggingIndex = -1
    private var photo: Bitmap? = null
    private val magnifierClip = Path()

    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = density * 2f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 120, 79)
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = density * 4f
    }
    private val magnifierBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = density * 3f
    }
    private val magnifierLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        // Drawn inside the zoomed canvas, so divide by the zoom to keep the on-screen width at 2dp.
        strokeWidth = density * 2f / MAGNIFIER_ZOOM
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }

    /**
     * Loads [file] (whose oriented size is [width] x [height]) and places the frame at [corners].
     * Calling it again, e.g. after a rotation, replaces the previous photo.
     */
    fun setImage(file: File, width: Int, height: Int, corners: List<Point>) {
        imageWidth = width
        imageHeight = height
        corners.take(4).forEachIndexed { index, point ->
            fractions[index * 2] = point.x.toFloat().coerceIn(0f, 1f)
            fractions[index * 2 + 1] = point.y.toFloat().coerceIn(0f, 1f)
        }
        updateImageRect()
        // A new request on this view clears the previous one, which drops the old bitmap via onResourceCleared.
        photo = null
        Glide.with(this).asBitmap().load(file).into(PhotoTarget())
        invalidate()
    }

    fun corners(): List<Point> =
        (0..3).map { Point(fractions[it * 2].toDouble(), fractions[it * 2 + 1].toDouble()) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageRect()
    }

    private fun updateImageRect() {
        if (imageWidth <= 0 || imageHeight <= 0 || width == 0 || height == 0) {
            imageRect.setEmpty()
            return
        }
        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val shownWidth = imageWidth * scale
        val shownHeight = imageHeight * scale
        val left = (width - shownWidth) / 2f
        val top = (height - shownHeight) / 2f
        imageRect.set(left, top, left + shownWidth, top + shownHeight)
    }

    private fun cornerX(index: Int) = imageRect.left + fractions[index * 2] * imageRect.width()
    private fun cornerY(index: Int) = imageRect.top + fractions[index * 2 + 1] * imageRect.height()

    private fun currentPhoto(): Bitmap? = photo?.takeIf { !it.isRecycled }

    override fun onDraw(canvas: Canvas) {
        if (imageRect.isEmpty) return
        currentPhoto()?.let { canvas.drawBitmap(it, null, imageRect, photoPaint) }
        path.reset()
        path.moveTo(cornerX(0), cornerY(0))
        for (index in 1..3) path.lineTo(cornerX(index), cornerY(index))
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)
        for (index in 0..3) {
            // White dot with an orange ring; the ring is drawn inside the dot's edge.
            canvas.drawCircle(cornerX(index), cornerY(index), HANDLE_RADIUS_DP * density, handlePaint)
            canvas.drawCircle(cornerX(index), cornerY(index), (HANDLE_RADIUS_DP - 2f) * density, handleBorderPaint)
        }
        if (draggingIndex >= 0) drawMagnifier(canvas, draggingIndex)
    }

    /** Zoomed view of the dragged corner, placed in the top corner opposite the finger so it is never covered. */
    private fun drawMagnifier(canvas: Canvas, index: Int) {
        val bitmap = currentPhoto() ?: return
        val pointX = cornerX(index)
        val pointY = cornerY(index)
        val radius = MAGNIFIER_RADIUS_DP * density
        val margin = MAGNIFIER_MARGIN_DP * density
        val centerX = if (pointX >= width / 2f) margin + radius else width - margin - radius
        val centerY = margin + radius

        magnifierClip.reset()
        magnifierClip.addCircle(centerX, centerY, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(magnifierClip)
        canvas.drawColor(Color.BLACK)
        canvas.translate(centerX, centerY)
        canvas.scale(MAGNIFIER_ZOOM, MAGNIFIER_ZOOM)
        canvas.translate(-pointX, -pointY)
        canvas.drawBitmap(bitmap, null, imageRect, photoPaint)
        canvas.drawPath(path, magnifierLinePaint)
        canvas.restore()

        val arm = CROSSHAIR_ARM_DP * density
        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, crosshairPaint)
        canvas.drawCircle(centerX, centerY, radius, magnifierBorderPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageRect.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingIndex = (0..3).minByOrNull { hypot(event.x - cornerX(it), event.y - cornerY(it)) }
                    ?.takeIf { hypot(event.x - cornerX(it), event.y - cornerY(it)) <= TOUCH_RADIUS_DP * density }
                    ?: -1
                if (draggingIndex < 0) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex < 0) return false
                fractions[draggingIndex * 2] = ((event.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
                fractions[draggingIndex * 2 + 1] = ((event.y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingIndex = -1
                invalidate()
                return true
            }
        }
        return false
    }

    /** Glide target sized to this view; Glide clears it when the host fragment is destroyed. */
    private inner class PhotoTarget : CustomViewTarget<CornerAdjustView, Bitmap>(this) {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            photo = resource
            invalidate()
        }

        override fun onResourceCleared(placeholder: Drawable?) {
            photo = null
            invalidate()
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            photo = null
            invalidate()
        }
    }

    private companion object {
        val BORDER_COLOR = Color.rgb(255, 120, 79)
        const val HANDLE_RADIUS_DP = 13f
        const val TOUCH_RADIUS_DP = 40f
        const val MAGNIFIER_RADIUS_DP = 46f
        const val MAGNIFIER_MARGIN_DP = 12f
        const val MAGNIFIER_ZOOM = 2.5f
        const val CROSSHAIR_ARM_DP = 8f
    }
}
