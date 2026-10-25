package com.af010.creator.docreader.ui.main.reader.office

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.view.children
import com.af010.creator.docreader.R
import com.editor.docsdk.DocumentException
import com.editor.docsdk.DocumentView
import com.wxiwei.office.reader.OfficeDocumentView

/** The SDK's own view inside a [DocumentView]: it has the page pictures and the reader state. */
val DocumentView.office: OfficeDocumentView?
    get() = children.firstOrNull { it is OfficeDocumentView } as? OfficeDocumentView

/**
 * The SDK's Word view only draws its pages once it has a background, and [DocumentView] opens
 * it without one (SDK 1.0.0): give it the screen's [color].
 */
fun OfficeDocumentView.paintBehindPages(@ColorInt color: Int) {
    val pages = documentView ?: return
    if (pages.background != null) return
    pages.setBackgroundColor(color)
    pages.invalidate()
}

/** What to tell the user when the SDK cannot open a document. */
@get:StringRes
val DocumentException.Reason.openMessage: Int
    get() = when (this) {
        DocumentException.Reason.UNSUPPORTED -> R.string.error_file_unsupported
        DocumentException.Reason.DAMAGED -> R.string.error_file_damaged
        DocumentException.Reason.NOT_FOUND -> R.string.error_file_not_found
        DocumentException.Reason.OUT_OF_MEMORY -> R.string.error_file_too_large_memory
        DocumentException.Reason.STORAGE -> R.string.error_file_storage
        else -> R.string.error_file_open_generic
    }
