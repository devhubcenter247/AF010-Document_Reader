@file:Suppress("DEPRECATION")

package com.ui.baselib.file

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import java.io.File

fun Bitmap.resizeWidth(newWidth: Int): Bitmap {
    val aspectRatio = this.height.toFloat() / this.width
    val targetHeight = (newWidth * aspectRatio).toInt()
    return this.scale(newWidth, targetHeight)
}

fun Bitmap.resizeHeight(newHeight: Int): Bitmap {
    val aspectRatio = this.width.toFloat() / this.height
    val targetWidth = (newHeight * aspectRatio).toInt()
    return this.scale(targetWidth, newHeight)
}

fun Bitmap.resize(newWidth: Int, newHeight: Int): Bitmap {
    return this.scale(newWidth, newHeight)
}

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getIconFromData(icon: Any): Drawable? {
    return when (icon) {
        is Int -> getDrawable(icon)
        is Bitmap -> icon.toDrawable(resources)
        is String -> {
            val file = File(icon)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(icon)
                bitmap?.toDrawable(resources)
            } else {
                loadDrawableFromAssets(icon)
            }
        }

        else -> null
    }
}

suspend fun Context.saveFileByBitmap(
    bitmap: Bitmap?,
    fileName: String,
    folderName: String,
    onSaveSuccess: (String) -> Unit = {},
    onSaveError: (String) -> Unit = {}
) {
    bitmap?.let {
        val savedUri = saveImageToGallery(it, fileName, folderName)
        if (savedUri != null) {
            onSaveSuccess("$savedUri")
        } else {
            onSaveError("Failed to save image")
        }
    } ?: onSaveError("Bitmap is null")
}

// endregion
