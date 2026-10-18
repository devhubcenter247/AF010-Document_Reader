package com.ui.baselib.extensions

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

// ============================================================================
// region Fade Animations
// ============================================================================

/**
 * Fade in animation - makes view visible with fade effect.
 *
 * @param duration Animation duration in milliseconds
 */
fun View.fadeIn(duration: Long = 300) {
    // Cancelling also drops a pending fadeOut() end action, so it can't hide the view afterwards.
    animate().cancel()
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setDuration(duration).start()
}

/**
 * Fade out animation - hides view with fade effect.
 *
 * @param duration Animation duration in milliseconds
 */
fun View.fadeOut(duration: Long = 300) {
    animate().cancel()
    animate().alpha(0f).setDuration(duration).withEndAction { visibility = View.GONE }.start()
}

// endregion

// ============================================================================
// region Rotation Animations
// ============================================================================

/**
 * Rotate view animation.
 *
 * @param from Starting rotation angle
 * @param to Ending rotation angle
 * @param duration Animation duration in milliseconds
 * @param loop Whether to loop infinitely
 * @return ObjectAnimator for control (pause, cancel, etc.)
 */
fun View.rotate(
    from: Float = 0f,
    to: Float = 360f,
    duration: Long = 3000L,
    loop: Boolean = false
): ObjectAnimator = ObjectAnimator.ofFloat(this, "rotation", from, to).apply {
    this.duration = duration
    if (loop) {
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.RESTART
    }
    start()
}

// endregion

// ============================================================================
// region Scale Animations
// ============================================================================

/**
 * Scale animation.
 *
 * @param from Starting scale
 * @param to Ending scale
 * @param duration Animation duration in milliseconds
 * @param onEnd Callback when animation ends
 */
fun View.scale(
    from: Float = 1f,
    to: Float = 1.2f,
    duration: Long = 300L,
    onEnd: (() -> Unit)? = null
) {
    scaleX = from
    scaleY = from
    animate()
        .scaleX(to).scaleY(to)
        .setDuration(duration)
        .withEndAction { onEnd?.invoke() }
        .start()
}

/**
 * Bounce animation (click feedback).
 *
 * @param scale Scale factor during bounce (less than 1 for shrink effect)
 * @param duration Animation duration in milliseconds for each phase
 */
fun View.bounce(scale: Float = 0.9f, duration: Long = 100L) {
    animate().scaleX(scale).scaleY(scale).setDuration(duration).withEndAction {
        animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
    }.start()
}

// endregion

// ============================================================================
// region Size Animations
// ============================================================================

/**
 * Animate height change.
 *
 * @param to Target height in pixels
 * @param duration Animation duration in milliseconds
 * @param interpolator Time interpolator for the animation
 * @param onEnd Callback when animation ends
 */
fun View.animateHeight(
    to: Int,
    duration: Long = 250L,
    interpolator: TimeInterpolator = FastOutSlowInInterpolator(),
    onEnd: (() -> Unit)? = null
) {
    ValueAnimator.ofInt(height.coerceAtLeast(0), to).apply {
        this.duration = duration
        this.interpolator = interpolator
        addUpdateListener {
            layoutParams = layoutParams.apply { height = it.animatedValue as Int }
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
    }.start()
}

/**
 * Animate width change.
 *
 * @param to Target width in pixels
 * @param duration Animation duration in milliseconds
 * @param interpolator Time interpolator for the animation
 * @param onEnd Callback when animation ends
 */
fun View.animateWidth(
    to: Int,
    duration: Long = 250L,
    interpolator: TimeInterpolator = FastOutSlowInInterpolator(),
    onEnd: (() -> Unit)? = null
) {
    ValueAnimator.ofInt(width.coerceAtLeast(0), to).apply {
        this.duration = duration
        this.interpolator = interpolator
        addUpdateListener {
            layoutParams = layoutParams.apply { width = it.animatedValue as Int }
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
    }.start()
}

// endregion

// ============================================================================
// region Translation Animations
// ============================================================================

/**
 * Shake animation (error feedback).
 *
 * @param offset Shake offset in pixels
 * @param duration Duration of each shake in milliseconds
 * @param repeat Multiplies [duration]; the shake pattern itself is always the same
 */
fun View.shake(offset: Float = 10f, duration: Long = 50L, repeat: Int = 3) {
    val animator = ObjectAnimator.ofFloat(
        this,
        "translationX",
        0f, offset, -offset, offset, -offset, 0f
    )
    animator.duration = duration * repeat
    animator.start()
}

// endregion
