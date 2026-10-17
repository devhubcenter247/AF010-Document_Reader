package com.af010.creator.docreader.ui.common

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.extensionLabel

/** Turns the view into the file's tile: its extension in white on the type's colour (bg_file_tile). */
fun TextView.showTypeTile(document: RecentDocument) {
    text = document.extensionLabel
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, document.type.colorRes))
}
