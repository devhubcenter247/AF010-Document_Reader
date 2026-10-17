package com.ui.baselib.widget.popup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * Wrapper container for popup content with rounded corners and shadow support.
 */
internal class PopupContentWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var bgColor: Int = Color.WHITE
    private var cornerRadius: Float = 0f
    private var cornerTopLeft: Float = 0f
    private var cornerTopRight: Float = 0f
    private var cornerBottomLeft: Float = 0f
    private var cornerBottomRight: Float = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val clipPath = Path()
    private val bgRect = RectF()

    private val roundOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (hasIndividualCorners()) {
                // For individual corners, use path-based outline on API 30+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    updateClipPath(view.width, view.height)
                    outline.setPath(clipPath)
                } else {
                    // Fallback to uniform radius
                    val maxRadius = maxOf(cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight)
                    outline.setRoundRect(0, 0, view.width, view.height, maxRadius)
                }
            } else {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    init {
        setWillNotDraw(false)
        outlineProvider = roundOutlineProvider
        clipToOutline = true
    }

    override fun setBackgroundColor(color: Int) {
        bgColor = color
        bgPaint.color = color
        invalidate()
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius
        cornerTopLeft = 0f
        cornerTopRight = 0f
        cornerBottomLeft = 0f
        cornerBottomRight = 0f
        updateClipPath(width, height)
        invalidateOutline()
        invalidate()
    }

    fun setCornerRadii(
        topLeft: Float = 0f,
        topRight: Float = 0f,
        bottomRight: Float = 0f,
        bottomLeft: Float = 0f
    ) {
        cornerTopLeft = topLeft
        cornerTopRight = topRight
        cornerBottomRight = bottomRight
        cornerBottomLeft = bottomLeft
        updateClipPath(width, height)
        invalidateOutline()
        invalidate()
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgRect.set(0f, 0f, w.toFloat(), h.toFloat())
        updateClipPath(w, h)
        invalidateOutline()
    }

    private fun hasIndividualCorners(): Boolean =
        cornerTopLeft > 0f || cornerTopRight > 0f || cornerBottomLeft > 0f || cornerBottomRight > 0f

    private fun getCornerRadii(w: Float, h: Float): FloatArray {
        val maxRadius = minOf(w / 2f, h / 2f)
        return if (hasIndividualCorners()) {
            floatArrayOf(
                minOf(cornerTopLeft, maxRadius), minOf(cornerTopLeft, maxRadius),
                minOf(cornerTopRight, maxRadius), minOf(cornerTopRight, maxRadius),
                minOf(cornerBottomRight, maxRadius), minOf(cornerBottomRight, maxRadius),
                minOf(cornerBottomLeft, maxRadius), minOf(cornerBottomLeft, maxRadius)
            )
        } else {
            val r = minOf(cornerRadius, maxRadius)
            floatArrayOf(r, r, r, r, r, r, r, r)
        }
    }

    private fun updateClipPath(w: Int, h: Int) {
        clipPath.reset()
        if (w <= 0 || h <= 0) return
        val radii = getCornerRadii(w.toFloat(), h.toFloat())
        clipPath.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radii, Path.Direction.CW)
        clipPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw background with rounded corners
        if (bgColor != Color.TRANSPARENT) {
            val radii = getCornerRadii(width.toFloat(), height.toFloat())
            val bgPath = Path().apply {
                addRoundRect(bgRect, radii, Path.Direction.CW)
            }
            canvas.drawPath(bgPath, bgPaint)
        }
        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Clip children to rounded corners
        if (cornerRadius > 0f || hasIndividualCorners()) {
            val save = canvas.save()
            canvas.clipPath(clipPath)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        } else {
            super.dispatchDraw(canvas)
        }
    }
}
