package com.ui.baselib.widget.layout

import android.content.pm.ApplicationInfo
import android.content.res.TypedArray
import android.graphics.*
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import com.ui.baselib.utils.parseHexColors
import kotlin.math.min

/**
 * Helper class for UI layout drawing operations.
 * Centralizes common drawing logic for all Ui*Layout classes.
 */
class UiLayoutHelper(private val view: View) {
    companion object {
        // Stroke options
        const val STROKE_NONE = 0
        const val STROKE_TOP = 1
        const val STROKE_LEFT = 2
        const val STROKE_BOTTOM = 4
        const val STROKE_RIGHT = 8
        const val STROKE_HORIZONTAL = 5      // top + bottom
        const val STROKE_VERTICAL = 10       // left + right
        const val STROKE_TOP_LEFT = 3        // top + left
        const val STROKE_TOP_RIGHT = 9       // top + right
        const val STROKE_BOTTOM_LEFT = 6     // bottom + left
        const val STROKE_BOTTOM_RIGHT = 12   // bottom + right
        const val STROKE_ALL = 15

        private val COLOR_SPLIT_REGEX = Regex("\\s+")

        // Defaults for the ambient shadow layer, relative to the key shadow.
        private const val AMBIENT_ALPHA_FACTOR = 0.5f
        private const val AMBIENT_RADIUS_FACTOR = 2.5f

        private const val TAG = "UiLayoutHelper"
    }

    // Corner radius
    var cornerRadius = 0f
    var cornerTopLeft = 0f
    var cornerTopRight = 0f
    var cornerBottomLeft = 0f
    var cornerBottomRight = 0f

    // Background
    var isGradient = false
    var bgGradientOrientation = GradientOrientation.TOP_TO_BOTTOM
    var bgGradientType = GradientType.LINEAR
    var bgGradientCenterX = 0.5f  // 0.0 to 1.0, relative to view width
    var bgGradientCenterY = 0.5f  // 0.0 to 1.0, relative to view height
    var bgGradientRadius = 0f    // 0 means auto-calculate based on view size
    var bgColor = Color.TRANSPARENT
    var bgColors: IntArray? = null

    // Stroke
    var stWidth = 0f
    var stTopWidth = -1f
    var stLeftWidth = -1f
    var stBottomWidth = -1f
    var stRightWidth = -1f
    var stColor = Color.TRANSPARENT
    var stColors: IntArray? = null
    var isStrokeGradient = false
    var strokeGradientOrientation = GradientOrientation.LEFT_TO_RIGHT
    var isDashed = false
    var dashSpace = 10f
    var strokeOption = STROKE_ALL
    var strokeCap = Paint.Cap.ROUND

    // Shadow
    var compatElevationDp = 0f
    var compatShadowColor = Color.argb(90, 0, 0, 0)
    // Custom blurred shadow; a radius > 0 replaces the platform elevation shadow.
    var shadowRadiusPx = 0f
    var shadowDxPx = 0f
    var shadowDyPx = 0f
    // Whether the custom shadow may turn off the parent's clipChildren (see allowShadowOutsideBounds).
    var shadowClipParent = ShadowClipParent.AUTO
    // Ambient layer: a wider, fainter, un-offset shadow under the key one (Material/CSS style).
    // null / < 0 derive it from the key shadow; a transparent color turns it off.
    var shadowAmbientColor: Int? = null
    var shadowAmbientRadiusPx = -1f

    // Dimension ratio and percentage sizing
    var dimenRatio: String? = null  // Same syntax as layout_constraintDimensionRatio
    var dimenRatioSide = DimenRatioSide.AUTO  // Which side is calculated from ratio
    var dimenRatioValue = 0f  // The actual ratio value (width/height)
    var widthPercent = -1f  // 0-100, -1 means not set
    var heightPercent = -1f  // 0-100, -1 means not set
    var maxWidthPercent = -1f
    var maxHeightPercent = -1f
    var minWidthPercent = -1f
    var minHeightPercent = -1f

    // ConstraintLayout may measure a child several times. Later passes can contain the
    // child's previously computed size rather than the parent's available size, so keep a
    // stable parent basis for percentage dimensions.
    private var percentParentWidth = 0
    private var percentParentHeight = 0

    /** Values of uiShadowClipParent, in attr enum order. */
    enum class ShadowClipParent {
        AUTO,   // Only when the view is its parent's only child (a dedicated wrapper)
        FORCE,  // Always; for parents without scrolling or overflowing children
        NEVER   // Leave the parent alone; the shadow is clipped to the view bounds
    }

    enum class DimenRatioSide {
        WIDTH,   // Width is calculated from height * ratio
        HEIGHT,  // Height is calculated from width / ratio
        AUTO     // Like Compose aspectRatio: the 0dp/wrap_content side is derived from the fixed one
    }

    // Internal
    private val strokeRectF = RectF()
    private val clipPath = Path()
    private val tmpRectF = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bgPath = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val strokePath = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val keyShadowMask = ShadowMask()
    private val ambientShadowMask = ShadowMask()

    /**
     * Caches a corner-radii FloatArray keyed by the geometry that produced it, so repeated
     * onDraw calls with unchanged size/corners reuse the same array instead of allocating a
     * fresh one every frame.
     */
    private class RadiiCache {
        var array: FloatArray? = null
        private var w = Float.NaN
        private var h = Float.NaN
        private var inset = Float.NaN
        private var tl = 0f
        private var tr = 0f
        private var bl = 0f
        private var br = 0f
        private var uniform = 0f

        fun matches(w: Float, h: Float, inset: Float, tl: Float, tr: Float, bl: Float, br: Float, uniform: Float) =
            array != null && this.w == w && this.h == h && this.inset == inset &&
                this.tl == tl && this.tr == tr && this.bl == bl && this.br == br && this.uniform == uniform

        fun store(array: FloatArray, w: Float, h: Float, inset: Float, tl: Float, tr: Float, bl: Float, br: Float, uniform: Float) {
            this.array = array
            this.w = w; this.h = h; this.inset = inset
            this.tl = tl; this.tr = tr; this.bl = bl; this.br = br; this.uniform = uniform
        }
    }

    private val cornerRadiiCache = RadiiCache()
    private val strokeRadiiCache = RadiiCache()

    // Background gradient shader cache - a Shader is otherwise reallocated on every onDraw.
    private var bgShaderCache: Shader? = null
    private var bgShaderCacheW = Float.NaN
    private var bgShaderCacheH = Float.NaN
    private var bgShaderCacheType: GradientType? = null
    private var bgShaderCacheOrientation: GradientOrientation? = null
    private var bgShaderCacheCenterX = Float.NaN
    private var bgShaderCacheCenterY = Float.NaN
    private var bgShaderCacheRadius = Float.NaN
    private var bgShaderCacheColors: IntArray? = null

    // Stroke gradient shader cache (stroke only supports LINEAR gradients).
    private var strokeShaderCache: Shader? = null
    private var strokeShaderCacheW = Float.NaN
    private var strokeShaderCacheH = Float.NaN
    private var strokeShaderCacheOrientation: GradientOrientation? = null
    private var strokeShaderCacheColors: IntArray? = null

    // Background path cache - rebuilding a round-rect Path every frame is wasted work when
    // geometry hasn't changed since the last draw.
    private var bgPathCacheW = Float.NaN
    private var bgPathCacheH = Float.NaN
    private var bgPathCacheRadii: FloatArray? = null

    // Stroke path cache for the common case only: a single uniform-width border on all 4 sides
    // (the STROKE_ALL branch below). strokePath is a shared mutable field also rebuilt from
    // scratch by drawVariableStroke()/drawPartialStroke() for the other stroke shapes, so those
    // functions null out strokePathCacheRadii to force a rebuild next time this fast path runs -
    // otherwise this cache could return a stale Path left over from a different stroke shape.
    private var strokePathCacheW = Float.NaN
    private var strokePathCacheH = Float.NaN
    private var strokePathCacheInset = Float.NaN
    private var strokePathCacheRadii: FloatArray? = null
    private val outlinePath = Path()

