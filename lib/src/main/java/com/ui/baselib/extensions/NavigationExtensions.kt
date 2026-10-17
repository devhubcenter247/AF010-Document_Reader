package com.ui.baselib.extensions

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.AnimRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.navigation.fragment.findNavController
import com.ui.baselib.R
import com.ui.baselib.api.putAnySafe

private const val TAG = "NavigationExtensions"

private fun argsToBundleOrNull(args: Array<out Pair<String, Any?>>): Bundle? {
    if (args.isEmpty()) return null
    return Bundle().apply {
        args.forEach { (key, value) ->
            try {
                putAnySafe(key, value)
            } catch (e: Throwable) {
                Log.e(TAG, "Skip argument \"$key\"", e)
            }
        }
    }
}

private fun buildAnimNavOptions(
    enterAnim: Int,
    exitAnim: Int,
    popEnterAnim: Int,
    popExitAnim: Int,
    extra: NavOptions.Builder.() -> Unit = {}
): NavOptions = NavOptions.Builder()
    .setEnterAnim(enterAnim)
    .setExitAnim(exitAnim)
    .setPopEnterAnim(popEnterAnim)
    .setPopExitAnim(popExitAnim)
    .apply(extra)
    .build()

fun NavController.animateNavigateWithOption(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    popUpToCurrent: Boolean = false,
    builder: NavOptions.Builder.() -> Unit = {}
) {
    val bundle = argsToBundleOrNull(args)
    val navOptionsBuilder = NavOptions.Builder().apply(builder)

    if (popUpToCurrent) {
        currentDestination?.id?.let { currentId ->
            navOptionsBuilder.setPopUpTo(currentId, inclusive = true)
        }
    }

    navigate(destination, bundle, navOptionsBuilder.build())
}

fun NavController.animateNavigate(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    popUpToCurrent: Boolean = false,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    animateNavigateWithOption(
        destination = destination,
        args = args,
        popUpToCurrent = popUpToCurrent
    ) {
        setEnterAnim(enterAnim)
        setExitAnim(exitAnim)
        setPopEnterAnim(popEnterAnim)
        setPopExitAnim(popExitAnim)
    }
}

fun NavController.safeNavigate(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    @AnimRes enterAnim: Int? = null,
    @AnimRes exitAnim: Int? = null,
    @AnimRes popEnterAnim: Int? = null,
    @AnimRes popExitAnim: Int? = null
) {
    try {
        if (currentDestination?.id == destination) return

        val bundle = argsToBundleOrNull(args)
        val hasCustomAnim = enterAnim != null || exitAnim != null || popEnterAnim != null || popExitAnim != null
        val navOptions = if (hasCustomAnim) {
            buildAnimNavOptions(
                enterAnim ?: R.anim.fade_in,
                exitAnim ?: R.anim.fade_out,
                popEnterAnim ?: R.anim.fade_in,
                popExitAnim ?: R.anim.fade_out
            )
        } else {
            null
        }

        navigate(destination, bundle, navOptions)
    } catch (e: Exception) {
        Log.e(TAG, "safeNavigate failed for destination=$destination", e)
    }
}

fun NavController.navigateSingleTop(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    val bundle = argsToBundleOrNull(args)
    val navOptions = buildAnimNavOptions(enterAnim, exitAnim, popEnterAnim, popExitAnim) {
        setLaunchSingleTop(true)
    }
    navigate(destination, bundle, navOptions)
}

fun NavController.navigateClearBackStack(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    val bundle = argsToBundleOrNull(args)
    val startId = graph.startDestinationId
    val navOptions = buildAnimNavOptions(enterAnim, exitAnim, popEnterAnim, popExitAnim) {
        setPopUpTo(startId, inclusive = true)
        setLaunchSingleTop(true)
    }
    navigate(destination, bundle, navOptions)
}

fun NavController.navigatePopUpTo(
    @IdRes destination: Int,
    @IdRes popUpTo: Int,
    inclusive: Boolean = false,
    vararg args: Pair<String, Any?>,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    val bundle = argsToBundleOrNull(args)
    val navOptions = buildAnimNavOptions(enterAnim, exitAnim, popEnterAnim, popExitAnim) {
        setPopUpTo(popUpTo, inclusive)
    }
    navigate(destination, bundle, navOptions)
}

fun NavController.navigateSlide(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    popUpToCurrent: Boolean = false
) {
    animateNavigate(
        destination = destination,
        args = args,
        popUpToCurrent = popUpToCurrent,
        enterAnim = R.anim.slide_in_right,
        exitAnim = R.anim.slide_out_left,
        popEnterAnim = R.anim.slide_in_left,
        popExitAnim = R.anim.slide_out_right
    )
}

