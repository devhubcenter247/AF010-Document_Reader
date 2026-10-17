package com.af010.creator.docreader.utils.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible

fun Window.setFullScreen() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        this.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
    val layoutParams = this.attributes
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    this.attributes = layoutParams
}

fun Window.hideSystemBar() {
    val windowInsetsController = WindowCompat.getInsetsController(this, this.decorView)
    windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
}

fun Context.isInternetAvailable(): Boolean {
    val connectivityManager =
        this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val network = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun View.visible() {
    this.isVisible = true
}

fun View.invisible() {
    this.isInvisible = true
}

fun View.gone() {
    this.isGone = true
}

inline fun <T> List<T>.moveItemToPosition(position: Int, predicate: (T) -> Boolean): List<T> {
    for (element in this.withIndex()) {
        if (predicate(element.value)) {
            return this.toMutableList().apply {
                removeAt(element.index)
                add(position - 1, element.value)
            }.toList()
        }
    }
    return this
}
