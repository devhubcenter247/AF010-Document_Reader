package com.af010.creator.docreader.ui.main.scan.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    init {
        setWillNotDraw(false)
    }

    private var displayedPoints: FloatArray? = null
    private var animator: ValueAnimator? = null
    private val path = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EDGE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 244, 183, 64)
        style = Paint.Style.FILL
    }

    fun setMappedPoints(value: FloatArray?) {
        val target = value?.takeIf { it.size == 8 }?.copyOf()
        animator?.cancel()

        if (target == null) {
            displayedPoints = null
            postInvalidateOnAnimation()
            return
        }

        val from = displayedPoints?.copyOf() ?: run {
            displayedPoints = target
            postInvalidateOnAnimation()
            return
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRACKING_ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                displayedPoints = FloatArray(8) { index -> from[index] + (target[index] - from[index]) * fraction }
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val points = displayedPoints ?: return
        path.reset()
        path.moveTo(points[0], points[1])
        for (index in 1..3) path.lineTo(points[index * 2], points[index * 2 + 1])
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)
    }

    private companion object {
        const val TRACKING_ANIMATION_MS = 60L
        val EDGE_COLOR = Color.rgb(244, 183, 64)
    }
}
