package com.ui.baselib.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.ui.baselib.api.putArgsSafely
import com.ui.baselib.api.putExtrasSafely
import com.ui.baselib.api.safeBundleOf
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

abstract class BaseFragment<VB : ViewBinding>(
    open val bindingFactory: (LayoutInflater) -> VB,
) : Fragment(), BaseHost {
    open val isFullSc: Boolean = false

    var appActivity: AppCompatActivity? = null
        private set
    private var _binding: VB? = null
    val binding: VB
        get() = _binding
            ?: throw IllegalStateException("Binding accessed outside of view lifecycle")

    val bindingOrNull: VB? get() = _binding
    // UI work follows the view: a fragment on the back stack keeps living without a view, so
    // tying jobs/collectors to the fragment would stack a new copy each time the view is rebuilt.
    private val uiOwner: LifecycleOwner get() = if (view != null) viewLifecycleOwner else this
    override val hostScope: CoroutineScope get() = uiOwner.lifecycleScope
    override val hostLifecycleOwner: LifecycleOwner get() = uiOwner
    override val defaultCollectState: Lifecycle.State = Lifecycle.State.RESUMED
    override val toastContext: Context? get() = context
    override val keyboardActivity: Activity? get() = appActivity
    override val mainHandlerHolder = MainHandlerHolder()

    val statusBarHeight: Int get() = requireActivity().statusBarInsetPx
    val navigationBarHeight: Int get() = requireActivity().navigationBarInsetPx

    fun appContext(): Context =
        context ?: appActivity ?: throw IllegalStateException("Fragment not attached")

    fun appContextOrNull(): Context? = context ?: appActivity

    val isFragmentVisible: Boolean
        get() = isAdded && !isHidden && view != null && isVisible

    val isSafeToUpdateUI: Boolean
        get() = isAdded && !isDetached && view != null && activity?.isFinishing != true

    val navHost: FragmentNavigator
        get() = appActivity as? FragmentNavigator
            ?: throw IllegalStateException(
                "Activity chứa $this không implement FragmentNavigator. " +
                        "Kiểm tra BaseActivity đã truyền fragmentContainerId + navGraph chưa."
            )

    fun navigateTo(
        routeName: String,
        vararg params: Pair<String, Any?>,
        addToBackStack: Boolean = true
    ) {
        val args = if (params.isEmpty()) null else safeBundleOf(*params)
        navHost.navigateTo(routeName, args, addToBackStack)
    }

    fun navigateTo(
        routeId: Int,
        vararg params: Pair<String, Any?>,
        addToBackStack: Boolean = true
    ) {
        val args = if (params.isEmpty()) null else safeBundleOf(*params)
        navHost.navigateTo(routeId, args, addToBackStack)
    }

    fun navigatePopUpTo(
        routeId: Int,
        popUpToId: Int,
        inclusive: Boolean = false,
        vararg params: Pair<String, Any?>,
    ) {
        val args = if (params.isEmpty()) null else safeBundleOf(*params)
        navHost.navigatePopUpTo(routeId, popUpToId, popUpToInclusive = inclusive, args = args)
    }

    fun popToRoot() = navHost.popToRoot()

    fun popBackTo(routeName: String, inclusive: Boolean = false): Boolean =
        navHost.popBackTo(routeName, inclusive)

    fun popBackTo(routeId: Int, inclusive: Boolean = false): Boolean =
        navHost.popBackTo(routeId, inclusive)

    abstract fun VB.initView()
    abstract fun VB.onClick()

    open fun initialize(context: Context) {}

    /**
     * System back while this fragment is shown. Return true when handled; false passes it on
     * to the activity (which pops the fragment back stack, then calls BaseActivity.backPressed()).
     * A back callback is only registered for fragments that override this.
     */
    open fun onBackPressed(): Boolean = false

    private fun registerBackCallback() {
        if (!overridesOnBackPressed(javaClass)) return
        val dispatcher = requireActivity().onBackPressedDispatcher
        // Tied to the view lifecycle, so there is exactly one callback per view.
        dispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isHidden && onBackPressed()) return
                isEnabled = false
                dispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        initialize(context)
        (activity as? AppCompatActivity)?.let { this.appActivity = it }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val root = bindingFactory(inflater).also { _binding = it }.root
        if (root.isBackgroundTransparent()) root.setBackgroundColor(themeBackgroundColor())
        root.isClickable = true
        return root
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Skip when the host BaseActivity already pads its root: the gap would be doubled.
        if (!isFullSc && (activity as? BaseActivity<*>)?.padsStatusBar != true) {
            applyStatusBarPadding()
        }
        registerBackCallback()
        binding.initView()
        binding.onClick()
        // BaseActivity already does this in dispatchTouchEvent; only needed for other hosts.
        if (activity !is BaseActivity<*>) {
            view.setOnTouchListener { _, ev ->
                hideKeyboardIfTouchedOutsideFocusedEditText(activity?.currentFocus, ev) { hideKeyboard() }
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandlerHolder.clear()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        appActivity = null
    }

    // BundleCompat avoids the typed Bundle getters on Android 13, which can crash (b/232589966).
    inline fun <reified T : android.os.Parcelable> parcelableArg(key: String): T? =
        arguments?.let { BundleCompat.getParcelable(it, key, T::class.java) }

    inline fun <reified T : java.io.Serializable> serializableArg(key: String): T? =
        arguments?.let { BundleCompat.getSerializable(it, key, T::class.java) }

    inline fun <reified T : Any> launchActivity(vararg params: Pair<String, Any?>) {
        val intent = Intent(requireContext(), T::class.java)
            .putExtrasSafely(*params, logTag = "BaseFragment")
        startActivity(intent)
    }

    inline fun <reified T : Any> launchAndFinish(vararg params: Pair<String, Any?>) {
        launchActivity<T>(*params)
        activity?.finish()
    }

    @PublishedApi
    internal val resultHelper = ActivityResultHelper(this) { listenerResult(it) }

    open fun listenerResult(result: ActivityResult) {}

    inline fun <reified T : Any> launcherForResult(
        vararg params: Pair<String, Any?>,
        noinline dataResult: (ActivityResult) -> Unit = { _ -> }
    ) {
        val intent = Intent(requireActivity(), T::class.java)
            .putExtrasSafely(*params, logTag = "BaseFragment")
        resultHelper.launch(intent, dataResult)
    }

    fun showKeyboard() = showKeyboard(binding.root)

    fun applyStatusBarPadding() {
        binding.root.padForStatusBar(statusBarHeight)
    }

    private fun View.isBackgroundTransparent(): Boolean {
        val background = this.background
        return background is ColorDrawable && background.color == Color.TRANSPARENT
    }

    /** The theme's window background color, so dark themes don't get a white screen. */
    private fun themeBackgroundColor(): Int {
        val value = TypedValue()
        val resolved = requireContext().theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        val isColor = value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        return if (resolved && isColor) value.data else Color.WHITE
    }

    inline fun <reified T : View> findView(id: Int): T? = view?.findViewById(id)

    fun setFragmentResult(requestKey: String, vararg params: Pair<String, Any?>) {
        val bundle = Bundle().putArgsSafely(*params, logTag = "BaseFragment")
        parentFragmentManager.setFragmentResult(requestKey, bundle)
    }

    fun listenFragmentResult(requestKey: String, listener: (Bundle) -> Unit) {
        parentFragmentManager.setFragmentResultListener(
            requestKey,
            viewLifecycleOwner
        ) { _, bundle ->
            listener(bundle)
        }
    }

    companion object {
        private val backOverrideCache = ConcurrentHashMap<Class<*>, Boolean>()

        /** Looked up once per fragment class; consumer-rules.pro keeps the method name for R8. */
        private fun overridesOnBackPressed(type: Class<*>): Boolean =
            backOverrideCache.getOrPut(type) {
                type.getMethod("onBackPressed").declaringClass != BaseFragment::class.java
            }

        inline fun <reified T : Fragment> newInstance(vararg params: Pair<String, Any?>): T {
            val fragment = T::class.java.getDeclaredConstructor().newInstance()
            fragment.arguments = safeBundleOf(*params)
            return fragment
        }
    }
}