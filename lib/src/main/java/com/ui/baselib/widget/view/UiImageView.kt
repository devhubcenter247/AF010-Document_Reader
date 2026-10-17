package com.ui.baselib.widget.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.withSave
import com.ui.baselib.R
import com.ui.baselib.widget.layout.IUiLayout
import com.ui.baselib.widget.layout.UiLayoutHelper
import com.ui.baselib.widget.layout.getColorList

@SuppressLint("CustomViewStyleable")
class UiImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    // Image-specific properties
    private var gradientIconColors: IntArray? = null
    private var gradientIconOrientation = UiLayoutHelper.GradientOrientation.LEFT_TO_RIGHT

    private val gradientIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val rectF = RectF()
    private val backgroundPath = Path()

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiImageView).apply {
            try {
                // Common layout attrs via helper
                helper.readCornerAttrs(
                    this,
                    R.styleable.UiImageView_uiCornerRadius,
                    R.styleable.UiImageView_uiCornerTopLeft,
                    R.styleable.UiImageView_uiCornerTopRight,
                    R.styleable.UiImageView_uiCornerBottomLeft,
                    R.styleable.UiImageView_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(
                    this,
                    R.styleable.UiImageView_uiBackgroundGradientEnabled,
                    R.styleable.UiImageView_uiBackgroundGradientStart,
                    R.styleable.UiImageView_uiBackgroundGradientCenter,
                    R.styleable.UiImageView_uiBackgroundGradientEnd,
                    R.styleable.UiImageView_uiBackgroundColor,
                    R.styleable.UiImageView_uiBackgroundGradientOrientation,
                    R.styleable.UiImageView_uiBackgroundGradientType,
                    R.styleable.UiImageView_uiBackgroundGradientCenterX,
                    R.styleable.UiImageView_uiBackgroundGradientCenterY,
                    R.styleable.UiImageView_uiBackgroundGradientRadius,
                    R.styleable.UiImageView_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(
                    this,
                    R.styleable.UiImageView_uiStrokeWidth,
                    R.styleable.UiImageView_uiStrokeColor,
                    R.styleable.UiImageView_uiStrokeDashed,
                    R.styleable.UiImageView_uiStrokeDashGap,
                    R.styleable.UiImageView_uiStrokeGradientColors,
                    R.styleable.UiImageView_uiStrokeGradientOrientation,
                    R.styleable.UiImageView_uiStrokeSides,
                    -1,
                    R.styleable.UiImageView_uiStrokeWidths,
                    R.styleable.UiImageView_uiStrokeGradientStart,
                    R.styleable.UiImageView_uiStrokeGradientCenter,
                    R.styleable.UiImageView_uiStrokeGradientEnd,
                    R.styleable.UiImageView_uiStrokeGradient
                )
                helper.readShadowAttrs(
                    this,
                    R.styleable.UiImageView_uiShadowColor,
                    R.styleable.UiImageView_uiShadowElevation,
                    R.styleable.UiImageView_uiShadowRadius,
                    R.styleable.UiImageView_uiShadowDx,
                    R.styleable.UiImageView_uiShadowDy,
                    R.styleable.UiImageView_uiShadowClipParent,
                    R.styleable.UiImageView_uiShadowAmbientColor,
                    R.styleable.UiImageView_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(
                    this,
                    R.styleable.UiImageView_uiDimensionRatio,
                    R.styleable.UiImageView_uiWidthPercent,
                    R.styleable.UiImageView_uiHeightPercent,
                    R.styleable.UiImageView_uiMaxWidthPercent,
                    R.styleable.UiImageView_uiMaxHeightPercent,
                    R.styleable.UiImageView_uiMinWidthPercent,
                    R.styleable.UiImageView_uiMinHeightPercent
                )

                // Image-specific attrs
                gradientIconColors = getColorList(R.styleable.UiImageView_uiIconGradientColors)
                    ?.takeIf { it.isNotEmpty() }
                gradientIconOrientation = getInt(R.styleable.UiImageView_uiIconGradientOrientation, 6)
                    .toGradientOrientation()
            } finally {
                recycle()
            }
        }

        helper.setupShadow()
        applyClipStrategy()
    }

    /**
     * Canvas.clipPath is not anti-aliased when hardware-accelerated, which shows up as
     * shimmering/flickering rounded corners while the view is translated (e.g. ViewPager2
     * paging). Prefer clipToOutline (hardware-accelerated + anti-aliased) whenever the
     * corners are uniform; only fall back to the manual clipPath draw for per-corner radii,
     * which Outline.setRoundRect can't express, forcing a software layer there for AA.
     */
    private fun applyClipStrategy() {
        if (helper.hasCustomShadow()) {
            // Outline clipping and a software layer would both cut off the shadow drawn
            // outside the bounds, so clip the content with the manual clipPath instead.
            clipToOutline = false
            setLayerType(LAYER_TYPE_NONE, null)
        } else if (helper.hasIndividualCorners()) {
            clipToOutline = false
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        } else {
            outlineProvider = helper.roundOutlineProvider
            clipToOutline = true
            setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        helper.onSizeChanged(w, h)
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
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radii = helper.getCornerRadii(w, h)
        // rectF is reused below as the saveLayer bounds for the gradient-icon overlay,
        // so it must reflect the current size regardless of the clip-to-outline branch.
        rectF.set(0f, 0f, w, h)

        // Outline-based clipping (set in applyClipStrategy) already clips uniform-radius
        // corners with hardware AA; only fall back to manual clipPath for per-corner radii.
        if (!clipToOutline) {
            backgroundPath.reset()
            backgroundPath.addRoundRect(rectF, radii, Path.Direction.CW)
        }

        // Outside the clip below, since the shadow extends past the view bounds.
        helper.drawShadow(canvas, w, h)

        canvas.withSave {
            if (!clipToOutline) {
                runCatching { canvas.clipPath(backgroundPath) }
            }

            // 3) Draw background
            helper.drawBackground(canvas, w, h)

            // 4) Draw image with optional gradient overlay
            val iconColors = gradientIconColors
            if (iconColors != null && iconColors.size > 1) {
                val saveCount = canvas.saveLayer(rectF, null)
                super.onDraw(canvas)
                drawGradientOverlay(canvas, w, h, iconColors)
                canvas.restoreToCount(saveCount)
            } else {
                super.onDraw(canvas)
            }

            // 5) Draw stroke
            helper.drawStroke(canvas, w, h)
        }
    }

    private fun drawGradientOverlay(canvas: Canvas, width: Float, height: Float, colors: IntArray) {
        gradientIconPaint.shader = createIconGradientShader(width, height, colors)
        canvas.drawRect(0f, 0f, width, height, gradientIconPaint)
    }

    private fun createIconGradientShader(width: Float, height: Float, colors: IntArray): Shader {
        val (x0, y0, x1, y1) = gradientIconOrientation.toCoordinates(width, height)
        return LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
    }

    private fun Int.toGradientOrientation() =
        UiLayoutHelper.GradientOrientation.entries.getOrElse(this) {
            UiLayoutHelper.GradientOrientation.LEFT_TO_RIGHT
        }

    // IUiLayout implementation
    override fun invalidateView() = invalidate()
    override fun invalidateViewOutline() {
        applyClipStrategy()
        invalidateOutline()
    }
    override fun updateViewClipPath() = helper.updateClipPath(width, height)

    // Image-specific fluent API
    fun setGradientIconColors(colors: IntArray?) = apply {
        gradientIconColors = colors
        invalidate()
    }

    fun setGradientIconColors(startColor: Int, endColor: Int) = apply {
        gradientIconColors = intArrayOf(startColor, endColor)
        invalidate()
    }

    fun setGradientIconColors(startColor: Int, centerColor: Int, endColor: Int) = apply {
        gradientIconColors = intArrayOf(startColor, centerColor, endColor)
        invalidate()
    }

    fun gradientIconOrientation(orientation: UiLayoutHelper.GradientOrientation) = apply {
        gradientIconOrientation = orientation
        invalidate()
    }

    fun clearGradientIcon() = apply {
        gradientIconColors = null
        invalidate()
    }

    fun applyStyle(block: UiImageView.() -> Unit) = apply(block)
}
