package com.af010.creator.docreader.utils.extensions

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView

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