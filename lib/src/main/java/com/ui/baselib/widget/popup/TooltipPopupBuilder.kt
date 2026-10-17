package com.ui.baselib.widget.popup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt

/**
 * A declarative tooltip popup builder with Compose-like DSL syntax.
 *
 * Usage:
 * ```kotlin
 * TooltipPopupBuilder(context)
 *     .anchor(targetView)
 *     .text("This is a tooltip")
 *     .position(TooltipPosition.TOP)
 *     .backgroundColor(Color.BLACK)
 *     .textColor(Color.WHITE)
 *     .showArrow(true)
 *     .autoDismiss(3000L)
 *     .show()
 * ```
 */
class TooltipPopupBuilder(private val context: Context) {

    private var anchor: View? = null
    private var text: CharSequence = ""
    private var position: TooltipPosition = TooltipPosition.TOP
    private var backgroundColor: Int = "#333333".toColorInt()
    private var textColor: Int = Color.WHITE
    private var textSize: Float = 12f
    private var cornerRadius: Float = dp(6f)
    private var paddingHorizontal: Int = dp(12f).toInt()
    private var paddingVertical: Int = dp(8f).toInt()
    private var showArrow: Boolean = true
    private var arrowSize: Float = dp(8f)
    private var maxWidth: Int = Int.MAX_VALUE
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var autoDismissDelay: Long = 0L
    private var outsideTouchable: Boolean = true

    private var onDismissListener: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null
    private val dismissRunnable = Runnable { dismiss() }

    // ---- Builder methods ----

    fun anchor(view: View) = apply { anchor = view }

    fun text(text: CharSequence) = apply { this.text = text }

    fun position(position: TooltipPosition) = apply { this.position = position }

    fun backgroundColor(@ColorInt color: Int) = apply { backgroundColor = color }

    fun textColor(@ColorInt color: Int) = apply { textColor = color }

    fun textSize(size: Float) = apply { textSize = size }

    fun cornerRadius(radius: Float) = apply { cornerRadius = radius }

    fun padding(horizontal: Int = paddingHorizontal, vertical: Int = paddingVertical) = apply {
        paddingHorizontal = horizontal
        paddingVertical = vertical
    }

    fun showArrow(show: Boolean) = apply { showArrow = show }

    fun arrowSize(size: Float) = apply { arrowSize = size }

    fun maxWidth(width: Int) = apply { maxWidth = width }

    fun offset(x: Int = 0, y: Int = 0) = apply {
        offsetX = x
        offsetY = y
    }

    fun autoDismiss(delayMs: Long) = apply { autoDismissDelay = delayMs }

    fun outsideTouchable(touchable: Boolean) = apply { outsideTouchable = touchable }

    fun onDismiss(listener: () -> Unit) = apply { onDismissListener = listener }

    // ---- Actions ----

    fun show(): TooltipPopupBuilder {
        val anchorView = anchor ?: throw IllegalStateException("Anchor view must be set")

        // Create tooltip view
        val tooltipView = TooltipView(context).apply {
            configure(
                text = this@TooltipPopupBuilder.text,
                bgColor = backgroundColor,
                txtColor = textColor,
                txtSize = textSize,
                radius = cornerRadius,
                padH = paddingHorizontal,
                padV = paddingVertical,
                showArrow = this@TooltipPopupBuilder.showArrow,
                arrowSize = this@TooltipPopupBuilder.arrowSize,
                position = position,
                maxWidth = maxWidth
            )
        }

        // Measure tooltip
        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.UNSPECIFIED
        )

        val tooltipWidth = tooltipView.measuredWidth
        val tooltipHeight = tooltipView.measuredHeight

        // Calculate position
        val (xOff, yOff, gravity) = calculateOffset(
            anchorView,
            tooltipWidth, tooltipHeight
        )

