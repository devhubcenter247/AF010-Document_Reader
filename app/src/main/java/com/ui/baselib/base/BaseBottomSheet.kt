package com.ui.baselib.base

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseBottomSheet<V : ViewBinding>(
    private val activity: FragmentActivity,
    private val bindingFactory: (LayoutInflater) -> V,
) : BottomSheetDialog(activity) {

    companion object {
        const val TAG = "BaseBottomSheet"
    }

    private val showingGuard = AtomicBoolean(false)
    private var _binding: V? = null

    protected val binding: V
        get() = _binding ?: throw IllegalStateException("Binding accessed before show()/after dismiss()")

    var onDismissListener: () -> Unit = {}

    // Dialog keeps a single dismiss listener; the internal reset must not be replaced by callers.
    private var externalDismissListener: DialogInterface.OnDismissListener? = null

    abstract fun V.onBind()

    init {
        setOnShowListener { trySetupBottomSheet() }

        super.setOnDismissListener { dialog ->
            showingGuard.set(false)
            _binding = null
            onDismissListener()
            externalDismissListener?.onDismiss(dialog)
        }
    }

    override fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        externalDismissListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hideSystemBars()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
    private fun hideSystemBars() {
        val win = window ?: return
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(win, win.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun ensureContentView() {
        if (_binding != null) return
        val vb = bindingFactory(LayoutInflater.from(context))
        _binding = vb
        vb.onBind()
        vb.root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        vb.root.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                val sheet = findViewById<View>(R.id.design_bottom_sheet)
                    ?: return@addOnLayoutChangeListener
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        setContentView(vb.root)
    }

    private fun trySetupBottomSheet() {
        window?.let { win ->
            win.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }
        hideSystemBars()

        val sheet = findViewById<View>(R.id.design_bottom_sheet) ?: return

        sheet.setBackgroundColor(Color.TRANSPARENT)
        sheet.fitsSystemWindows = false
        sheet.setPadding(0, 0, 0, 0)
        sheet.layoutParams = sheet.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }

        var parent = sheet.parent
        while (parent is View) {
            val view = parent
            view.fitsSystemWindows = false
            view.setPadding(0, 0, 0, 0)
            // Don't pad for system bars, but pass insets down so the sheet still sees the IME.
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
            parent = view.parent
        }
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
            // Lift the content above the keyboard so a focused field isn't hidden behind it. Only
            // for a keyboard the sheet itself asked for: one left open by the screen below is on
            // its way out, and its inset would leave the sheet floating above an empty gap.
            val typing = currentFocus?.onCheckIsTextEditor() == true
            val ime = if (typing) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            v.updatePadding(bottom = ime)
            WindowInsetsCompat.CONSUMED
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
            (sheet.parent as? ViewGroup)?.layoutTransition = null
        }
    }

    override fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { show() }
            return
        }

        if (!showingGuard.compareAndSet(false, true)) return
        if (isShowing) return

        try {
            ensureContentView()
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            super.show()
            hideSystemBars()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } catch (_: Throwable) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            showingGuard.set(false)
        }
    }

    fun dismissSafe() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { dismissSafe() }
            return
        }

        if (!isShowing) {
            showingGuard.set(false)
            return
        }

        try {
            dismiss()
        } finally {
            showingGuard.set(false)
        }
    }
}