package com.ui.baselib.widget.layout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout
import com.ui.baselib.R

@Suppress("DEPRECATION")
open class UiFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiFrameLayout).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiFrameLayout_uiCornerRadius,
                    R.styleable.UiFrameLayout_uiCornerTopLeft,
                    R.styleable.UiFrameLayout_uiCornerTopRight,
                    R.styleable.UiFrameLayout_uiCornerBottomLeft,
                    R.styleable.UiFrameLayout_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiFrameLayout_uiBackgroundGradientEnabled,
                    R.styleable.UiFrameLayout_uiBackgroundGradientStart,
                    R.styleable.UiFrameLayout_uiBackgroundGradientCenter,
                    R.styleable.UiFrameLayout_uiBackgroundGradientEnd,
                    R.styleable.UiFrameLayout_uiBackgroundColor,
                    R.styleable.UiFrameLayout_uiBackgroundGradientOrientation,
                    R.styleable.UiFrameLayout_uiBackgroundGradientType,
                    R.styleable.UiFrameLayout_uiBackgroundGradientCenterX,
                    R.styleable.UiFrameLayout_uiBackgroundGradientCenterY,
                    R.styleable.UiFrameLayout_uiBackgroundGradientRadius,
                    R.styleable.UiFrameLayout_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiFrameLayout_uiStrokeWidth,
                    R.styleable.UiFrameLayout_uiStrokeColor,
                    R.styleable.UiFrameLayout_uiStrokeDashed,
                    R.styleable.UiFrameLayout_uiStrokeDashGap,
                    R.styleable.UiFrameLayout_uiStrokeGradientColors,
                    R.styleable.UiFrameLayout_uiStrokeGradientOrientation,
                    R.styleable.UiFrameLayout_uiStrokeSides,
                    R.styleable.UiFrameLayout_uiStrokeCap,
                    R.styleable.UiFrameLayout_uiStrokeWidths,
                    R.styleable.UiFrameLayout_uiStrokeGradientStart,
                    R.styleable.UiFrameLayout_uiStrokeGradientCenter,
                    R.styleable.UiFrameLayout_uiStrokeGradientEnd,
                    R.styleable.UiFrameLayout_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiFrameLayout_uiShadowColor,
                    R.styleable.UiFrameLayout_uiShadowElevation,
                    R.styleable.UiFrameLayout_uiShadowRadius,
                    R.styleable.UiFrameLayout_uiShadowDx,
                    R.styleable.UiFrameLayout_uiShadowDy,
                    R.styleable.UiFrameLayout_uiShadowClipParent,
                    R.styleable.UiFrameLayout_uiShadowAmbientColor,
                    R.styleable.UiFrameLayout_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiFrameLayout_uiDimensionRatio,
                    R.styleable.UiFrameLayout_uiWidthPercent,
                    R.styleable.UiFrameLayout_uiHeightPercent,
                    R.styleable.UiFrameLayout_uiMaxWidthPercent,
                    R.styleable.UiFrameLayout_uiMaxHeightPercent,
                    R.styleable.UiFrameLayout_uiMinWidthPercent,
                    R.styleable.UiFrameLayout_uiMinHeightPercent
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
            super.dispatchDraw(canvas)
        }
        helper.drawStroke(canvas, w, h)
    }

    override fun invalidateView() = invalidate()
    override fun invalidateViewOutline() = invalidateOutline()
    override fun updateViewClipPath() = helper.updateClipPath(width, height)

    fun applyStyle(block: UiFrameLayout.() -> Unit) = apply(block)
}
