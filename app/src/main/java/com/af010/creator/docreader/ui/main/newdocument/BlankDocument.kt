package com.af010.creator.docreader.ui.main.newdocument

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.utils.AppUtils
import com.editor.docsdk.DocumentType as SdkDocumentType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * A blank Word, Excel or PowerPoint file, made at once and opened ready to edit: no form first.
 * It is named "Untitled-Oct-18" (or the next free "-2", "-3"…) in Download › Folio; Save a copy
 * in the editor puts it somewhere else under another name.
 */
fun ComponentActivity.createBlankDocument(type: DocumentType) {
    val toolbox: PdfToolbox = GlobalContext.get().get()
    val repository: DocumentRepository = GlobalContext.get().get()
    val sdkType = when (type) {
        DocumentType.Excel -> SdkDocumentType.EXCEL
        DocumentType.Ppt -> SdkDocumentType.POWERPOINT
        else -> SdkDocumentType.WORD
    }
    lifecycleScope.launch {
        runCatching {
            val directory = AppUtils.ensureDocumentDirectory()
            toolbox.createDocument(sdkType, freeFile(directory, type.blankExtension))
        }.onSuccess { file ->
            AppUtils.notifyMediaScanner(this@createBlankDocument, file.path)
            repository.refresh()
            OfficeActivity.start(this@createBlankDocument, file.path, edit = true)
        }.onFailure {
            Toast.makeText(this@createBlankDocument, R.string.error_file_storage, Toast.LENGTH_SHORT).show()
        }
    }
}

private val DocumentType.blankExtension: String
    get() = when (this) {
        DocumentType.Excel -> "xlsx"
        DocumentType.Ppt -> "pptx"
        else -> "docx"
    }

/** "Untitled-Oct-18.docx", or the next name not taken yet in [directory]. */
private fun ComponentActivity.freeFile(directory: String, extension: String): File {
    val base = getString(R.string.untitled) + "-" + SimpleDateFormat("MMM-dd", Locale.ENGLISH).format(Date())
    return generateSequence(1) { it + 1 }
        .map { File(directory, (if (it == 1) base else "$base-$it") + ".$extension") }
        .first { !it.exists() }
}
