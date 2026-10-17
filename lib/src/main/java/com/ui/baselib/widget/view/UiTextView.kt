package com.ui.baselib.widget.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.TextUtils
import android.view.Gravity
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatTextView
import com.ui.baselib.R
import com.ui.baselib.widget.layout.IUiLayout
import com.ui.baselib.widget.layout.UiLayoutHelper

enum class GradientOrientation {
    TOP_TO_BOTTOM, TR_BL, RIGHT_TO_LEFT, BR_TL,
    BOTTOM_TO_TOP, BL_TR, LEFT_TO_RIGHT, TL_BR
}
typealias TextGradientOrientation = GradientOrientation

enum class LineOption(val value: Int) {
    NONE(0), TOP(1), BOTTOM(2), CENTER(3);

    companion object {
        fun fromValue(value: Int): LineOption = entries.find { it.value == value } ?: NONE
    }
}

enum class DrawableAlign {
    EDGE,  // TextView default: horizontal drawables at the view edges
    TEXT   // Horizontal drawables next to the text, the group placed by the horizontal gravity
}

private data class Quad(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

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

fun TextView.textGradient(
    startColor: Int,
    endColor: Int,
    orientation: GradientOrientation = GradientOrientation.LEFT_TO_RIGHT
) {
    val textStr = text?.toString().orEmpty()
    if (textStr.isEmpty()) {
        paint.shader = null
        return
    }
    val width = paint.measureText(textStr)
    val height = textSize * 1.3f
    val (x0, y0, x1, y1) = orientation.toCoordinates(width, height)

    paint.shader = LinearGradient(
        x0, y0, x1, y1,
        intArrayOf(startColor, endColor),
        null,
        Shader.TileMode.CLAMP
    )
    invalidate()
}
fun TextView.textGradient(
      listColor: IntArray,
      orientation: GradientOrientation = GradientOrientation.RIGHT_TO_LEFT
) {
    val textStr = text?.toString().orEmpty()
    if (textStr.isEmpty()) {
        paint.shader = null
        return
    }
    val width = paint.measureText(textStr)
    val height = textSize * 1.3f
    val (x0, y0, x1, y1) = orientation.toCoordinates(width, height)

    paint.shader = LinearGradient(
        x0, y0, x1, y1,
        listColor,
        null,
        Shader.TileMode.CLAMP
    )
    invalidate()
}

class UiTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    // Text-specific properties
    private var lineOption: LineOption = LineOption.NONE
    private var tColor = currentTextColor
    private var tColorHint = currentHintTextColor
    // Compound drawables: 0 keeps the drawable's own bounds (intrinsic size).
    private var drawableWidth = 0
    private var drawableHeight = 0
    private var drawableAlign = DrawableAlign.EDGE

    // DrawableAlign.TEXT pads the free space around the text + drawables group; the user's
    // own padding is kept apart so it can be restored.
    private var basePaddingStart = 0
    private var basePaddingEnd = 0
    private var alignExtraStart = 0
    private var alignExtraEnd = 0
    private var applyingAlignPadding = false

    // Text gradient
    private var textGradient = false
    private var textGradientStart = Color.TRANSPARENT
    private var textGradientCenter = Color.TRANSPARENT
    private var textGradientEnd = Color.TRANSPARENT
    private var textGradientOrientation: GradientOrientation = GradientOrientation.LEFT_TO_RIGHT
    private var needReapplyTextGradient = false

    private val topLinePaint = Paint()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.UiTextView).apply {
            try {
                // Common layout attrs via helper
                helper.readCornerAttrs(
                    this,
                    R.styleable.UiTextView_uiCornerRadius,
                    R.styleable.UiTextView_uiCornerTopLeft,
                    R.styleable.UiTextView_uiCornerTopRight,
                    R.styleable.UiTextView_uiCornerBottomLeft,
                    R.styleable.UiTextView_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(
                    this,
                    R.styleable.UiTextView_uiBackgroundGradientEnabled,
                    R.styleable.UiTextView_uiBackgroundGradientStart,
                    R.styleable.UiTextView_uiBackgroundGradientCenter,
                    R.styleable.UiTextView_uiBackgroundGradientEnd,
                    R.styleable.UiTextView_uiBackgroundColor,
                    R.styleable.UiTextView_uiBackgroundGradientOrientation,
                    R.styleable.UiTextView_uiBackgroundGradientType,
                    R.styleable.UiTextView_uiBackgroundGradientCenterX,
                    R.styleable.UiTextView_uiBackgroundGradientCenterY,
                    R.styleable.UiTextView_uiBackgroundGradientRadius,
                    R.styleable.UiTextView_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(
                    this,
                    R.styleable.UiTextView_uiStrokeWidth,
                    R.styleable.UiTextView_uiStrokeColor,
                    R.styleable.UiTextView_uiStrokeDashed,
                    R.styleable.UiTextView_uiStrokeDashGap,
                    R.styleable.UiTextView_uiStrokeGradientColors,
                    R.styleable.UiTextView_uiStrokeGradientOrientation,
                    R.styleable.UiTextView_uiStrokeSides,
                    -1,
                    R.styleable.UiTextView_uiStrokeWidths,
                    R.styleable.UiTextView_uiStrokeGradientStart,
                    R.styleable.UiTextView_uiStrokeGradientCenter,
                    R.styleable.UiTextView_uiStrokeGradientEnd,
                    R.styleable.UiTextView_uiStrokeGradient
                )
                helper.readShadowAttrs(
                    this,
                    R.styleable.UiTextView_uiShadowColor,
                    R.styleable.UiTextView_uiShadowElevation,
                    R.styleable.UiTextView_uiShadowRadius,
                    R.styleable.UiTextView_uiShadowDx,
                    R.styleable.UiTextView_uiShadowDy,
                    R.styleable.UiTextView_uiShadowClipParent,
                    R.styleable.UiTextView_uiShadowAmbientColor,
                    R.styleable.UiTextView_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(
                    this,
                    R.styleable.UiTextView_uiDimensionRatio,
                    R.styleable.UiTextView_uiWidthPercent,
                    R.styleable.UiTextView_uiHeightPercent,
                    R.styleable.UiTextView_uiMaxWidthPercent,
                    R.styleable.UiTextView_uiMaxHeightPercent,
                    R.styleable.UiTextView_uiMinWidthPercent,
                    R.styleable.UiTextView_uiMinHeightPercent
                )

                // Text-specific attrs
                tColor = getColor(R.styleable.UiTextView_uiTextColor, currentTextColor)
                tColorHint = getColor(R.styleable.UiTextView_uiTextColorHint, currentHintTextColor)

                lineOption = LineOption.fromValue(getInt(R.styleable.UiTextView_uiTextDecoration, 0))

                // Text gradient
                textGradient = getBoolean(R.styleable.UiTextView_uiTextGradientEnabled, false)
                textGradientStart = getColor(R.styleable.UiTextView_uiTextGradientStart, Color.TRANSPARENT)
                textGradientCenter = getColor(R.styleable.UiTextView_uiTextGradientCenter, Color.TRANSPARENT)
                textGradientEnd = getColor(R.styleable.UiTextView_uiTextGradientEnd, Color.TRANSPARENT)
                textGradientOrientation = getInt(R.styleable.UiTextView_uiTextGradientOrientation, 6).toGradientOrientation()

                val size = getDimensionPixelSize(R.styleable.UiTextView_uiDrawableSize, 0)
                drawableWidth = getDimensionPixelSize(R.styleable.UiTextView_uiDrawableWidth, size)
                drawableHeight = getDimensionPixelSize(R.styleable.UiTextView_uiDrawableHeight, size)
                getColorStateList(R.styleable.UiTextView_uiDrawableTint)?.let { compoundDrawableTintList = it }
                if (hasValue(R.styleable.UiTextView_uiDrawablePadding)) {
                    compoundDrawablePadding = getDimensionPixelSize(R.styleable.UiTextView_uiDrawablePadding, 0)
                }
                drawableAlign = DrawableAlign.entries
                    .getOrElse(getInt(R.styleable.UiTextView_uiDrawableAlign, 0)) { DrawableAlign.EDGE }

                val wantSingleLine = getBoolean(R.styleable.UiTextView_uiSingleLine, false)
                if (wantSingleLine) {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            } finally {
                recycle()
            }
        }

        helper.setupShadow()
        applyTextStyles()
        basePaddingStart = paddingStart
        basePaddingEnd = paddingEnd
        applyDrawableSize()
    }

    private fun Int.toGradientOrientation(): GradientOrientation =
        GradientOrientation.entries.getOrElse(this) {
            GradientOrientation.LEFT_TO_RIGHT
        }

    private fun applyTextStyles() {
        // Clear line flags first
        paintFlags = paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv() and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // Apply line option
        when (lineOption) {
            LineOption.BOTTOM -> paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            LineOption.CENTER -> paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            LineOption.TOP, LineOption.NONE -> { /* TOP is drawn manually in onDraw, NONE does nothing */ }
        }

        setHintTextColor(tColorHint)

        if (textGradient) {
            applyTextGradient()
        } else {
            paint.shader = null
            setTextColor(tColor)
        }
    }

    /** Re-sets the current drawables so the setters below resize them. */
    private fun applyDrawableSize() {
        val (start, top, end, bottom) = compoundDrawablesRelative
        setCompoundDrawablesRelative(start, top, end, bottom)
    }

    /**
     * Every way of setting compound drawables (XML, *WithIntrinsicBounds, the fluent API) ends
     * in these two setters, so a drawable swapped in later keeps the configured size.
     * They also run from TextView's constructor, before the size fields are read.
     */
    override fun setCompoundDrawables(left: Drawable?, top: Drawable?, right: Drawable?, bottom: Drawable?) {
        super.setCompoundDrawables(left?.sized(), top?.sized(), right?.sized(), bottom?.sized())
        requestAlignLayout()
    }

    override fun setCompoundDrawablesRelative(start: Drawable?, top: Drawable?, end: Drawable?, bottom: Drawable?) {
        super.setCompoundDrawablesRelative(start?.sized(), top?.sized(), end?.sized(), bottom?.sized())
        requestAlignLayout()
    }

    private fun Drawable.sized(): Drawable {
        val w = drawableWidth
        val h = drawableHeight
        val iw = intrinsicWidth
        val ih = intrinsicHeight
        val hasRatio = iw > 0 && ih > 0
        when {
            w > 0 && h > 0 -> setBounds(0, 0, w, h)
            w > 0 -> setBounds(0, 0, w, if (hasRatio) w * ih / iw else w)
            h > 0 -> setBounds(0, 0, if (hasRatio) h * iw / ih else h, h)
            bounds.isEmpty -> setBounds(0, 0, iw.coerceAtLeast(0), ih.coerceAtLeast(0))
        }
        return this
    }

    // ---------------------------------------------------------------- DrawableAlign.TEXT

    // TextView may skip relayout on setText when its size is fixed; the group width still changes.
    private fun requestAlignLayout() {
        if (drawableAlign == DrawableAlign.TEXT) requestLayout()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        onUserPaddingChanged()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        super.setPaddingRelative(start, top, end, bottom)
        onUserPaddingChanged()
    }

    /** Padding set from outside replaces the aligned one: it becomes the new base. */
    private fun onUserPaddingChanged() {
        if (applyingAlignPadding) return
        alignExtraStart = 0
        alignExtraEnd = 0
        basePaddingStart = paddingStart
        basePaddingEnd = paddingEnd
    }

    /**
     * Narrows the content box to exactly text + horizontal drawables, so TextView itself draws
     * them side by side; the free space goes to the start/end padding per the gravity.
     * Returns true when the padding changed and the view must be measured again.
     */
    private fun updateAlignPadding(widthMeasureSpec: Int): Boolean {
        var extraStart = 0
        var extraEnd = 0
        if (drawableAlign == DrawableAlign.TEXT &&
            MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        ) {
            val (start, _, end, _) = compoundDrawablesRelative
            val drawablesWidth = listOfNotNull(start, end).sumOf { it.bounds.width() + compoundDrawablePadding }
            val content = measuredWidth - basePaddingStart - basePaddingEnd
            val shown = text.takeIf { it.isNotEmpty() } ?: hint ?: ""
            val textWidth = kotlin.math.ceil(Layout.getDesiredWidth(shown, paint)).toInt()
                .coerceAtMost((content - drawablesWidth).coerceAtLeast(0))
            val free = content - drawablesWidth - textWidth
            if (free > 0) {
                val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
                val towardEnd = when (Gravity.getAbsoluteGravity(gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.CENTER_HORIZONTAL -> 0.5f
                    Gravity.RIGHT -> if (rtl) 0f else 1f
                    else -> if (rtl) 1f else 0f
                }
                extraStart = (free * towardEnd).toInt()
                extraEnd = free - extraStart
            }
        }
        if (extraStart == alignExtraStart && extraEnd == alignExtraEnd) return false
        alignExtraStart = extraStart
        alignExtraEnd = extraEnd
        applyingAlignPadding = true
        super.setPaddingRelative(basePaddingStart + extraStart, paddingTop, basePaddingEnd + extraEnd, paddingBottom)
        applyingAlignPadding = false
        return true
    }

    private fun applyTextGradient() {
        val textStr = text?.toString().orEmpty()
        if (textStr.isEmpty()) {
            needReapplyTextGradient = true
            paint.shader = null
            return
        }
        val width = paint.measureText(textStr)
        val height = textSize * 1.3f
        val (x0, y0, x1, y1) = textGradientOrientation.toCoordinates(width, height)
        paint.shader = LinearGradient(
            x0, y0, x1, y1,
            if (textGradientCenter != Color.TRANSPARENT) intArrayOf(
                textGradientStart,
                textGradientCenter,
                textGradientEnd
            ) else intArrayOf(textGradientStart, textGradientEnd),
            null,
            Shader.TileMode.CLAMP
        )
        invalidate()
        needReapplyTextGradient = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (helper.shouldApplyCustomMeasure()) {
            val dm = context.resources.displayMetrics
            val specW = MeasureSpec.getSize(widthMeasureSpec)
            val specH = MeasureSpec.getSize(heightMeasureSpec)
            val parentWidth = if (specW > 0) specW else
                (parent as? android.view.View)?.width?.takeIf { it > 0 } ?: dm.widthPixels
            val parentHeight = if (specH > 0) specH else
                (parent as? android.view.View)?.height?.takeIf { it > 0 } ?: dm.heightPixels
            val result = helper.measureWithConstraints(
                widthMeasureSpec, heightMeasureSpec, parentWidth, parentHeight
            )
            val wSpec = if (result.widthCustomized)
                MeasureSpec.makeMeasureSpec(result.width, MeasureSpec.EXACTLY)
                else widthMeasureSpec
            val hSpec = if (result.heightCustomized)
                MeasureSpec.makeMeasureSpec(result.height, MeasureSpec.EXACTLY)
                else heightMeasureSpec
            super.onMeasure(wSpec, hSpec)
            if (updateAlignPadding(wSpec)) super.onMeasure(wSpec, hSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (updateAlignPadding(widthMeasureSpec)) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (textGradient) applyTextGradient()
        requestAlignLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        helper.onSizeChanged(w, h)
        if (textGradient || needReapplyTextGradient) applyTextGradient()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }
        helper.drawShadow(canvas, w, h)
        helper.drawBackground(canvas, w, h)
        helper.drawClipped(canvas) {
            super.onDraw(canvas)
        }

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
        topLinePaint.strokeWidth = textSize / 12f // Similar thickness to underline
        canvas.drawLine(startX, lineY, endX, lineY, topLinePaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        helper.drawStroke(canvas, w, h)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    // IUiLayout implementation
    override fun invalidateView() = invalidate()
    override fun invalidateViewOutline() = invalidateOutline()
    override fun updateViewClipPath() = helper.updateClipPath(width, height)

    // Text-specific fluent API
    fun setLineOption(option: LineOption) = apply {
        this.lineOption = option
        applyTextStyles()
        invalidate()
    }

    @Deprecated("Use setLineOption(LineOption.BOTTOM) instead", ReplaceWith("setLineOption(if (underline) LineOption.BOTTOM else LineOption.NONE)"))
    fun textUnderline(underline: Boolean) = apply {
        setLineOption(if (underline) LineOption.BOTTOM else LineOption.NONE)
    }

    fun setTextGradient(
        startColor: Int,
        centerColor: Int,
        endColor: Int,
        orientation: GradientOrientation = GradientOrientation.TOP_TO_BOTTOM
    ) = apply {
        textGradient = true
        textGradientStart = startColor
        textGradientCenter = centerColor
        textGradientEnd = endColor
        textGradientOrientation = orientation
        applyTextGradient()
    }

    fun clearTextGradient() = apply {
        textGradient = false
        paint.shader = null
        setTextColor(tColor)
        invalidate()
    }

    fun textColor(color: Int) = apply {
        tColor = color
        if (!textGradient) setTextColor(color)
    }

    fun hintColor(color: Int) = apply {
        tColorHint = color
        setHintTextColor(color)
    }

    fun drawableSize(size: Int) = drawableSize(size, size)

    /** 0 on one axis derives it from the drawable's aspect ratio; 0 on both keeps the intrinsic size. */
    fun drawableSize(width: Int, height: Int) = apply {
        drawableWidth = width
        drawableHeight = height
        compoundDrawablesRelative.forEach { it?.setBounds(0, 0, 0, 0) } // let sized() start over
        applyDrawableSize()
    }

    fun drawableTint(@ColorInt color: Int) = drawableTint(ColorStateList.valueOf(color))

    /** Tints every compound drawable, current and future; null removes the tint. */
    fun drawableTint(tint: ColorStateList?) = apply {
        compoundDrawableTintList = tint
    }

    fun drawablePadding(padding: Int) = apply {
        compoundDrawablePadding = padding
        requestAlignLayout()
    }

    fun drawableAlign(align: DrawableAlign) = apply {
        drawableAlign = align
        if (align == DrawableAlign.EDGE && (alignExtraStart != 0 || alignExtraEnd != 0)) {
            alignExtraStart = 0
            alignExtraEnd = 0
            applyingAlignPadding = true
            super.setPaddingRelative(basePaddingStart, paddingTop, basePaddingEnd, paddingBottom)
            applyingAlignPadding = false
        }
        requestLayout()
    }

    /** Sets the compound drawables by resource; 0 clears that side. Size and tint are applied. */
    fun drawables(
        @DrawableRes start: Int = 0,
        @DrawableRes top: Int = 0,
        @DrawableRes end: Int = 0,
        @DrawableRes bottom: Int = 0,
    ) = apply {
        fun load(res: Int) = if (res == 0) null else ContextCompat.getDrawable(context, res)
        setCompoundDrawablesRelative(load(start), load(top), load(end), load(bottom))
    }

    fun drawableStart(@DrawableRes res: Int) = apply {
        val (_, top, end, bottom) = compoundDrawablesRelative
        setCompoundDrawablesRelative(if (res == 0) null else ContextCompat.getDrawable(context, res), top, end, bottom)
    }

    fun drawableEnd(@DrawableRes res: Int) = apply {
        val (start, top, _, bottom) = compoundDrawablesRelative
        setCompoundDrawablesRelative(start, top, if (res == 0) null else ContextCompat.getDrawable(context, res), bottom)
    }
    fun applyStyle(block: UiTextView.() -> Unit) = apply(block)
}
