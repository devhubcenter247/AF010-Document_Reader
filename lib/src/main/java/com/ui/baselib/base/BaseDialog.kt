package com.ui.baselib.base

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import androidx.viewbinding.ViewBinding
import com.ui.baselib.R
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

abstract class BaseDialog<V : ViewBinding>(
      private val context: Context,
      val bindingFactory: (LayoutInflater) -> V,
      var cancelAble: Boolean = true,
      val isFull: Boolean = false
) :
    Dialog(context, if (!isFull) R.style.BaseDialog else R.style.BaseDialogFull), LifecycleOwner {
    private val TAG: String = BaseDialog::class.java.name
    val binding: V by lazy { bindingFactory(layoutInflater) }
    // Dismiss only stops the dialog (CREATED), so collectors started in initView() survive a
    // re-show; it is destroyed with the host activity, which also dismisses it (no WindowLeaked).
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val hostObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) destroy()
    }

    val dialogScope: CoroutineScope get() = lifecycleScope

    fun stringRes(@StringRes res: Int): String = context.getString(res)

    protected abstract fun V.initView()

    init {
        require(context is Activity) { "BaseDialog requires an Activity context" }
        initialize()
        (context as? LifecycleOwner)?.lifecycle?.addObserver(hostObserver)
    }

    private fun initialize() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setGravity(Gravity.CENTER)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    open fun hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window?.decorView?.rootView?.windowToken, 0)
    }

    open fun showKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.root, 0)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        registry.currentState = Lifecycle.State.CREATED
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.initView()
        setCancelable(cancelAble)
        this.setCanceledOnTouchOutside(cancelAble)
    }

    override fun show() {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (registry.currentState == Lifecycle.State.DESTROYED) return

        try {
            if (isShowing) {
                dismiss()
            }
            super.show()
            registry.currentState = Lifecycle.State.RESUMED
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun dismiss() {
        try {
            super.dismiss()
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    private fun destroy() {
        (context as? LifecycleOwner)?.lifecycle?.removeObserver(hostObserver)
        if (isShowing) dismiss()
        // LifecycleRegistry can't jump from INITIALIZED (never shown) straight to DESTROYED.
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.CREATED
        }
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
