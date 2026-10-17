package com.ui.baselib.widget.layout

import android.graphics.Color
import android.view.View

interface IUiLayout {
    val helper: UiLayoutHelper

    fun invalidateView()
    fun invalidateViewOutline()
    fun updateViewClipPath()
}

private inline fun <T> T.applyHelper(
    block: UiLayoutHelper.() -> Unit
): T where T : IUiLayout, T : View = apply {
    helper.block()
    invalidateView()
}

private inline fun <T> T.applyHelperWithOutline(
    block: UiLayoutHelper.() -> Unit
): T where T : IUiLayout, T : View = apply {
    helper.block()
    updateViewClipPath()
    invalidateViewOutline()
    invalidateView()
}

fun <T> T.setCompatElevation(
    elevationDp: Float = helper.compatElevationDp,
    radiusDp: Float = helper.cornerRadius / resources.displayMetrics.density,
    color: Int = helper.compatShadowColor,
    shadowRadius: Float = helper.shadowRadiusPx,
    shadowDx: Float = helper.shadowDxPx,
    shadowDy: Float = helper.shadowDyPx
): T where T : IUiLayout, T : View = apply {
    helper.apply {
        compatElevationDp = elevationDp
        cornerRadius = dp(radiusDp)
        compatShadowColor = color
        shadowRadiusPx = shadowRadius
        shadowDxPx = shadowDx
        shadowDyPx = shadowDy
        setupShadow()
    }
    invalidateViewOutline()
}

fun <T> T.cornerRadius(radius: Float): T where T : IUiLayout, T : View = applyHelperWithOutline {
    cornerRadius = radius
    cornerTopLeft = 0f
    cornerTopRight = 0f
    cornerBottomLeft = 0f
    cornerBottomRight = 0f
}

fun <T> T.cornerRadii(
    topLeft: Float = 0f,
    topRight: Float = 0f,
    bottomRight: Float = 0f,
    bottomLeft: Float = 0f
): T where T : IUiLayout, T : View = applyHelperWithOutline {
    cornerTopLeft = topLeft
    cornerTopRight = topRight
    cornerBottomRight = bottomRight
    cornerBottomLeft = bottomLeft
}

fun <T> T.cornerTopLeft(radius: Float): T where T : IUiLayout, T : View =
    applyHelperWithOutline { cornerTopLeft = radius }

fun <T> T.cornerTopRight(radius: Float): T where T : IUiLayout, T : View =
    applyHelperWithOutline { cornerTopRight = radius }

fun <T> T.cornerBottomLeft(radius: Float): T where T : IUiLayout, T : View =
    applyHelperWithOutline { cornerBottomLeft = radius }

fun <T> T.cornerBottomRight(radius: Float): T where T : IUiLayout, T : View =
    applyHelperWithOutline { cornerBottomRight = radius }

fun <T> T.rounded(radius: Float): T where T : IUiLayout, T : View = cornerRadius(radius)

fun <T> T.corner(radius: Float): T where T : IUiLayout, T : View = cornerRadius(radius)

fun <T> T.corners(
    tl: Float = 0f,
    tr: Float = 0f,
    br: Float = 0f,
    bl: Float = 0f
): T where T : IUiLayout, T : View = cornerRadii(tl, tr, br, bl)

fun <T> T.isBackgroundGradient(isBgGradient: Boolean = true): T where T : IUiLayout, T : View =
    apply { helper.setBackgroundGradientEnabled(isBgGradient) }

fun <T> T.backgroundGradient(
    start: Int,
    end: Int,
    center: Int = Color.TRANSPARENT
): T where T : IUiLayout, T : View = applyHelper {
    bgColors = if (center != Color.TRANSPARENT) intArrayOf(start, center, end) else intArrayOf(start, end)
}

fun <T> T.backgroundGradient(vararg colors: Int): T where T : IUiLayout, T : View = applyHelper {
    bgColors = colors.takeIf { it.isNotEmpty() }
}

fun <T> T.backgroundOrientation(
    orientation: UiLayoutHelper.GradientOrientation
): T where T : IUiLayout, T : View = applyHelper {
    isGradient = true
    bgGradientOrientation = orientation
}

fun <T> T.backgroundGradientType(
    type: UiLayoutHelper.GradientType
): T where T : IUiLayout, T : View = applyHelper {
    isGradient = true
    bgGradientType = type
}

fun <T> T.linearGradient(): T where T : IUiLayout, T : View =
    backgroundGradientType(UiLayoutHelper.GradientType.LINEAR)

fun <T> T.radialGradient(): T where T : IUiLayout, T : View =
    backgroundGradientType(UiLayoutHelper.GradientType.RADIAL)

fun <T> T.sweepGradient(): T where T : IUiLayout, T : View =
    backgroundGradientType(UiLayoutHelper.GradientType.SWEEP)

fun <T> T.radialGradient(
    centerX: Float,
    centerY: Float,
    radius: Float = 0f
): T where T : IUiLayout, T : View = applyHelper {
    isGradient = true
    bgGradientType = UiLayoutHelper.GradientType.RADIAL
    bgGradientCenterX = centerX
    bgGradientCenterY = centerY
    bgGradientRadius = radius
}

fun <T> T.sweepGradient(
    centerX: Float,
    centerY: Float
): T where T : IUiLayout, T : View = applyHelper {
    isGradient = true
    bgGradientType = UiLayoutHelper.GradientType.SWEEP
    bgGradientCenterX = centerX
    bgGradientCenterY = centerY
}

