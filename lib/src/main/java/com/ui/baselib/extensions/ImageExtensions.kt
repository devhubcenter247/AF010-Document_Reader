package com.ui.baselib.extensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
// ============================================================================
// region Bitmap Extraction
// ============================================================================

/**
 * Get the current bitmap from this ImageView.
 *
 * @return Bitmap or null if not available
 */
fun ImageView.imageBitmap(): Bitmap? {
    return try {
        when (val drawable = this.drawable) {
            is BitmapDrawable -> drawable.bitmap
            is VectorDrawable -> {
                val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

// endregion

// ============================================================================
// region Gradient Icon
// ============================================================================

/**
 * Gradient orientation for icons.
 */
enum class GradientOrientation {
    TOP_TO_BOTTOM, TR_BL, RIGHT_TO_LEFT, BR_TL,
    BOTTOM_TO_TOP, BL_TR, LEFT_TO_RIGHT, TL_BR
}

/**
 * Coordinate quad for gradient.
 */
data class Quad(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/**
 * Convert gradient orientation to coordinates.
 */
fun GradientOrientation.toCoords(w: Float, h: Float): Quad = when (this) {
    GradientOrientation.TOP_TO_BOTTOM -> Quad(0f, 0f, 0f, h)
    GradientOrientation.BOTTOM_TO_TOP -> Quad(0f, h, 0f, 0f)
    GradientOrientation.LEFT_TO_RIGHT -> Quad(0f, 0f, w, 0f)
    GradientOrientation.RIGHT_TO_LEFT -> Quad(w, 0f, 0f, 0f)
    GradientOrientation.TL_BR -> Quad(0f, 0f, w, h)
    GradientOrientation.TR_BL -> Quad(w, 0f, 0f, h)
    GradientOrientation.BL_TR -> Quad(0f, h, w, 0f)
    GradientOrientation.BR_TL -> Quad(w, h, 0f, 0f)
}

/**
 * Apply gradient tint to ImageView icon.
 *
 * @param colors Gradient colors (minimum 2)
 * @param orientation Gradient direction
 */
fun ImageView.gradientIcon(
    @ColorInt vararg colors: Int,
    orientation: GradientOrientation = GradientOrientation.TOP_TO_BOTTOM
) {
    if (colors.size < 2) return
    val src = drawable ?: return

    val targetW = src.intrinsicWidth.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: 100
    val targetH = src.intrinsicHeight.takeIf { it > 0 } ?: height.takeIf { it > 0 } ?: 100

    val original: Bitmap = src.safeToBitmap(targetW, targetH)
    val result = createBitmap(original.width, original.height)
    val canvas = Canvas(result)
    canvas.drawBitmap(original, 0f, 0f, null)

    val (x0, y0, x1, y1) = orientation.toCoords(original.width.toFloat(), original.height.toFloat())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    canvas.drawRect(0f, 0f, original.width.toFloat(), original.height.toFloat(), paint)

    setImageDrawable(result.toDrawable(resources))
}

private fun Drawable.safeToBitmap(w: Int, h: Int): Bitmap {
    return this.toBitmap(w, h, Bitmap.Config.ARGB_8888)
}

// endregion
