package com.af010.creator.docreader.ui.main.files

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.PdfReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Documents that reach the app as a [Uri] (the system picker, "Open with" from another app).
 * The readers work on file paths, so the document is copied into the app's own storage first;
 * edits are saved to that copy, not to the file it came from.
 */
object OpenedFiles {
    private const val DIRECTORY = "opened"

    /**
     * The file to open for [uri], or null when it cannot be read. A `file://` uri the app can
     * read is used as it is; [mimeType] names the extension when the uri's own name has none.
     */
    suspend fun resolve(context: Context, uri: Uri, mimeType: String? = null): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uri.scheme == ContentResolver.SCHEME_FILE) {
                    val file = uri.path?.let(::File)
                    if (file != null && file.canRead()) return@runCatching file
                }
                val name = fileName(context, uri, mimeType) ?: return@runCatching null
                val copy = File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copy.outputStream().use { input.copyTo(it) }
                } ?: return@runCatching null
                copy
            }.getOrNull()
        }

    /** Shows [file] in its reader; false when the app has no reader for it. */
    fun open(activity: Activity, file: File): Boolean {
        when {
            file.extension.equals("pdf", ignoreCase = true) -> PdfReaderActivity.start(activity, file.path)
            OfficeActivity.canOpen(file.name) -> OfficeActivity.start(activity, file.path)
            else -> return false
        }
        return true
    }

    private fun fileName(context: Context, uri: Uri, mimeType: String?): String? {
        val name = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        if (isOpenable(name)) return name
        // Mail and chat apps sometimes hand over a name without its extension.
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType ?: context.contentResolver.getType(uri))
        return if (extension.isNullOrEmpty()) name else "$name.$extension"
    }

    private fun isOpenable(name: String) =
        name.endsWith(".pdf", ignoreCase = true) || OfficeActivity.canOpen(name)
}
