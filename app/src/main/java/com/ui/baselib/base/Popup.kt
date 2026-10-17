package com.ui.baselib.base

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import androidx.annotation.StyleRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

class PopupBuilder<VB : ViewBinding> private constructor(
    private val context: Context,
    private val bindingInflater: (LayoutInflater) -> VB
) {
    // Configuration
    private var width: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var gravity: Int = Gravity.NO_GRAVITY
    private var dimAmount: Float = 0f
    private var outsideTouchable: Boolean = true
    private var focusable: Boolean = true
    private var clippingEnabled: Boolean = true
    private var elevation: Float = 0f
    private var autoCloseMillis: Long = 0L
    private var animationStyle: Int = -1
    private var dismissOnBackPress: Boolean = true
    private var hideKeyboardOnDismiss: Boolean = true

    // Callbacks
    private var onBind: ((VB, PopupController) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onShow: (() -> Unit)? = null

    // State
    private var popupWindow: PopupWindow? = null
    private var binding: VB? = null
    private var autoCloseHandler: Handler? = null
    private var autoCloseRunnable: Runnable? = null

    companion object {
        fun <VB : ViewBinding> with(
            context: Context,
            bindingInflater: (LayoutInflater) -> VB
        ): PopupBuilder<VB> = PopupBuilder(context, bindingInflater)
    }

    // ========== Size & Position ==========

    fun size(width: Int, height: Int) = apply {
        this.width = width
        this.height = height
    }

    fun matchWidth() = apply {
        this.width = ViewGroup.LayoutParams.MATCH_PARENT
    }

    fun wrapContent() = apply {
        this.width = ViewGroup.LayoutParams.WRAP_CONTENT
        this.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    fun offset(x: Int = 0, y: Int = 0) = apply {
        this.offsetX = x
        this.offsetY = y
    }

    fun gravity(gravity: Int) = apply {
        this.gravity = gravity
    }

    // ========== Appearance ==========

    fun dimBehind(amount: Float = 0.5f) = apply {
        this.dimAmount = amount.coerceIn(0f, 1f)
    }

    fun elevation(dp: Float) = apply {
        this.elevation = dp
    }

    fun animationStyle(@StyleRes style: Int) = apply {
        this.animationStyle = style
    }

    // ========== Behavior ==========

    fun outsideTouchable(enable: Boolean) = apply {
        this.outsideTouchable = enable
    }

    fun focusable(enable: Boolean) = apply {
        this.focusable = enable
    }

    fun clippingEnabled(enable: Boolean) = apply {
        this.clippingEnabled = enable
    }

    fun dismissOnBackPress(enable: Boolean) = apply {
        this.dismissOnBackPress = enable
    }

    fun hideKeyboardOnDismiss(enable: Boolean) = apply {
        this.hideKeyboardOnDismiss = enable
    }

    fun autoCloseAfter(millis: Long) = apply {
        this.autoCloseMillis = millis
    }

    // ========== Callbacks ==========

    fun onBind(callback: (VB, PopupController) -> Unit) = apply {
        this.onBind = callback
    }

    fun onDismiss(callback: () -> Unit) = apply {
        this.onDismiss = callback
    }

    fun onShow(callback: () -> Unit) = apply {
        this.onShow = callback
    }

    // ========== Lifecycle ==========

    fun attachToLifecycle(owner: LifecycleOwner) = apply {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dismiss()
            }
        })
    }

    // ========== Show Methods ==========

    fun showAsDropDown(anchor: View): PopupController {
        createPopup()
        measurePopup(anchor)

        anchor.post {
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val anchorX = location[0]
            val anchorY = location[1]

            val screenHeight = context.resources.displayMetrics.heightPixels
            val popupHeight = binding?.root?.measuredHeight ?: 0

            val spaceBelow = screenHeight - anchorY - anchor.height
            val spaceAbove = anchorY

            val showBelow = spaceBelow >= popupHeight || spaceBelow >= spaceAbove

            if (showBelow) {
                popupWindow?.showAsDropDown(anchor, offsetX, offsetY)
            } else {
                val y = anchorY - popupHeight + offsetY
                popupWindow?.showAtLocation(anchor, Gravity.NO_GRAVITY, anchorX + offsetX, y)
            }

            onPopupShown()
        }

        return createController()
    }

    fun showAtLocation(parent: View, gravity: Int = Gravity.CENTER, x: Int = 0, y: Int = 0): PopupController {
        createPopup()
        measurePopup(parent)

        parent.post {
            popupWindow?.showAtLocation(parent, gravity, x + offsetX, y + offsetY)
            onPopupShown()
        }

        return createController()
    }

    fun showAtAnchor(
        anchor: View,
        position: AnchorPosition = AnchorPosition.BOTTOM
    ): PopupController {
        createPopup()
        measurePopup(anchor)

        anchor.post {
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val anchorX = location[0]
            val anchorY = location[1]

            val popupWidth = binding?.root?.measuredWidth ?: 0
            val popupHeight = binding?.root?.measuredHeight ?: 0

            val (x, y) = when (position) {
                AnchorPosition.TOP -> {
                    anchorX + (anchor.width - popupWidth) / 2 to anchorY - popupHeight
                }
                AnchorPosition.BOTTOM -> {
                    anchorX + (anchor.width - popupWidth) / 2 to anchorY + anchor.height
                }
                AnchorPosition.LEFT -> {
                    anchorX - popupWidth to anchorY + (anchor.height - popupHeight) / 2
                }
                AnchorPosition.RIGHT -> {
                    anchorX + anchor.width to anchorY + (anchor.height - popupHeight) / 2
                }
                AnchorPosition.TOP_LEFT -> anchorX to anchorY - popupHeight
                AnchorPosition.TOP_RIGHT -> anchorX + anchor.width - popupWidth to anchorY - popupHeight
                AnchorPosition.BOTTOM_LEFT -> anchorX to anchorY + anchor.height
                AnchorPosition.BOTTOM_RIGHT -> anchorX + anchor.width - popupWidth to anchorY + anchor.height
            }

            popupWindow?.showAtLocation(anchor, Gravity.NO_GRAVITY, x + offsetX, y + offsetY)
            onPopupShown()
        }

        return createController()
    }

    // ========== Control Methods ==========

    fun dismiss() {
        cancelAutoClose()
        popupWindow?.dismiss()
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    fun update() {
        popupWindow?.let { popup ->
            binding?.let { b ->
                onBind?.invoke(b, createController())
            }
        }
    }

    // ========== Private Methods ==========

    private fun createPopup() {
        binding = bindingInflater(LayoutInflater.from(context))

        popupWindow = PopupWindow(context).apply {
            contentView = binding!!.root
            width = this@PopupBuilder.width
            height = this@PopupBuilder.height
            isFocusable = this@PopupBuilder.focusable
            isOutsideTouchable = this@PopupBuilder.outsideTouchable
            isClippingEnabled = this@PopupBuilder.clippingEnabled
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            if (elevation > 0) {
                this.elevation = this@PopupBuilder.elevation
            }

            if (animationStyle != -1) {
                this.animationStyle = animationStyle
            }

            setOnDismissListener {
                if (hideKeyboardOnDismiss) {
                    context.hideKeyboard()
                }
                cancelAutoClose()
                onDismiss?.invoke()
            }
        }

        // Invoke bind callback
        onBind?.invoke(binding!!, createController())
    }

    private fun measurePopup(anchor: View) {
        val widthSpec = when (width) {
            ViewGroup.LayoutParams.MATCH_PARENT -> View.MeasureSpec.makeMeasureSpec(
                context.resources.displayMetrics.widthPixels,
                View.MeasureSpec.EXACTLY
            )
            ViewGroup.LayoutParams.WRAP_CONTENT -> View.MeasureSpec.makeMeasureSpec(
                anchor.width,
                View.MeasureSpec.AT_MOST
            )
            else -> View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        }

        binding?.root?.measure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
    }

    private fun onPopupShown() {
        applyDimBehind()
        setupAutoClose()
        onShow?.invoke()
    }

    private fun applyDimBehind() {
        if (dimAmount > 0 && popupWindow?.isShowing == true) {
            try {
                val container = popupWindow?.contentView?.rootView
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (container != null && wm != null) {
                    val params = container.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        it.flags = it.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        it.dimAmount = dimAmount
                        wm.updateViewLayout(container, it)
                    }
                }
            } catch (e: Exception) {
                // Ignore if unable to apply dim
            }
        }
    }

    private fun setupAutoClose() {
        if (autoCloseMillis > 0) {
            autoCloseHandler = Handler(Looper.getMainLooper())
            autoCloseRunnable = Runnable { dismiss() }
            autoCloseHandler?.postDelayed(autoCloseRunnable!!, autoCloseMillis)
        }
    }

    private fun cancelAutoClose() {
        autoCloseRunnable?.let { autoCloseHandler?.removeCallbacks(it) }
        autoCloseHandler = null
        autoCloseRunnable = null
    }

    private fun createController(): PopupController = object : PopupController {
        override fun dismiss() = this@PopupBuilder.dismiss()
        override fun isShowing(): Boolean = this@PopupBuilder.isShowing()
        override fun update() = this@PopupBuilder.update()
    }
}