fun <T> T.gradientCenter(centerX: Float, centerY: Float): T where T : IUiLayout, T : View = applyHelper {
    bgGradientCenterX = centerX
    bgGradientCenterY = centerY
}

fun <T> T.gradientCenterX(centerX: Float): T where T : IUiLayout, T : View =
    applyHelper { bgGradientCenterX = centerX }

fun <T> T.gradientCenterY(centerY: Float): T where T : IUiLayout, T : View =
    applyHelper { bgGradientCenterY = centerY }

fun <T> T.gradientRadius(radius: Float): T where T : IUiLayout, T : View =
    applyHelper { bgGradientRadius = radius }

fun <T> T.strokeWidth(width: Float): T where T : IUiLayout, T : View =
    applyHelper { stWidth = width }

fun <T> T.stWidth(width: Float): T where T : IUiLayout, T : View = strokeWidth(width)

fun <T> T.strokeTopWidth(width: Float): T where T : IUiLayout, T : View =
    applyHelper { stTopWidth = width }

fun <T> T.strokeLeftWidth(width: Float): T where T : IUiLayout, T : View =
    applyHelper { stLeftWidth = width }

fun <T> T.strokeBottomWidth(width: Float): T where T : IUiLayout, T : View =
    applyHelper { stBottomWidth = width }

fun <T> T.strokeRightWidth(width: Float): T where T : IUiLayout, T : View =
    applyHelper { stRightWidth = width }

fun <T> T.strokeWidths(
    top: Float,
    left: Float,
    bottom: Float,
    right: Float
): T where T : IUiLayout, T : View = applyHelper {
    stTopWidth = top
    stLeftWidth = left
    stBottomWidth = bottom
    stRightWidth = right
}

fun <T> T.strokeDashed(dashed: Boolean, spacing: Float = 10f): T where T : IUiLayout, T : View = applyHelper {
    isDashed = dashed
    dashSpace = spacing
}

fun <T> T.dashed(spacing: Float = 10f): T where T : IUiLayout, T : View = strokeDashed(true, spacing)

fun <T> T.strokeGradientColors(colors: IntArray): T where T : IUiLayout, T : View =
    apply { helper.setStColors(*colors) }

fun <T> T.strokeGradient(vararg colors: Int): T where T : IUiLayout, T : View =
    apply { helper.setStColors(*colors) }

fun <T> T.strokeGradient(enabled: Boolean): T where T : IUiLayout, T : View =
    applyHelper { isStrokeGradient = enabled }

fun <T> T.strokeOrientation(
    orientation: UiLayoutHelper.GradientOrientation
): T where T : IUiLayout, T : View = applyHelper { strokeGradientOrientation = orientation }

fun <T> T.stColors(vararg colors: Int): T where T : IUiLayout, T : View =
    apply { helper.setStColors(*colors) }

fun <T> T.stColors(
    orientation: UiLayoutHelper.GradientOrientation,
    vararg colors: Int
): T where T : IUiLayout, T : View = apply { helper.setStColors(orientation, *colors) }

fun <T> T.stColor(color: Int): T where T : IUiLayout, T : View = applyHelper {
    isStrokeGradient = false
    stColor = color
}

fun <T> T.strokeOption(option: Int): T where T : IUiLayout, T : View =
    applyHelper { strokeOption = option }

fun <T> T.strokeTop(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_TOP)
fun <T> T.strokeLeft(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_LEFT)
fun <T> T.strokeBottom(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_BOTTOM)
fun <T> T.strokeRight(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_RIGHT)
fun <T> T.strokeHorizontal(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_HORIZONTAL)
fun <T> T.strokeVertical(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_VERTICAL)
fun <T> T.strokeTopLeft(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_TOP_LEFT)
fun <T> T.strokeTopRight(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_TOP_RIGHT)
fun <T> T.strokeBottomLeft(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_BOTTOM_LEFT)
fun <T> T.strokeBottomRight(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_BOTTOM_RIGHT)
fun <T> T.strokeAll(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_ALL)
fun <T> T.strokeNone(): T where T : IUiLayout, T : View = strokeOption(UiLayoutHelper.STROKE_NONE)

fun <T> T.strokeSides(
    top: Boolean = false,
    left: Boolean = false,
    bottom: Boolean = false,
    right: Boolean = false
): T where T : IUiLayout, T : View = strokeOption(
    (if (top) UiLayoutHelper.STROKE_TOP else 0) or
            (if (left) UiLayoutHelper.STROKE_LEFT else 0) or
            (if (bottom) UiLayoutHelper.STROKE_BOTTOM else 0) or
            (if (right) UiLayoutHelper.STROKE_RIGHT else 0)
)

fun <T> T.bgColors(vararg colors: Int): T where T : IUiLayout, T : View =
    applyHelper { bgColors = colors.takeIf { it.isNotEmpty() } }

fun <T> T.bgColors(
    orientation: UiLayoutHelper.GradientOrientation,
    vararg colors: Int
): T where T : IUiLayout, T : View = applyHelper {
    bgColors = colors.takeIf { it.isNotEmpty() }
    bgGradientOrientation = orientation
}

fun <T> T.bgColor(color: Int): T where T : IUiLayout, T : View = applyHelper {
    bgColors = null
    bgColor = color
}
