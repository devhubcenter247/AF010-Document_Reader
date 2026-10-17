package com.ui.baselib.utils

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

// ============================================================================
// region Dimension Conversions - Int Extensions
// ============================================================================

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Int.dpF: Float
    get() = this * Resources.getSystem().displayMetrics.density

val Int.sp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics
    )

val Int.px: Int
    get() = this

val Int.pxToDp: Float
    get() = this / Resources.getSystem().displayMetrics.density

val Int.pxToSp: Float
    get() = this / TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, 1f, Resources.getSystem().displayMetrics
    )

// endregion

// ============================================================================
// region Dimension Conversions - Float Extensions
// ============================================================================

val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

val Float.dpToInt: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.sp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics
    )

val Float.pxToDp: Float
    get() = this / Resources.getSystem().displayMetrics.density

val Float.pxToSp: Float
    get() = this / TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, 1f, Resources.getSystem().displayMetrics
    )

// endregion

// ============================================================================
// region Dimension Conversions - Double Extensions
// ============================================================================

val Double.dp: Double
    get() = this * Resources.getSystem().displayMetrics.density

val Double.dpToInt: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

// endregion

// ============================================================================
// region Context - Screen Dimensions
// ============================================================================

val Context.screenWidth: Int
    get() = resources.displayMetrics.widthPixels

val Context.screenHeight: Int
    get() = resources.displayMetrics.heightPixels

val Context.screenSize: Size
    get() = Size(screenWidth, screenHeight)

val Context.realScreenWidth: Int
    @Suppress("DEPRECATION")
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.currentWindowMetrics.bounds.width()
    } else {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        point.x
    }

val Context.realScreenHeight: Int
    @Suppress("DEPRECATION")
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.currentWindowMetrics.bounds.height()
    } else {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        point.y
    }

val Context.realScreenSize: Size
    get() = Size(realScreenWidth, realScreenHeight)

// endregion

// ============================================================================
// region Context - Display Metrics
// ============================================================================

val Context.density: Float
    get() = resources.displayMetrics.density

val Context.densityDpi: Int
    get() = resources.displayMetrics.densityDpi

val Context.scaledDensity: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics
    )

// endregion

// ============================================================================
// region Context - System Bars Height
// ============================================================================

val Context.statusBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 24.dp
    }

val Context.navigationBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

val Context.actionBarHeight: Int
    get() {
        val tv = TypedValue()
        return if (theme.resolveAttribute(R.attr.actionBarSize, tv, true)) {
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            56.dp
        }
    }

// endregion

// ============================================================================
// region Context - Percentage Calculations
// ============================================================================

fun Context.widthPercent(percent: Float): Int =
    (screenWidth * percent / 100f).toInt()

fun Context.heightPercent(percent: Float): Int =
    (screenHeight * percent / 100f).toInt()

fun Context.widthFraction(fraction: Float): Int =
    (screenWidth * fraction).toInt()

fun Context.heightFraction(fraction: Float): Int =
    (screenHeight * fraction).toInt()

// endregion

// ============================================================================
// region Activity - Screen Extensions
// ============================================================================

val Activity.usableScreenHeight: Int
    get() = screenHeight - statusBarHeight - navigationBarHeight

val Activity.usableScreenSize: Size
    get() = Size(screenWidth, usableScreenHeight)

// endregion

// ============================================================================
// region Activity - Fullscreen & System UI
// ============================================================================

fun Activity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

fun Activity.setFullscreen(hideStatusBar: Boolean = true, hideNavigationBar: Boolean = true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.let { controller ->
            if (hideStatusBar) {
                controller.hide(WindowInsets.Type.statusBars())
            }
            if (hideNavigationBar) {
                controller.hide(WindowInsets.Type.navigationBars())
            }
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = buildLegacyFlags(hideStatusBar, hideNavigationBar)
    }
}

