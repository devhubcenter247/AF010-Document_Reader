package com.ui.baselib.widget.layout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.ui.baselib.R

@Suppress("DEPRECATION")
open class UiConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiConstraintLayout).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiConstraintLayout_uiCornerRadius,
                    R.styleable.UiConstraintLayout_uiCornerTopLeft,
                    R.styleable.UiConstraintLayout_uiCornerTopRight,
                    R.styleable.UiConstraintLayout_uiCornerBottomLeft,
                    R.styleable.UiConstraintLayout_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientEnabled,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientStart,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientCenter,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientEnd,
                    R.styleable.UiConstraintLayout_uiBackgroundColor,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientOrientation,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientType,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientCenterX,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientCenterY,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientRadius,
                    R.styleable.UiConstraintLayout_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiConstraintLayout_uiStrokeWidth,
                    R.styleable.UiConstraintLayout_uiStrokeColor,
                    R.styleable.UiConstraintLayout_uiStrokeDashed,
                    R.styleable.UiConstraintLayout_uiStrokeDashGap,
                    R.styleable.UiConstraintLayout_uiStrokeGradientColors,
                    R.styleable.UiConstraintLayout_uiStrokeGradientOrientation,
                    R.styleable.UiConstraintLayout_uiStrokeSides,
                    R.styleable.UiConstraintLayout_uiStrokeCap,
                    R.styleable.UiConstraintLayout_uiStrokeWidths,
                    R.styleable.UiConstraintLayout_uiStrokeGradientStart,
                    R.styleable.UiConstraintLayout_uiStrokeGradientCenter,
                    R.styleable.UiConstraintLayout_uiStrokeGradientEnd,
                    R.styleable.UiConstraintLayout_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiConstraintLayout_uiShadowColor,
                    R.styleable.UiConstraintLayout_uiShadowElevation,
                    R.styleable.UiConstraintLayout_uiShadowRadius,
                    R.styleable.UiConstraintLayout_uiShadowDx,
                    R.styleable.UiConstraintLayout_uiShadowDy,
                    R.styleable.UiConstraintLayout_uiShadowClipParent,
                    R.styleable.UiConstraintLayout_uiShadowAmbientColor,
                    R.styleable.UiConstraintLayout_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiConstraintLayout_uiDimensionRatio,
                    R.styleable.UiConstraintLayout_uiWidthPercent,
                    R.styleable.UiConstraintLayout_uiHeightPercent,
                    R.styleable.UiConstraintLayout_uiMaxWidthPercent,
                    R.styleable.UiConstraintLayout_uiMaxHeightPercent,
                    R.styleable.UiConstraintLayout_uiMinWidthPercent,
                    R.styleable.UiConstraintLayout_uiMinHeightPercent
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

    fun applyStyle(block: UiConstraintLayout.() -> Unit) = apply(block)
}
