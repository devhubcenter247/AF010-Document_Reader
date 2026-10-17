package com.ui.baselib.widget.view

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import com.ui.baselib.R

class UiLinearProgressIndicator @JvmOverloads constructor(
      context: Context,
      attrs: AttributeSet? = null,
      defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var trackThickness: Float = dpToPx(4f)
    private var trackCornerRadius: Float = dpToPx(2f)

    private var gradientStartColor: Int = "#000000".toColorInt()
    private var gradientEndColor: Int = "#FFFFFFFF".toColorInt()
    private var trackColor: Int = 0x33FFFFFF

    private var progress: Int = 0
    private var maxProgress: Int = 100
    private var animatedProgress: Float = 0f
    private var isIndeterminate: Boolean = false

    private var animationDuration: Long = 300L
    private var progressAnimator: ValueAnimator? = null
    private var indeterminateAnimatorSet: AnimatorSet? = null

    private var indicator1Head: Float = 0f
    private var indicator1Tail: Float = 0f

    private var indicator2Head: Float = 0f
    private var indicator2Tail: Float = 0f

    private val trackRect = RectF()
    private val progressRect = RectF()

    private var gradientShader: LinearGradient? = null
    private var lastWidth: Int = 0

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                it, R.styleable.UiLinearProgressIndicator, defStyleAttr, 0
            )
            try {
                gradientStartColor = typedArray.getColor(
                    R.styleable.UiLinearProgressIndicator_uiProgressGradientStart,
                    gradientStartColor
                )
                gradientEndColor = typedArray.getColor(
                    R.styleable.UiLinearProgressIndicator_uiProgressGradientEnd,
                    gradientEndColor
                )
                trackColor = typedArray.getColor(
                    R.styleable.UiLinearProgressIndicator_uiProgressTrackColor,
                    trackColor
                )
                trackThickness = typedArray.getDimension(
                    R.styleable.UiLinearProgressIndicator_uiProgressTrackThickness,
                    trackThickness
                )
                trackCornerRadius = typedArray.getDimension(
                    R.styleable.UiLinearProgressIndicator_uiProgressTrackCornerRadius,
                    trackCornerRadius
                )
                maxProgress = typedArray.getInt(
                    R.styleable.UiLinearProgressIndicator_uiProgressMax,
                    maxProgress
                )
                progress = typedArray.getInt(
                    R.styleable.UiLinearProgressIndicator_uiProgress,
                    progress
                )
                isIndeterminate = typedArray.getBoolean(
                    R.styleable.UiLinearProgressIndicator_uiProgressIndeterminate,
                    isIndeterminate
                )
                animationDuration = typedArray.getInt(
                    R.styleable.UiLinearProgressIndicator_uiProgressAnimationDuration,
                    animationDuration.toInt()
                ).toLong()
            }
            finally {
                typedArray.recycle()
            }
        }

        trackPaint.apply {
            style = Paint.Style.FILL
            color = trackColor
        }

        progressPaint.apply {
            style = Paint.Style.FILL
        }

        animatedProgress = progress.toFloat()

        if (isIndeterminate) {
            startIndeterminateAnimation()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (trackThickness + paddingTop + paddingBottom).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            height
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradientShader()
    }

    private fun updateGradientShader() {
        val contentWidth = width - paddingLeft - paddingRight
        if (contentWidth > 0 && contentWidth != lastWidth) {
            lastWidth = contentWidth
            gradientShader = LinearGradient(
                paddingLeft.toFloat(),
                0f,
                (paddingLeft + contentWidth).toFloat(),
                0f,
                gradientStartColor,
                gradientEndColor,
                Shader.TileMode.CLAMP
            )
            progressPaint.shader = gradientShader
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom

        if (contentWidth <= 0 || contentHeight <= 0) return

        val trackTop = paddingTop + (contentHeight - trackThickness) / 2
        val trackBottom = trackTop + trackThickness
        val trackLeft = paddingLeft.toFloat()
        val trackRight = (paddingLeft + contentWidth).toFloat()

        trackRect.set(trackLeft, trackTop, trackRight, trackBottom)
        canvas.drawRoundRect(trackRect, trackCornerRadius, trackCornerRadius, trackPaint)

        if (isIndeterminate) {
            drawIndeterminateProgress(
                canvas,
                trackLeft,
                trackTop,
                trackRight,
                trackBottom,
                contentWidth
            )
        } else {
            drawDeterminateProgress(canvas, trackLeft, trackTop, trackBottom, contentWidth)
        }
    }

    private fun drawDeterminateProgress(
          canvas: Canvas,
          trackLeft: Float,
          trackTop: Float,
          trackBottom: Float,
          contentWidth: Int
    ) {
        if (animatedProgress > 0) {
            val progressWidth = (contentWidth * animatedProgress / maxProgress)
            val progressRight = trackLeft + progressWidth

            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                val progressLeft = trackLeft + contentWidth - progressWidth
                progressRect.set(progressLeft, trackTop, trackLeft + contentWidth, trackBottom)
            } else {
                progressRect.set(trackLeft, trackTop, progressRight, trackBottom)
            }

            canvas.drawRoundRect(progressRect, trackCornerRadius, trackCornerRadius, progressPaint)
        }
    }

    private fun drawIndeterminateProgress(
          canvas: Canvas,
          trackLeft: Float,
          trackTop: Float,
          trackRight: Float,
          trackBottom: Float,
          contentWidth: Int
    ) {
        val start1 = trackLeft + contentWidth * indicator1Tail
        val end1 = trackLeft + contentWidth * indicator1Head
        if (end1 > start1 && end1 > trackLeft && start1 < trackRight) {
            val clampedStart1 = maxOf(start1, trackLeft)
            val clampedEnd1 = minOf(end1, trackRight)
            progressRect.set(clampedStart1, trackTop, clampedEnd1, trackBottom)
            canvas.drawRoundRect(progressRect, trackCornerRadius, trackCornerRadius, progressPaint)
        }

        val start2 = trackLeft + contentWidth * indicator2Tail
        val end2 = trackLeft + contentWidth * indicator2Head
        if (end2 > start2 && end2 > trackLeft && start2 < trackRight) {
            val clampedStart2 = maxOf(start2, trackLeft)
            val clampedEnd2 = minOf(end2, trackRight)
            progressRect.set(clampedStart2, trackTop, clampedEnd2, trackBottom)
            canvas.drawRoundRect(progressRect, trackCornerRadius, trackCornerRadius, progressPaint)
        }
    }

    fun setProgress(value: Int, animate: Boolean = true) {
        val clampedValue = value.coerceIn(0, maxProgress)
        val wasIndeterminate = isIndeterminate

        if (isIndeterminate) {
            stopIndeterminateAnimation()
            isIndeterminate = false
        }

        if (progress == clampedValue && !wasIndeterminate) return

        progress = clampedValue

        if (animate) {
            animateProgress(clampedValue.toFloat())
        } else {
            animatedProgress = clampedValue.toFloat()
            invalidate()
        }
    }

    fun getProgress(): Int = progress

    fun setMax(max: Int) {
        val clampedMax = max.coerceAtLeast(1)
        if (maxProgress == clampedMax) return

        maxProgress = clampedMax
        progressAnimator?.cancel()
        progress = progress.coerceIn(0, maxProgress)
        animatedProgress = progress.toFloat()
        invalidate()
    }

    fun getMax(): Int = maxProgress

    fun setIndeterminate(indeterminate: Boolean) {
        if (isIndeterminate == indeterminate) return

        isIndeterminate = indeterminate

        if (indeterminate) {
            startIndeterminateAnimation()
        } else {
            stopIndeterminateAnimation()
            animatedProgress = progress.toFloat()
            invalidate()
        }
    }

    fun isIndeterminate(): Boolean = isIndeterminate

    fun setGradientStartColor(color: Int) {
        gradientStartColor = color
        lastWidth = 0
        updateGradientShader()
        invalidate()
    }

    fun setGradientEndColor(color: Int) {
        gradientEndColor = color
        lastWidth = 0
        updateGradientShader()
        invalidate()
    }

    fun setGradientColors(startColor: Int, endColor: Int) {
        gradientStartColor = startColor
        gradientEndColor = endColor
        lastWidth = 0
        updateGradientShader()
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = color
        invalidate()
    }

    fun setTrackThickness(thickness: Float) {
        trackThickness = thickness
        requestLayout()
        invalidate()
    }

    fun setTrackCornerRadius(radius: Float) {
        trackCornerRadius = radius
        invalidate()
    }

    fun setAnimationDuration(duration: Long) {
        animationDuration = duration
    }

    private fun animateProgress(targetProgress: Float) {
        progressAnimator?.cancel()

        progressAnimator = ValueAnimator.ofFloat(animatedProgress, targetProgress).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                animatedProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private var phaseAnimator: ValueAnimator? = null

    private fun startIndeterminateAnimation() {
        stopIndeterminateAnimation()

        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val phase = animator.animatedValue as Float
                updateIndicatorPositions(phase)
                invalidate()
            }
            start()
        }
    }

    private fun updateIndicatorPositions(phase: Float) {
        indicator1Head = easeOutCubic(smoothStep(0f, 0.6f, phase))
        indicator1Tail = easeInQuad(smoothStep(0.1f, 0.7f, phase))

        val phase2 = (phase + 0.55f) % 1f
        indicator2Head = easeOutQuad(smoothStep(0f, 0.5f, phase2))
        indicator2Tail = easeInCubic(smoothStep(0.2f, 0.65f, phase2))
    }

    private fun smoothStep(start: Float, end: Float, value: Float): Float {
        if (value <= start) return 0f
        if (value >= end) return 1f
        return (value - start) / (end - start)
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
    private fun easeInQuad(t: Float): Float = t * t
    private fun easeInCubic(t: Float): Float = t * t * t

    private fun stopIndeterminateAnimation() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        indeterminateAnimatorSet?.cancel()
        indeterminateAnimatorSet = null
        indicator1Head = 0f
        indicator1Tail = 0f
        indicator2Head = 0f
        indicator2Tail = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isIndeterminate) {
            startIndeterminateAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        phaseAnimator?.cancel()
        indeterminateAnimatorSet?.cancel()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == VISIBLE && isIndeterminate) {
            startIndeterminateAnimation()
        } else if (visibility != VISIBLE) {
            stopIndeterminateAnimation()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}