// endregion

// ============================================================================
// region PopupController - Interface for controlling popup
// ============================================================================

interface PopupController {
    fun dismiss()
    fun isShowing(): Boolean
    fun update()
}

// endregion

// ============================================================================
// region AnchorPosition - Enum for popup positioning
// ============================================================================

enum class AnchorPosition {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

// endregion

// ============================================================================
// region Extension Functions
// ============================================================================

inline fun <reified VB : ViewBinding> Context.popup(
    noinline bindingInflater: (LayoutInflater) -> VB
): PopupBuilder<VB> = PopupBuilder.with(this, bindingInflater)

inline fun <reified VB : ViewBinding> View.showPopup(
    noinline bindingInflater: (LayoutInflater) -> VB,
    position: AnchorPosition = AnchorPosition.BOTTOM,
    crossinline onBind: (VB, PopupController) -> Unit = { _, _ -> }
): PopupController {
    return PopupBuilder.with(context, bindingInflater)
        .onBind { binding, controller -> onBind(binding, controller) }
        .showAtAnchor(this, position)
}

// endregion

// ============================================================================
// region Utility Extensions
// ============================================================================

val Context.statusBarHeight: Int
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    get() {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

val Context.navigationBarHeight: Int
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    get() {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

fun Context.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val windowToken = (this as? Activity)?.currentFocus?.windowToken
        ?: (this as? Activity)?.window?.decorView?.windowToken
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}
