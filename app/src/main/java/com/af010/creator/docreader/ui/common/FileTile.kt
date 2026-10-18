package com.af010.creator.docreader.ui.common

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.extensionLabel
import com.af010.creator.docreader.utils.extensions.softColorRes
import java.io.File
import java.util.Locale

/** Turns the view into the file's tile: its extension in white on the type's colour (bg_file_tile). */
fun TextView.showTypeTile(document: RecentDocument) {
    text = document.extensionLabel
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, document.type.colorRes))
}

/**
 * The same tile for a file on disk, which may also be a picture or a ZIP; [soft] draws it in the
 * type's pale colour with the letters in the strong one.
 */
fun TextView.showTypeTile(file: File, soft: Boolean = false) {
    text = file.extension.uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }
    val color = AppUtils.documentTypeOf(file)?.colorRes ?: when (file.extension.lowercase(Locale.ROOT)) {
        "zip" -> R.color.color_file_zip
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> R.color.color_file_image
        else -> R.color.color_file_txt
    }
    val type = AppUtils.documentTypeOf(file)
    if (soft && type != null) {
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, type.softColorRes))
        setTextColor(ContextCompat.getColor(context, color))
    } else {
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
    }
}
