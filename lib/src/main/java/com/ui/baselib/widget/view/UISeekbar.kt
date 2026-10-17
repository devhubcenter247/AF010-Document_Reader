package com.ui.baselib.widget.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.ui.baselib.R
import kotlin.math.roundToInt

/**
 * Themed drop-in replacement for [android.widget.SeekBar]: draggable thumb over a
 * rounded track, with solid or gradient fill, optional step/tick marks, a custom
 * thumb drawable, and a floating value label bubble while dragging.
 */
class UISeekbar @JvmOverloads constructor(
      context: Context,
      attrs: AttributeSet? = null,
      defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: UISeekbar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: UISeekbar) {}
        fun onStopTrackingTouch(seekBar: UISeekbar) {}
    }

    private var min = 0
    private var max = 100
    private var progress = 0
    private var stepSize = 0

    private var trackColor = ContextCompat.getColor(context, R.color.switch_track_off)
    private var trackHeight = 4f * resources.displayMetrics.density
    private var trackCornerRadius = -1f
    private var trackGradientStart = 0
    private var trackGradientEnd = 0
    private var hasTrackGradient = false

    private var progressColor = ContextCompat.getColor(context, R.color.primary)
    private var progressGradientStart = 0
    private var progressGradientEnd = 0
    private var hasProgressGradient = false

    private var thumbColor = ContextCompat.getColor(context, R.color.primary)
    private var thumbRadius = 8f * resources.displayMetrics.density
    private var thumbStrokeWidth = 0f
    private var thumbStrokeColor = 0
    private var thumbDrawable: Drawable? = null

    private var thumbElevation = 0f
    private var thumbShadowColor = 0x40000000

    private var showTicks = false
    private var tickColor = 0
    private var tickRadius = 2f * resources.displayMetrics.density

    private var showValueLabel = false
    private var valueLabelAlwaysVisible = false
    private var valueLabelBgColor = ContextCompat.getColor(context, R.color.primary)
    private var valueLabelTextColor = 0xFFFFFFFF.toInt()
    private var valueLabelTextSize = 11f * resources.displayMetrics.scaledDensity
    private var valueLabelPadding = 6f * resources.displayMetrics.density
    private var valueLabelCornerRadius = 6f * resources.displayMetrics.density
    private var valueLabelMinWidth = 0f
    private var valueLabelFormatter: (Int) -> String = { it.toString() }

    private val valueLabelMargin = 4f * resources.displayMetrics.density
    private val valueLabelPointerHeight = 5f * resources.displayMetrics.density
    private val valueLabelPointerHalfWidth = 5f * resources.displayMetrics.density
    private var valueLabelBubbleHeight = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valueLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valueLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val valueLabelPath = Path()

    private val trackRect = RectF()
    private val progressRect = RectF()
    private val valueLabelRect = RectF()

    private var isDragging = false

    private var lastShaderWidth = 0
    private var progressGradientShader: LinearGradient? = null
    private var trackGradientShader: LinearGradient? = null

    private var onSeekBarChangeListener: OnSeekBarChangeListener? = null
    private var onProgressChangedListener: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.UISeekbar, defStyleAttr, 0).apply {
            try {
                min = getInt(R.styleable.UISeekbar_uiSeekbarMin, min)
                max = getInt(R.styleable.UISeekbar_uiSeekbarMax, max)
                progress = getInt(R.styleable.UISeekbar_uiSeekbarProgress, progress)
                stepSize = getInt(R.styleable.UISeekbar_uiSeekbarStepSize, stepSize)

                trackColor = getColor(R.styleable.UISeekbar_uiSeekbarTrackColor, trackColor)
                trackHeight = getDimension(R.styleable.UISeekbar_uiSeekbarTrackHeight, trackHeight)
                trackCornerRadius =
                    getDimension(R.styleable.UISeekbar_uiSeekbarTrackCornerRadius, trackCornerRadius)
                if (hasValue(R.styleable.UISeekbar_uiSeekbarTrackGradientStart) &&
                    hasValue(R.styleable.UISeekbar_uiSeekbarTrackGradientEnd)
                ) {
                    trackGradientStart = getColor(R.styleable.UISeekbar_uiSeekbarTrackGradientStart, 0)
                    trackGradientEnd = getColor(R.styleable.UISeekbar_uiSeekbarTrackGradientEnd, 0)
                    hasTrackGradient = true
                }

                progressColor = getColor(R.styleable.UISeekbar_uiSeekbarProgressColor, progressColor)
                if (hasValue(R.styleable.UISeekbar_uiSeekbarProgressGradientStart) &&
                    hasValue(R.styleable.UISeekbar_uiSeekbarProgressGradientEnd)
                ) {
                    progressGradientStart = getColor(R.styleable.UISeekbar_uiSeekbarProgressGradientStart, 0)
                    progressGradientEnd = getColor(R.styleable.UISeekbar_uiSeekbarProgressGradientEnd, 0)
                    hasProgressGradient = true
                }

                thumbColor = getColor(R.styleable.UISeekbar_uiSeekbarThumbColor, thumbColor)
                thumbRadius = getDimension(R.styleable.UISeekbar_uiSeekbarThumbRadius, thumbRadius)
                thumbStrokeWidth =
                    getDimension(R.styleable.UISeekbar_uiSeekbarThumbStrokeWidth, thumbStrokeWidth)
                thumbStrokeColor = getColor(R.styleable.UISeekbar_uiSeekbarThumbStrokeColor, thumbStrokeColor)
                thumbDrawable = getDrawable(R.styleable.UISeekbar_uiSeekbarThumbDrawable)

                thumbElevation = getDimension(R.styleable.UISeekbar_uiSeekbarThumbElevation, thumbElevation)
                thumbShadowColor = getColor(R.styleable.UISeekbar_uiSeekbarThumbShadowColor, thumbShadowColor)

                showTicks = getBoolean(R.styleable.UISeekbar_uiSeekbarShowTicks, showTicks)
                tickColor = getColor(R.styleable.UISeekbar_uiSeekbarTickColor, thumbColor)
                tickRadius = getDimension(R.styleable.UISeekbar_uiSeekbarTickRadius, tickRadius)

                showValueLabel = getBoolean(R.styleable.UISeekbar_uiSeekbarShowValueLabel, showValueLabel)
                valueLabelAlwaysVisible = getBoolean(
                    R.styleable.UISeekbar_uiSeekbarValueLabelAlwaysVisible,
                    valueLabelAlwaysVisible
                )
                valueLabelBgColor =
                    getColor(R.styleable.UISeekbar_uiSeekbarValueLabelBackgroundColor, valueLabelBgColor)
                valueLabelTextColor =
                    getColor(R.styleable.UISeekbar_uiSeekbarValueLabelTextColor, valueLabelTextColor)
                valueLabelTextSize =
                    getDimension(R.styleable.UISeekbar_uiSeekbarValueLabelTextSize, valueLabelTextSize)
                valueLabelPadding =
                    getDimension(R.styleable.UISeekbar_uiSeekbarValueLabelPadding, valueLabelPadding)
                valueLabelCornerRadius = getDimension(
                    R.styleable.UISeekbar_uiSeekbarValueLabelCornerRadius,
                    valueLabelCornerRadius
                )
                valueLabelMinWidth =
                    getDimension(R.styleable.UISeekbar_uiSeekbarValueLabelMinWidth, valueLabelMinWidth)
            }
            finally {
                recycle()
            }
        }

        progress = snapToStep(progress)

        trackPaint.style = Paint.Style.FILL
        progressPaint.style = Paint.Style.FILL

        if (thumbElevation > 0) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            shadowPaint.setShadowLayer(thumbElevation, 0f, thumbElevation / 2, thumbShadowColor)
        }

        updateValueLabelMetrics()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (
            maxOf(trackHeight, thumbRadius * 2) + paddingTop + paddingBottom + topInset()
            ).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradientShaders()
    }

    private fun topInset(): Float =
        if (showValueLabel) valueLabelBubbleHeight + valueLabelMargin + valueLabelPointerHeight else 0f

    private fun updateValueLabelMetrics() {
        valueLabelTextPaint.textSize = valueLabelTextSize
        val fm = valueLabelTextPaint.fontMetrics
        valueLabelBubbleHeight = (fm.descent - fm.ascent) + valueLabelPadding * 2
    }

    private fun updateGradientShaders() {
        val start = thumbStartX()
        val end = thumbEndX()
        val shaderWidth = (end - start).roundToInt()
        if (shaderWidth <= 0 || shaderWidth == lastShaderWidth) return
        lastShaderWidth = shaderWidth

        if (hasProgressGradient) {
            progressGradientShader =
                LinearGradient(start, 0f, end, 0f, progressGradientStart, progressGradientEnd, Shader.TileMode.CLAMP)
            progressPaint.shader = progressGradientShader
        }
        if (hasTrackGradient) {
            trackGradientShader =
                LinearGradient(start, 0f, end, 0f, trackGradientStart, trackGradientEnd, Shader.TileMode.CLAMP)
            trackPaint.shader = trackGradientShader
        }
    }

    private fun thumbStartX() = paddingLeft + thumbRadius
    private fun thumbEndX() = width - paddingRight - thumbRadius

    private fun isRtl(): Boolean = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun progressRatio(): Float =
        if (max > min) (progress - min).toFloat() / (max - min).toFloat() else 0f

    /** Visual position ratio along the track (0 = trackLeft, 1 = trackRight), mirrored in RTL. */
    private fun visualRatio(logicalRatio: Float): Float =
        if (isRtl()) 1f - logicalRatio else logicalRatio

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val trackAreaTop = paddingTop + topInset()
        val trackAreaHeight = height - trackAreaTop - paddingBottom
        if (width <= 0 || trackAreaHeight <= 0) return

        val trackTop = trackAreaTop + (trackAreaHeight - trackHeight) / 2f
        val trackBottom = trackTop + trackHeight
        val trackLeft = thumbStartX()
        val trackRight = thumbEndX()
        val cornerRadius = if (trackCornerRadius >= 0) trackCornerRadius else trackHeight / 2

        if (!hasTrackGradient) trackPaint.color = trackColor
        trackRect.set(trackLeft, trackTop, trackRight, trackBottom)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        if (showTicks && stepSize > 0) {
            drawTicks(canvas, trackLeft, trackRight, trackTop, trackBottom)
        }

        val thumbX = trackLeft + (trackRight - trackLeft) * visualRatio(progressRatio())
        val rtl = isRtl()

        if (!hasProgressGradient) progressPaint.color = progressColor
        if (rtl) {
            if (thumbX < trackRight) {
                progressRect.set(thumbX, trackTop, trackRight, trackBottom)
                canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)
            }
        } else {
            if (thumbX > trackLeft) {
                progressRect.set(trackLeft, trackTop, thumbX, trackBottom)
                canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)
            }
        }

        val thumbY = trackAreaTop + trackAreaHeight / 2f

        drawThumb(canvas, thumbX, thumbY)

        if (showValueLabel && (isDragging || valueLabelAlwaysVisible)) {
            drawValueLabel(canvas, thumbX, thumbY)
        }
    }

    private fun drawTicks(
          canvas: Canvas,
          trackLeft: Float,
          trackRight: Float,
          trackTop: Float,
          trackBottom: Float
    ) {
        tickPaint.color = tickColor
        val centerY = (trackTop + trackBottom) / 2f
        val range = max - min
        if (range <= 0) return

        var value = min
        while (value <= max) {
            val ratio = (value - min).toFloat() / range.toFloat()
            val x = trackLeft + (trackRight - trackLeft) * visualRatio(ratio)
            canvas.drawCircle(x, centerY, tickRadius, tickPaint)
            value += stepSize
        }
        if (range % stepSize != 0) {
            val edgeX = if (isRtl()) trackLeft else trackRight
            canvas.drawCircle(edgeX, centerY, tickRadius, tickPaint)
        }
    }

    private fun drawThumb(canvas: Canvas, thumbX: Float, thumbY: Float) {
        if (thumbElevation > 0) {
            canvas.drawCircle(thumbX, thumbY, thumbRadius, shadowPaint)
        }

        val drawable = thumbDrawable
        if (drawable != null) {
            val left = (thumbX - thumbRadius).roundToInt()
            val top = (thumbY - thumbRadius).roundToInt()
            val right = (thumbX + thumbRadius).roundToInt()
            val bottom = (thumbY + thumbRadius).roundToInt()
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        } else {
            thumbPaint.color = thumbColor
            canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

            if (thumbStrokeWidth > 0) {
                thumbStrokePaint.strokeWidth = thumbStrokeWidth
                thumbStrokePaint.color = thumbStrokeColor
                canvas.drawCircle(thumbX, thumbY, thumbRadius - thumbStrokeWidth / 2, thumbStrokePaint)
            }
        }
    }

    private fun drawValueLabel(canvas: Canvas, thumbX: Float, thumbY: Float) {
        val text = valueLabelFormatter(progress)
        val textWidth = valueLabelTextPaint.measureText(text)
        val bubbleWidth = maxOf(textWidth + valueLabelPadding * 2, valueLabelMinWidth)
        val bubbleHalfWidth = bubbleWidth / 2f

        val bubbleBottom = thumbY - thumbRadius - valueLabelMargin - valueLabelPointerHeight
        val bubbleTop = bubbleBottom - valueLabelBubbleHeight

        var bubbleLeft = thumbX - bubbleHalfWidth
        var bubbleRight = thumbX + bubbleHalfWidth
        if (bubbleLeft < 0f) {
            bubbleRight -= bubbleLeft
            bubbleLeft = 0f
        }
        if (bubbleRight > width) {
            bubbleLeft -= (bubbleRight - width)
            bubbleRight = width.toFloat()
        }

        valueLabelBgPaint.color = valueLabelBgColor
        valueLabelRect.set(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom)
        canvas.drawRoundRect(
            valueLabelRect,
            valueLabelCornerRadius,
            valueLabelCornerRadius,
            valueLabelBgPaint
        )

        val pointerX = thumbX.coerceIn(
            bubbleLeft + valueLabelCornerRadius,
            bubbleRight - valueLabelCornerRadius
        )
        valueLabelPath.reset()
        valueLabelPath.moveTo(pointerX - valueLabelPointerHalfWidth, bubbleBottom)
        valueLabelPath.lineTo(pointerX, bubbleBottom + valueLabelPointerHeight)
        valueLabelPath.lineTo(pointerX + valueLabelPointerHalfWidth, bubbleBottom)
        valueLabelPath.close()
        canvas.drawPath(valueLabelPath, valueLabelBgPaint)

        valueLabelTextPaint.color = valueLabelTextColor
        val textY =
            bubbleTop + valueLabelBubbleHeight / 2f - (valueLabelTextPaint.descent() + valueLabelTextPaint.ascent()) / 2f
        canvas.drawText(text, (bubbleLeft + bubbleRight) / 2f, textY, valueLabelTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                updateProgressFromTouch(event.x)
                onSeekBarChangeListener?.onStartTrackingTouch(this)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateProgressFromTouch(event.x)
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onSeekBarChangeListener?.onStopTrackingTouch(this)
                    invalidate()
                    performClick()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onSeekBarChangeListener?.onStopTrackingTouch(this)
                    invalidate()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateProgressFromTouch(x: Float) {
        val start = thumbStartX()
        val end = thumbEndX()
        val positionRatio = if (end > start) ((x - start) / (end - start)).coerceIn(0f, 1f) else 0f
        val progressRatio = visualRatio(positionRatio)
        val newProgress = (min + progressRatio * (max - min)).roundToInt()
        setProgressInternal(newProgress, fromUser = true)
    }

    private fun snapToStep(value: Int): Int {
        val clamped = value.coerceIn(min, max)
        if (stepSize <= 0) return clamped
        val steps = ((clamped - min).toFloat() / stepSize).roundToInt()
        return (min + steps * stepSize).coerceIn(min, max)
    }

    private fun setProgressInternal(value: Int, fromUser: Boolean) {
        val snapped = snapToStep(value)
        if (progress == snapped) return

        progress = snapped
        invalidate()
        onSeekBarChangeListener?.onProgressChanged(this, progress, fromUser)
        onProgressChangedListener?.invoke(progress, fromUser)
    }

    fun getProgress(): Int = progress

    fun setProgress(value: Int) {
        setProgressInternal(value, fromUser = false)
    }

    fun getMin(): Int = min

    fun setMin(value: Int) {
        min = value
        progress = snapToStep(progress)
        invalidate()
    }

    fun getMax(): Int = max

    fun setMax(value: Int) {
        max = value
        progress = snapToStep(progress)
        invalidate()
    }

    fun getStepSize(): Int = stepSize

    fun setStepSize(value: Int) {
        stepSize = value
        progress = snapToStep(progress)
        invalidate()
    }

    fun setShowTicks(show: Boolean) {
        showTicks = show
        invalidate()
    }

    fun setTickColor(color: Int) {
        tickColor = color
        invalidate()
    }

    fun setTickRadius(radius: Float) {
        tickRadius = radius
        invalidate()
    }

    fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener?) {
        onSeekBarChangeListener = listener
    }

    fun setOnProgressChangedListener(listener: (progress: Int, fromUser: Boolean) -> Unit) {
        onProgressChangedListener = listener
    }

    fun setTrackColor(color: Int) {
        hasTrackGradient = false
        trackPaint.shader = null
        trackColor = color
        invalidate()
    }

    fun setTrackGradientColors(startColor: Int, endColor: Int) {
        trackGradientStart = startColor
        trackGradientEnd = endColor
        hasTrackGradient = true
        lastShaderWidth = 0
        updateGradientShaders()
        invalidate()
    }

    fun setProgressColor(color: Int) {
        hasProgressGradient = false
        progressPaint.shader = null
        progressColor = color
        invalidate()
    }

    fun setProgressGradientColors(startColor: Int, endColor: Int) {
        progressGradientStart = startColor
        progressGradientEnd = endColor
        hasProgressGradient = true
        lastShaderWidth = 0
        updateGradientShaders()
        invalidate()
    }

    fun setThumbColor(color: Int) {
        thumbColor = color
        invalidate()
    }

    fun setThumbRadius(radius: Float) {
        thumbRadius = radius
        lastShaderWidth = 0
        requestLayout()
        invalidate()
    }

    fun setThumbDrawable(drawable: Drawable?) {
        thumbDrawable = drawable
        invalidate()
    }

    fun setThumbDrawable(@DrawableRes resId: Int) {
        setThumbDrawable(ContextCompat.getDrawable(context, resId))
    }

    fun setTrackHeight(height: Float) {
        trackHeight = height
        requestLayout()
        invalidate()
    }

    fun setShowValueLabel(show: Boolean) {
        showValueLabel = show
        requestLayout()
        invalidate()
    }

    fun setValueLabelAlwaysVisible(always: Boolean) {
        valueLabelAlwaysVisible = always
        invalidate()
    }

    fun setValueLabelColors(backgroundColor: Int, textColor: Int) {
        valueLabelBgColor = backgroundColor
        valueLabelTextColor = textColor
        invalidate()
    }

    fun setValueLabelTextSize(size: Float) {
        valueLabelTextSize = size
        updateValueLabelMetrics()
        requestLayout()
        invalidate()
    }

    fun setValueLabelFormatter(formatter: (Int) -> String) {
        valueLabelFormatter = formatter
        invalidate()
    }
}
