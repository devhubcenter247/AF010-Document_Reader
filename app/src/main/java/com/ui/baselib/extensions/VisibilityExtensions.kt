package com.ui.baselib.extensions

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible

// ============================================================================
// region Basic Visibility Extensions
// ============================================================================

fun View.postDelayedSafe(delayMillis: Long, action: () -> Unit) {
    var listener: View.OnAttachStateChangeListener? = null
    val runnable = Runnable {
        listener?.let { removeOnAttachStateChangeListener(it) }
        listener = null
        action()
    }
    postDelayed(runnable, delayMillis)
    listener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {
            removeCallbacks(runnable)
            removeOnAttachStateChangeListener(this)
            listener = null
        }
    }
    addOnAttachStateChangeListener(listener)
}
private inline fun View.onMain(crossinline action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action()
    else Handler(Looper.getMainLooper()).post { action() }
}

/**
 * Set view visibility to GONE.
 */
fun View.gone() = onMain { visibility = View.GONE }

/**
 * Set view visibility to VISIBLE.
 */
fun View.visible() = onMain { visibility = View.VISIBLE }

/**
 * Set view visibility to INVISIBLE.
 */
fun View.invisible() = onMain { visibility = View.INVISIBLE }

/**
 * Set view visibility based on condition.
 *
 * @param show If true, set VISIBLE; otherwise GONE
 */
fun View.visibleOrGone(show: Boolean) = if (show) visible() else gone()

/**
 * Set view visibility based on condition.
 *
 * @param show If true, set VISIBLE; otherwise INVISIBLE
 */
fun View.visibleOrInvisible(show: Boolean) = if (show) visible() else invisible()

// endregion

// ============================================================================
// region Animated Visibility Extensions
// ============================================================================

private const val DEFAULT_ANIM_DURATION = 200L

/**
 * Animate view to VISIBLE with fade in effect.
 *
 * @param duration Animation duration in milliseconds
 * @param endAction Callback called at start (false) and end (true) of animation
 */
fun View.animateVisible(duration: Long = DEFAULT_ANIM_DURATION, endAction: (Boolean) -> Unit = {}) {
    if (isVisible && alpha == 1f) return

    animate().cancel()
    clearAnimation()
    if (visibility != View.VISIBLE) {
        alpha = 0f
    }
    endAction(false)
    visibility = View.VISIBLE

    animate()
        .alpha(1f)
        .setDuration(duration)
        .withEndAction {
            alpha = 1f
            endAction(true)
        }
        .start()
}

/**
 * Animate view to GONE with fade out effect.
 *
 * @param duration Animation duration in milliseconds
 * @param endAction Callback called at start (false) and end (true) of animation
 */
fun View.animateGone(duration: Long = DEFAULT_ANIM_DURATION, endAction: (Boolean) -> Unit = {}) {
    if (isGone) return

    // Cancel (not skip) a running fade-in, and fade out from wherever it got to.
    animate().cancel()
    clearAnimation()
    endAction(false)
    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            alpha = 1f
            visibility = View.GONE
            endAction(true)
        }
        .start()
}

/**
 * Animate view to INVISIBLE with fade out effect.
 *
 * @param duration Animation duration in milliseconds
 * @param endAction Callback called at start (false) and end (true) of animation
 */
fun View.animateInvisible(
    duration: Long = DEFAULT_ANIM_DURATION,
    endAction: (Boolean) -> Unit = {}
) {
    if (isInvisible) return

    animate().cancel()
    clearAnimation()
    endAction(false)

    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            alpha = 1f
            visibility = View.INVISIBLE
            endAction(true)
        }
        .start()
}

// endregion

// ============================================================================
// region Visibility Change Listener
// ============================================================================

private fun View.wrapOnMain(onChange: (Boolean) -> Unit): (Boolean) -> Unit = { visible ->
    if (Looper.myLooper() == Looper.getMainLooper()) {
        onChange(visible)
    } else {
        post { onChange(visible) }
    }
}

/**
 * Listen for visibility changes of this view.
 *
 * @param onChange Callback with visibility state (true = visible)
 */
fun View.doOnVisibilityChange(onChange: (Boolean) -> Unit) {
    val safeOnChange = wrapOnMain(onChange)

    var lastVisible = isVisible
    safeOnChange(lastVisible)

    val listener = ViewTreeObserver.OnGlobalLayoutListener {
        val nowVisible = isVisible
        if (nowVisible != lastVisible) {
            lastVisible = nowVisible
            safeOnChange(nowVisible)
        }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
            removeOnAttachStateChangeListener(this)
        }
    })
}

// endregion