        // Create popup
        popupWindow = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = this@TooltipPopupBuilder.outsideTouchable
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            setOnDismissListener {
                tooltipView.removeCallbacks(dismissRunnable)
                onDismissListener?.invoke()
            }
        }

        popupWindow?.showAsDropDown(
            anchorView,
            xOff + offsetX,
            yOff + offsetY,
            gravity
        )

        // Auto dismiss
        if (autoDismissDelay > 0) {
            tooltipView.postDelayed(dismissRunnable, autoDismissDelay)
        }

        return this
    }

    fun dismiss() {
        popupWindow?.dismiss()
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    // ---- Helper ----

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private fun calculateOffset(
        anchor: View,
        tooltipWidth: Int,
        tooltipHeight: Int
    ): Triple<Int, Int, Int> {
        return when (position) {
            TooltipPosition.TOP -> {
                val xOff = (anchor.width - tooltipWidth) / 2
                val yOff = -tooltipHeight - anchor.height
                Triple(xOff, yOff, Gravity.TOP or Gravity.START)
            }
            TooltipPosition.BOTTOM -> {
                val xOff = (anchor.width - tooltipWidth) / 2
                val yOff = 0
                Triple(xOff, yOff, Gravity.TOP or Gravity.START)
            }
            TooltipPosition.LEFT -> {
                val xOff = -tooltipWidth
                val yOff = -anchor.height / 2 - tooltipHeight / 2
                Triple(xOff, yOff, Gravity.TOP or Gravity.START)
            }
            TooltipPosition.RIGHT -> {
                val xOff = anchor.width
                val yOff = -anchor.height / 2 - tooltipHeight / 2
                Triple(xOff, yOff, Gravity.TOP or Gravity.START)
            }
        }
    }

    enum class TooltipPosition {
        TOP, BOTTOM, LEFT, RIGHT
    }

    // ---- Custom tooltip view ----

    private class TooltipView(context: Context) : FrameLayout(context) {

        private val textView = TextView(context)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var bgColor: Int = Color.BLACK
        private var radius: Float = 0f
        private var showArrow: Boolean = true
        private var arrowSize: Float = 0f
        private var position: TooltipPosition = TooltipPosition.TOP

        private val bgPath = Path()
        private val bgRect = RectF()

        init {
            setWillNotDraw(false)
            addView(textView)
        }

        fun configure(
            text: CharSequence,
            bgColor: Int,
            txtColor: Int,
            txtSize: Float,
            radius: Float,
            padH: Int,
            padV: Int,
            showArrow: Boolean,
            arrowSize: Float,
            position: TooltipPosition,
            maxWidth: Int
        ) {
            this.bgColor = bgColor
            this.radius = radius
            this.showArrow = showArrow
            this.arrowSize = arrowSize
            this.position = position

            bgPaint.color = bgColor

            textView.apply {
                this.text = text
                setTextColor(txtColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, txtSize)
                if (maxWidth < Int.MAX_VALUE) {
                    this.maxWidth = maxWidth - padH * 2
                }
            }

            // Adjust padding based on arrow position
            val (pLeft, pTop, pRight, pBottom) = when (position) {
                TooltipPosition.TOP -> listOf(padH, padV, padH, padV + arrowSize.toInt())
                TooltipPosition.BOTTOM -> listOf(padH, padV + arrowSize.toInt(), padH, padV)
                TooltipPosition.LEFT -> listOf(padH, padV, padH + arrowSize.toInt(), padV)
                TooltipPosition.RIGHT -> listOf(padH + arrowSize.toInt(), padV, padH, padV)
            }
            setPadding(pLeft, pTop, pRight, pBottom)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            updatePath(w.toFloat(), h.toFloat())
        }

        private fun updatePath(w: Float, h: Float) {
            bgPath.reset()

            val arrowH = if (showArrow) arrowSize else 0f

            when (position) {
                TooltipPosition.TOP -> {
                    bgRect.set(0f, 0f, w, h - arrowH)
                    bgPath.addRoundRect(bgRect, radius, radius, Path.Direction.CW)

                    if (showArrow) {
                        val centerX = w / 2
                        bgPath.moveTo(centerX - arrowH, h - arrowH)
                        bgPath.lineTo(centerX, h)
                        bgPath.lineTo(centerX + arrowH, h - arrowH)
                        bgPath.close()
                    }
                }
                TooltipPosition.BOTTOM -> {
                    bgRect.set(0f, arrowH, w, h)
                    bgPath.addRoundRect(bgRect, radius, radius, Path.Direction.CW)

                    if (showArrow) {
                        val centerX = w / 2
                        bgPath.moveTo(centerX - arrowH, arrowH)
                        bgPath.lineTo(centerX, 0f)
                        bgPath.lineTo(centerX + arrowH, arrowH)
                        bgPath.close()
                    }
                }
                TooltipPosition.LEFT -> {
                    bgRect.set(0f, 0f, w - arrowH, h)
                    bgPath.addRoundRect(bgRect, radius, radius, Path.Direction.CW)

                    if (showArrow) {
                        val centerY = h / 2
                        bgPath.moveTo(w - arrowH, centerY - arrowH)
                        bgPath.lineTo(w, centerY)
                        bgPath.lineTo(w - arrowH, centerY + arrowH)
                        bgPath.close()
                    }
                }
                TooltipPosition.RIGHT -> {
                    bgRect.set(arrowH, 0f, w, h)
                    bgPath.addRoundRect(bgRect, radius, radius, Path.Direction.CW)

                    if (showArrow) {
                        val centerY = h / 2
                        bgPath.moveTo(arrowH, centerY - arrowH)
                        bgPath.lineTo(0f, centerY)
                        bgPath.lineTo(arrowH, centerY + arrowH)
                        bgPath.close()
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawPath(bgPath, bgPaint)
            super.onDraw(canvas)
        }
    }

    companion object {
        inline fun create(context: Context, block: TooltipPopupBuilder.() -> Unit): TooltipPopupBuilder {
            return TooltipPopupBuilder(context).apply(block)
        }
    }
}

/** Extension for DSL-style tooltip creation */
inline fun Context.tooltip(block: TooltipPopupBuilder.() -> Unit): TooltipPopupBuilder {
    return TooltipPopupBuilder.create(this, block)
}

/** Extension for showing tooltip on this view */
inline fun View.showTooltip(block: TooltipPopupBuilder.() -> Unit): TooltipPopupBuilder {
    return TooltipPopupBuilder(context).apply {
        anchor(this@showTooltip)
        block()
    }.show()
}
