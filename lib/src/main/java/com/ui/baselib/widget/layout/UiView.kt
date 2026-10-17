package com.ui.baselib.widget.layout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.ui.baselib.R

class UiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiView).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiView_uiCornerRadius,
                    R.styleable.UiView_uiCornerTopLeft,
                    R.styleable.UiView_uiCornerTopRight,
                    R.styleable.UiView_uiCornerBottomLeft,
                    R.styleable.UiView_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiView_uiBackgroundGradientEnabled,
                    R.styleable.UiView_uiBackgroundGradientStart,
                    R.styleable.UiView_uiBackgroundGradientCenter,
                    R.styleable.UiView_uiBackgroundGradientEnd,
                    R.styleable.UiView_uiBackgroundColor,
                    R.styleable.UiView_uiBackgroundGradientOrientation,
                    R.styleable.UiView_uiBackgroundGradientType,
                    R.styleable.UiView_uiBackgroundGradientCenterX,
                    R.styleable.UiView_uiBackgroundGradientCenterY,
                    R.styleable.UiView_uiBackgroundGradientRadius,
                    R.styleable.UiView_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiView_uiStrokeWidth,
                    R.styleable.UiView_uiStrokeColor,
                    R.styleable.UiView_uiStrokeDashed,
                    R.styleable.UiView_uiStrokeDashGap,
                    R.styleable.UiView_uiStrokeGradientColors,
                    R.styleable.UiView_uiStrokeGradientOrientation,
                    R.styleable.UiView_uiStrokeSides,
                    R.styleable.UiView_uiStrokeCap,
                    R.styleable.UiView_uiStrokeWidths,
                    R.styleable.UiView_uiStrokeGradientStart,
                    R.styleable.UiView_uiStrokeGradientCenter,
                    R.styleable.UiView_uiStrokeGradientEnd,
                    R.styleable.UiView_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiView_uiShadowColor,
                    R.styleable.UiView_uiShadowElevation,
                    R.styleable.UiView_uiShadowRadius,
                    R.styleable.UiView_uiShadowDx,
                    R.styleable.UiView_uiShadowDy,
                    R.styleable.UiView_uiShadowClipParent,
                    R.styleable.UiView_uiShadowAmbientColor,
                    R.styleable.UiView_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiView_uiDimensionRatio,
                    R.styleable.UiView_uiWidthPercent,
                    R.styleable.UiView_uiHeightPercent,
                    R.styleable.UiView_uiMaxWidthPercent,
                    R.styleable.UiView_uiMaxHeightPercent,
                    R.styleable.UiView_uiMinWidthPercent,
                    R.styleable.UiView_uiMinHeightPercent
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

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        helper.drawShadow(canvas, w, h)
        helper.drawBackground(canvas, w, h)
        helper.drawClipped(canvas) {
            super.onDraw(canvas)
        }
        helper.drawStroke(canvas, w, h)
    }

    override fun invalidateView() = invalidate()
    override fun invalidateViewOutline() = invalidateOutline()
    override fun updateViewClipPath() = helper.updateClipPath(width, height)
}
