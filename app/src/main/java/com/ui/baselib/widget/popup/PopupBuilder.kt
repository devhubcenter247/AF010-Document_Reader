package com.ui.baselib.widget.popup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import androidx.core.graphics.drawable.toDrawable
import com.ui.baselib.utils.bottomOnScreen
import com.ui.baselib.utils.locationYOnScreen
import com.ui.baselib.utils.rightOnScreen
import com.ui.baselib.utils.screenHeight
import com.ui.baselib.utils.screenWidth

/**
 * A declarative popup builder with Compose-like DSL syntax.
 *
 * Usage:
 * ```kotlin
 * PopupBuilder(context)
 *     .anchor(anchorView)
 *     .width(300.dp)
 *     .height(WRAP_CONTENT)
 *     .gravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
 *     .offset(x = 0, y = 8.dp)
 *     .cornerRadius(12f)
 *     .backgroundColor(Color.WHITE)
 *     .elevation(8f)
 *     .dimAmount(0.3f)
 *     .animationStyle(AnimationStyle.SCALE)
 *     .outsideTouchable(true)
 *     .focusable(true)
 *     .content { inflater, parent ->
 *         // Return your content view
 *         inflater.inflate(R.layout.popup_content, parent, false)
 *     }
 *     .onDismiss { /* handle dismiss */ }
 *     .onItemClick { position, view -> /* handle click */ }
 *     .show()
 * ```
 *
 * Or with ViewBinding:
 * ```kotlin
 * PopupBuilder(context)
 *     .anchor(anchorView)
 *     .contentBinding { PopupContentBinding.inflate(it) }
 *     .setup { binding ->
 *         binding.textView.text = "Hello"
 *         binding.button.setOnClickListener { dismiss() }
 *     }
 *     .show()
 * ```
 */
class PopupBuilder(private val context: Context) {

    private var anchor: View? = null
    private var width: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var gravity: Int = Gravity.NO_GRAVITY
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var backgroundColor: Int = Color.WHITE
    private var cornerRadius: Float = 0f
    private var elevation: Float = 0f
    private var dimAmount: Float = 0f
    private var animationStyle: AnimationStyle = AnimationStyle.FADE
    private var animationDuration: Long = 200L
    private var animationInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    private var outsideTouchable: Boolean = true
    private var focusable: Boolean = true
    private var clippingEnabled: Boolean = false
    private var enterTransition: ((View) -> Animator)? = null
    private var exitTransition: ((View) -> Animator)? = null

    private var contentProvider: ((LayoutInflater, ViewGroup?) -> View)? = null
    private var bindingProvider: ((LayoutInflater) -> ViewBinding)? = null
    private var setupBlock: (PopupScope.() -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null
    private var onShowListener: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null
    private var contentView: View? = null
    private var currentBinding: ViewBinding? = null

    // ---- Builder methods ----

    /** Set the anchor view for the popup */
    fun anchor(view: View) = apply { anchor = view }

    /** Set popup width (use WRAP_CONTENT, MATCH_PARENT, or specific dp value) */
    fun width(width: Int) = apply { this.width = width }

    /** Set popup height (use WRAP_CONTENT, MATCH_PARENT, or specific dp value) */
    fun height(height: Int) = apply { this.height = height }

    /** Set popup size (width and height) */
    fun size(width: Int, height: Int) = apply {
        this.width = width
        this.height = height
    }

    /** Set the gravity for popup positioning relative to anchor */
    fun gravity(gravity: Int) = apply { this.gravity = gravity }

    /** Set horizontal and vertical offset from anchor */
    fun offset(x: Int = 0, y: Int = 0) = apply {
        offsetX = x
        offsetY = y
    }

    /** Set background color */
    fun backgroundColor(color: Int) = apply { backgroundColor = color }

    /** Set corner radius in dp */
    fun cornerRadius(radius: Float) = apply { cornerRadius = radius }

    /** Set elevation/shadow depth */
    fun elevation(elevation: Float) = apply { this.elevation = elevation }

    /** Set dim amount for background (0f = no dim, 1f = fully dimmed) */
    fun dimAmount(amount: Float) = apply { dimAmount = amount.coerceIn(0f, 1f) }

    /** Set animation style */
    fun animationStyle(style: AnimationStyle) = apply { animationStyle = style }

    /** Set animation duration in milliseconds */
    fun animationDuration(duration: Long) = apply { animationDuration = duration }

    /** Set animation interpolator */
    fun animationInterpolator(interpolator: Interpolator) =
        apply { animationInterpolator = interpolator }

    /** Set custom enter transition animator */
    fun enterTransition(transition: (View) -> Animator) = apply { enterTransition = transition }

    /** Set custom exit transition animator */
    fun exitTransition(transition: (View) -> Animator) = apply { exitTransition = transition }

    /** Set whether clicking outside dismisses the popup */
    fun outsideTouchable(touchable: Boolean) = apply { outsideTouchable = touchable }

    /** Set whether the popup can receive focus */
    fun focusable(focusable: Boolean) = apply { this.focusable = focusable }

    /** Set whether the popup should be clipped to the screen */
    fun clippingEnabled(enabled: Boolean) = apply { clippingEnabled = enabled }

    /** Provide content view through lambda */
    fun content(provider: (inflater: LayoutInflater, parent: ViewGroup?) -> View) = apply {
        contentProvider = provider
    }

    /** Provide content view from layout resource */
    fun content(@LayoutRes layoutRes: Int) = apply {
        contentProvider = { inflater, parent ->
            inflater.inflate(layoutRes, parent, false)
        }
    }

    /** Provide content view using ViewBinding */
    fun <VB : ViewBinding> contentBinding(provider: (LayoutInflater) -> VB) = apply {
        bindingProvider = provider
    }

    /** Setup the popup content (called after content is created) */
    fun setup(block: PopupScope.() -> Unit) = apply { setupBlock = block }

    /** Set dismiss listener */
    fun onDismiss(listener: () -> Unit) = apply { onDismissListener = listener }

    /** Set show listener */
    fun onShow(listener: () -> Unit) = apply { onShowListener = listener }

    // ---- Actions ----

    /** Build and show the popup */
    @SuppressLint("ClickableViewAccessibility")
    fun show(): PopupBuilder {
        val anchorView = anchor ?: throw IllegalStateException("Anchor view must be set")
        val wrapper = preparePopup()

        // Show popup
        popupWindow?.showAsDropDown(anchorView, offsetX, offsetY, gravity)

        // Apply enter animation
        applyEnterAnimation(wrapper)

        onShowListener?.invoke()

        return this
    }

    /** Show popup at specific location on screen */
    @SuppressLint("ClickableViewAccessibility")
    fun showAtLocation(
          parent: View, gravity: Int = Gravity.CENTER, x: Int = 0, y: Int = 0
    ): PopupBuilder {
        val wrapper = preparePopup()

        // Show popup at location
        popupWindow?.showAtLocation(parent, gravity, x, y)

        // Apply enter animation
        applyEnterAnimation(wrapper)

        onShowListener?.invoke()

        return this
    }
    /**
     * Show popup with auto-calculated position based on parent view.
     * Automatically determines whether to show above or below the parent,
     * and aligns horizontally based on the gravity.
     *
     * @param parent The anchor view to position relative to
     * @param popupWidth The width of the popup (used for position calculation)
     * @param popupHeight The height of the popup (used for position calculation)
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showAutoLocation(
        parent: View,
        popupWidth: Int = width,
        popupHeight: Int = height
    ): PopupBuilder {
        val wrapper = preparePopup()

        // Calculate Y position - show below if enough space, otherwise show above
        val spaceBelow = parent.context.screenHeight - parent.bottomOnScreen
        val showAbove = spaceBelow < popupHeight
        val yPosition = if (showAbove) {
            parent.locationYOnScreen - popupHeight
        } else {
            parent.bottomOnScreen
        }

        // Calculate X position (for Gravity.END, x is distance from right edge of screen)
        val xPosition = parent.context.screenWidth - parent.rightOnScreen

        // Show popup at calculated location
        popupWindow?.showAtLocation(
            parent,
            Gravity.TOP or Gravity.END,
            xPosition,
            yPosition
        )

        // Apply enter animation
        applyEnterAnimation(wrapper)

        onShowListener?.invoke()

        return this
    }

    private fun preparePopup(): PopupContentWrapper {
        val content = createContentView()
        contentView = content

        val wrapper = createWrapper(content)
        popupWindow = createPopupWindow(wrapper)

        setupBlock?.invoke(PopupScope(this, content, currentBinding))
        return wrapper
    }

    private fun createContentView(): View {
        val inflater = LayoutInflater.from(context)
        currentBinding = null
        val bindingProvider = bindingProvider
        val contentProvider = contentProvider
        return when {
            bindingProvider != null -> bindingProvider.invoke(inflater).also {
                currentBinding = it
            }.root
            contentProvider != null -> contentProvider.invoke(inflater, null)
            else -> throw IllegalStateException("Content must be provided via content() or contentBinding()")
        }
    }

    private fun createWrapper(content: View): PopupContentWrapper =
        PopupContentWrapper(context).apply {
            setBackgroundColor(backgroundColor)
            setCornerRadius(cornerRadius)
            if (elevation > 0f) {
                setElevation(elevation)
            }
            addView(content)
        }

    private fun createPopupWindow(wrapper: View): PopupWindow =
        PopupWindow(wrapper, width, height).apply {
            isOutsideTouchable = outsideTouchable
            isFocusable = this@PopupBuilder.focusable
            isClippingEnabled = clippingEnabled
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = this@PopupBuilder.elevation
            setOnDismissListener {
                onDismissListener?.invoke()
            }
        }

    /** Dismiss the popup */
    fun dismiss() {
        val popup = popupWindow ?: return
        val wrapper = popup.contentView

        if (wrapper != null && animationStyle != AnimationStyle.NONE) {
            applyExitAnimation(wrapper) {
                popup.dismiss()
            }
        } else {
            popup.dismiss()
        }
    }

    /** Check if popup is currently showing */
    fun isShowing(): Boolean = popupWindow?.isShowing == true

    /** Update popup content */
    fun update(block: PopupScope.() -> Unit) {
        contentView?.let { view ->
            block.invoke(PopupScope(this, view, currentBinding))
        }
    }

    // ---- Animation helpers ----

    private fun applyEnterAnimation(view: View) {
        val animator = enterTransition?.invoke(view) ?: when (animationStyle) {
            AnimationStyle.NONE -> return
            AnimationStyle.FADE -> createFadeInAnimator(view)
            AnimationStyle.SCALE -> createScaleInAnimator(view)
            AnimationStyle.SLIDE_UP -> createSlideUpInAnimator(view)
            AnimationStyle.SLIDE_DOWN -> createSlideDownInAnimator(view)
            AnimationStyle.SCALE_FADE -> createScaleFadeInAnimator(view)
        }

        animator.duration = animationDuration
        animator.interpolator = animationInterpolator
        animator.start()
    }

    private fun applyExitAnimation(view: View, onEnd: () -> Unit) {
        val animator = exitTransition?.invoke(view) ?: when (animationStyle) {
            AnimationStyle.NONE -> {
                onEnd()
                return
            }
            AnimationStyle.FADE -> createFadeOutAnimator(view)
            AnimationStyle.SCALE -> createScaleOutAnimator(view)
            AnimationStyle.SLIDE_UP -> createSlideUpOutAnimator(view)
            AnimationStyle.SLIDE_DOWN -> createSlideDownOutAnimator(view)
            AnimationStyle.SCALE_FADE -> createScaleFadeOutAnimator(view)
        }

        animator.duration = animationDuration
        animator.interpolator = animationInterpolator
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd()
            }
        })
        animator.start()
    }

    private fun createFadeInAnimator(view: View): Animator {
        view.alpha = 0f
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
    }

    private fun createFadeOutAnimator(view: View): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
    }

    private fun createScaleInAnimator(view: View): Animator {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.alpha = 0f
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.8f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.8f, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
            )
        }
    }

    private fun createScaleOutAnimator(view: View): Animator {
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.8f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.8f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
            )
        }
    }

    private fun createSlideUpInAnimator(view: View): Animator {
        view.translationY = view.height.toFloat()
        view.alpha = 0f
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.height.toFloat(), 0f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
            )
        }
    }

    private fun createSlideUpOutAnimator(view: View): Animator {
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -view.height.toFloat()),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
            )
        }
    }

    private fun createSlideDownInAnimator(view: View): Animator {
        view.translationY = -view.height.toFloat()
        view.alpha = 0f
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -view.height.toFloat(), 0f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
            )
        }
    }

    private fun createSlideDownOutAnimator(view: View): Animator {
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, view.height.toFloat()),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
            )
        }
    }

    private fun createScaleFadeInAnimator(view: View): Animator {
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.alpha = 0f
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
            )
        }
    }

    private fun createScaleFadeOutAnimator(view: View): Animator {
        return android.animation.AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.5f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.5f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
            )
        }
    }

    /** Animation style presets */
    enum class AnimationStyle {
        NONE, FADE, SCALE, SLIDE_UP, SLIDE_DOWN, SCALE_FADE
    }

    /** Scope class for setup block, provides access to popup controls */
    class PopupScope(
          private val builder: PopupBuilder, val contentView: View, val pBinding: ViewBinding?
    ) {
        /** Dismiss the popup */
        fun dismiss() = builder.dismiss()

        /** Check if popup is showing */
        fun isShowing() = builder.isShowing()

        /** Get typed binding */
        @Suppress("UNCHECKED_CAST")
        fun <VB : ViewBinding> binding(): VB = pBinding as VB

        /** Find view by ID */
        fun <T : View> findViewById(id: Int): T = contentView.findViewById(id)
    }

    companion object {
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

        /** DSL entry point */
        inline fun create(context: Context, block: PopupBuilder.() -> Unit): PopupBuilder {
            return PopupBuilder(context).apply(block)
        }
    }
}

/** Extension function for DSL-style popup creation */
inline fun Context.popup(block: PopupBuilder.() -> Unit): PopupBuilder {
    return PopupBuilder.create(this, block)
}

/** Extension function for showing popup anchored to this view */
inline fun View.showPopup(block: PopupBuilder.() -> Unit): PopupBuilder {
    return PopupBuilder(context).apply {
        anchor(this@showPopup)
        block()
    }.show()
}
