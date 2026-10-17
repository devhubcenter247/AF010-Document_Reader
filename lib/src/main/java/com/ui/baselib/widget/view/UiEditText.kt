package com.ui.baselib.widget.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.withSave
import com.ui.baselib.R
import kotlin.math.max
import com.ui.baselib.widget.layout.getColorList

class UiEditText @JvmOverloads constructor(
      context: Context,
      attrs: AttributeSet? = null,
      defStyleAttr: Int = 0
) : AppCompatEditText(context, attrs, defStyleAttr) {

    enum class BgGradientType { LINEAR, RADIAL, SWEEP }
    enum class DrawableAlign { START, END, TOP, BOTTOM }

    private var cornerRadius = 0f

    // Drawable state variables
    private var drawableEmpty: Drawable? = null
    private var drawableNotEmpty: Drawable? = null
    private var drawableAlign = DrawableAlign.END
    private var drawableSize = 0
    private var drawablePadding = 0
    private var drawableTint = Color.TRANSPARENT
    private var drawableEmptyTint = Color.TRANSPARENT
    private var drawableNotEmptyTint = Color.TRANSPARENT
    private var drawableEmptyVisible = true
    private var drawableNotEmptyVisible = true
    private var onDrawableEmptyClick: ((DrawableAlign) -> Unit)? = null
    private var onDrawableNotEmptyClick: ((DrawableAlign) -> Unit)? = null
    private var onDrawableClick: (() -> Unit)? = null
    private var currentDrawable: Drawable? = null
    private var isGradient = false
    private var bgGradientStart = Color.TRANSPARENT
    private var bgGradientCenter = Color.TRANSPARENT
    private var bgGradientEnd = Color.TRANSPARENT
    private var bgGradientOrientation = GradientOrientation.TOP_TO_BOTTOM
    private var bgGradientType = BgGradientType.LINEAR
    private var bgGradientCenterX = 0.5f
    private var bgGradientCenterY = 0.5f
    private var bgGradientRadius = 0f
    private var bgColor = Color.TRANSPARENT
    private var focusBgColor = Color.TRANSPARENT
    private var isFocusGradient = false
    private var focusBgGradientStart = Color.TRANSPARENT
    private var focusBgGradientCenter = Color.TRANSPARENT
    private var focusBgGradientEnd = Color.TRANSPARENT
    private var focusBgGradientOrientation = GradientOrientation.TOP_TO_BOTTOM
    private var focusBgGradientType = BgGradientType.LINEAR
    private var focusBgGradientCenterX = 0.5f
    private var focusBgGradientCenterY = 0.5f
    private var focusBgGradientRadius = 0f
    private var stWidth = 0f
    private var stColor = Color.TRANSPARENT
    private var strokeGradient: IntArray? = null
    private var isStrokeGradient = false
    private var stColors: IntArray? = null
    private var bgColors: IntArray? = null
    private var strokeGradientOrientation = GradientOrientation.LEFT_TO_RIGHT
    private var isDashed = false
    private var dashSpace = 10f
    private var focusStWidth = 0f
    private var focusStColor = Color.TRANSPARENT
    private var focusStrokeGradient: IntArray? = null
    private var focusStrokeGradientOrientation = GradientOrientation.LEFT_TO_RIGHT
    private var isFocusDashed = false
    private var focusDashSpace = 10f
    private var lineOption: LineOption = LineOption.NONE
    private var tColor = currentTextColor
    private var tColorHint = currentHintTextColor
    private var textGradient = false
    private var textGradientStart = Color.TRANSPARENT
    private var textGradientEnd = Color.TRANSPARENT
    private var textGradientOrientation = TextGradientOrientation.LEFT_TO_RIGHT
    private val clipPath = Path()
    private val strokeRectF = RectF()
    private val bgRectF = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val topLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        context.obtainStyledAttributes(attrs, R.styleable.UiEditText).apply {
            try {
                cornerRadius = getDimension(R.styleable.UiEditText_uiCornerRadius, 0f)

                isGradient = getBoolean(R.styleable.UiEditText_uiBackgroundGradientEnabled, false)
                bgGradientStart =
                    getColor(R.styleable.UiEditText_uiBackgroundGradientStart, Color.TRANSPARENT)
                bgGradientCenter =
                    getColor(R.styleable.UiEditText_uiBackgroundGradientCenter, Color.TRANSPARENT)
                bgGradientEnd = getColor(R.styleable.UiEditText_uiBackgroundGradientEnd, Color.TRANSPARENT)
                bgColor = getColor(R.styleable.UiEditText_uiBackgroundColor, Color.TRANSPARENT)
                bgGradientOrientation =
                    getInt(R.styleable.UiEditText_uiBackgroundGradientOrientation, 0).toGradientOrientation()
                bgGradientType = getInt(R.styleable.UiEditText_uiBackgroundGradientType, 0).toBgGradientType()
                bgGradientCenterX = getFloat(R.styleable.UiEditText_uiBackgroundGradientCenterX, 0.5f)
                bgGradientCenterY = getFloat(R.styleable.UiEditText_uiBackgroundGradientCenterY, 0.5f)
                bgGradientRadius = getDimension(R.styleable.UiEditText_uiBackgroundGradientRadius, 0f)
                // "#F00 #0F0" or an @array/ of colors
                bgColors = getColorList(R.styleable.UiEditText_uiBackgroundGradientColors)

                focusBgColor = getColor(R.styleable.UiEditText_uiFocusedBackgroundColor, bgColor)
                isFocusGradient = getBoolean(R.styleable.UiEditText_uiFocusedBackgroundGradientEnabled, false)
                focusBgGradientStart =
                    getColor(R.styleable.UiEditText_uiFocusedBackgroundGradientStart, Color.TRANSPARENT)
                focusBgGradientCenter =
                    getColor(R.styleable.UiEditText_uiFocusedBackgroundGradientCenter, Color.TRANSPARENT)
                focusBgGradientEnd =
                    getColor(R.styleable.UiEditText_uiFocusedBackgroundGradientEnd, Color.TRANSPARENT)
                focusBgGradientOrientation = bgGradientOrientation
                focusBgGradientType = getInt(
                    R.styleable.UiEditText_uiFocusedBackgroundGradientType,
                    bgGradientType.ordinal
                ).toBgGradientType()
                focusBgGradientCenterX =
                    getFloat(R.styleable.UiEditText_uiFocusedBackgroundGradientCenterX, bgGradientCenterX)
                focusBgGradientCenterY =
                    getFloat(R.styleable.UiEditText_uiFocusedBackgroundGradientCenterY, bgGradientCenterY)
                focusBgGradientRadius =
                    getDimension(R.styleable.UiEditText_uiFocusedBackgroundGradientRadius, bgGradientRadius)

                stWidth = getDimension(R.styleable.UiEditText_uiStrokeWidth, 0f)
                stColor = getColor(R.styleable.UiEditText_uiStrokeColor, Color.TRANSPARENT)
                isDashed = getBoolean(R.styleable.UiEditText_uiStrokeDashed, false)
                dashSpace = getDimension(R.styleable.UiEditText_uiStrokeDashGap, 10f)
                // Priority 1: Read from strokeGradientStart/Center/End color attrs
                val stGradientStart =
                    getColor(R.styleable.UiEditText_uiStrokeGradientStart, Color.TRANSPARENT)
                val stGradientCenter =
                    getColor(R.styleable.UiEditText_uiStrokeGradientCenter, Color.TRANSPARENT)
                val stGradientEnd =
                    getColor(R.styleable.UiEditText_uiStrokeGradientEnd, Color.TRANSPARENT)
                if (stGradientStart != Color.TRANSPARENT || stGradientEnd != Color.TRANSPARENT) {
                    strokeGradient = if (stGradientCenter != Color.TRANSPARENT) {
                        intArrayOf(stGradientStart, stGradientCenter, stGradientEnd)
                    } else {
                        intArrayOf(stGradientStart, stGradientEnd)
                    }
                }
                // A color list ("#F00 #0F0" or @array/) overrides the start/center/end attrs above
                getColorList(R.styleable.UiEditText_uiStrokeGradientColors)
                    ?.let { strokeGradient = it }
                isStrokeGradient = if (hasValue(R.styleable.UiEditText_uiStrokeGradient)) {
                    getBoolean(R.styleable.UiEditText_uiStrokeGradient, false)
                } else {
                    strokeGradient?.size?.let { it >= 2 } == true
                }

                strokeGradientOrientation =
                    getInt(R.styleable.UiEditText_uiStrokeGradientOrientation, 6).toStrokeOrientation()

                focusStWidth = getDimension(R.styleable.UiEditText_uiFocusedStrokeWidth, stWidth)
                focusStColor = getColor(R.styleable.UiEditText_uiFocusedStrokeColor, stColor)
                focusStrokeGradientOrientation = strokeGradientOrientation
                isFocusDashed = getBoolean(R.styleable.UiEditText_uiFocusedStrokeDashed, isDashed)
                focusDashSpace = getDimension(R.styleable.UiEditText_uiFocusedStrokeDashGap, dashSpace)
                focusStrokeGradient = getColorList(R.styleable.UiEditText_uiFocusedStrokeGradientColors)

                tColor = getColor(R.styleable.UiEditText_uiTextColor, currentTextColor)
                tColorHint = getColor(R.styleable.UiEditText_uiTextColorHint, currentHintTextColor)

                lineOption = LineOption.fromValue(getInt(R.styleable.UiEditText_uiTextDecoration, 0))
                textGradient = getBoolean(R.styleable.UiEditText_uiTextGradientEnabled, false)
                textGradientStart =
                    getColor(R.styleable.UiEditText_uiTextGradientStart, Color.TRANSPARENT)
                textGradientEnd =
                    getColor(R.styleable.UiEditText_uiTextGradientEnd, Color.TRANSPARENT)
                textGradientOrientation =
                    getInt(R.styleable.UiEditText_uiTextGradientOrientation, 6).toTextOrientation()

                // Drawable attributes
                drawableEmpty = getDrawable(R.styleable.UiEditText_uiDrawableEmpty)
                drawableNotEmpty = getDrawable(R.styleable.UiEditText_uiDrawableNotEmpty)
                drawableAlign = getInt(R.styleable.UiEditText_uiDrawablePosition, 1).toDrawableAlign()
                drawableSize = getDimensionPixelSize(R.styleable.UiEditText_uiDrawableSize, 0)
                drawablePadding = getDimensionPixelSize(R.styleable.UiEditText_uiDrawablePadding, 0)
                drawableTint = getColor(R.styleable.UiEditText_uiDrawableTint, Color.TRANSPARENT)
                drawableEmptyTint = getColor(R.styleable.UiEditText_uiDrawableEmptyTint, drawableTint)
                drawableNotEmptyTint = getColor(R.styleable.UiEditText_uiDrawableNotEmptyTint, drawableTint)
                drawableEmptyVisible = getBoolean(R.styleable.UiEditText_uiDrawableEmptyVisible, true)
                drawableNotEmptyVisible = getBoolean(R.styleable.UiEditText_uiDrawableNotEmptyVisible, true)
            }
            finally {
                recycle()
            }
        }

        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true

        applyTextStyles()
        setupDrawableTextWatcher()
        updateDrawableState()
    }

    private fun Int.toDrawableAlign() = DrawableAlign.entries
        .getOrElse(this) { DrawableAlign.END }

    private fun setupDrawableTextWatcher() {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateDrawableState()
            }
        })
    }

    private fun updateDrawableState() {
        val isEmpty = text.isNullOrEmpty()
        val visible = if (isEmpty) drawableEmptyVisible else drawableNotEmptyVisible
        val newDrawable = if (isEmpty) drawableEmpty else drawableNotEmpty

        currentDrawable = newDrawable?.mutate()

        // Apply size if specified
        currentDrawable?.let { drawable ->
            if (drawableSize > 0) {
                drawable.setBounds(0, 0, drawableSize, drawableSize)
            } else {
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            }

            // When not visible, keep drawable for space but make it invisible
            drawable.alpha = if (visible) 255 else 0

            // Apply tint only when visible
            if (visible) {
                val tint = if (isEmpty) {
                    if (drawableEmptyTint != Color.TRANSPARENT) drawableEmptyTint else drawableTint
                } else {
                    if (drawableNotEmptyTint != Color.TRANSPARENT) drawableNotEmptyTint else drawableTint
                }
                if (tint != Color.TRANSPARENT) {
                    DrawableCompat.setTint(drawable, tint)
                }
            }
        }

        applyDrawable()
    }

    private fun applyDrawable() {
        val (start, top, end, bottom) = when (drawableAlign) {
            DrawableAlign.START -> arrayOf(currentDrawable, null, null, null)
            DrawableAlign.END -> arrayOf(null, null, currentDrawable, null)
            DrawableAlign.TOP -> arrayOf(null, currentDrawable, null, null)
            DrawableAlign.BOTTOM -> arrayOf(null, null, null, currentDrawable)
        }
        setCompoundDrawablesRelative(start, top, end, bottom)
        if (drawablePadding > 0) {
            compoundDrawablePadding = drawablePadding
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && currentDrawable != null) {
            val drawableBounds = currentDrawable?.bounds ?: return super.onTouchEvent(event)
            val touchX = event.x.toInt()
            val touchY = event.y.toInt()

            val isDrawableClicked = when (drawableAlign) {
                DrawableAlign.END -> {
                    val drawableStart = width - paddingEnd - drawableBounds.width() - compoundDrawablePadding
                    touchX >= drawableStart
                }
                DrawableAlign.START -> {
                    val drawableEnd = paddingStart + drawableBounds.width() + compoundDrawablePadding
                    touchX <= drawableEnd
                }
                DrawableAlign.TOP -> {
                    val drawableBottom = paddingTop + drawableBounds.height() + compoundDrawablePadding
                    touchY <= drawableBottom
                }
                DrawableAlign.BOTTOM -> {
                    val drawableTop = height - paddingBottom - drawableBounds.height() - compoundDrawablePadding
                    touchY >= drawableTop
                }
            }

            if (isDrawableClicked) {
                val isEmpty = text.isNullOrEmpty()
                val visible = if (isEmpty) drawableEmptyVisible else drawableNotEmptyVisible
                if (visible) {
                    when {
                        isEmpty && onDrawableEmptyClick != null -> {
                            onDrawableEmptyClick?.invoke(drawableAlign)
                            return true
                        }
                        !isEmpty && onDrawableNotEmptyClick != null -> {
                            onDrawableNotEmptyClick?.invoke(drawableAlign)
                            return true
                        }
                        onDrawableClick != null -> {
                            onDrawableClick?.invoke()
                            return true
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private var hasFocusState = false
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        hasFocusState = gainFocus
        invalidate()
    }

    private fun applyTextStyles() {
        if (textGradient) {
            setGradient(textGradientStart, textGradientEnd, textGradientOrientation)
        } else {
            setTextColor(tColor)
        }

        setHintTextColor(tColorHint)

        // Clear line flags first
        paintFlags = paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv() and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // Apply line option
        when (lineOption) {
            LineOption.BOTTOM -> paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            LineOption.CENTER -> paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            LineOption.TOP, LineOption.NONE -> { /* TOP drawn manually, NONE does nothing */ }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }
        val focused = super.isFocused
        val radius = minOf(w / 2f, h / 2f, cornerRadius)
        bgPaint.shader = null
        bgPaint.alpha = 255
        bgPaint.style = Paint.Style.FILL
        if (focused && isFocusGradient) {
            val colors = if (focusBgGradientCenter != Color.TRANSPARENT) {
                intArrayOf(focusBgGradientStart, focusBgGradientCenter, focusBgGradientEnd)
            } else {
                intArrayOf(focusBgGradientStart, focusBgGradientEnd)
            }
            bgPaint.shader = createGradientShader(
                w,
                h,
                colors,
                focusBgGradientType,
                focusBgGradientOrientation,
                focusBgGradientCenterX,
                focusBgGradientCenterY,
                focusBgGradientRadius
            )
        } else {
            val colors = bgColors
            if (colors != null && colors.size >= 2) {
                // Use bgColors array (highest priority)
                bgPaint.shader = createGradientShader(
                    w,
                    h,
                    colors,
                    bgGradientType,
                    bgGradientOrientation,
                    bgGradientCenterX,
                    bgGradientCenterY,
                    bgGradientRadius
                )
            } else if (isGradient) {
                val colors = if (bgGradientCenter != Color.TRANSPARENT) {
                    intArrayOf(bgGradientStart, bgGradientCenter, bgGradientEnd)
                } else {
                    intArrayOf(bgGradientStart, bgGradientEnd)
                }
                bgPaint.shader = createGradientShader(
                    w,
                    h,
                    colors,
                    bgGradientType,
                    bgGradientOrientation,
                    bgGradientCenterX,
                    bgGradientCenterY,
                    bgGradientRadius
                )
            } else {
                bgPaint.color = if (focused) focusBgColor else bgColor
            }
        }

        // Translate canvas to compensate for scroll offset so background stays fixed
        canvas.withSave {
            translate(scrollX.toFloat(), scrollY.toFloat())
            drawRoundRect(0f, 0f, w, h, radius, radius, bgPaint)
            clipPath.reset()
            bgRectF.set(0f, 0f, w, h)
            clipPath.addRoundRect(bgRectF, radius, radius, Path.Direction.CW)
            runCatching { clipPath(this@UiEditText.clipPath) }
        }
        super.onDraw(canvas)

        // Draw top line manually (no built-in Paint flag for this)
        if (lineOption == LineOption.TOP) {
            drawTopLine(canvas)
        }
    }

    private fun drawTopLine(canvas: Canvas) {
        val textStr = text?.toString().orEmpty()
        if (textStr.isEmpty()) return

        val textWidth = paint.measureText(textStr)
        val startX = when (gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK) {
            android.view.Gravity.CENTER_HORIZONTAL -> (width - textWidth) / 2f
            android.view.Gravity.RIGHT, android.view.Gravity.END -> width - paddingEnd - textWidth
            else -> paddingStart.toFloat()
        }
        val endX = startX + textWidth

        // Position line at the top of the text (ascent line)
        val fontMetrics = paint.fontMetrics
        val baseline = when (gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK) {
            android.view.Gravity.CENTER_VERTICAL -> (height - fontMetrics.bottom - fontMetrics.top) / 2f
            android.view.Gravity.BOTTOM -> height - paddingBottom - fontMetrics.bottom
            else -> paddingTop - fontMetrics.top
        }
        val lineY = baseline + fontMetrics.top

        topLinePaint.set(paint)
        topLinePaint.style = Paint.Style.STROKE
        topLinePaint.strokeWidth = textSize / 12f
        canvas.drawLine(startX, lineY, endX, lineY, topLinePaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val radius = minOf(w / 2f, h / 2f, cornerRadius)
        val focused = super.isFocused
        val currentStWidth = if (focused) focusStWidth else stWidth
        if (currentStWidth > 0f) {
            val maxStrokeWidth = minOf(w, h)
            val dashed = if (focused) isFocusDashed else isDashed
            val space = if (focused) focusDashSpace else dashSpace
            val hasValidDashEffect = dashed && space > 0f
            val safeStrokeWidth = if (hasValidDashEffect && currentStWidth >= maxStrokeWidth) {
                (maxStrokeWidth - 1f).coerceAtLeast(0f)
            } else {
                currentStWidth.coerceAtMost(maxStrokeWidth)
            }
            strokePaint.shader = null
            strokePaint.alpha = 255
            strokePaint.pathEffect = null
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = safeStrokeWidth
            strokePaint.strokeJoin = Paint.Join.ROUND
            if (hasValidDashEffect) {
                strokePaint.pathEffect = DashPathEffect(floatArrayOf(space, space), 0f)
            }
            // Priority: stColors > strokeGradient > solid color
            val gradColors = stColors ?: if (focused) {
                focusStrokeGradient
            } else {
                strokeGradient?.takeIf { isStrokeGradient }
            }
            val ori = if (focused) focusStrokeGradientOrientation else strokeGradientOrientation
            if (gradColors != null && gradColors.size >= 2) {
                val (x0, y0, x1, y1) = ori.toCoordinates(w, h)
                strokePaint.shader = LinearGradient(x0, y0, x1, y1, gradColors, null, Shader.TileMode.CLAMP)
            } else {
                strokePaint.color = if (focused) focusStColor else stColor
            }
            // Translate canvas to compensate for scroll offset so border stays fixed
            canvas.withSave {
                translate(scrollX.toFloat(), scrollY.toFloat())
                if (!hasValidDashEffect && currentStWidth >= maxStrokeWidth) {
                    strokePaint.style = Paint.Style.FILL
                    drawRoundRect(0f, 0f, w, h, radius, radius, strokePaint)
                } else if (safeStrokeWidth > 0f) {
                    val inset = safeStrokeWidth / 2f
                    strokeRectF.set(inset, inset, w - inset, h - inset)
                    val strokeRadius = max(0f, radius - inset)
                    drawRoundRect(strokeRectF, strokeRadius, strokeRadius, strokePaint)
                }
            }
        }
    }

    private fun createGradientShader(
          w: Float, h: Float, colors: IntArray,
          type: BgGradientType, orientation: GradientOrientation,
          centerX: Float, centerY: Float, radius: Float
    ): Shader {
        return when (type) {
            BgGradientType.LINEAR -> {
                val (x0, y0, x1, y1) = orientation.toCoordinates(w, h)
                LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
            }
            BgGradientType.RADIAL -> {
                val cx = w * centerX
                val cy = h * centerY
                val r = if (radius > 0f) radius else kotlin.math.max(w, h) / 2f
                RadialGradient(cx, cy, r, colors, null, Shader.TileMode.CLAMP)
            }
            BgGradientType.SWEEP -> {
                val cx = w * centerX
                val cy = h * centerY
                SweepGradient(cx, cy, colors, null)
            }
        }
    }

    fun setGradient(startColor: Int, endColor: Int, orientation: TextGradientOrientation) {
        val width = paint.measureText(text.toString())
        val height = textSize * 1.3f
        val (x0, y0, x1, y1) = orientation.toCoordinates(width, height)

        paint.shader = LinearGradient(x0, y0, x1, y1, startColor, endColor, Shader.TileMode.CLAMP)
        invalidate()
    }

    enum class TextGradientOrientation {
        TOP_TO_BOTTOM, TR_BL, RIGHT_TO_LEFT, BR_TL,
        BOTTOM_TO_TOP, BL_TR, LEFT_TO_RIGHT, TL_BR
    }

    enum class GradientOrientation {
        TOP_TO_BOTTOM, TR_BL, RIGHT_TO_LEFT, BR_TL,
        BOTTOM_TO_TOP, BL_TR, LEFT_TO_RIGHT, TL_BR
    }

    private fun Int.toTextOrientation() = TextGradientOrientation.entries
        .getOrElse(this) { TextGradientOrientation.LEFT_TO_RIGHT }

    private fun Int.toStrokeOrientation() = UiEditText.GradientOrientation.entries
        .getOrElse(this) { GradientOrientation.LEFT_TO_RIGHT }

    private fun Int.toGradientOrientation() = UiEditText.GradientOrientation.entries
        .getOrElse(this) { GradientOrientation.TOP_TO_BOTTOM }

    private fun Int.toBgGradientType() = BgGradientType.entries
        .getOrElse(this) { BgGradientType.LINEAR }

    private fun TextGradientOrientation.toCoordinates(w: Float, h: Float) = when (this) {
        TextGradientOrientation.TOP_TO_BOTTOM -> Quad(0f, 0f, 0f, h)
        TextGradientOrientation.BOTTOM_TO_TOP -> Quad(0f, h, 0f, 0f)
        TextGradientOrientation.LEFT_TO_RIGHT -> Quad(0f, 0f, w, 0f)
        TextGradientOrientation.RIGHT_TO_LEFT -> Quad(w, 0f, 0f, 0f)
        TextGradientOrientation.TL_BR -> Quad(0f, 0f, w, h)
        TextGradientOrientation.TR_BL -> Quad(w, 0f, 0f, h)
        TextGradientOrientation.BL_TR -> Quad(0f, h, w, 0f)
        TextGradientOrientation.BR_TL -> Quad(w, h, 0f, 0f)
    }

    private fun GradientOrientation.toCoordinates(w: Float, h: Float) = when (this) {
        GradientOrientation.TOP_TO_BOTTOM -> Quad(0f, 0f, 0f, h)
        GradientOrientation.BOTTOM_TO_TOP -> Quad(0f, h, 0f, 0f)
        GradientOrientation.LEFT_TO_RIGHT -> Quad(0f, 0f, w, 0f)
        GradientOrientation.RIGHT_TO_LEFT -> Quad(w, 0f, 0f, 0f)
        GradientOrientation.TL_BR -> Quad(0f, 0f, w, h)
        GradientOrientation.TR_BL -> Quad(w, 0f, 0f, h)
        GradientOrientation.BL_TR -> Quad(0f, h, w, 0f)
        GradientOrientation.BR_TL -> Quad(w, h, 0f, 0f)
    }

    private data class Quad(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    fun cornerRadius(radius: Float) = apply {
        cornerRadius = radius
        invalidate()
    }

    fun background(color: Int) = apply {
        isGradient = false
        bgColor = color
        invalidate()
    }

    fun backgroundGradientStart(color: Int) = apply {
        isGradient = true
        bgGradientStart = color
        invalidate()
    }

    fun backgroundGradientCenter(color: Int) = apply {
        isGradient = true
        bgGradientCenter = color
        invalidate()
    }

    fun backgroundGradient(start: Int, end: Int, center: Int) = apply {
        isGradient = true
        bgGradientStart = start
        bgGradientCenter = center
        bgGradientEnd = end
        invalidate()
    }

    fun backgroundGradientEnd(color: Int) = apply {
        isGradient = true
        bgGradientEnd = color
        invalidate()
    }

    fun backgroundOrientation(orientation: GradientOrientation) = apply {
        isGradient = true
        bgGradientOrientation = orientation
        invalidate()
    }

    fun backgroundGradientType(type: BgGradientType) = apply {
        isGradient = true
        bgGradientType = type
        invalidate()
    }

    fun linearGradient() = backgroundGradientType(BgGradientType.LINEAR)

    fun radialGradient() = backgroundGradientType(BgGradientType.RADIAL)

    fun sweepGradient() = backgroundGradientType(BgGradientType.SWEEP)

    fun gradientCenter(centerX: Float, centerY: Float) = apply {
        bgGradientCenterX = centerX
        bgGradientCenterY = centerY
        invalidate()
    }

    fun gradientRadius(radius: Float) = apply {
        bgGradientRadius = radius
        invalidate()
    }

    fun radialGradient(centerX: Float, centerY: Float, radius: Float = 0f) = apply {
        isGradient = true
        bgGradientType = BgGradientType.RADIAL
        bgGradientCenterX = centerX
        bgGradientCenterY = centerY
        bgGradientRadius = radius
        invalidate()
    }

    fun sweepGradient(centerX: Float, centerY: Float) = apply {
        isGradient = true
        bgGradientType = BgGradientType.SWEEP
        bgGradientCenterX = centerX
        bgGradientCenterY = centerY
        invalidate()
    }

    fun focusBackground(color: Int) = apply {
        isFocusGradient = false
        focusBgColor = color
        invalidate()
    }

    fun focusBackgroundGradientStart(color: Int) = apply {
        isFocusGradient = true
        focusBgGradientStart = color
        invalidate()
    }

    fun focusBackgroundGradientCenter(color: Int) = apply {
        isFocusGradient = true
        focusBgGradientCenter = color
        invalidate()
    }

    fun focusBackgroundGradient(start: Int, end: Int, center: Int) = apply {
        isFocusGradient = true
        focusBgGradientStart = start
        focusBgGradientCenter = center
        focusBgGradientEnd = end
        invalidate()
    }

    fun focusBackgroundGradientEnd(color: Int) = apply {
        isFocusGradient = true
        focusBgGradientEnd = color
        invalidate()
    }

    fun focusBackgroundOrientation(orientation: GradientOrientation) = apply {
        isFocusGradient = true
        focusBgGradientOrientation = orientation
        invalidate()
    }

    fun focusBackgroundGradientType(type: BgGradientType) = apply {
        isFocusGradient = true
        focusBgGradientType = type
        invalidate()
    }

    fun focusLinearGradient() = focusBackgroundGradientType(BgGradientType.LINEAR)

    fun focusRadialGradient() = focusBackgroundGradientType(BgGradientType.RADIAL)

    fun focusSweepGradient() = focusBackgroundGradientType(BgGradientType.SWEEP)

    fun focusGradientCenter(centerX: Float, centerY: Float) = apply {
        focusBgGradientCenterX = centerX
        focusBgGradientCenterY = centerY
        invalidate()
    }

    fun focusGradientRadius(radius: Float) = apply {
        focusBgGradientRadius = radius
        invalidate()
    }

    fun focusRadialGradient(centerX: Float, centerY: Float, radius: Float = 0f) = apply {
        isFocusGradient = true
        focusBgGradientType = BgGradientType.RADIAL
        focusBgGradientCenterX = centerX
        focusBgGradientCenterY = centerY
        focusBgGradientRadius = radius
        invalidate()
    }

    fun focusSweepGradient(centerX: Float, centerY: Float) = apply {
        isFocusGradient = true
        focusBgGradientType = BgGradientType.SWEEP
        focusBgGradientCenterX = centerX
        focusBgGradientCenterY = centerY
        invalidate()
    }

    fun strokeWidth(width: Float) = apply {
        stWidth = width
        invalidate()
    }

    fun strokeColor(color: Int) = apply {
        stColors = null
        isStrokeGradient = false
        stColor = color
        invalidate()
    }

    fun strokeDashed(dashed: Boolean, spacing: Float = 10f) = apply {
        isDashed = dashed
        dashSpace = spacing
        invalidate()
    }

    fun strokeGradientColors(colors: IntArray) = apply {
        stColors = null
        when (colors.size) {
            0 -> {
                strokeGradient = null
                isStrokeGradient = false
            }
            1 -> {
                strokeGradient = null
                isStrokeGradient = false
                stColor = colors[0]
            }
            else -> {
                strokeGradient = colors.copyOf()
                isStrokeGradient = true
            }
        }
        invalidate()
    }

    fun strokeOrientation(orientation: GradientOrientation) = apply {
        strokeGradientOrientation = orientation
        invalidate()
    }

    fun focusStrokeWidth(width: Float) = apply {
        focusStWidth = width
        invalidate()
    }

    fun focusStrokeColor(color: Int) = apply {
        stColors = null
        focusStrokeGradient = null
        focusStColor = color
        invalidate()
    }

    fun focusStrokeDashed(dashed: Boolean, spacing: Float = 10f) = apply {
        isFocusDashed = dashed
        focusDashSpace = spacing
        invalidate()
    }

    fun focusStrokeGradientColors(colors: IntArray) = apply {
        stColors = null
        when (colors.size) {
            0 -> focusStrokeGradient = null
            1 -> {
                focusStrokeGradient = null
                focusStColor = colors[0]
            }
            else -> focusStrokeGradient = colors.copyOf()
        }
        invalidate()
    }

    fun focusStrokeOrientation(orientation: GradientOrientation) = apply {
        focusStrokeGradientOrientation = orientation
        invalidate()
    }

    fun rounded(radius: Float) = apply {
        cornerRadius(radius)
    }

    fun solidBackground(color: Int) = apply {
        isGradient = false
        bgColor = color
        invalidate()
    }

    fun backgroundGradient(vararg colors: Int) = apply {
        isGradient = true
        when (colors.size) {
            0 -> { /* no-op */
            }
            1 -> {
                bgGradientStart = colors[0]; bgGradientCenter = Color.TRANSPARENT; bgGradientEnd =
                    Color.TRANSPARENT
            }
            2 -> {
                bgGradientStart = colors[0]; bgGradientCenter = Color.TRANSPARENT; bgGradientEnd =
                    colors[1]
            }
            else -> {
                bgGradientStart = colors[0]; bgGradientCenter = colors[1]; bgGradientEnd = colors[2]
            }
        }
        invalidate()
    }

    fun focusBackgroundGradient(vararg colors: Int) = apply {
        isFocusGradient = true
        when (colors.size) {
            0 -> { /* no-op */
            }
            1 -> {
                focusBgGradientStart = colors[0]; focusBgGradientCenter =
                    Color.TRANSPARENT; focusBgGradientEnd =
                    Color.TRANSPARENT
            }
            2 -> {
                focusBgGradientStart = colors[0]; focusBgGradientCenter =
                    Color.TRANSPARENT; focusBgGradientEnd =
                    colors[1]
            }
            else -> {
                focusBgGradientStart = colors[0]; focusBgGradientCenter =
                    colors[1]; focusBgGradientEnd = colors[2]
            }
        }
        invalidate()
    }

    fun strokeGradient(vararg colors: Int) = strokeGradientColors(colors)

    fun strokeGradient(enabled: Boolean) = apply {
        isStrokeGradient = enabled
        invalidate()
    }

    fun focusStrokeGradient(vararg colors: Int) = focusStrokeGradientColors(colors)

    fun dashed(spacing: Float = 10f): UiEditText = apply {
        return strokeDashed(true, spacing)
    }

    fun focusDashed(spacing: Float = 10f): UiEditText = apply {
        return focusStrokeDashed(true, spacing)
    }

    fun applyStyle(block: UiEditText.() -> Unit) = apply {
        block()
    }

    fun edtSingleLine(single: Boolean = true) = apply {
        isSingleLine = single
        if (single) {
            maxLines = 1
            setHorizontallyScrolling(true)
        }
        invalidate()
    }

    fun setLineOption(option: LineOption) = apply {
        this.lineOption = option
        applyTextStyles()
        invalidate()
    }

    // ==================== Drawable Functions ====================

    /**
     * Set drawable shown when text is empty
     */
    fun drawableEmpty(drawable: Drawable?) = apply {
        this.drawableEmpty = drawable
        updateDrawableState()
    }

    /**
     * Set drawable shown when text is empty (by resource id)
     */
    fun drawableEmpty(drawableRes: Int) = apply {
        this.drawableEmpty = ContextCompat.getDrawable(context, drawableRes)
        updateDrawableState()
    }

    /**
     * Set drawable shown when text is not empty
     */
    fun drawableNotEmpty(drawable: Drawable?) = apply {
        this.drawableNotEmpty = drawable
        updateDrawableState()
    }

    /**
     * Set drawable shown when text is not empty (by resource id)
     */
    fun drawableNotEmpty(drawableRes: Int) = apply {
        this.drawableNotEmpty = ContextCompat.getDrawable(context, drawableRes)
        updateDrawableState()
    }

    /**
     * Set both drawables at once
     */
    fun drawables(empty: Drawable?, notEmpty: Drawable?) = apply {
        this.drawableEmpty = empty
        this.drawableNotEmpty = notEmpty
        updateDrawableState()
    }

    /**
     * Set both drawables at once (by resource id)
     */
    fun drawables(emptyRes: Int, notEmptyRes: Int) = apply {
        this.drawableEmpty = ContextCompat.getDrawable(context, emptyRes)
        this.drawableNotEmpty = ContextCompat.getDrawable(context, notEmptyRes)
        updateDrawableState()
    }

    /**
     * Set drawable alignment (START, END, TOP, BOTTOM)
     */
    fun drawableAlign(align: DrawableAlign) = apply {
        this.drawableAlign = align
        updateDrawableState()
    }

    /**
     * Set drawable size in pixels
     */
    fun drawableSize(size: Int) = apply {
        this.drawableSize = size
        updateDrawableState()
    }

    /**
     * Set drawable padding in pixels
     */
    fun drawablePadding(padding: Int) = apply {
        this.drawablePadding = padding
        updateDrawableState()
    }

    /**
     * Set tint for both drawables
     */
    fun drawableTint(color: Int) = apply {
        this.drawableTint = color
        this.drawableEmptyTint = color
        this.drawableNotEmptyTint = color
        updateDrawableState()
    }

    /**
     * Set tint for empty state drawable
     */
    fun drawableEmptyTint(color: Int) = apply {
        this.drawableEmptyTint = color
        updateDrawableState()
    }

    /**
     * Set tint for not empty state drawable
     */
    fun drawableNotEmptyTint(color: Int) = apply {
        this.drawableNotEmptyTint = color
        updateDrawableState()
    }

    /**
     * Set visibility for empty state drawable.
     * When false, drawable is invisible but still reserves space (prevents height change).
     */
    fun drawableEmptyVisible(visible: Boolean) = apply {
        this.drawableEmptyVisible = visible
        updateDrawableState()
    }

    /**
     * Set visibility for not-empty state drawable.
     * When false, drawable is invisible but still reserves space (prevents height change).
     */
    fun drawableNotEmptyVisible(visible: Boolean) = apply {
        this.drawableNotEmptyVisible = visible
        updateDrawableState()
    }

    /**
     * Set click listener for drawable when text is empty
     */
    fun onDrawableEmptyClick(listener: (DrawableAlign) -> Unit) = apply {
        this.onDrawableEmptyClick = listener
    }

    /**
     * Set click listener for drawable when text is not empty
     */
    fun onDrawableNotEmptyClick(listener: (DrawableAlign) -> Unit) = apply {
        this.onDrawableNotEmptyClick = listener
    }

    /**
     * Set click listener for drawable (called for both states if specific listeners are not set)
     */
    fun onDrawableClick(listener: () -> Unit) = apply {
        this.onDrawableClick = listener
    }

    /**
     * Clear text and trigger drawable update
     */
    fun clearText() {
        setText("")
    }

    /**
     * Check if text is currently empty
     */
    fun isEmpty(): Boolean = text.isNullOrEmpty()

    /**
     * Check if text is not empty
     */
    fun isNotEmpty(): Boolean = !text.isNullOrEmpty()
}
