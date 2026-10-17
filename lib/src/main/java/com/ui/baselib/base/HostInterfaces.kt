package com.ui.baselib.base

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ui.baselib.R
import com.ui.baselib.lifecycle.launchCollect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



data class Route(val id: Int, val name: String)

object RouteRegistry {
    private var counter = 0
    private val routesByName = mutableMapOf<String, Route>()
    private val routesById = mutableMapOf<Int, Route>()

    @Synchronized
    fun register(name: String): Route {
        routesByName[name]?.let { return it }
        // Skip ids already taken by register(id, name) so auto ids never overwrite them.
        while (counter in routesById) counter++
        val route = Route(id = counter++, name = name)
        routesByName[name] = route
        routesById[route.id] = route
        return route
    }

    @Synchronized
    fun register(id: Int, name: String): Route {
        routesByName[name]?.let { existing ->
            check(existing.id == id) {
                "Route '$name' đã được đăng ký với id=${existing.id}, không thể đăng ký lại với id=$id"
            }
            return existing
        }
        routesById[id]?.let { existing ->
            check(existing.name == name) {
                "Route id=$id đã được đăng ký với tên '${existing.name}', không thể đăng ký lại với tên '$name'"
            }
        }
        val route = Route(id = id, name = name)
        routesByName[name] = route
        routesById[id] = route
        return route
    }

    fun byName(name: String): Route = routesByName[name]
        ?: throw IllegalArgumentException("Route '$name' chưa được đăng ký trong NavGraph")

    fun byId(id: Int): Route = routesById[id]
        ?: throw IllegalArgumentException("Route id=$id chưa được đăng ký trong NavGraph")

    fun byIdOrNull(id: Int): Route? = routesById[id]
}

class NavGraph {
    private val factories = mutableMapOf<Int, (Bundle?) -> Fragment>()

    var startDestination: Route? = null
        private set

    fun addDestination(
        name: String,
        isStartDestination: Boolean = false,
        factory: (Bundle?) -> Fragment
    ): Route {
        val route = RouteRegistry.register(name)
        factories[route.id] = factory
        if (isStartDestination) setStart(route)
        return route
    }

    fun addDestination(
        id: Int,
        name: String,
        isStartDestination: Boolean = false,
        factory: (Bundle?) -> Fragment
    ): Route {
        val route = RouteRegistry.register(id, name)
        factories[route.id] = factory
        if (isStartDestination) setStart(route)
        return route
    }

    private fun setStart(route: Route) {
        check(startDestination == null || startDestination == route) {
            "NavGraph đã có startDestination '${startDestination?.name}', không thể đặt thêm '${route.name}'"
        }
        startDestination = route
    }

    fun createFragment(routeId: Int, args: Bundle?): Fragment {
        val factory = factories[routeId]
            ?: throw IllegalStateException(
                "Không tìm thấy fragment cho route id=$routeId (${RouteRegistry.byIdOrNull(routeId)?.name}). " +
                        "Bạn quên addDestination() cho route này trong NavGraph?"
            )
        return factory(args)
    }

    fun hasDestination(routeId: Int): Boolean = factories.containsKey(routeId)
}

interface FragmentNavigator {
    fun navigateTo(routeName: String, args: Bundle? = null, addToBackStack: Boolean = true)
    fun navigateTo(routeId: Int, args: Bundle? = null, addToBackStack: Boolean = true)
    fun goBackFragment(): Boolean
    fun popBackTo(routeName: String, inclusive: Boolean = false): Boolean
    fun popBackTo(routeId: Int, inclusive: Boolean = false): Boolean
    fun popToRoot()
    fun currentFragmentTag(): String?
    fun navigatePopUpTo(
        routeName: String,
        popUpTo: String,
        popUpToInclusive: Boolean = false,
        args: Bundle? = null,
        addToBackStack: Boolean = true,
        @AnimRes enterAnim: Int = R.anim.fade_in,
        @AnimRes exitAnim: Int = R.anim.fade_out,
        @AnimRes popEnterAnim: Int = R.anim.fade_in,
        @AnimRes popExitAnim: Int = R.anim.fade_out
    )
    fun navigatePopUpTo(
        routeId: Int,
        popUpToId: Int,
        popUpToInclusive: Boolean = false,
        args: Bundle? = null,
        addToBackStack: Boolean = true,
        @AnimRes enterAnim: Int = R.anim.fade_in,
        @AnimRes exitAnim: Int = R.anim.fade_out,
        @AnimRes popEnterAnim: Int = R.anim.fade_in,
        @AnimRes popExitAnim: Int = R.anim.fade_out
    )
}
interface BaseHost {

    /* ---------------------------------------------------------- Coroutines */
    val hostScope: CoroutineScope

    fun launchMain(block: suspend CoroutineScope.() -> Unit): Job =
        hostScope.launch(Dispatchers.Main, block = block)

    fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
        hostScope.launch(Dispatchers.IO, block = block)

    fun launchDefault(block: suspend CoroutineScope.() -> Unit): Job =
        hostScope.launch(Dispatchers.Default, block = block)

    suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main, block = block)

    suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.IO, block = block)

    suspend fun <T> withDefault(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Default, block = block)

    /* --------------------------------------------------------- StateFlow */
    val hostLifecycleOwner: LifecycleOwner
    val defaultCollectState: Lifecycle.State get() = Lifecycle.State.STARTED

    fun <T> StateFlow<T>.collect(
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        state: Lifecycle.State = defaultCollectState,
        collector: (T) -> Unit,
    ): Job = launchCollect(hostLifecycleOwner, dispatcher, state, collector)

    fun <T> StateFlow<T>.mainCollect(collector: (T) -> Unit) =
        collect(Dispatchers.Main, collector = collector)
    fun <T> Flow<T>.collect(
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        state: Lifecycle.State = defaultCollectState,
        collector: (T) -> Unit,
    ): Job = launchCollect(hostLifecycleOwner, dispatcher, state, collector)

    fun <T> Flow<T>.mainCollect(collector: (T) -> Unit) =
        collect(Dispatchers.Main, collector = collector)
    /* -------------------------------------------------------------- Toast */
    val toastContext: Context?

    fun toast(message: String) {
        toastContext?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    fun toast(@StringRes resId: Int) {
        toastContext?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    fun toastLong(message: String) {
        toastContext?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    fun toastLong(@StringRes resId: Int) {
        toastContext?.let { Toast.makeText(it, resId, Toast.LENGTH_LONG).show() }
    }

    /* ----------------------------------------------------------- Keyboard */
    /* Delegates to the low-level showKeyboardOn/hideKeyboard extensions in
       HostSupport.kt, which stay the single source of truth for IMM calls. */
    val keyboardActivity: Activity?

    fun showKeyboard(view: View?) {
        keyboardActivity?.showKeyboardOn(view)
    }

    fun hideKeyboard() {
        keyboardActivity?.hideKeyboard()
    }

    /* --------------------------------------------------------- Main handler */
    val mainHandlerHolder: MainHandlerHolder

    fun post(action: () -> Unit) = mainHandlerHolder.post(action)

    fun postDelayed(delayMs: Long, action: () -> Unit) = mainHandlerHolder.postDelayed(delayMs, action)
}
class MainHandlerHolder {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun post(action: () -> Unit) {
        handler.post(action)
    }

    fun postDelayed(delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMs)
    }

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }
}

/**
 * Wraps the "launch for result with a one-shot callback" pattern shared by
 * BaseActivity and BaseFragment. Must be constructed while [caller] can still
 * register a launcher (i.e. as a field initializer, before STARTED).
 *
 * Only one launch is tracked at a time: a second [launch] before the first result replaces
 * its callback. The callback is also lost if the host is recreated (rotation, process death)
 * while the other activity is open; handle results that must survive that in `onResult`.
 */
class ActivityResultHelper(
    caller: ActivityResultCaller,
    private val onResult: (ActivityResult) -> Unit = {},
) {
    private var callback: ((ActivityResult) -> Unit)? = null

    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onResult(result)
        callback?.invoke(result)
        callback = null
    }

    fun launch(intent: Intent, dataResult: (ActivityResult) -> Unit = {}) {
        callback = dataResult
        launcher.launch(intent)
    }
}

/* ---------------------------------------------------- System bar sizes */

val Context.statusBarHeightPx: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

val Context.navigationBarHeightPx: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

/* Real system bar sizes from window insets (right for gesture nav and cutouts), with the
   dimen lookup as fallback before the window has insets. Ignores visibility because the
   app hides the bars in immersive mode but content must still clear them when they show. */

private fun Activity.systemBarInsets(): androidx.core.graphics.Insets? =
    window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

val Activity.statusBarInsetPx: Int
    get() = systemBarInsets()?.top ?: statusBarHeightPx

val Activity.navigationBarInsetPx: Int
    get() = systemBarInsets()?.bottom ?: navigationBarHeightPx

/**
 * Pads the top of this view for the status bar. [fallbackPx] is applied right away so the first
 * frame doesn't jump; once the window reports insets, the real status bar height replaces it
 * (it differs from the dimen on notched devices and in landscape). Insets are passed on
 * unconsumed, so children still receive them.
 */
internal fun View.padForStatusBar(fallbackPx: Int) {
    val basePaddingTop = paddingTop
    updatePadding(top = basePaddingTop + fallbackPx)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        // Ignoring visibility: the bars are hidden in immersive mode but can be swiped in.
        val top = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
        v.updatePadding(top = basePaddingTop + top)
        insets
    }
    // A view added after the window dispatched insets (a fragment's root) must ask again.
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}

/* ------------------------------------------------- Keyboard (low level) */
/* Called by KeyboardHost's default methods — keep the actual IMM calls here. */

@Suppress("DEPRECATION")
fun Activity.showKeyboardOn(view: View?) {
    try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    } catch (_: Throwable) {
    }
}

fun Activity.hideKeyboard() {
    try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.rootView.windowToken, 0)
    } catch (_: Throwable) {
    }
}

/* ---------------------------------- Tap outside a focused EditText --- */

/**
 * Call from `dispatchTouchEvent` (Activity) or a root [View.setOnTouchListener]
 * (Fragment) to hide the keyboard and drop focus when the user taps outside
 * the currently focused [EditText].
 */
inline fun hideKeyboardIfTouchedOutsideFocusedEditText(
    focusedView: View?,
    ev: MotionEvent,
    hideKeyboard: () -> Unit,
) {
    // Only a new gesture can be a "tap outside"; skipping MOVE/UP avoids per-frame work while scrolling.
    if (ev.actionMasked == MotionEvent.ACTION_DOWN && focusedView is EditText) {
        val rect = Rect()
        focusedView.getGlobalVisibleRect(rect)
        if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
            hideKeyboard()
            focusedView.clearFocus()
        }
    }
}

