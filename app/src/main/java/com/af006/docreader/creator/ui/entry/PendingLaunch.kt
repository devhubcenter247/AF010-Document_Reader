package com.af010.creator.docreader.ui.entry

import android.app.Activity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.main.files.OpenedFiles
import com.d010.pdf.reader.base.toastShort
import java.io.File

/**
 * What the app was started for (a shortcut, a document from another app), kept while the
 * splash and the first-run screens are shown; the main screen opens it once it is reached.
 */
object PendingLaunch {
    var shortcut: AppShortcut? = null
    var file: File? = null

    fun clear() {
        shortcut = null
        file = null
    }

    fun consume(activity: Activity) {
        val shortcut = shortcut
        val file = file
        clear()
        shortcut?.open(activity)
        if (file != null && !OpenedFiles.open(activity, file)) {
            activity.toastShort(activity.getString(R.string.file_open_with_failed))
        }
    }
}
