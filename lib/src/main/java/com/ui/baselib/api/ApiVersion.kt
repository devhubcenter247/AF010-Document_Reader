package com.ui.baselib.api

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

val isApi30orHigher: Boolean
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
val isApi30to33: Boolean
    get() = Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.TIRAMISU

val isApiFrom24to29: Boolean
    get() = Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.Q

val isApiFromHigher26: Boolean
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

val isApi33orHigher: Boolean
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
