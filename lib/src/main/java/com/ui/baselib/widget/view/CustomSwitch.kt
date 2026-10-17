package com.ui.baselib.widget.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.ui.baselib.R

class CustomSwitch @JvmOverloads constructor(
      context: Context,
      attrs: AttributeSet? = null,
      defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isChecked = false
    private var thumbPosition = 0f

    // Track colors
    private var trackColorOff = ContextCompat.getColor(context, R.color.switch_track_off)
    private var trackColorOn = ContextCompat.getColor(context, R.color.primary)

    // Thumb colors
    private var thumbColorOff = ContextCompat.getColor(context, R.color.switch_thumb_off)
    private var thumbColorOn = ContextCompat.getColor(context, R.color.white)

    // Track stroke
    private var trackStrokeWidth = 2f * resources.displayMetrics.density
    private var trackStrokeColorOff = trackColorOff
    private var trackStrokeColorOn = trackColorOn

    // Thumb stroke
    private var thumbStrokeWidth = 0f
    private var thumbStrokeColorOff = Color.TRANSPARENT
    private var thumbStrokeColorOn = Color.TRANSPARENT

    // Sizes
    private var customThumbRadius = -1f
    private var customThumbPadding = 4f * resources.displayMetrics.density
    private var customSwitchWidth = 52f * resources.displayMetrics.density
    private var customSwitchHeight = 28f * resources.displayMetrics.density

    // Thumb shadow
    private var thumbElevation = 0f
    private var thumbShadowColor = "#40000000".toColorInt()

    // Animation
    private var animDuration = 200L

    // Corner radius (-1 = auto/pill shape)
    private var customTrackCornerRadius = -1f

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Dimensions
    private var trackWidth = 0f
    private var trackHeight = 0f
    private var thumbRadius = 0f
    private var trackRect = RectF()

    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null
    private var onUserCheckedChangeListener: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.CustomSwitch, 0, 0).apply {
            try {
                // Track colors
                trackColorOff = getColor(R.styleable.CustomSwitch_uiSwitchTrackColorOff, trackColorOff)
                trackColorOn = getColor(R.styleable.CustomSwitch_uiSwitchTrackColorOn, trackColorOn)

                // Thumb colors
                thumbColorOff = getColor(R.styleable.CustomSwitch_uiSwitchThumbColorOff, thumbColorOff)
                thumbColorOn = getColor(R.styleable.CustomSwitch_uiSwitchThumbColorOn, thumbColorOn)

                // State
                isChecked = getBoolean(R.styleable.CustomSwitch_uiSwitchChecked, false)

                // Track stroke
                trackStrokeWidth =
                    getDimension(R.styleable.CustomSwitch_uiSwitchTrackStrokeWidth, trackStrokeWidth)
                trackStrokeColorOff =
                    getColor(R.styleable.CustomSwitch_uiSwitchTrackStrokeColorOff, trackColorOff)
                trackStrokeColorOn =
                    getColor(R.styleable.CustomSwitch_uiSwitchTrackStrokeColorOn, trackColorOn)

                // Thumb stroke
                thumbStrokeWidth =
                    getDimension(R.styleable.CustomSwitch_uiSwitchThumbStrokeWidth, thumbStrokeWidth)
                thumbStrokeColorOff =
                    getColor(R.styleable.CustomSwitch_uiSwitchThumbStrokeColorOff, thumbStrokeColorOff)
                thumbStrokeColorOn =
                    getColor(R.styleable.CustomSwitch_uiSwitchThumbStrokeColorOn, thumbStrokeColorOn)

                // Sizes
                customThumbRadius =
                    getDimension(R.styleable.CustomSwitch_uiSwitchThumbRadius, customThumbRadius)
                customThumbPadding =
                    getDimension(R.styleable.CustomSwitch_uiSwitchThumbPadding, customThumbPadding)
                customSwitchWidth =
                    getDimension(R.styleable.CustomSwitch_uiSwitchWidth, customSwitchWidth)
                customSwitchHeight =
                    getDimension(R.styleable.CustomSwitch_uiSwitchHeight, customSwitchHeight)

                // Thumb shadow
                thumbElevation =
                    getDimension(R.styleable.CustomSwitch_uiSwitchThumbElevation, thumbElevation)
                thumbShadowColor =
                    getColor(R.styleable.CustomSwitch_uiSwitchThumbShadowColor, thumbShadowColor)

                // Animation
                animDuration = getInt(
                    R.styleable.CustomSwitch_uiSwitchAnimationDuration,
                    animDuration.toInt()
                ).toLong()

                // Corner radius
                customTrackCornerRadius = getDimension(
                    R.styleable.CustomSwitch_uiSwitchTrackCornerRadius,
                    customTrackCornerRadius
                )
            }
            finally {
                recycle()
            }
        }

        thumbPosition = if (isChecked) 1f else 0f

        // Setup shadow paint
        if (thumbElevation > 0) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            shadowPaint.setShadowLayer(thumbElevation, 0f, thumbElevation / 2, thumbShadowColor)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = customSwitchWidth.toInt()
        val desiredHeight = customSwitchHeight.toInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        trackWidth = w.toFloat()
        trackHeight = h.toFloat()

        // Calculate thumb radius
        thumbRadius = if (customThumbRadius > 0) {
            customThumbRadius
        } else {
            (trackHeight - customThumbPadding * 2) / 2
        }

        trackRect.set(0f, 0f, trackWidth, trackHeight)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Corner radius (auto = pill shape)
        val cornerRadius = if (customTrackCornerRadius >= 0) {
            customTrackCornerRadius
        } else {
            trackHeight / 2
        }

        // Draw track background
        drawTrack(canvas, cornerRadius)

        // Draw track stroke
        drawTrackStroke(canvas, cornerRadius)

        // Calculate thumb position
        val thumbStartX = customThumbPadding + thumbRadius
        val thumbEndX = trackWidth - customThumbPadding - thumbRadius
        val thumbX = thumbStartX + (thumbEndX - thumbStartX) * thumbPosition
        val thumbY = trackHeight / 2

        // Draw thumb shadow
        if (thumbElevation > 0) {
            shadowPaint.color = lerpColor(thumbColorOff, thumbColorOn, thumbPosition)
            canvas.drawCircle(thumbX, thumbY, thumbRadius, shadowPaint)
        }

        // Draw thumb
        thumbPaint.color = lerpColor(thumbColorOff, thumbColorOn, thumbPosition)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

        // Draw thumb stroke
        if (thumbStrokeWidth > 0) {
            thumbStrokePaint.strokeWidth = thumbStrokeWidth
            thumbStrokePaint.color =
                lerpColor(thumbStrokeColorOff, thumbStrokeColorOn, thumbPosition)
            canvas.drawCircle(thumbX, thumbY, thumbRadius - thumbStrokeWidth / 2, thumbStrokePaint)
        }
    }

    private fun drawTrack(canvas: Canvas, cornerRadius: Float) {
        trackPaint.style = Paint.Style.FILL
        trackPaint.color = lerpColor(trackColorOff, trackColorOn, thumbPosition)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)
    }

    private fun drawTrackStroke(canvas: Canvas, cornerRadius: Float) {
        if (trackStrokeWidth > 0) {
            trackStrokePaint.strokeWidth = trackStrokeWidth
            trackStrokePaint.color =
                lerpColor(trackStrokeColorOff, trackStrokeColorOn, thumbPosition)

            val strokeRect = RectF(
                trackStrokeWidth / 2,
                trackStrokeWidth / 2,
                trackWidth - trackStrokeWidth / 2,
                trackHeight - trackStrokeWidth / 2
            )
            canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, trackStrokePaint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        toggle()
        return true
    }

    fun toggle() {
        setChecked(!isChecked, animate = true, fromUser = true)
    }

    /**
     * Set the checked state of the switch
     * @param checked The new checked state
     * @param animate Whether to animate the change
     * @param fromUser Whether this change was initiated by user interaction
     */
    fun setChecked(checked: Boolean, animate: Boolean = false, fromUser: Boolean = false) {
        if (isChecked == checked) return

        isChecked = checked
        onCheckedChangeListener?.invoke(isChecked)
        if (fromUser) {
            onUserCheckedChangeListener?.invoke(isChecked)
        }

        if (animate) {
            animateThumb(if (checked) 1f else 0f)
        } else {
            thumbPosition = if (checked) 1f else 0f
            invalidate()
        }
    }

    fun isChecked(): Boolean = isChecked

    /**
     * Set listener for all checked state changes (both user and programmatic)
     */
    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }

    /**
     * Set listener for only user-initiated checked state changes
     * This listener will NOT be called when setChecked() is called programmatically
     */
    fun setOnUserCheckedChangeListener(listener: (Boolean) -> Unit) {
        onUserCheckedChangeListener = listener
    }

    // Programmatic setters for customization
    fun setTrackColors(colorOff: Int, colorOn: Int) {
        trackColorOff = colorOff
        trackColorOn = colorOn
        invalidate()
    }

    fun setThumbColors(colorOff: Int, colorOn: Int) {
        thumbColorOff = colorOff
        thumbColorOn = colorOn
        invalidate()
    }

    fun setTrackStroke(width: Float, colorOff: Int, colorOn: Int) {
        trackStrokeWidth = width
        trackStrokeColorOff = colorOff
        trackStrokeColorOn = colorOn
        invalidate()
    }

    fun setThumbStroke(width: Float, colorOff: Int, colorOn: Int) {
        thumbStrokeWidth = width
        thumbStrokeColorOff = colorOff
        thumbStrokeColorOn = colorOn
        invalidate()
    }

    fun setThumbShadow(elevation: Float, color: Int = thumbShadowColor) {
        thumbElevation = elevation
        thumbShadowColor = color
        if (elevation > 0) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            shadowPaint.setShadowLayer(elevation, 0f, elevation / 2, color)
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            shadowPaint.clearShadowLayer()
        }
        invalidate()
    }

    fun setAnimationDuration(duration: Long) {
        animDuration = duration
    }

    fun setTrackCornerRadius(radius: Float) {
        customTrackCornerRadius = radius
        invalidate()
    }

    private fun animateThumb(targetPosition: Float) {
        ValueAnimator.ofFloat(thumbPosition, targetPosition).apply {
            duration = animDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                thumbPosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun lerpColor(colorA: Int, colorB: Int, t: Float): Int {
        val a =
            ((colorA shr 24) and 0xFF) + (((colorB shr 24) and 0xFF) - ((colorA shr 24) and 0xFF)) * t
        val r =
            ((colorA shr 16) and 0xFF) + (((colorB shr 16) and 0xFF) - ((colorA shr 16) and 0xFF)) * t
        val g =
            ((colorA shr 8) and 0xFF) + (((colorB shr 8) and 0xFF) - ((colorA shr 8) and 0xFF)) * t
        val b = (colorA and 0xFF) + ((colorB and 0xFF) - (colorA and 0xFF)) * t
        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
}