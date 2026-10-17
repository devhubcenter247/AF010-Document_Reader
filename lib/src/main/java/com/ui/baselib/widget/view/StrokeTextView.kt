package com.ui.baselib.widget.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.ui.baselib.R
import kotlin.math.ceil
import androidx.core.graphics.withTranslation
import com.ui.baselib.widget.layout.getColorList

/**
 * A TextView that draws text with an outline stroke effect.
 * Supports:
 * - Text stroke (outline) with customizable color, width, and gradient
 * - Text fill with solid color or gradient
 * - Text shadow
 * - Proper handling of italic/cursive fonts (no clipping)
 */
class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr)
{

    // Text stroke (outline)
    private var textStrokeWidth = 0f
    private var textStrokeColor = Color.WHITE
    private var textStrokeGradient: IntArray? = null
    private var textStrokeGradientOrientation = GradientOrientation.LEFT_TO_RIGHT

    // Text fill
    private var textFillColor = Color.BLACK
    private var textFillGradient: IntArray? = null
    private var textFillGradientOrientation = GradientOrientation.LEFT_TO_RIGHT

    // Text shadow
    private var textShadowRadius = 0f
    private var textShadowDx = 0f
    private var textShadowDy = 0f
    private var textShadowColor = Color.TRANSPARENT

    // Internal
    private var needUpdateShader = true
    private var strokeShader: Shader? = null
    private var fillShader: Shader? = null

    // Extra space for italic/stroke
    private var extraLeft = 0f
    private var extraRight = 0f

    init {
        context.obtainStyledAttributes(attrs, R.styleable.StrokeTextView).apply {
            try {
                // Text stroke attrs
                textStrokeWidth = getDimension(R.styleable.StrokeTextView_uiTextStrokeWidth, 0f)
                textStrokeColor = getColor(R.styleable.StrokeTextView_uiTextStrokeColor, Color.WHITE)
                textStrokeGradient = getColorList(R.styleable.StrokeTextView_uiTextStrokeGradientColors)
                    ?.takeIf { it.isNotEmpty() }
                textStrokeGradientOrientation = getInt(R.styleable.StrokeTextView_uiTextStrokeGradientOrientation, 6).toGradientOrientation()

                // Text fill attrs
                textFillColor = getColor(R.styleable.StrokeTextView_uiTextFillColor, currentTextColor)
                textFillGradient = getColorList(R.styleable.StrokeTextView_uiTextFillGradientColors)
                    ?.takeIf { it.isNotEmpty() }
                textFillGradientOrientation = getInt(R.styleable.StrokeTextView_uiTextFillGradientOrientation, 6).toGradientOrientation()

                // Text shadow attrs
                textShadowRadius = getDimension(R.styleable.StrokeTextView_uiTextShadowRadius, 0f)
                textShadowDx = getDimension(R.styleable.StrokeTextView_uiTextShadowDx, 0f)
                textShadowDy = getDimension(R.styleable.StrokeTextView_uiTextShadowDy, 0f)
                textShadowColor = getColor(R.styleable.StrokeTextView_uiTextShadowColor, Color.TRANSPARENT)
            } finally {
                recycle()
            }
        }

        calculateExtraSpace()
    }

