package com.af010.creator.docreader.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.af010.creator.docreader.R

/**
 * A screen showing one file (a reader, Organize): its dialogs fill their main button with the
 * colour of the file's type instead of the brand blue.
 */
interface DocumentAccent {
    @get:ColorRes
    val accentColor: Int?
}

/**
 * Fills [buttons] (DialogPrimaryButton, on bg_button_primary) with [accent], or with the colour of
 * the host screen when it is a [DocumentAccent]; nothing changes when neither gives one.
 */
fun Dialog.tintAccent(@ColorRes accent: Int?, vararg buttons: View) {
    val res = accent ?: context.hostAccent() ?: return
    val color = ContextCompat.getColor(context, res)
    buttons.forEach { it.fillAccent(color) }
}

/** A button on bg_button_primary (or any filled shape) in [color], darker while pressed. */
fun View.fillAccent(@ColorInt color: Int) {
    backgroundTintList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        ),
        intArrayOf(
            ContextCompat.getColor(context, R.color.color_handle),
            ColorUtils.blendARGB(color, Color.BLACK, 0.15f),
            color
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        outlineSpotShadowColor = color
        outlineAmbientShadowColor = color
    }
}

/** [fillAccent] in the colour of the screen showing the file (a sheet over a reader, the reader itself). */
fun View.fillHostAccent() {
    val res = context.hostAccent() ?: return
    fillAccent(ContextCompat.getColor(context, res))
}

/** The [DocumentAccent] colour of the activity behind [this] context, if it has one. */
@ColorRes
fun Context.hostAccent(): Int? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is DocumentAccent) return current.accentColor
        current = current.baseContext
    }
    return null
}
