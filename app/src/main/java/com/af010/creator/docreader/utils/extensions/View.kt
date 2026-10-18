package com.af010.creator.docreader.utils.extensions

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

fun ImageView.locked() {
    val matrix = ColorMatrix()
    matrix.setSaturation(0f) //0 means grayscale
    val filter = ColorMatrixColorFilter(matrix)
    this.colorFilter = filter
}

fun ImageView.unlock() {
    this.colorFilter = null
    this.imageAlpha = 255
}

/** Sets the compound drawables (0 for none) at [sizeRes] × [sizeRes], the icon size the layout uses. */
fun TextView.setCompoundDrawablesSized(
    @DimenRes sizeRes: Int,
    @DrawableRes start: Int = 0,
    @DrawableRes top: Int = 0,
    @DrawableRes end: Int = 0,
    @DrawableRes bottom: Int = 0
) {
    val size = resources.getDimensionPixelSize(sizeRes)
    val (s, t, e, b) = listOf(start, top, end, bottom).map { id ->
        if (id == 0) null else ContextCompat.getDrawable(context, id)?.apply { setBounds(0, 0, size, size) }
    }
    setCompoundDrawablesRelative(s, t, e, b)
}
