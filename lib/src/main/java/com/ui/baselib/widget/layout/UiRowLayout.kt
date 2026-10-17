package com.ui.baselib.widget.layout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import com.ui.baselib.R

class UiRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), IUiLayout {

    override val helper = UiLayoutHelper(this)
    private var justifyContent: Int = 0

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.UiRowLayout).apply {
            try {
                helper.readCornerAttrs(this,
                    R.styleable.UiRowLayout_uiCornerRadius,
                    R.styleable.UiRowLayout_uiCornerTopLeft,
                    R.styleable.UiRowLayout_uiCornerTopRight,
                    R.styleable.UiRowLayout_uiCornerBottomLeft,
                    R.styleable.UiRowLayout_uiCornerBottomRight
                )
                helper.readBackgroundAttrs(this,
                    R.styleable.UiRowLayout_uiBackgroundGradientEnabled,
                    R.styleable.UiRowLayout_uiBackgroundGradientStart,
                    R.styleable.UiRowLayout_uiBackgroundGradientCenter,
                    R.styleable.UiRowLayout_uiBackgroundGradientEnd,
                    R.styleable.UiRowLayout_uiBackgroundColor,
                    R.styleable.UiRowLayout_uiBackgroundGradientOrientation,
                    R.styleable.UiRowLayout_uiBackgroundGradientType,
                    R.styleable.UiRowLayout_uiBackgroundGradientCenterX,
                    R.styleable.UiRowLayout_uiBackgroundGradientCenterY,
                    R.styleable.UiRowLayout_uiBackgroundGradientRadius,
                    R.styleable.UiRowLayout_uiBackgroundGradientColors
                )
                helper.readStrokeAttrs(this,
                    R.styleable.UiRowLayout_uiStrokeWidth,
                    R.styleable.UiRowLayout_uiStrokeColor,
                    R.styleable.UiRowLayout_uiStrokeDashed,
                    R.styleable.UiRowLayout_uiStrokeDashGap,
                    R.styleable.UiRowLayout_uiStrokeGradientColors,
                    R.styleable.UiRowLayout_uiStrokeGradientOrientation,
                    R.styleable.UiRowLayout_uiStrokeSides,
                    R.styleable.UiRowLayout_uiStrokeCap,
                    R.styleable.UiRowLayout_uiStrokeWidths,
                    R.styleable.UiRowLayout_uiStrokeGradientStart,
                    R.styleable.UiRowLayout_uiStrokeGradientCenter,
                    R.styleable.UiRowLayout_uiStrokeGradientEnd,
                    R.styleable.UiRowLayout_uiStrokeGradient
                )
                helper.readShadowAttrs(this,
                    R.styleable.UiRowLayout_uiShadowColor,
                    R.styleable.UiRowLayout_uiShadowElevation,
                    R.styleable.UiRowLayout_uiShadowRadius,
                    R.styleable.UiRowLayout_uiShadowDx,
                    R.styleable.UiRowLayout_uiShadowDy,
                    R.styleable.UiRowLayout_uiShadowClipParent,
                    R.styleable.UiRowLayout_uiShadowAmbientColor,
                    R.styleable.UiRowLayout_uiShadowAmbientRadius,
                )
                helper.readDimensionAttrs(this,
                    R.styleable.UiRowLayout_uiDimensionRatio,
                    R.styleable.UiRowLayout_uiWidthPercent,
                    R.styleable.UiRowLayout_uiHeightPercent,
                    R.styleable.UiRowLayout_uiMaxWidthPercent,
                    R.styleable.UiRowLayout_uiMaxHeightPercent,
                    R.styleable.UiRowLayout_uiMinWidthPercent,
                    R.styleable.UiRowLayout_uiMinHeightPercent
                )
                justifyContent = getInt(R.styleable.UiRowLayout_uiRowJustifyContent, 0)
            } finally {
                recycle()
            }
        }
        helper.setupShadow()
        orientation = HORIZONTAL
        updateJustifyContent()
    }

    private fun updateJustifyContent() {
        gravity = when (justifyContent) {
            1 -> Gravity.CENTER_HORIZONTAL
            2 -> Gravity.END
            3, 4, 5 -> Gravity.START or Gravity.CENTER_VERTICAL
            else -> Gravity.START
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (wSpec, hSpec) = helper.resolveMeasureSpecsReusable(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(wSpec, hSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // CENTER (1) and END (2) are handled via `gravity` in updateJustifyContent();
        // only the distributed options need manual child positioning here.
        if (justifyContent !in 3..5) return
        val n = childCount
        if (n <= 1) return

        fun View.marginStartEnd(): Pair<Int, Int> {
            val lp = layoutParams as? MarginLayoutParams ?: return 0 to 0
            return lp.marginStart to lp.marginEnd
        }

        val totalWidth = width - paddingStart - paddingEnd
        var totalChildrenWidth = 0
        for (i in 0 until n) {
            val child = getChildAt(i)
            val (marginStart, marginEnd) = child.marginStartEnd()
            totalChildrenWidth += child.measuredWidth + marginStart + marginEnd
        }

        val spaceBetween = when (justifyContent) {
            3 -> (totalWidth - totalChildrenWidth) / (n - 1) // space_between: gaps only between children
            4 -> (totalWidth - totalChildrenWidth) / n         // space_around: equal gap around each child
            5 -> (totalWidth - totalChildrenWidth) / (n + 1)   // space_evenly: equal gap incl. both edges
            else -> 0
        }
        var currentX = paddingStart
        when (justifyContent) {
            4 -> currentX += spaceBetween / 2 // half-gap before the first child
            5 -> currentX += spaceBetween
        }
        for (i in 0 until n) {
            val child = getChildAt(i)
            val (marginStart, marginEnd) = child.marginStartEnd()
            currentX += marginStart
            val wChild = child.measuredWidth
            child.layout(currentX, child.top, currentX + wChild, child.bottom)
            currentX += wChild + marginEnd + spaceBetween
        }
    }

    fun setJustifyContent(justify: Int) {
        justifyContent = justify
        updateJustifyContent()
        requestLayout()
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

    fun applyStyle(block: UiRowLayout.() -> Unit) = apply(block)
}