fun NavController.navigateSlideUp(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    popUpToCurrent: Boolean = false
) {
    animateNavigate(
        destination = destination,
        args = args,
        popUpToCurrent = popUpToCurrent,
        enterAnim = R.anim.slide_in_bottom,
        exitAnim = R.anim.fade_out,
        popEnterAnim = R.anim.fade_in,
        popExitAnim = R.anim.slide_out_bottom
    )
}

fun NavController.navigateDeepLink(
    deepLink: String,
    navOptions: NavOptions? = null
) {
    try {
        val request = NavDeepLinkRequest.Builder
            .fromUri(Uri.parse(deepLink))
            .build()
        navigate(request, navOptions)
    } catch (e: Exception) {
        Log.e(TAG, "navigateDeepLink failed for $deepLink", e)
    }
}

fun NavController.navigateDeepLinkAnimated(
    deepLink: String,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    val navOptions = buildAnimNavOptions(enterAnim, exitAnim, popEnterAnim, popExitAnim)
    navigateDeepLink(deepLink, navOptions)
}

fun NavController.popBackStackSafe(): Boolean {
    return try {
        popBackStack()
    } catch (_: Exception) {
        false
    }
}

fun NavController.popBackStackTo(
    @IdRes destination: Int,
    inclusive: Boolean = false
): Boolean {
    return try {
        popBackStack(destination, inclusive)
    } catch (_: Exception) {
        false
    }
}

fun NavController.isCurrentDestination(@IdRes destinationId: Int): Boolean {
    return currentDestination?.id == destinationId
}

fun NavController.hasDestinationInBackStack(@IdRes destinationId: Int): Boolean {
    return try {
        getBackStackEntry(destinationId)
        true
    } catch (_: Exception) {
        false
    }
}

fun <T> NavController.setResult(key: String, value: T) {
    previousBackStackEntry?.savedStateHandle?.set(key, value)
}

fun <T> NavController.getResultLiveData(key: String) =
    currentBackStackEntry?.savedStateHandle?.getLiveData<T>(key)

fun Fragment.safeNavigate(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>
) {
    try {
        findNavController().safeNavigate(destination, *args)
    } catch (e: Exception) {
        Log.e("NavigationExtensions", "safeNavigate failed for destination=$destination", e)
    }
}

fun Fragment.animateNavigate(
    @IdRes destination: Int,
    vararg args: Pair<String, Any?>,
    popUpToCurrent: Boolean = false,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    try {
        findNavController().animateNavigate(
            destination = destination,
            args = args,
            popUpToCurrent = popUpToCurrent,
            enterAnim = enterAnim,
            exitAnim = exitAnim,
            popEnterAnim = popEnterAnim,
            popExitAnim = popExitAnim
        )
    } catch (e: Exception) {
        Log.e("NavigationExtensions", "animateNavigate failed for destination=$destination", e)
    }
}


fun Fragment.popBackStackSafe(): Boolean {
    return try {
        findNavController().popBackStackSafe()
    } catch (_: Exception) {
        false
    }
}

fun Fragment.navigatePopUpTo(
    @IdRes destination: Int,
    @IdRes popUpTo: Int,
    inclusive: Boolean = false,
    vararg args: Pair<String, Any?>,
    @AnimRes enterAnim: Int = R.anim.fade_in,
    @AnimRes exitAnim: Int = R.anim.fade_out,
    @AnimRes popEnterAnim: Int = R.anim.fade_in,
    @AnimRes popExitAnim: Int = R.anim.fade_out
) {
    try {
        findNavController().navigatePopUpTo(
            destination = destination,
            popUpTo = popUpTo,
            inclusive = inclusive,
            args = args,
            enterAnim = enterAnim,
            exitAnim = exitAnim,
            popEnterAnim = popEnterAnim,
            popExitAnim = popExitAnim
        )
    } catch (e: Exception) {
        Log.e(TAG, "navigatePopUpTo failed for destination=$destination", e)
    }
}

fun <T> Fragment.setNavigationResult(key: String, value: T) {
    try {
        findNavController().setResult(key, value)
    } catch (e: Exception) {
        Log.e(TAG, "setNavigationResult failed for key=$key", e)
    }
}

inline fun <reified T> Fragment.observeNavigationResult(
    key: String,
    crossinline onResult: (T) -> Unit
) {
    val navController = try {
        findNavController()
    } catch (_: Exception) {
        return
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle ?: return

    viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            savedStateHandle.get<T>(key)?.let { result ->
                onResult(result)
                savedStateHandle.remove<T>(key)
            }
        }
    })
}