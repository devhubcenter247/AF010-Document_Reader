package com.af010.creator.docreader.ui.common

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.extensionLabel
import java.io.File
import java.util.Locale

/** Turns the view into the file's tile: its extension in white on the type's colour (bg_file_tile). */
fun TextView.showTypeTile(document: RecentDocument) {
    text = document.extensionLabel
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, document.type.colorRes))
}

/** The same tile for a file on disk, which may also be a picture or a ZIP. */
fun TextView.showTypeTile(file: File) {
    text = file.extension.uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }
    val color = AppUtils.documentTypeOf(file)?.colorRes ?: when (file.extension.lowercase(Locale.ROOT)) {
        "zip" -> R.color.color_file_zip
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> R.color.color_file_image
        else -> R.color.color_file_txt
    }
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
}