    private fun calculateExtraSpace() {
        val textStr = text?.toString().orEmpty()
        if (textStr.isEmpty()) {
            extraLeft = 0f
            extraRight = 0f
            return
        }

        // Get actual text bounds - this accounts for glyph overhang
        val bounds = Rect()
        paint.getTextBounds(textStr, 0, textStr.length, bounds)

        // Calculate how much the text overhangs to the left (negative left bound)
        val leftOverhang = if (bounds.left < 0) -bounds.left.toFloat() else 0f

        // Calculate right overhang
        val textWidth = paint.measureText(textStr)
        val rightOverhang = if (bounds.right > textWidth) bounds.right - textWidth else 0f

        // Stroke extra - needs space on both sides
        val strokeExtra = textStrokeWidth + 2f

        // Shadow extra
        val shadowLeft = if (textShadowRadius > 0) textShadowRadius + (-textShadowDx).coerceAtLeast(0f) else 0f
        val shadowRight = if (textShadowRadius > 0) textShadowRadius + textShadowDx.coerceAtLeast(0f) else 0f

        extraLeft = leftOverhang + strokeExtra + shadowLeft + 4f
        extraRight = rightOverhang + strokeExtra + shadowRight + 4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        calculateExtraSpace()

        // Add extra width for italic/stroke/shadow
        val extraWidth = ceil(extraLeft + extraRight).toInt()
        if (extraWidth > 0) {
            val newWidth = measuredWidth + extraWidth
            setMeasuredDimension(newWidth, measuredHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        needUpdateShader = true
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        needUpdateShader = true
    }

    override fun setTypeface(tf: android.graphics.Typeface?) {
        super.setTypeface(tf)
        calculateExtraSpace()
        requestLayout()
    }

    override fun setTypeface(tf: android.graphics.Typeface?, style: Int) {
        super.setTypeface(tf, style)
        calculateExtraSpace()
        requestLayout()
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        calculateExtraSpace()
        requestLayout()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        calculateExtraSpace()
        requestLayout()
    }

    private fun updateShaders() {
        val textStr = text?.toString().orEmpty()
        if (textStr.isEmpty()) {
            strokeShader = null
            fillShader = null
            return
        }

        val textWidth = paint.measureText(textStr)
        val textHeight = textSize

        // Create stroke gradient shader
        textStrokeGradient?.takeIf { it.size > 1 }?.let { colors ->
            val (x0, y0, x1, y1) = textStrokeGradientOrientation.toCoordinates(textWidth, textHeight)
            strokeShader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        } ?: run { strokeShader = null }

        // Create fill gradient shader
        textFillGradient?.takeIf { it.size > 1 }?.let { colors ->
            val (x0, y0, x1, y1) = textFillGradientOrientation.toCoordinates(textWidth, textHeight)
            fillShader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        } ?: run { fillShader = null }

        needUpdateShader = false
    }

    override fun onDraw(canvas: Canvas) {
        if (needUpdateShader) updateShaders()

        val textPaint = paint

        // Save original paint state
        val originalStyle = textPaint.style
        val originalStrokeWidth = textPaint.strokeWidth
        val originalShader = textPaint.shader
        val originalTextColors = textColors
        val originalAlpha = textPaint.alpha

        // Translate canvas to give space for italic on left
        canvas.withTranslation(extraLeft, 0f) {
            // 1) Draw text shadow (if enabled) - on stroke layer
            if (textShadowRadius > 0 && textShadowColor != Color.TRANSPARENT) {
                textPaint.setShadowLayer(
                    textShadowRadius,
                    textShadowDx,
                    textShadowDy,
                    textShadowColor
                )
            }

            // 2) Draw stroke (outline) first - behind fill
            if (textStrokeWidth > 0) {
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = textStrokeWidth
                textPaint.strokeJoin = Paint.Join.ROUND
                textPaint.strokeCap = Paint.Cap.ROUND

                if (strokeShader != null) {
                    // TextView reapplies currentTextColor inside super.onDraw(), so
                    // use an opaque carrier color for shader rendering.
                    setTextColor(Color.WHITE)
                    textPaint.alpha = 255
                    textPaint.shader = strokeShader
                } else {
                    textPaint.shader = null
                    setTextColor(textStrokeColor)
                }
                super.onDraw(this)
            }

            // Clear shadow for fill (shadow only on stroke)
            textPaint.clearShadowLayer()

            // 3) Draw fill (text) on top
            textPaint.style = Paint.Style.FILL
            textPaint.strokeWidth = originalStrokeWidth

            if (fillShader != null) {
                setTextColor(Color.WHITE)
                textPaint.alpha = 255
                textPaint.shader = fillShader
            } else {
                textPaint.shader = null
                setTextColor(textFillColor)
            }
            super.onDraw(this)

        }

        // Restore original paint state
        textPaint.style = originalStyle
        textPaint.strokeWidth = originalStrokeWidth
        textPaint.shader = originalShader
        setTextColor(originalTextColors)
        textPaint.alpha = originalAlpha
    }

    private fun Int.toGradientOrientation(): GradientOrientation =
        GradientOrientation.entries.getOrElse(this) { GradientOrientation.LEFT_TO_RIGHT }

    private fun GradientOrientation.toCoordinates(w: Float, h: Float): FloatArray = when (this) {
        GradientOrientation.TOP_TO_BOTTOM -> floatArrayOf(0f, 0f, 0f, h)
        GradientOrientation.BOTTOM_TO_TOP -> floatArrayOf(0f, h, 0f, 0f)
        GradientOrientation.LEFT_TO_RIGHT -> floatArrayOf(0f, 0f, w, 0f)
        GradientOrientation.RIGHT_TO_LEFT -> floatArrayOf(w, 0f, 0f, 0f)
        GradientOrientation.TL_BR -> floatArrayOf(0f, 0f, w, h)
        GradientOrientation.TR_BL -> floatArrayOf(w, 0f, 0f, h)
        GradientOrientation.BL_TR -> floatArrayOf(0f, h, w, 0f)
        GradientOrientation.BR_TL -> floatArrayOf(w, h, 0f, 0f)
    }

    // Fluent API
    fun textStrokeWidth(width: Float) = apply {
        textStrokeWidth = width
        calculateExtraSpace()
        requestLayout()
        invalidate()
    }

    fun textStrokeColor(color: Int) = apply {
        textStrokeGradient = null
        textStrokeColor = color
        invalidate()
    }

    fun textStrokeGradient(vararg colors: Int) = apply {
        textStrokeGradient = if (colors.isNotEmpty()) colors.copyOf() else null
        needUpdateShader = true
        invalidate()
    }

    fun textStrokeGradientOrientation(orientation: GradientOrientation) = apply {
        textStrokeGradientOrientation = orientation
        needUpdateShader = true
        invalidate()
    }

    fun textFillColor(color: Int) = apply {
        textFillGradient = null
        textFillColor = color
        invalidate()
    }

    fun textFillGradient(vararg colors: Int) = apply {
        textFillGradient = if (colors.isNotEmpty()) colors.copyOf() else null
        needUpdateShader = true
        invalidate()
    }

    fun textFillGradientOrientation(orientation: GradientOrientation) = apply {
        textFillGradientOrientation = orientation
        needUpdateShader = true
        invalidate()
    }

    fun textShadow(radius: Float, dx: Float = 0f, dy: Float = 0f, color: Int = Color.BLACK) = apply {
        textShadowRadius = radius
        textShadowDx = dx
        textShadowDy = dy
        textShadowColor = color
        calculateExtraSpace()
        requestLayout()
        invalidate()
    }

    fun clearTextShadow() = apply {
        textShadowRadius = 0f
        textShadowDx = 0f
        textShadowDy = 0f
        textShadowColor = Color.TRANSPARENT
        calculateExtraSpace()
        requestLayout()
        invalidate()
    }

    fun applyStyle(block: StrokeTextView.() -> Unit) = apply(block)
}