fun Activity.exitFullscreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.show(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

fun Activity.hideStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.hide(WindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

fun Activity.showStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.show(WindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

fun Activity.hideNavigationBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.hide(WindowInsets.Type.navigationBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

@Suppress("DEPRECATION")
private fun buildLegacyFlags(hideStatusBar: Boolean, hideNavigationBar: Boolean): Int {
    var flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    if (hideStatusBar) {
        flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
    }
    if (hideNavigationBar) {
        flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
    return flags
}

// endregion

// ============================================================================
// region View - Insets Extensions
// ============================================================================

fun View.doOnApplyWindowInsets(block: (View, WindowInsetsCompat) -> Unit) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        block(v, insets)
        insets
    }
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

val View.statusBarInset: Int
    get() = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0

val View.navigationBarInset: Int
    get() = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

val View.imeInset: Int
    get() = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

val View.isKeyboardVisible: Boolean
    get() = ViewCompat.getRootWindowInsets(this)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

// endregion

// ============================================================================
// region View - Size Extensions
// ============================================================================

fun View.setSize(width: Int, height: Int) {
    layoutParams = layoutParams?.apply {
        this.width = width
        this.height = height
    }
}

fun View.setWidth(width: Int) {
    layoutParams = layoutParams?.apply {
        this.width = width
    }
}

fun View.setHeight(height: Int) {
    layoutParams = layoutParams?.apply {
        this.height = height
    }
}

// endregion

// ============================================================================
// region View - Location Extensions
// ============================================================================

/** Get view's X position on screen */
val View.locationXOnScreen: Int
    get() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0]
    }

/** Get view's Y position on screen */
val View.locationYOnScreen: Int
    get() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1]
    }

/** Get view's location on screen as Pair(x, y) */
val View.locationOnScreen: Pair<Int, Int>
    get() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0] to location[1]
    }

/** Get view's location on screen as Point */
val View.locationOnScreenAsPoint: Point
    get() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Point(location[0], location[1])
    }

/** Get view's X position in window */
val View.locationXInWindow: Int
    get() {
        val location = IntArray(2)
        getLocationInWindow(location)
        return location[0]
    }

/** Get view's Y position in window */
val View.locationYInWindow: Int
    get() {
        val location = IntArray(2)
        getLocationInWindow(location)
        return location[1]
    }

/** Get view's location in window as Pair(x, y) */
val View.locationInWindow: Pair<Int, Int>
    get() {
        val location = IntArray(2)
        getLocationInWindow(location)
        return location[0] to location[1]
    }

/** Get view's center X on screen */
val View.centerXOnScreen: Int
    get() = locationXOnScreen + width / 2

/** Get view's center Y on screen */
val View.centerYOnScreen: Int
    get() = locationYOnScreen + height / 2

/** Get view's center on screen as Pair(x, y) */
val View.centerOnScreen: Pair<Int, Int>
    get() = centerXOnScreen to centerYOnScreen

/** Get view's bottom Y on screen */
val View.bottomOnScreen: Int
    get() = locationYOnScreen + height

/** Get view's right X on screen */
val View.rightOnScreen: Int
    get() = locationXOnScreen + width

// endregion

// ============================================================================
// region Utility Functions
// ============================================================================

fun dpToPx(dp: Float): Float = dp * Resources.getSystem().displayMetrics.density

fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

fun pxToDp(px: Float): Float = px / Resources.getSystem().displayMetrics.density

fun pxToDp(px: Int): Float = px / Resources.getSystem().displayMetrics.density

fun spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, Resources.getSystem().displayMetrics)

fun pxToSp(px: Float): Float =
    px / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, Resources.getSystem().displayMetrics)

fun Context.dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

fun Context.pxToDp(px: Float): Float = px / resources.displayMetrics.density

fun Context.pxToDp(px: Int): Float = px / resources.displayMetrics.density

fun Context.spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

fun Context.pxToSp(px: Float): Float =
    px / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

fun View.dpToPx(dp: Float): Float = context.dpToPx(dp)

fun View.dpToPx(dp: Int): Int = context.dpToPx(dp)

fun View.pxToDp(px: Float): Float = context.pxToDp(px)

fun View.pxToDp(px: Int): Float = context.pxToDp(px)

fun View.spToPx(sp: Float): Float = context.spToPx(sp)

fun View.pxToSp(px: Float): Float = context.pxToSp(px)

fun getScreenDimensions(context: Context): Pair<Int, Int> =
    context.screenWidth to context.screenHeight

fun getRealScreenDimensions(context: Context): Pair<Int, Int> =
    context.realScreenWidth to context.realScreenHeight

// endregion
