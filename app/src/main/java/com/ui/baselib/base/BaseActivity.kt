package com.ui.baselib.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.ui.baselib.api.hideSystemBar
import com.ui.baselib.api.putExtrasSafely
import com.ui.baselib.api.setFullScreen
import kotlinx.coroutines.CoroutineScope

abstract class BaseActivity<VB : ViewBinding>(
    private val bindingFactory: (LayoutInflater) -> VB,
) : AppCompatActivity(), BaseHost, FragmentNavigator {
    open val fullStatus: Boolean = false
    internal val padsStatusBar: Boolean get() = !fullStatus
    open val fragmentContainerId: Int = View.NO_ID
    open val navGraph: NavGraph? = null
    open val hideKeyboardWhenTouch : Boolean = true

    // Main-thread only, so the lazies below skip lazy()'s default synchronization.
    val binding: VB by lazy(LazyThreadSafetyMode.NONE) { bindingFactory(layoutInflater) }
    override val hostScope: CoroutineScope get() = lifecycleScope
    override val hostLifecycleOwner: LifecycleOwner get() = this
    override val toastContext: Context? get() = this
    override val keyboardActivity: Activity? get() = this
    override val mainHandlerHolder = MainHandlerHolder()

    val statusBarHeight: Int by lazy(LazyThreadSafetyMode.NONE) { statusBarHeightPx }
    val navigationBarHeight: Int by lazy(LazyThreadSafetyMode.NONE) { navigationBarHeightPx }
    open val observeKeyboard: Boolean = false
    var keyboardState: KeyboardState = KeyboardState(false, 0)
        private set
    private val graph: NavGraph? by lazy(LazyThreadSafetyMode.NONE) { navGraph }
    private val fm: FragmentManager get() = supportFragmentManager

    private val windowInsetsController: WindowInsetsControllerCompat by lazy(LazyThreadSafetyMode.NONE) {
        WindowCompat.getInsetsController(window, window.decorView)
    }

    abstract fun backPressed()
    abstract fun initialize()
    abstract fun VB.setData()
    abstract fun VB.onClick()
    open fun isFinishFirstFlow(): Boolean = true
    open fun startDestination(): String? = graph?.startDestination?.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root.apply {
            if (!fullStatus) padForStatusBar(statusBarHeight)
        })
        if (observeKeyboard) {
            findViewById<View>(android.R.id.content).observeKeyboardState(this) {
                keyboardState = it
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackFragment()) backPressed()
        }

        initialize()
        binding.setData()
        binding.onClick()

        if (savedInstanceState == null) {
            startDestination()?.let { navigateTo(it, addToBackStack = false) }
        }

        window.hideSystemBar()
        window.setFullScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandlerHolder.clear()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) post { hideNavigation() }
    }

    private fun requireGraph(): NavGraph {
        val graph = checkNotNull(graph) { "navGraph chưa được override trong Activity này" }
        check(fragmentContainerId != View.NO_ID) { "fragmentContainerId chưa được override trong Activity này" }
        return graph
    }

    private fun routeId(name: String): Int {
        graph // Building the graph registers its routes, which byName() looks up.
        return RouteRegistry.byName(name).id
    }

    override fun navigateTo(routeName: String, args: Bundle?, addToBackStack: Boolean) =
        navigateTo(routeId(routeName), args, addToBackStack)

    override fun navigateTo(routeId: Int, args: Bundle?, addToBackStack: Boolean) {
        val graph = requireGraph()
        if (fm.isStateSaved) return
        val route = RouteRegistry.byId(routeId)
        fm.beginTransaction()
            .replace(fragmentContainerId, graph.createFragment(routeId, args), route.name)
            .apply { if (addToBackStack) addToBackStack(route.name) }
            .commit()
    }

    override fun navigatePopUpTo(
        routeName: String,
        popUpTo: String,
        popUpToInclusive: Boolean,
        args: Bundle?,
        addToBackStack: Boolean,
        enterAnim: Int,
        exitAnim: Int,
        popEnterAnim: Int,
        popExitAnim: Int
    ) = navigatePopUpTo(
        routeId(routeName), routeId(popUpTo), popUpToInclusive, args, addToBackStack,
        enterAnim, exitAnim, popEnterAnim, popExitAnim
    )

    override fun navigatePopUpTo(
        routeId: Int,
        popUpToId: Int,
        popUpToInclusive: Boolean,
        args: Bundle?,
        addToBackStack: Boolean,
        enterAnim: Int,
        exitAnim: Int,
        popEnterAnim: Int,
        popExitAnim: Int
    ) {
        val graph = requireGraph()
        if (fm.isStateSaved) return
        val target = RouteRegistry.byId(routeId)
        fm.popBackStack(RouteRegistry.byId(popUpToId).name, popFlag(popUpToInclusive))
        fm.beginTransaction()
            .setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
            .replace(fragmentContainerId, graph.createFragment(routeId, args), target.name)
            .apply { if (addToBackStack) addToBackStack(target.name) }
            .commit()
    }

    override fun goBackFragment(): Boolean {
        // popBackStack() after onSaveInstanceState throws; report "not handled" instead.
        if (fm.isStateSaved || fm.backStackEntryCount == 0) return false
        fm.popBackStack()
        return true
    }

    override fun popToRoot() {
        if (fm.isStateSaved) return
        val poppedNow = runCatching {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }.isSuccess
        if (poppedNow) {
            ensureStartDestination()
        } else {
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            window.decorView.post { ensureStartDestination() }
        }
    }

    private fun ensureStartDestination() {
        val start = startDestination() ?: return
        if (fragmentContainerId == View.NO_ID || isFinishing || isDestroyed || fm.isStateSaved) return
        if (currentFragmentTag() != start) navigateTo(start, addToBackStack = false)
    }

    override fun popBackTo(routeName: String, inclusive: Boolean): Boolean =
        popBackTo(routeId(routeName), inclusive)

    override fun popBackTo(routeId: Int, inclusive: Boolean): Boolean {
        graph // Registers the graph's routes (see routeId()).
        val name = RouteRegistry.byId(routeId).name
        val exists =
            (0 until fm.backStackEntryCount).any { fm.getBackStackEntryAt(it).name == name }
        if (exists) fm.popBackStack(name, popFlag(inclusive))
        return exists
    }

    override fun currentFragmentTag(): String? =
        if (fragmentContainerId == View.NO_ID) null
        else fm.findFragmentById(fragmentContainerId)?.tag

    private fun popFlag(inclusive: Boolean) =
        if (inclusive) FragmentManager.POP_BACK_STACK_INCLUSIVE else 0

    inline fun <reified T : Any> launchActivity(vararg params: Pair<String, Any?>) {
        startActivity(Intent(this, T::class.java).putExtrasSafely(*params, logTag = "BaseActivity"))
    }

    inline fun <reified T : Any> launchAndFinish(vararg params: Pair<String, Any?>) {
        launchActivity<T>(*params)
        finish()
    }

    val resultHelper = ActivityResultHelper(this) { listenerResult(it) }

    open fun listenerResult(result: ActivityResult) {}

    inline fun <reified T : Any> launcherForResult(
        vararg params: Pair<String, Any?>,
        noinline dataResult: (ActivityResult) -> Unit = { _ -> }
    ) {
        val intent = Intent(this, T::class.java).putExtrasSafely(*params, logTag = "BaseActivity")
        resultHelper.launch(intent, dataResult)
    }


    data class KeyboardState(val visible: Boolean, val heightPx: Int)

    fun showKeyboard() = showKeyboard(binding.root)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if(hideKeyboardWhenTouch){
            hideKeyboardIfTouchedOutsideFocusedEditText(currentFocus, ev) { hideKeyboard() }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun View.observeKeyboardState(
        owner: LifecycleOwner,
        onChanged: (KeyboardState) -> Unit
    ): () -> Unit {
        val root = this
        val cleanup = { ViewCompat.setWindowInsetsAnimationCallback(root, null) }

        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    onChanged(insets.toKeyboardState())
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    onChanged(root.currentKeyboardState())
                }
            }
        )
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanup()
                owner.lifecycle.removeObserver(this)
            }
        })
        root.post { onChanged(root.currentKeyboardState()) }
        return cleanup
    }

    fun View.currentKeyboardState(): KeyboardState =
        ViewCompat.getRootWindowInsets(this)?.toKeyboardState() ?: KeyboardState(false, 0)

    private fun WindowInsetsCompat.toKeyboardState() = KeyboardState(
        visible = isVisible(WindowInsetsCompat.Type.ime()),
        heightPx = getInsets(WindowInsetsCompat.Type.ime()).bottom
    )

    fun hideNavigation() {
        windowInsetsController.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    open fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    fun shouldShowDialog(permissions: Array<String>): Boolean =
        permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
}
