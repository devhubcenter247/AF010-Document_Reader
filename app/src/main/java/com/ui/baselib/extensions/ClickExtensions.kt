package com.ui.baselib.extensions

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

// ============================================================================
// region Click Handling Extensions
// ============================================================================

/** Global timestamp for preventing simultaneous clicks across all views */
private var globalLastClickTime = 0L

/**
 * Unified click extension with anti-double-click protection.
 *
 * Usage:
 *   view.click { /* action */ }           // Default 500ms throttle
 *   view.click(1000) { /* action */ }     // Custom 1000ms throttle
 *   view.click(0) { /* action */ }        // No throttle (immediate)
 *
 * @param throttle Minimum time between clicks in milliseconds (default: 500ms)
 * @param action The click action to perform
 */
fun View.click(throttle: Long = 300L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (throttle <= 0 || now - lastClickTime > throttle) {
            lastClickTime = now
            action(view)
        }
    }
}

/**
 * Click extension with GLOBAL anti-double-click protection.
 * Prevents simultaneous clicks on different views (e.g., 2-finger tap).
 *
 * Usage:
 *   view.clickSingle { /* action */ }           // Default 750ms global throttle
 *   view.clickSingle(1000) { /* action */ }     // Custom 1000ms global throttle
 *
 * @param throttle Minimum time between ANY clicks in milliseconds (default: 750ms)
 * @param action The click action to perform
 */
fun View.clickSingle(throttle: Long = 750L, action: (View) -> Unit) {
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (now - globalLastClickTime > throttle) {
            globalLastClickTime = now
            action(view)
        }
    }
}

/**
 * Click with disable effect - disables view temporarily after click.
 * Good for buttons that trigger async operations.
 *
 * @param disableTime Time to keep view disabled in milliseconds (default: 1500ms)
 * @param action The click action to perform
 */
fun View.clickDisable(disableTime: Long = 1500L, action: (View) -> Unit) {
    setOnClickListener { view ->
        view.isEnabled = false
        view.postDelayed({ view.isEnabled = true }, disableTime)
        action(view)
    }
}

/**
 * Long press with throttle protection.
 *
 * @param throttle Minimum time between long presses in milliseconds (default: 500ms)
 * @param action The long press action to perform
 */
fun View.longClick(throttle: Long = 500L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnLongClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (throttle <= 0 || now - lastClickTime > throttle) {
            lastClickTime = now
            action(view)
        }
        true
    }
}

/**
 * Set enabled state for this view and all its children recursively.
 *
 * @param enabled Whether the views should be enabled
 */
fun View.setAllEnabled(enabled: Boolean) {
    isEnabled = enabled
    if (this is ViewGroup) children.forEach { child -> child.setAllEnabled(enabled) }
}

/**
 * Run action with view enabled/disabled state temporarily changed.
 *
 * @param enabled Temporary enabled state
 * @param action Action to run
 */
inline fun View.withEnabled(enabled: Boolean = true, action: () -> Unit) {
    val wasEnabled = isEnabled
    isEnabled = enabled
    try {
        action()
    } finally {
        isEnabled = wasEnabled
    }
}

// endregion
