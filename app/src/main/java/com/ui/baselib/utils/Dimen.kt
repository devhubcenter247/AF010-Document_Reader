package com.ui.baselib.utils

import android.content.Context

private const val SDP_PACKAGE = "com.intuit.sdp"
private const val SSP_PACKAGE = "com.intuit.ssp"

fun Context.dimenSdp(dimen: Int = 0): Float =
    dimenFromPackage(dimen = dimen, suffix = "sdp", packageName = SDP_PACKAGE)

fun Context.dimenSsp(dimen: Int = 0): Float =
    dimenFromPackage(dimen = dimen, suffix = "ssp", packageName = SSP_PACKAGE)

private fun Context.dimenFromPackage(
    dimen: Int,
    suffix: String,
    packageName: String
): Float {
    if (dimen <= 0) return 0f
    val resourceName = "_$dimen$suffix"
    val resourceId = resources.getIdentifier(resourceName, "dimen", packageName)
    return if (resourceId != 0) resources.getDimension(resourceId) else 0f
}
