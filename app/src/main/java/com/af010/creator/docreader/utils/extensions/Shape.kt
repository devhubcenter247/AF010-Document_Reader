package com.af010.creator.docreader.utils.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.Px

/*
 * Colour changes on the shape drawables the layouts use as backgrounds (res/drawable/bg_*).
 * Each view mutates its own copy, so other views inflated from the same drawable keep theirs.
 * Layouts and pictures carry their border in the foreground, above their content; any other
 * view has it in the background shape.
 */

/** Fill colour of the view's background shape; a plain shape is created when there is none. */
fun View.setFillColor(@ColorInt color: Int) {
    val shape = background?.findShape()
    if (shape != null) shape.setColor(color)
    else background = GradientDrawable().apply { setColor(color) }
}

/** Border of the view's shape; a width of 0 removes it. */
fun View.setStroke(@Px width: Int, @ColorInt color: Int) {
    if (this is ViewGroup || this is ImageView) {
        val shape = foreground?.findShape() ?: GradientDrawable().apply {
            background?.findShape()?.let { fill ->
                // the border follows the outline: an oval fill (a colour dot) gets a round border
                shape = fill.shape
                fill.cornerRadii?.let { cornerRadii = it } ?: run { cornerRadius = fill.cornerRadius }
            }
            foreground = this
        }
        shape.setStroke(width, color)
        return
    }
    val shape = background?.findShape() ?: GradientDrawable().also { background = it }
    shape.setStroke(width, color)
}

/** A filled circle of [color] as the background, for colour swatches built in code. */
fun View.setCircle(@ColorInt color: Int) {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
}

private fun Drawable.findShape(): GradientDrawable? = when (val drawable = mutate()) {
    is GradientDrawable -> drawable
    is LayerDrawable -> (0 until drawable.numberOfLayers)
        .firstNotNullOfOrNull { drawable.getDrawable(it) as? GradientDrawable }
    else -> null
}
