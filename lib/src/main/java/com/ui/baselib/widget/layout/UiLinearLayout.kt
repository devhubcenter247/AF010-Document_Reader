package com.ui.baselib.widget.layout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.LinearLayout
import com.ui.baselib.R

@Suppress("DEPRECATION")
open class UiLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiLinearLayout).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiLinearLayout_uiCornerRadius,
                    R.styleable.UiLinearLayout_uiCornerTopLeft,
                    R.styleable.UiLinearLayout_uiCornerTopRight,
                    R.styleable.UiLinearLayout_uiCornerBottomLeft,
                    R.styleable.UiLinearLayout_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiLinearLayout_uiBackgroundGradientEnabled,
                    R.styleable.UiLinearLayout_uiBackgroundGradientStart,
                    R.styleable.UiLinearLayout_uiBackgroundGradientCenter,
                    R.styleable.UiLinearLayout_uiBackgroundGradientEnd,
                    R.styleable.UiLinearLayout_uiBackgroundColor,
                    R.styleable.UiLinearLayout_uiBackgroundGradientOrientation,
                    R.styleable.UiLinearLayout_uiBackgroundGradientType,
                    R.styleable.UiLinearLayout_uiBackgroundGradientCenterX,
                    R.styleable.UiLinearLayout_uiBackgroundGradientCenterY,
                    R.styleable.UiLinearLayout_uiBackgroundGradientRadius,
                    R.styleable.UiLinearLayout_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiLinearLayout_uiStrokeWidth,
                    R.styleable.UiLinearLayout_uiStrokeColor,
                    R.styleable.UiLinearLayout_uiStrokeDashed,
                    R.styleable.UiLinearLayout_uiStrokeDashGap,
                    R.styleable.UiLinearLayout_uiStrokeGradientColors,
                    R.styleable.UiLinearLayout_uiStrokeGradientOrientation,
                    R.styleable.UiLinearLayout_uiStrokeSides,
                    R.styleable.UiLinearLayout_uiStrokeCap,
                    R.styleable.UiLinearLayout_uiStrokeWidths,
                    R.styleable.UiLinearLayout_uiStrokeGradientStart,
                    R.styleable.UiLinearLayout_uiStrokeGradientCenter,
                    R.styleable.UiLinearLayout_uiStrokeGradientEnd,
                    R.styleable.UiLinearLayout_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiLinearLayout_uiShadowColor,
                    R.styleable.UiLinearLayout_uiShadowElevation,
                    R.styleable.UiLinearLayout_uiShadowRadius,
                    R.styleable.UiLinearLayout_uiShadowDx,
                    R.styleable.UiLinearLayout_uiShadowDy,
                    R.styleable.UiLinearLayout_uiShadowClipParent,
                    R.styleable.UiLinearLayout_uiShadowAmbientColor,
                    R.styleable.UiLinearLayout_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiLinearLayout_uiDimensionRatio,
                    R.styleable.UiLinearLayout_uiWidthPercent,
                    R.styleable.UiLinearLayout_uiHeightPercent,
                    R.styleable.UiLinearLayout_uiMaxWidthPercent,
                    R.styleable.UiLinearLayout_uiMaxHeightPercent,
                    R.styleable.UiLinearLayout_uiMinWidthPercent,
                    R.styleable.UiLinearLayout_uiMinHeightPercent
                )
            } finally {
                recycle()
            }
        }
        helper.setupShadow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (wSpec, hSpec) = helper.resolveMeasureSpecsReusable(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(wSpec, hSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        helper.onSizeChanged(w, h)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        helper.drawShadow(canvas, w, h)
        helper.drawBackground(canvas, w, h)
        helper.drawClipped(canvas) {
            onDrawUnderChildren(canvas)
            super.dispatchDraw(canvas)
            onDrawOverChildren(canvas)
        }
        helper.drawStroke(canvas, w, h)
    }

    /** Drawn after the layout background but BEHIND the child views (clipped to the corners). */
    protected open fun onDrawUnderChildren(canvas: Canvas) {}

    /** Drawn ON TOP of the child views but below the stroke (clipped to the corners). */
    protected open fun onDrawOverChildren(canvas: Canvas) {}

    // IUiLayout implementation
    override fun invalidateView() = invalidate()
    override fun invalidateViewOutline() = invalidateOutline()
    override fun updateViewClipPath() = helper.updateClipPath(width, height)

    fun applyStyle(block: UiLinearLayout.() -> Unit) = apply(block)
}
