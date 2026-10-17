package com.ui.baselib.api

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

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

fun Window.lightStatusBar(isLightStatusBar: Boolean) {
    val controller = WindowInsetsControllerCompat(this, this.decorView)
    controller.isAppearanceLightStatusBars = isLightStatusBar
}

fun Fragment.activityRun(ifNull: ()-> Unit ={}, default: (Activity)-> Unit = {}){
    activity?.let {
        default(it)
    } ?: run {
        ifNull()
    }
}

