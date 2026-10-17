package com.ui.baselib.widget.layout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.ui.baselib.R

@Suppress("DEPRECATION")
open class UiRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiRelativeLayout).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiRelativeLayout_uiCornerRadius,
                    R.styleable.UiRelativeLayout_uiCornerTopLeft,
                    R.styleable.UiRelativeLayout_uiCornerTopRight,
                    R.styleable.UiRelativeLayout_uiCornerBottomLeft,
                    R.styleable.UiRelativeLayout_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientEnabled,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientStart,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientCenter,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientEnd,
                    R.styleable.UiRelativeLayout_uiBackgroundColor,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientOrientation,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientType,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientCenterX,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientCenterY,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientRadius,
                    R.styleable.UiRelativeLayout_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiRelativeLayout_uiStrokeWidth,
                    R.styleable.UiRelativeLayout_uiStrokeColor,
                    R.styleable.UiRelativeLayout_uiStrokeDashed,
                    R.styleable.UiRelativeLayout_uiStrokeDashGap,
                    R.styleable.UiRelativeLayout_uiStrokeGradientColors,
                    R.styleable.UiRelativeLayout_uiStrokeGradientOrientation,
                    R.styleable.UiRelativeLayout_uiStrokeSides,
                    R.styleable.UiRelativeLayout_uiStrokeCap,
                    R.styleable.UiRelativeLayout_uiStrokeWidths,
                    R.styleable.UiRelativeLayout_uiStrokeGradientStart,
                    R.styleable.UiRelativeLayout_uiStrokeGradientCenter,
                    R.styleable.UiRelativeLayout_uiStrokeGradientEnd,
                    R.styleable.UiRelativeLayout_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiRelativeLayout_uiShadowColor,
                    R.styleable.UiRelativeLayout_uiShadowElevation,
                    R.styleable.UiRelativeLayout_uiShadowRadius,
                    R.styleable.UiRelativeLayout_uiShadowDx,
                    R.styleable.UiRelativeLayout_uiShadowDy,
                    R.styleable.UiRelativeLayout_uiShadowClipParent,
                    R.styleable.UiRelativeLayout_uiShadowAmbientColor,
                    R.styleable.UiRelativeLayout_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiRelativeLayout_uiDimensionRatio,
                    R.styleable.UiRelativeLayout_uiWidthPercent,
                    R.styleable.UiRelativeLayout_uiHeightPercent,
                    R.styleable.UiRelativeLayout_uiMaxWidthPercent,
                    R.styleable.UiRelativeLayout_uiMaxHeightPercent,
                    R.styleable.UiRelativeLayout_uiMinWidthPercent,
                    R.styleable.UiRelativeLayout_uiMinHeightPercent
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

    fun applyStyle(block: UiRelativeLayout.() -> Unit) = apply(block)
}
