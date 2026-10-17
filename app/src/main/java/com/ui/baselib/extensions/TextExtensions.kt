package com.ui.baselib.extensions

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.core.view.doOnAttach
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.MutableLiveData

// ============================================================================
// region Text Change Listeners
// ============================================================================

/**
 * Listen for text changes with debouncing.
 * Callback is invoked after text stops changing for 150ms.
 *
 * @param callback Callback with the current text
 */
fun TextView.afterTextChanged(callback: (String) -> Unit) = apply {
    addDebouncedAfterTextChanged(150, callback)
}

/**
 * Debounced afterTextChanged. The pending call is dropped when the view detaches, so it can't
 * fire after the screen holding it is gone.
 */
private fun TextView.addDebouncedAfterTextChanged(debounceMs: Long, callback: (String) -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var runnable: Runnable? = null

    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            runnable?.let { handler.removeCallbacks(it) }
            val nextRunnable = Runnable { callback(s.toString()) }
            runnable = nextRunnable
            handler.postDelayed(nextRunnable, debounceMs)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            runnable?.let { handler.removeCallbacks(it) }
            runnable = null
        }
    })
}

/**
 * Listen for text changes before they happen.
 *
 * @param callback Callback with the text before change
 */
fun TextView.beforeTextChanged(callback: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            callback(s.toString())
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

/**
 * Listen for text changes in real-time.
 *
 * @param callback Callback with the current text
 */
fun TextView.textChanged(callback: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            callback(s.toString())
        }
    })
}

/**
 * Unified text change listener with configurable behavior.
 *
 * @param mode When to trigger callback: BEFORE, DURING, or AFTER change
 * @param debounceMs Debounce delay for AFTER mode (default: 150ms)
 * @param callback Callback with the text
 */
fun TextView.onTextChange(
    mode: TextChangeMode = TextChangeMode.AFTER,
    debounceMs: Long = 150,
    callback: (String) -> Unit
) {
    when (mode) {
        TextChangeMode.BEFORE -> beforeTextChanged(callback)
        TextChangeMode.DURING -> textChanged(callback)
        TextChangeMode.AFTER -> addDebouncedAfterTextChanged(debounceMs, callback)
    }
}

enum class TextChangeMode {
    BEFORE, DURING, AFTER
}

// endregion

// ============================================================================
// region Focus Change Listener
// ============================================================================

/**
 * Listen for focus changes with current text.
 *
 * @param callback Callback with (hasFocus, currentText)
 */
fun TextView.onFocusChange(callback: (Boolean, String) -> Unit) = apply {
    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        callback(hasFocus, text.toString())
    }
}

// endregion

// ============================================================================
// region Key Listener
// ============================================================================

/**
 * Listen for key down events.
 *
 * @param callback Callback with key code
 */
fun TextView.keyDown(callback: (Int) -> Unit) {
    setOnKeyListener { _, keyCode, event ->
        if (event.action == KeyEvent.ACTION_DOWN) {
            callback(keyCode)
            true
        } else {
            false
        }
    }
}

// endregion

// ============================================================================
// region LiveData Binding
// ============================================================================

/**
 * Bind TextView text to a MutableLiveData.
 * Set this property to automatically update text when LiveData changes.
 * Observes with the view tree's owner (a fragment's view lifecycle inside a fragment), so it
 * works with themed contexts and stops when the view is destroyed.
 */
var TextView.listenText: MutableLiveData<String>?
    get() = throw UnsupportedOperationException("Getter is not supported for listenText")
    set(value) {
        value ?: return
        doOnAttach { view ->
            val owner = view.findViewTreeLifecycleOwner()
                ?: view.context.findLifecycleOwner()
                ?: return@doOnAttach
            value.observe(owner) { text ->
                this.text = text.toString()
            }
        }
    }

// endregion

/** Unwraps ContextThemeWrapper & co. to the Activity (e.g. views in a plain Dialog). */
private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