    /** Matches the drawn shape, so elevation shadows follow per-corner radii too. */
    val roundOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (!hasIndividualCorners() || view.width <= 0 || view.height <= 0) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                return
            }
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            outlinePath.reset()
            outlinePath.addRoundRect(0f, 0f, w, h, getCornerRadii(w, h), Path.Direction.CW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                outline.setPath(outlinePath)
            } else {
                @Suppress("DEPRECATION")
                outline.setConvexPath(outlinePath)
            }
        }
    }

    /**
     * Updates the background mode and schedules a new display-list recording.
     *
     * The same Paint instance is reused between frames, so changing modes also clears
     * incompatible paint state and invalidates the display list.
     */
    fun setBackgroundGradientEnabled(enabled: Boolean) {
        isGradient = enabled
        if (!enabled) {
            bgPaint.shader = null
        }
        view.invalidate()
    }

    fun dp(value: Float): Float = value * view.resources.displayMetrics.density

    // Read common attributes from TypedArray
    fun readCornerAttrs(
          ta: TypedArray,
          cornerRadiusAttr: Int,
          topLeftAttr: Int,
          topRightAttr: Int,
          bottomLeftAttr: Int,
          bottomRightAttr: Int
    ) {
        cornerRadius = ta.getDimension(cornerRadiusAttr, 0f)
        cornerTopLeft = ta.getDimension(topLeftAttr, 0f)
        cornerTopRight = ta.getDimension(topRightAttr, 0f)
        cornerBottomLeft = ta.getDimension(bottomLeftAttr, 0f)
        cornerBottomRight = ta.getDimension(bottomRightAttr, 0f)
    }

    fun readBackgroundAttrs(
          ta: TypedArray,
          isGradientAttr: Int,
          gradientStartAttr: Int,
          gradientCenterAttr: Int,
          gradientEndAttr: Int,
          colorAttr: Int,
          orientationAttr: Int,
          gradientTypeAttr: Int = -1,
          gradientCenterXAttr: Int = -1,
          gradientCenterYAttr: Int = -1,
          gradientRadiusAttr: Int = -1,
          gradientColorsAttr: Int = -1
    ) {
        val isGradientExplicit = ta.hasValue(isGradientAttr)
        isGradient = ta.getBoolean(isGradientAttr, false)
        bgColor = ta.getColor(colorAttr, Color.TRANSPARENT)
        bgGradientOrientation = ta.getInt(orientationAttr, 0).toGradientOrientation()

        if (gradientTypeAttr != -1) {
            bgGradientType = ta.getInt(gradientTypeAttr, 0).toGradientType()
        }
        if (gradientCenterXAttr != -1) {
            bgGradientCenterX = ta.getFloat(gradientCenterXAttr, 0.5f)
        }
        if (gradientCenterYAttr != -1) {
            bgGradientCenterY = ta.getFloat(gradientCenterYAttr, 0.5f)
        }
        if (gradientRadiusAttr != -1) {
            bgGradientRadius = ta.getDimension(gradientRadiusAttr, 0f)
        }
        val gradientStart = ta.getColor(gradientStartAttr, Color.TRANSPARENT)
        val gradientCenter = ta.getColor(gradientCenterAttr, Color.TRANSPARENT)
        val gradientEnd = ta.getColor(gradientEndAttr, Color.TRANSPARENT)
        if (gradientStart != Color.TRANSPARENT || gradientEnd != Color.TRANSPARENT) {
            bgColors = if (gradientCenter != Color.TRANSPARENT) {
                intArrayOf(gradientStart, gradientCenter, gradientEnd)
            } else {
                intArrayOf(gradientStart, gradientEnd)
            }
            if (!isGradientExplicit) isGradient = true
        }
        // A color list ("#F00 #0F0" or @array/) overrides start/center/end.
        if (gradientColorsAttr != -1) {
            ta.getColorList(gradientColorsAttr)
                ?.takeIf { it.size >= 2 }
                ?.let {
                    bgColors = it
                    if (!isGradientExplicit) isGradient = true
                }
        }
    }

    fun readStrokeAttrs(
          ta: TypedArray,
          widthAttr: Int,
          colorAttr: Int,
          dashedAttr: Int,
          dashSpaceAttr: Int,
          gradientAttr: Int,
          orientationAttr: Int,
          optionAttr: Int,
          capAttr: Int = -1,
          widthsAttr: Int = -1,
          gradientStartAttr: Int = -1,
          gradientCenterAttr: Int = -1,
          gradientEndAttr: Int = -1,
          gradientEnabledAttr: Int = -1
    ) {
        stWidth = ta.getDimension(widthAttr, 0f)
        if (widthsAttr != -1) {
            ta.getString(widthsAttr)?.let { applyStrokeWidthsString(it) }
        }
        stColor = ta.getColor(colorAttr, Color.TRANSPARENT)
        isDashed = ta.getBoolean(dashedAttr, false)
        dashSpace = ta.getDimension(dashSpaceAttr, 10f)
        strokeGradientOrientation = ta.getInt(orientationAttr, 6).toStrokeOrientation()
        strokeOption = ta.getInt(optionAttr, STROKE_ALL)
        if (capAttr != -1) {
            strokeCap = ta.getInt(capAttr, 1).toStrokeCap()
        }

        // Priority 1: Read from strokeGradientStart/Center/End color attrs
        if (gradientStartAttr != -1 && gradientEndAttr != -1) {
            val gradientStart = ta.getColor(gradientStartAttr, Color.TRANSPARENT)
            val gradientCenter = if (gradientCenterAttr != -1) ta.getColor(gradientCenterAttr, Color.TRANSPARENT) else Color.TRANSPARENT
            val gradientEnd = ta.getColor(gradientEndAttr, Color.TRANSPARENT)
            if (gradientStart != Color.TRANSPARENT || gradientEnd != Color.TRANSPARENT) {
                stColors = if (gradientCenter != Color.TRANSPARENT) {
                    intArrayOf(gradientStart, gradientCenter, gradientEnd)
                } else {
                    intArrayOf(gradientStart, gradientEnd)
                }
            }
        }
        // A color list ("#F00 #0F0" or @array/) overrides start/center/end.
        ta.getColorList(gradientAttr)
            ?.takeIf { it.size >= 2 }
            ?.let { stColors = it }

        // Preserve legacy auto-detection when the flag is omitted. Keeping the colors while
        // disabled allows strokeGradient(true) to restore them later from Kotlin.
        isStrokeGradient = if (gradientEnabledAttr != -1 && ta.hasValue(gradientEnabledAttr)) {
            ta.getBoolean(gradientEnabledAttr, false)
        } else {
            stColors?.size?.let { it >= 2 } == true
        }
    }

    private fun Int.toStrokeCap(): Paint.Cap = when (this) {
        0 -> Paint.Cap.BUTT
        1 -> Paint.Cap.ROUND
        2 -> Paint.Cap.SQUARE
        else -> Paint.Cap.ROUND
    }

    /**
     * Applies a compact "top left bottom right" per-side stroke width string (matching the
     * order of the [strokeWidths] fluent setter). One token sets all four sides uniformly.
     * Each token is a raw dimension: "2dp"/"2dip" (density-scaled), "2sp" (font-scaled),
     * "2px" (as-is), or a bare number (treated as dp) — e.g. `app:uiStrokeWidths="2dp 0 0 4dp"`.
     * Unlike `@dimen/_Nsdp` attrs, these are plain literals with no resource-reference support.
     */
    private fun applyStrokeWidthsString(raw: String) {
        val tokens = raw.trim().split(COLOR_SPLIT_REGEX).filter { it.isNotEmpty() }
        val dm = view.resources.displayMetrics
        val values = tokens.map { parseDimensionToken(it, dm.density, dm.scaledDensity) }
        when (values.size) {
            1 -> {
                stTopWidth = values[0]; stLeftWidth = values[0]
                stBottomWidth = values[0]; stRightWidth = values[0]
            }
            4 -> {
                stTopWidth = values[0]; stLeftWidth = values[1]
                stBottomWidth = values[2]; stRightWidth = values[3]
            }
        }
    }

    private fun parseDimensionToken(token: String, density: Float, scaledDensity: Float): Float {
        val t = token.trim()
        return when {
            t.endsWith("dip", true) -> t.dropLast(3).trim().toFloatOrNull()?.times(density)
            t.endsWith("dp", true) -> t.dropLast(2).trim().toFloatOrNull()?.times(density)
            t.endsWith("sp", true) -> t.dropLast(2).trim().toFloatOrNull()?.times(scaledDensity)
            t.endsWith("px", true) -> t.dropLast(2).trim().toFloatOrNull()
            else -> t.toFloatOrNull()?.times(density)
        } ?: -1f
    }

    fun readShadowAttrs(
          ta: TypedArray,
          colorAttr: Int,
          elevationAttr: Int,
          radiusAttr: Int = -1,
          dxAttr: Int = -1,
          dyAttr: Int = -1,
          clipParentAttr: Int = -1,
          ambientColorAttr: Int = -1,
          ambientRadiusAttr: Int = -1,
    ) {
        compatShadowColor = ta.getColor(colorAttr, compatShadowColor)
        val elevPx = ta.getDimension(elevationAttr, dp(compatElevationDp))
        compatElevationDp = elevPx / view.resources.displayMetrics.density
        if (radiusAttr >= 0) shadowRadiusPx = ta.getDimension(radiusAttr, shadowRadiusPx)
        if (dxAttr >= 0) shadowDxPx = ta.getDimension(dxAttr, shadowDxPx)
        if (dyAttr >= 0) shadowDyPx = ta.getDimension(dyAttr, shadowDyPx)
        if (ambientColorAttr >= 0 && ta.hasValue(ambientColorAttr)) {
            shadowAmbientColor = ta.getColor(ambientColorAttr, Color.TRANSPARENT)
        }
        if (ambientRadiusAttr >= 0 && ta.hasValue(ambientRadiusAttr)) {
            shadowAmbientRadiusPx = ta.getDimension(ambientRadiusAttr, shadowAmbientRadiusPx)
        }
        if (clipParentAttr >= 0) {
            shadowClipParent = ShadowClipParent.entries
                .getOrNull(ta.getInt(clipParentAttr, shadowClipParent.ordinal)) ?: shadowClipParent
        }
          }

    fun readDimensionAttrs(
          ta: TypedArray,
          dimenRatioAttr: Int = -1,
          widthPercentAttr: Int = -1,
          heightPercentAttr: Int = -1,
          maxWidthPercentAttr: Int = -1,
          maxHeightPercentAttr: Int = -1,
          minWidthPercentAttr: Int = -1,
          minHeightPercentAttr: Int = -1
    ) {
        fun readPercent(attr: Int): Float {
            if (attr == -1 || !ta.hasValue(attr)) return -1f
            return when (ta.peekValue(attr)?.type) {
                TypedValue.TYPE_FRACTION -> ta.getFraction(attr, 1, 1, -1f) * 100f
                TypedValue.TYPE_FLOAT -> ta.getFloat(attr, -1f).let {
                    // Compose-style 0..1 values; values above 1 remain usable as 0..100.
                    if (it in 0f..1f) it * 100f else it
                }
                else -> -1f
            }
        }
        if (dimenRatioAttr != -1) {
            ta.getString(dimenRatioAttr)?.let { parseDimenRatio(it) }
        }
        if (widthPercentAttr != -1) {
            readPercent(widthPercentAttr).takeIf { it >= 0f }?.let { widthPercent = it }
        }
        if (heightPercentAttr != -1) {
            readPercent(heightPercentAttr).takeIf { it >= 0f }?.let { heightPercent = it }
        }
        if (maxWidthPercentAttr != -1) {
            readPercent(maxWidthPercentAttr).takeIf { it >= 0f }?.let { maxWidthPercent = it }
        }
        if (maxHeightPercentAttr != -1) {
            readPercent(maxHeightPercentAttr).takeIf { it >= 0f }?.let { maxHeightPercent = it }
        }
        if (minWidthPercentAttr != -1) {
            readPercent(minWidthPercentAttr).takeIf { it >= 0f }?.let { minWidthPercent = it }
        }
        if (minHeightPercentAttr != -1) {
            readPercent(minHeightPercentAttr).takeIf { it >= 0f }?.let { minHeightPercent = it }
        }
    }

    fun parseDimenRatio(ratio: String) {
        dimenRatio = ratio
        val trimmed = ratio.trim()
        if (trimmed.isEmpty()) {
            dimenRatioValue = 0f
            dimenRatioSide = DimenRatioSide.AUTO
            return
        }

        var ratioStr = trimmed
        dimenRatioSide = DimenRatioSide.AUTO

        // Match ConstraintLayout's [W|H],[float|width:height] grammar. A side prefix is
        // recognized only when separated by a comma; e.g. "H,16:9".
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex > 0 && commaIndex < trimmed.lastIndex) {
            when (trimmed.substring(0, commaIndex).trim().uppercase()) {
                "W" -> dimenRatioSide = DimenRatioSide.WIDTH
                "H" -> dimenRatioSide = DimenRatioSide.HEIGHT
            }
            ratioStr = trimmed.substring(commaIndex + 1).trim()
        }

        // Parse ratio value: "16:9" or "1.78"
        dimenRatioValue = if (ratioStr.contains(":")) {
            val parts = ratioStr.split(":")
            if (parts.size == 2) {
                val w = parts[0].trim().toFloatOrNull() ?: 0f
                val h = parts[1].trim().toFloatOrNull() ?: 0f
                if (h > 0f) w / h else 0f
            } else 0f
        } else {
            ratioStr.toFloatOrNull() ?: 0f
        }
    }

    data class MeasureResult(
        var width: Int = 0,
        var height: Int = 0,
        var widthCustomized: Boolean = false,
        var heightCustomized: Boolean = false,
    )

    class ResolvedMeasureSpecs internal constructor(
        var width: Int = 0,
        var height: Int = 0,
    ) {
        operator fun component1() = width
        operator fun component2() = height
    }

    // Measurement always runs on the UI thread and is non-reentrant for a given View, so one
    // result object per helper can safely be reused and avoids garbage during repeated layouts.
    private val reusableMeasureResult = MeasureResult()
    private val reusableResolvedSpecs = ResolvedMeasureSpecs()

    fun measureWithConstraints(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        parentWidth: Int,
        parentHeight: Int
    ): MeasureResult {
        // ConstraintLayout must be the single owner of 0dp percent/ratio geometry. Applying
        // the same percent again in this view's onMeasure would square it (e.g. 35% of 35%).
        if (syncConstraintLayoutParams(parentWidth, parentHeight)) {
            return reusableMeasureResult.apply {
                width = View.MeasureSpec.getSize(widthMeasureSpec)
                height = View.MeasureSpec.getSize(heightMeasureSpec)
                widthCustomized = false
                heightCustomized = false
            }
        }
        val laidOutParent = view.parent as? ViewGroup
        val laidOutParentWidth = laidOutParent?.width
            ?.minus(laidOutParent.paddingLeft + laidOutParent.paddingRight)
            ?.coerceAtLeast(0) ?: 0
        val laidOutParentHeight = laidOutParent?.height
            ?.minus(laidOutParent.paddingTop + laidOutParent.paddingBottom)
            ?.coerceAtLeast(0) ?: 0

        // Prefer an already laid-out parent. During the first layout pass retain the largest
        // available spec, preventing 50% from becoming 50% of its own previous result.
        if (laidOutParentWidth > 0) percentParentWidth = laidOutParentWidth
        else if (parentWidth > percentParentWidth) percentParentWidth = parentWidth
        if (laidOutParentHeight > 0) percentParentHeight = laidOutParentHeight
        else if (parentHeight > percentParentHeight) percentParentHeight = parentHeight
        val percentBaseWidth = percentParentWidth.takeIf { it > 0 } ?: parentWidth
        val percentBaseHeight = percentParentHeight.takeIf { it > 0 } ?: parentHeight

        val specW = View.MeasureSpec.getSize(widthMeasureSpec)
        val specH = View.MeasureSpec.getSize(heightMeasureSpec)
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)

        var measuredWidth = specW
        var measuredHeight = specH
        var widthCustomized = false
        var heightCustomized = false

        // ConstraintLayout represents layout_width="0dp" as EXACTLY, frequently with size 0.
        // It is still a flexible (match-constraints) dimension and must be overridable. Only an
        // actually fixed dimension should win over the Ui percent, matching Compose fillMaxWidth.
        val widthIsMatchConstraint = view.layoutParams?.width == 0
        val heightIsMatchConstraint = view.layoutParams?.height == 0

        if (widthPercent >= 0f && percentBaseWidth > 0 &&
            (widthMode != View.MeasureSpec.EXACTLY || widthIsMatchConstraint)
        ) {
            val computed = (percentBaseWidth * widthPercent / 100f).toInt().coerceAtLeast(0)
            measuredWidth = if (widthMode == View.MeasureSpec.AT_MOST && specW > 0)
                minOf(computed, specW) else computed
            widthCustomized = true
        }

        if (heightPercent >= 0f && percentBaseHeight > 0 &&
            (heightMode != View.MeasureSpec.EXACTLY || heightIsMatchConstraint)
        ) {
            val computed = (percentBaseHeight * heightPercent / 100f).toInt().coerceAtLeast(0)
            measuredHeight = if (heightMode == View.MeasureSpec.AT_MOST && specH > 0)
                minOf(computed, specH) else computed
            heightCustomized = true
        }

        // Apply min/max percentage constraints
        if (minWidthPercent >= 0f && percentBaseWidth > 0) {
            val minW = (percentBaseWidth * minWidthPercent / 100f).toInt().coerceAtLeast(0)
            if (measuredWidth < minW) {
                measuredWidth = minW; widthCustomized = true
            }
        }
        if (maxWidthPercent >= 0f && percentBaseWidth > 0) {
            val maxW = (percentBaseWidth * maxWidthPercent / 100f).toInt().coerceAtLeast(0)
            if (measuredWidth > maxW) {
                measuredWidth = maxW; widthCustomized = true
            }
        }
        if (minHeightPercent >= 0f && percentBaseHeight > 0) {
            val minH = (percentBaseHeight * minHeightPercent / 100f).toInt().coerceAtLeast(0)
            if (measuredHeight < minH) {
                measuredHeight = minH; heightCustomized = true
            }
        }
        if (maxHeightPercent >= 0f && percentBaseHeight > 0) {
            val maxH = (percentBaseHeight * maxHeightPercent / 100f).toInt().coerceAtLeast(0)
            if (measuredHeight > maxH) {
                measuredHeight = maxH; heightCustomized = true
            }
        }

        // Like layout_constraintDimensionRatio, ratio participates only when at least one
        // dimension is match-constraints (0dp). Fixed dp/match_parent dimensions stay fixed.
        if (dimenRatioValue > 0f && (widthIsMatchConstraint || heightIsMatchConstraint)) {
            when (resolveRatioSide(widthMode, specW, heightMode, specH)) {
                DimenRatioSide.HEIGHT, DimenRatioSide.AUTO -> {
                    val baseW = if (measuredWidth > 0) measuredWidth else parentWidth
                    if (baseW > 0) {
                        val computed = (baseW / dimenRatioValue).toInt().coerceAtLeast(0)
                        measuredHeight = if (heightMode == View.MeasureSpec.AT_MOST && specH > 0)
                            minOf(computed, specH) else computed
                        heightCustomized = true
                    }
                }

                DimenRatioSide.WIDTH -> {
                    val baseH = if (measuredHeight > 0) measuredHeight else parentHeight
                    if (baseH > 0) {
                        val computed = (baseH * dimenRatioValue).toInt().coerceAtLeast(0)
                        measuredWidth = if (widthMode == View.MeasureSpec.AT_MOST && specW > 0)
                            minOf(computed, specW) else computed
                        widthCustomized = true
                    }
                }
            }
        }

        return reusableMeasureResult.apply {
            width = measuredWidth.coerceAtLeast(0)
            height = measuredHeight.coerceAtLeast(0)
            this.widthCustomized = widthCustomized
            this.heightCustomized = heightCustomized
        }
    }

    /**
     * AUTO: the side declared as 0dp / wrap_content is computed from the fixed side
     * (match_parent or exact dp). Falls back to whichever spec has a real size, then HEIGHT.
     */
    private fun resolveRatioSide(widthMode: Int, specW: Int, heightMode: Int, specH: Int): DimenRatioSide {
        if (dimenRatioSide != DimenRatioSide.AUTO) return dimenRatioSide
        val lp = view.layoutParams
        val widthFree = lp != null && (lp.width == 0 || lp.width == ViewGroup.LayoutParams.WRAP_CONTENT)
        val heightFree = lp != null && (lp.height == 0 || lp.height == ViewGroup.LayoutParams.WRAP_CONTENT)
        return when {
            widthFree && !heightFree -> DimenRatioSide.WIDTH
            heightFree && !widthFree -> DimenRatioSide.HEIGHT
            specW > 0 && widthMode != View.MeasureSpec.UNSPECIFIED -> DimenRatioSide.HEIGHT
            specH > 0 && heightMode != View.MeasureSpec.UNSPECIFIED -> DimenRatioSide.WIDTH
            else -> DimenRatioSide.HEIGHT
        }
    }

    /**
     * Resolves width/height MeasureSpecs against the percent/ratio constraints configured on
     * this helper. Returns the original specs unchanged when no custom measurement is set.
     * Shared by every Ui*Layout's onMeasure() to avoid duplicating this logic per class.
     */
    fun resolveMeasureSpecsReusable(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ): ResolvedMeasureSpecs {
        if (!shouldApplyCustomMeasure()) {
            return reusableResolvedSpecs.apply {
                width = widthMeasureSpec
                height = heightMeasureSpec
            }
        }

        val dm = view.resources.displayMetrics
        val specW = View.MeasureSpec.getSize(widthMeasureSpec)
        val specH = View.MeasureSpec.getSize(heightMeasureSpec)
        val parentWidth = if (specW > 0) specW else
            (view.parent as? View)?.width?.takeIf { it > 0 } ?: dm.widthPixels
        val parentHeight = if (specH > 0) specH else
            (view.parent as? View)?.height?.takeIf { it > 0 } ?: dm.heightPixels

        val result = measureWithConstraints(widthMeasureSpec, heightMeasureSpec, parentWidth, parentHeight)
        val wSpec = if (result.widthCustomized)
            View.MeasureSpec.makeMeasureSpec(result.width, View.MeasureSpec.EXACTLY)
            else widthMeasureSpec
        val hSpec = if (result.heightCustomized)
            View.MeasureSpec.makeMeasureSpec(result.height, View.MeasureSpec.EXACTLY)
            else heightMeasureSpec
        return reusableResolvedSpecs.apply {
            width = wSpec
            height = hSpec
        }
    }

    /** Compatibility API. Ui widgets use [resolveMeasureSpecsReusable] to avoid this Pair. */
    fun resolveMeasureSpecs(widthMeasureSpec: Int, heightMeasureSpec: Int): Pair<Int, Int> {
        val result = resolveMeasureSpecsReusable(widthMeasureSpec, heightMeasureSpec)
        return result.width to result.height
    }

    fun shouldApplyCustomMeasure(): Boolean {
        return dimenRatioValue > 0f ||
                widthPercent >= 0f ||
                heightPercent >= 0f ||
                maxWidthPercent >= 0f ||
                maxHeightPercent >= 0f ||
                minWidthPercent >= 0f ||
                minHeightPercent >= 0f
    }

    // minSdk is 24 (lib) / 26 (app), both >= LOLLIPOP (21), so elevation-based
    // shadows are always available — no pre-Lollipop fallback needed.
    fun setupShadow() {
        view.outlineProvider = roundOutlineProvider
        view.clipToOutline = false
        // The custom shadow is drawn by drawShadow(); an elevation would add a second one.
        ViewCompat.setElevation(view, if (hasCustomShadow()) 0f else dp(compatElevationDp))
        applyPlatformShadowColor()
        allowShadowOutsideBounds()
        view.invalidate()
    }

    fun hasCustomShadow(): Boolean = shadowRadiusPx > 0f && Color.alpha(compatShadowColor) != 0

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                allowShadowOutsideBounds()
                syncConstraintLayoutParams()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    /**
     * Let ConstraintLayout's solver own percentage and ratio geometry when this view is its
     * child. Custom onMeasure alone cannot override a 0dp dimension tied to constraints.
     */
    private fun syncConstraintLayoutParams(
        parentWidth: Int = (view.parent as? ViewGroup)?.let {
            (it.width - it.paddingLeft - it.paddingRight).coerceAtLeast(0)
        } ?: 0,
        parentHeight: Int = (view.parent as? ViewGroup)?.let {
            (it.height - it.paddingTop - it.paddingBottom).coerceAtLeast(0)
        } ?: 0,
    ): Boolean {
        val lp = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        var solverOwnsSizing = false
        if (lp.width == 0 && widthPercent >= 0f) {
            lp.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
            lp.matchConstraintPercentWidth = widthPercent / 100f
            solverOwnsSizing = true
        }
        if (lp.height == 0 && heightPercent >= 0f) {
            lp.matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
            lp.matchConstraintPercentHeight = heightPercent / 100f
            solverOwnsSizing = true
        }
        if (lp.width == 0 && parentWidth > 0) {
            if (minWidthPercent >= 0f) {
                lp.matchConstraintMinWidth = (parentWidth * minWidthPercent / 100f).toInt()
                solverOwnsSizing = true
            }
            if (maxWidthPercent >= 0f) {
                lp.matchConstraintMaxWidth = (parentWidth * maxWidthPercent / 100f).toInt()
                solverOwnsSizing = true
            }
        }
        if (lp.height == 0 && parentHeight > 0) {
            if (minHeightPercent >= 0f) {
                lp.matchConstraintMinHeight = (parentHeight * minHeightPercent / 100f).toInt()
                solverOwnsSizing = true
            }
            if (maxHeightPercent >= 0f) {
                lp.matchConstraintMaxHeight = (parentHeight * maxHeightPercent / 100f).toInt()
                solverOwnsSizing = true
            }
        }
        if (dimenRatio != null && (lp.width == 0 || lp.height == 0)) {
            lp.dimensionRatio = dimenRatio?.trim()?.takeIf { it.isNotEmpty() }
            solverOwnsSizing = true
        }
        return solverOwnsSizing
    }

    /**
     * The custom shadow lies outside the view bounds, which a clipping parent would cut off.
     * clipChildren=false un-clips *every* child of the parent, so a scrolling sibling
     * (RecyclerView, ScrollView, ...) would draw its content over the views around it.
     * AUTO therefore only touches a parent that holds nothing but this view.
     */
    private fun allowShadowOutsideBounds() {
        if (!hasCustomShadow()) return
        val parent = view.parent as? ViewGroup ?: return
        when (shadowClipParent) {
            ShadowClipParent.FORCE -> parent.clipChildren = false
            ShadowClipParent.NEVER -> Unit
            ShadowClipParent.AUTO ->
                if (parent.childCount == 1) parent.clipChildren = false
                else if (parent.clipChildren) warnShadowClipped()
        }
    }

    private var warnedShadowClipped = false

    private fun warnShadowClipped() {
        val debuggable = (view.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || warnedShadowClipped) return
        warnedShadowClipped = true
        val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        Log.w(TAG, "Custom shadow of ${view.javaClass.simpleName}${name?.let { " @id/$it" } ?: ""} " +
            "is clipped: its parent has other children, so clipChildren is left on. Wrap the view " +
            "in its own container with padding for the shadow, or set uiShadowClipParent=\"force\".")
    }

    /**
     * Draws the custom shadow in two layers, like Material/CSS box-shadow: a wide, faint
     * ambient layer with no offset, then the key layer (uiShadowRadius, offset by dx/dy).
     * It extends past the view bounds, so the parent must not clip children (see
     * allowShadowOutsideBounds / shadowClipParent) and needs room via margin/padding.
     * Call it before any clip.
     *
     * BlurMaskFilter isn't supported by the hardware canvas below API 28, so each blurred
     * mask is rendered once into an ALPHA_8 bitmap on a software canvas and tinted with the
     * shadow color when drawn.
     */
    fun drawShadow(canvas: Canvas, w: Float, h: Float) {
        if (!hasCustomShadow() || w <= 0f || h <= 0f) return
        val radii = getCornerRadii(w, h)

        val ambientColor = shadowAmbientColor ?: compatShadowColor.scaleAlpha(AMBIENT_ALPHA_FACTOR)
        val ambientRadius = shadowAmbientRadiusPx.takeIf { it >= 0f }
            ?: (shadowRadiusPx * AMBIENT_RADIUS_FACTOR)
        if (Color.alpha(ambientColor) != 0 && ambientRadius > 0f) {
            ambientShadowMask.draw(canvas, w, h, ambientRadius, radii, 0f, 0f, ambientColor)
        }
        keyShadowMask.draw(canvas, w, h, shadowRadiusPx, radii, shadowDxPx, shadowDyPx, compatShadowColor)
    }

    private fun Int.scaleAlpha(factor: Float): Int =
        (this and 0x00FFFFFF) or ((Color.alpha(this) * factor).toInt().coerceIn(0, 255) shl 24)

    /** One cached blurred mask of the rounded shape. */
    private inner class ShadowMask {
        private var bitmap: Bitmap? = null
        private var cacheW = Float.NaN
        private var cacheH = Float.NaN
        private var cacheRadius = Float.NaN
        private var cacheRadii: FloatArray? = null

        fun draw(canvas: Canvas, w: Float, h: Float, r: Float, radii: FloatArray, dx: Float, dy: Float, color: Int) {
            val mask = obtain(w, h, r, radii) ?: return
            val pad = shadowPadding(r)
            shadowPaint.color = color
            canvas.drawBitmap(mask, dx - pad, dy - pad, shadowPaint)
        }

        private fun obtain(w: Float, h: Float, r: Float, radii: FloatArray): Bitmap? {
            bitmap?.let {
                if (cacheW == w && cacheH == h && cacheRadius == r && cacheRadii === radii) return it
            }
            val pad = shadowPadding(r)
            val bw = (w + 2 * pad).toInt().coerceAtLeast(1)
            val bh = (h + 2 * pad).toInt().coerceAtLeast(1)
            val newBitmap = runCatching { Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8) }.getOrNull() ?: return null
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            }
            val path = Path().apply {
                addRoundRect(RectF(pad, pad, pad + w, pad + h), radii, Path.Direction.CW)
            }
            Canvas(newBitmap).drawPath(path, maskPaint)
            bitmap?.recycle()
            bitmap = newBitmap
            cacheW = w; cacheH = h; cacheRadius = r; cacheRadii = radii
            return newBitmap
        }
    }

    /**
     * How far the blur reaches past the shape. BlurMaskFilter turns the radius into
     * sigma = r * 0.57735 + 0.5 and the Gaussian fades out at ~3 sigma; padding by only r
     * would cut the tail off into a visible hard edge.
     */
    private fun shadowPadding(r: Float): Float = kotlin.math.ceil(3f * (r * 0.57735f + 0.5f))

    fun applyPlatformShadowColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = compatShadowColor
            view.outlineAmbientShadowColor = compatShadowColor
        }
    }

    fun onSizeChanged(w: Int, h: Int) {
        updateClipPath(w, h)
        view.invalidateOutline()
    }

    fun updateClipPath(w: Int, h: Int) {
        clipPath.reset()
        if (w <= 0 || h <= 0) return
        tmpRectF.set(0f, 0f, w.toFloat(), h.toFloat())
        val radii = getCornerRadii(w.toFloat(), h.toFloat())
        clipPath.addRoundRect(tmpRectF, radii, Path.Direction.CW)
        clipPath.close()
    }

    fun getClipPath(): Path = clipPath

    fun hasIndividualCorners(): Boolean =
        cornerTopLeft > 0f || cornerTopRight > 0f || cornerBottomLeft > 0f || cornerBottomRight > 0f

    fun hasCorners(): Boolean = cornerRadius > 0f || hasIndividualCorners()

    /**
     * Runs [block] clipped to the rounded corners. A path clip is costly on every frame, so a
     * square view (the common case) skips it.
     */
    inline fun drawClipped(canvas: Canvas, block: () -> Unit) {
        if (!hasCorners()) {
            block()
            return
        }
        val save = canvas.save()
        canvas.clipPath(getClipPath())
        try {
            block()
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun hasBackground(): Boolean {
        val gradientColors = bgColors
        return (isGradient && gradientColors != null && gradientColors.size >= 2) ||
            Color.alpha(bgColor) != 0
    }

    fun getCornerRadii(w: Float, h: Float): FloatArray {
        cornerRadiiCache.array?.let {
            if (cornerRadiiCache.matches(w, h, 0f, cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight, cornerRadius)) {
                return it
            }
        }
        val maxRadius = min(w / 2f, h / 2f)
        val radii = if (hasIndividualCorners()) {
            floatArrayOf(
                min(cornerTopLeft, maxRadius), min(cornerTopLeft, maxRadius),
                min(cornerTopRight, maxRadius), min(cornerTopRight, maxRadius),
                min(cornerBottomRight, maxRadius), min(cornerBottomRight, maxRadius),
                min(cornerBottomLeft, maxRadius), min(cornerBottomLeft, maxRadius)
            )
        } else {
            val r = min(cornerRadius, maxRadius)
            floatArrayOf(r, r, r, r, r, r, r, r)
        }
        cornerRadiiCache.store(radii, w, h, 0f, cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight, cornerRadius)
        return radii
    }

    fun getStrokeCornerRadii(w: Float, h: Float, inset: Float): FloatArray {
        strokeRadiiCache.array?.let {
            if (strokeRadiiCache.matches(w, h, inset, cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight, cornerRadius)) {
                return it
            }
        }
        val maxRadius = min(w / 2f, h / 2f)
        val radii = if (hasIndividualCorners()) {
            floatArrayOf(
                (min(cornerTopLeft, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerTopLeft, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerTopRight, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerTopRight, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerBottomRight, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerBottomRight, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerBottomLeft, maxRadius) - inset).coerceAtLeast(0f),
                (min(cornerBottomLeft, maxRadius) - inset).coerceAtLeast(0f)
            )
        } else {
            val r = (min(cornerRadius, maxRadius) - inset).coerceAtLeast(0f)
            floatArrayOf(r, r, r, r, r, r, r, r)
        }
        strokeRadiiCache.store(radii, w, h, inset, cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight, cornerRadius)
        return radii
    }

    fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        if (!hasBackground()) return
        val radii = getCornerRadii(w, h)
        val gradientColors = bgColors
        if (isGradient && gradientColors != null && gradientColors.size >= 2) {
            // Paint.color also changes Paint.alpha. A translucent solid background used by the
            // previous frame would otherwise make the restored gradient translucent as well.
            bgPaint.alpha = 255
            bgPaint.shader = obtainBgShader(w, h, gradientColors)
        } else {
            bgPaint.shader = null
            bgPaint.color = bgColor
        }
        if (bgPathCacheRadii !== radii || bgPathCacheW != w || bgPathCacheH != h) {
            bgPath.reset()
            bgPath.addRoundRect(tmpRectF.apply { set(0f, 0f, w, h) }, radii, Path.Direction.CW)
            bgPathCacheRadii = radii; bgPathCacheW = w; bgPathCacheH = h
        }
        canvas.drawPath(bgPath, bgPaint)
    }

    /** Reuses the previously built gradient Shader when size/colors/orientation are unchanged. */
    private fun obtainBgShader(w: Float, h: Float, colors: IntArray): Shader {
        bgShaderCache?.let {
            if (bgShaderCacheW == w && bgShaderCacheH == h &&
                bgShaderCacheType == bgGradientType && bgShaderCacheOrientation == bgGradientOrientation &&
                bgShaderCacheCenterX == bgGradientCenterX && bgShaderCacheCenterY == bgGradientCenterY &&
                bgShaderCacheRadius == bgGradientRadius &&
                bgShaderCacheColors?.contentEquals(colors) == true
            ) {
                return it
            }
        }
        val shader = when (bgGradientType) {
            GradientType.LINEAR -> {
                val (x0, y0, x1, y1) = bgGradientOrientation.toCoordinates(w, h)
                LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
            }
            GradientType.RADIAL -> {
                val centerX = w * bgGradientCenterX
                val centerY = h * bgGradientCenterY
                val radius = if (bgGradientRadius > 0f) bgGradientRadius else {
                    kotlin.math.max(w, h) / 2f
                }
                RadialGradient(centerX, centerY, radius, colors, null, Shader.TileMode.CLAMP)
            }
            GradientType.SWEEP -> {
                val centerX = w * bgGradientCenterX
                val centerY = h * bgGradientCenterY
                SweepGradient(centerX, centerY, colors, null)
            }
        }
        bgShaderCache = shader
        bgShaderCacheW = w; bgShaderCacheH = h
        bgShaderCacheType = bgGradientType; bgShaderCacheOrientation = bgGradientOrientation
        bgShaderCacheCenterX = bgGradientCenterX; bgShaderCacheCenterY = bgGradientCenterY
        bgShaderCacheRadius = bgGradientRadius; bgShaderCacheColors = colors.copyOf()
        return shader
    }

    fun drawStroke(canvas: Canvas, w: Float, h: Float) {
        if (strokeOption == STROKE_NONE || w <= 0f || h <= 0f) return

        val topWidth = selectedStrokeWidth(STROKE_TOP, stTopWidth)
        val leftWidth = selectedStrokeWidth(STROKE_LEFT, stLeftWidth)
        val bottomWidth = selectedStrokeWidth(STROKE_BOTTOM, stBottomWidth)
        val rightWidth = selectedStrokeWidth(STROKE_RIGHT, stRightWidth)
        val uniformWidth = when {
            topWidth > 0f -> topWidth
            leftWidth > 0f -> leftWidth
            bottomWidth > 0f -> bottomWidth
            rightWidth > 0f -> rightWidth
            else -> return
        }.takeIf { width ->
            (topWidth <= 0f || topWidth == width) &&
                (leftWidth <= 0f || leftWidth == width) &&
                (bottomWidth <= 0f || bottomWidth == width) &&
                (rightWidth <= 0f || rightWidth == width)
        }

        val activeOption =
            (if (topWidth > 0f) STROKE_TOP else 0) or
                (if (leftWidth > 0f) STROKE_LEFT else 0) or
                (if (bottomWidth > 0f) STROKE_BOTTOM else 0) or
                (if (rightWidth > 0f) STROKE_RIGHT else 0)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.strokeCap = this.strokeCap
        val hasValidDashEffect = isDashed && dashSpace > 0f
        strokePaint.pathEffect = if (hasValidDashEffect) obtainDashEffect() else null
        val gradientColors = stColors?.takeIf { isStrokeGradient }
        if (gradientColors != null && gradientColors.size >= 2) {
            // Paint.color also changes Paint.alpha. Preserve the gradient colors' own alpha
            // instead of modulating the shader with a translucent solid stroke from a prior draw.
            strokePaint.alpha = 255
            strokePaint.shader = obtainStrokeShader(w, h, gradientColors)
        } else {
            strokePaint.shader = null
            strokePaint.color = stColor
        }

        if (uniformWidth != null && uniformWidth < min(w, h)) {
            strokePaint.strokeWidth = uniformWidth
            val inset = uniformWidth / 2f
            if (activeOption == STROKE_ALL) {
                val strokeRadii = getStrokeCornerRadii(w, h, inset)
                if (strokePathCacheRadii !== strokeRadii || strokePathCacheInset != inset ||
                    strokePathCacheW != w || strokePathCacheH != h
                ) {
                    strokeRectF.set(inset, inset, w - inset, h - inset)
                    strokePath.reset()
                    strokePath.fillType = Path.FillType.WINDING
                    strokePath.addRoundRect(strokeRectF, strokeRadii, Path.Direction.CW)
                    strokePathCacheRadii = strokeRadii
                    strokePathCacheInset = inset; strokePathCacheW = w; strokePathCacheH = h
                }
                canvas.drawPath(strokePath, strokePaint)
            } else {
                drawPartialStroke(canvas, w, h, inset, strokePaint, activeOption)
            }
        } else if (hasValidDashEffect) {
            drawDashedVariableStroke(
                canvas, w, h, topWidth, leftWidth, bottomWidth, rightWidth
            )
        } else {
            drawVariableStroke(
                canvas, w, h, topWidth, leftWidth, bottomWidth, rightWidth
            )
        }
    }

    private var dashEffectCache: DashPathEffect? = null
    private var dashEffectCacheSpace = Float.NaN

    private fun obtainDashEffect(): DashPathEffect {
        dashEffectCache?.let { if (dashEffectCacheSpace == dashSpace) return it }
        return DashPathEffect(floatArrayOf(dashSpace, dashSpace), 0f).also {
            dashEffectCache = it
            dashEffectCacheSpace = dashSpace
        }
    }

    /** Reuses the previously built gradient Shader when size/colors/orientation are unchanged. */
    private fun obtainStrokeShader(w: Float, h: Float, colors: IntArray): Shader {
        strokeShaderCache?.let {
            if (strokeShaderCacheW == w && strokeShaderCacheH == h &&
                strokeShaderCacheOrientation == strokeGradientOrientation &&
                strokeShaderCacheColors?.contentEquals(colors) == true
            ) {
                return it
            }
        }
        val (x0, y0, x1, y1) = strokeGradientOrientation.toCoordinates(w, h)
        val shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        strokeShaderCache = shader
        strokeShaderCacheW = w; strokeShaderCacheH = h
        strokeShaderCacheOrientation = strokeGradientOrientation
        strokeShaderCacheColors = colors.copyOf()
        return shader
    }

    private fun selectedStrokeWidth(side: Int, overrideWidth: Float): Float {
        if (strokeOption and side == 0) return 0f
        return (if (overrideWidth >= 0f) overrideWidth else stWidth).coerceAtLeast(0f)
    }

    /**
     * Renders an asymmetric border as the area between the view's outer shape and an inner
     * rounded shape inset independently on each side.
     */
    private fun drawVariableStroke(
        canvas: Canvas,
        w: Float,
        h: Float,
        topWidth: Float,
        leftWidth: Float,
        bottomWidth: Float,
        rightWidth: Float
    ) {
        val top = topWidth.coerceAtMost(h)
        val left = leftWidth.coerceAtMost(w)
        val bottom = bottomWidth.coerceAtMost(h)
        val right = rightWidth.coerceAtMost(w)
        val innerLeft = left
        val innerTop = top
        val innerRight = (w - right).coerceAtLeast(innerLeft)
        val innerBottom = (h - bottom).coerceAtLeast(innerTop)

        // strokePath is shared with the STROKE_ALL fast path in drawStroke(); invalidate its
        // cache since we're about to overwrite the Path with a different shape here.
        strokePathCacheRadii = null
        strokePath.reset()
        strokePath.fillType = Path.FillType.EVEN_ODD
        strokeRectF.set(0f, 0f, w, h)
        val outerRadii = getCornerRadii(w, h)
        strokePath.addRoundRect(strokeRectF, outerRadii, Path.Direction.CW)

        if (innerRight > innerLeft && innerBottom > innerTop) {
            val innerMaxRadiusX = (innerRight - innerLeft) / 2f
            val innerMaxRadiusY = (innerBottom - innerTop) / 2f
            val innerRadii = floatArrayOf(
                (outerRadii[0] - left).coerceIn(0f, innerMaxRadiusX),
                (outerRadii[1] - top).coerceIn(0f, innerMaxRadiusY),
                (outerRadii[2] - right).coerceIn(0f, innerMaxRadiusX),
                (outerRadii[3] - top).coerceIn(0f, innerMaxRadiusY),
                (outerRadii[4] - right).coerceIn(0f, innerMaxRadiusX),
                (outerRadii[5] - bottom).coerceIn(0f, innerMaxRadiusY),
                (outerRadii[6] - left).coerceIn(0f, innerMaxRadiusX),
                (outerRadii[7] - bottom).coerceIn(0f, innerMaxRadiusY)
            )
            strokeRectF.set(innerLeft, innerTop, innerRight, innerBottom)
            strokePath.addRoundRect(strokeRectF, innerRadii, Path.Direction.CW)
        }

        strokePaint.style = Paint.Style.FILL
        strokePaint.pathEffect = null
        canvas.drawPath(strokePath, strokePaint)
        strokePaint.style = Paint.Style.STROKE
    }

    private fun drawDashedVariableStroke(
        canvas: Canvas,
        w: Float,
        h: Float,
        topWidth: Float,
        leftWidth: Float,
        bottomWidth: Float,
        rightWidth: Float
    ) {
        drawDashedSide(canvas, w, h, STROKE_TOP, topWidth)
        drawDashedSide(canvas, w, h, STROKE_RIGHT, rightWidth)
        drawDashedSide(canvas, w, h, STROKE_BOTTOM, bottomWidth)
        drawDashedSide(canvas, w, h, STROKE_LEFT, leftWidth)
    }

    private fun drawDashedSide(canvas: Canvas, w: Float, h: Float, option: Int, width: Float) {
        if (width <= 0f) return
        val maxWidth = if (option == STROKE_TOP || option == STROKE_BOTTOM) h else w
        val safeWidth = width.coerceAtMost(maxWidth)
        strokePaint.strokeWidth = safeWidth
        drawPartialStroke(canvas, w, h, safeWidth / 2f, strokePaint, option)
    }

    /**
     * Draws a subset of the four sides. A corner arc is included whenever EITHER of its two
     * adjacent sides is active — e.g. `strokeOption = STROKE_TOP` alone still curves into both
     * top corners — instead of requiring both adjacent sides, which used to leave the line
     * floating as a flat, inset segment disconnected from the shape's rounded corners.
     *
     * The 8 clockwise features (corner arc, side line) x4 are walked starting right after a
     * genuine gap so that a run wrapping across the array boundary (e.g. LEFT+TOP) is still
     * drawn as one continuous subpath rather than being split with a spurious round cap.
     */
    private fun drawPartialStroke(
        canvas: Canvas,
        w: Float,
        h: Float,
        inset: Float,
        paint: Paint,
        option: Int
    ) {
        // strokePath is shared with the STROKE_ALL fast path in drawStroke(); invalidate its
        // cache since we're about to overwrite the Path with a different shape here.
        strokePathCacheRadii = null
        strokePath.reset()
        strokePath.fillType = Path.FillType.WINDING
        val radii = getStrokeCornerRadii(w, h, inset)
        val topLeftR = radii[0]
        val topRightR = radii[2]
        val bottomRightR = radii[4]
        val bottomLeftR = radii[6]

        val hasTop = option and STROKE_TOP != 0
        val hasRight = option and STROKE_RIGHT != 0
        val hasBottom = option and STROKE_BOTTOM != 0
        val hasLeft = option and STROKE_LEFT != 0

        val features = booleanArrayOf(
            (hasTop || hasLeft) && topLeftR > 0,          // 0: top-left arc
            hasTop,                                        // 1: top line
            (hasTop || hasRight) && topRightR > 0,         // 2: top-right arc
            hasRight,                                      // 3: right line
            (hasRight || hasBottom) && bottomRightR > 0,   // 4: bottom-right arc
            hasBottom,                                     // 5: bottom line
            (hasBottom || hasLeft) && bottomLeftR > 0,     // 6: bottom-left arc
            hasLeft                                        // 7: left line
        )
        if (features.none { it }) return

        val gapIndex = features.indices.firstOrNull { !features[it] }
        val start = if (gapIndex != null) (gapIndex + 1) % 8 else 0

        for (k in 0 until 8) {
            val i = (start + k) % 8
            if (!features[i]) continue
            val isNewRun = k == 0 || !features[(start + k - 1) % 8]
            when (i) {
                0 -> {
                    if (isNewRun) strokePath.moveTo(inset, inset + topLeftR)
                    strokePath.arcTo(inset, inset, inset + topLeftR * 2, inset + topLeftR * 2, 180f, 90f, false)
                }
                1 -> {
                    if (isNewRun) strokePath.moveTo(inset + topLeftR, inset)
                    strokePath.lineTo(w - inset - topRightR, inset)
                }
                2 -> {
                    if (isNewRun) strokePath.moveTo(w - inset - topRightR, inset)
                    strokePath.arcTo(w - inset - topRightR * 2, inset, w - inset, inset + topRightR * 2, -90f, 90f, false)
                }
                3 -> {
                    if (isNewRun) strokePath.moveTo(w - inset, inset + topRightR)
                    strokePath.lineTo(w - inset, h - inset - bottomRightR)
                }
                4 -> {
                    if (isNewRun) strokePath.moveTo(w - inset, h - inset - bottomRightR)
                    strokePath.arcTo(w - inset - bottomRightR * 2, h - inset - bottomRightR * 2, w - inset, h - inset, 0f, 90f, false)
                }
                5 -> {
                    if (isNewRun) strokePath.moveTo(w - inset - bottomRightR, h - inset)
                    strokePath.lineTo(inset + bottomLeftR, h - inset)
                }
                6 -> {
                    if (isNewRun) strokePath.moveTo(inset + bottomLeftR, h - inset)
                    strokePath.arcTo(inset, h - inset - bottomLeftR * 2, inset + bottomLeftR * 2, h - inset, 90f, 90f, false)
                }
                7 -> {
                    if (isNewRun) strokePath.moveTo(inset, h - inset - bottomLeftR)
                    strokePath.lineTo(inset, inset + topLeftR)
                }
            }
        }
        canvas.drawPath(strokePath, paint)
    }

    // Gradient type
    enum class GradientType {
        LINEAR, RADIAL, SWEEP
    }

    // Gradient orientation
    enum class GradientOrientation {
        TOP_TO_BOTTOM, TR_BL, RIGHT_TO_LEFT, BR_TL,
        BOTTOM_TO_TOP, BL_TR, LEFT_TO_RIGHT, TL_BR;

        fun toCoordinates(w: Float, h: Float) = when (this) {
            TOP_TO_BOTTOM -> Quad(0f, 0f, 0f, h)
            BOTTOM_TO_TOP -> Quad(0f, h, 0f, 0f)
            LEFT_TO_RIGHT -> Quad(0f, 0f, w, 0f)
            RIGHT_TO_LEFT -> Quad(w, 0f, 0f, 0f)
            TL_BR -> Quad(0f, 0f, w, h)
            TR_BL -> Quad(w, 0f, 0f, h)
            BL_TR -> Quad(0f, h, w, 0f)
            BR_TL -> Quad(w, h, 0f, 0f)
        }
    }

    data class Quad(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    private fun Int.toGradientOrientation() =
        GradientOrientation.entries.getOrElse(this) { GradientOrientation.TOP_TO_BOTTOM }

    private fun Int.toStrokeOrientation() =
        GradientOrientation.entries.getOrElse(this) { GradientOrientation.LEFT_TO_RIGHT }

    private fun Int.toGradientType() = GradientType.entries.getOrElse(this) { GradientType.LINEAR }
    // ==================== Programmatic Setters ====================
    /** Set background gradient colors */
    fun setBgColors(vararg colors: Int): UiLayoutHelper {
        bgColors = colors.takeIf { it.size >= 2 }?.copyOf()
        isGradient = bgColors != null
        view.invalidate()
        return this
    }

    /** Set background gradient colors with orientation */
    fun setBgColors(orientation: GradientOrientation, vararg colors: Int): UiLayoutHelper {
        bgColors = colors.takeIf { it.size >= 2 }?.copyOf()
        isGradient = bgColors != null
        bgGradientOrientation = orientation
        view.invalidate()
        return this
    }

    /** Set stroke gradient colors */
    fun setStColors(vararg colors: Int): UiLayoutHelper {
        when (colors.size) {
            0 -> {
                stColors = null
                isStrokeGradient = false
            }
            1 -> {
                stColors = null
                stColor = colors[0]
                isStrokeGradient = false
            }
            else -> {
                stColors = colors.copyOf()
                isStrokeGradient = true
            }
        }
        view.invalidate()
        return this
    }

    /** Set stroke gradient colors with orientation */
    fun setStColors(orientation: GradientOrientation, vararg colors: Int): UiLayoutHelper {
        strokeGradientOrientation = orientation
        when (colors.size) {
            0 -> {
                stColors = null
                isStrokeGradient = false
            }
            1 -> {
                stColors = null
                stColor = colors[0]
                isStrokeGradient = false
            }
            else -> {
                stColors = colors.copyOf()
                isStrokeGradient = true
            }
        }
        view.invalidate()
        return this
    }

    /** Set solid background color */
    fun setBgColor(color: Int): UiLayoutHelper {
        bgColor = color
        bgColors = null
        view.invalidate()
        return this
    }

    /** Set solid stroke color */
    fun setStColor(color: Int): UiLayoutHelper {
        stColor = color
        isStrokeGradient = false
        view.invalidate()
        return this
    }

    /** Set corner radius for all corners */
    fun setCorner(radius: Float): UiLayoutHelper {
        cornerRadius = radius
        cornerTopLeft = 0f
        cornerTopRight = 0f
        cornerBottomLeft = 0f
        cornerBottomRight = 0f
        updateClipPath(view.width, view.height)
        view.invalidateOutline()
        view.invalidate()
        return this
    }

    /** Set individual corner radii (tl, tr, br, bl) */
    fun setCorners(tl: Float, tr: Float, br: Float, bl: Float): UiLayoutHelper {
        cornerRadius = 0f
        cornerTopLeft = tl
        cornerTopRight = tr
        cornerBottomRight = br
        cornerBottomLeft = bl
        updateClipPath(view.width, view.height)
        view.invalidateOutline()
        view.invalidate()
        return this
    }

    /** Set stroke width */
    fun setStWidth(width: Float): UiLayoutHelper {
        stWidth = width
        view.invalidate()
        return this
    }

    /** Set dimension ratio (e.g., "16:9", "W,16:9", "H,4:3") */
    fun setDimenRatio(ratio: String?): UiLayoutHelper {
        if (ratio == null) {
            dimenRatio = null
            dimenRatioValue = 0f
            dimenRatioSide = DimenRatioSide.AUTO
            (view.layoutParams as? ConstraintLayout.LayoutParams)?.dimensionRatio = null
        } else {
            parseDimenRatio(ratio)
        }
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set dimension ratio with explicit side */
    fun setDimenRatio(ratio: Float, side: DimenRatioSide = DimenRatioSide.AUTO): UiLayoutHelper {
        dimenRatioValue = ratio
        dimenRatioSide = side
        dimenRatio = when (side) {
            DimenRatioSide.WIDTH -> "W,$ratio"
            DimenRatioSide.HEIGHT -> "H,$ratio"
            DimenRatioSide.AUTO -> "$ratio"
        }
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set width as percentage of parent (0-100) */
    fun setWidthPercent(percent: Float): UiLayoutHelper {
        widthPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set height as percentage of parent (0-100) */
    fun setHeightPercent(percent: Float): UiLayoutHelper {
        heightPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set both width and height as percentage of parent */
    fun setSizePercent(wPercent: Float, hPercent: Float): UiLayoutHelper {
        widthPercent = wPercent
        heightPercent = hPercent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set max width as percentage of parent (0-100) */
    fun setMaxWidthPercent(percent: Float): UiLayoutHelper {
        maxWidthPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set max height as percentage of parent (0-100) */
    fun setMaxHeightPercent(percent: Float): UiLayoutHelper {
        maxHeightPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set min width as percentage of parent (0-100) */
    fun setMinWidthPercent(percent: Float): UiLayoutHelper {
        minWidthPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }

    /** Set min height as percentage of parent (0-100) */
    fun setMinHeightPercent(percent: Float): UiLayoutHelper {
        minHeightPercent = percent
        syncConstraintLayoutParams()
        view.requestLayout()
        return this
    }
}

/**
 * Reads a color list given either as a string ("#F00 #0F0 00F") or as an @array/ reference
 * (for attrs with format="string|reference"). Null when the attr isn't set.
 */
internal fun TypedArray.getColorList(index: Int): IntArray? {
    if (!hasValue(index)) return null
    val resId = getResourceId(index, 0)
    if (resId != 0 && resources.getResourceTypeName(resId) == "array") {
        return resources.getIntArray(resId)
    }
    return getString(index)?.parseHexColors()
}
