package com.af010.creator.docreader.ui.main.files

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.utils.AppUtils
import java.security.MessageDigest
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.PdfReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Documents that reach the app as a [Uri] (the system picker, "Open with" from another app).
 * The readers work on file paths, so the document is copied into the app's own storage first,
 * one folder per uri (two files with the same name never share a copy), with the uri beside it.
 * After a save the readers call [writeBack]: the edits go back to the original when its app lets
 * us write, else to a copy in the app's Download folder, so they never stay hidden in the app.
 * Copies unused for [KEEP_DAYS] days are deleted.
 */
object OpenedFiles {
    private const val DIRECTORY = "opened"
    private const val SOURCE = ".source"
    private const val KEEP_DAYS = 7

    /** Where the edits of a saved document went. */
    sealed interface WriteBack {
        /** Not a copy of a uri: the save already wrote the file itself. */
        data object NotACopy : WriteBack
        data object Original : WriteBack
        /** The original could not be written: the edits are in [file] (in the app's Download folder). */
        data class Copy(val file: File) : WriteBack
        data object Failed : WriteBack
    }

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
                val root = File(context.filesDir, DIRECTORY)
                val folder = File(root, key(uri))
                removeOld(root, keep = folder)
                folder.mkdirs()
                val copy = File(folder, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copy.outputStream().use { input.copyTo(it) }
                } ?: return@runCatching null
                File(folder, SOURCE).writeText(uri.toString())
                folder.setLastModified(System.currentTimeMillis())
                copy
            }.getOrNull()
        }

    /** After [file] was saved: sends the edits to where the document came from (see [WriteBack]). */
    suspend fun writeBack(context: Context, file: File): WriteBack = withContext(Dispatchers.IO) {
        val folder = file.parentFile ?: return@withContext WriteBack.NotACopy
        if (folder.parentFile?.canonicalPath != File(context.filesDir, DIRECTORY).canonicalPath) {
            return@withContext WriteBack.NotACopy
        }
        folder.setLastModified(System.currentTimeMillis())
        val source = runCatching { Uri.parse(File(folder, SOURCE).readText()) }.getOrNull()
        if (source != null && writeTo(context, source, file)) return@withContext WriteBack.Original
        // Read-only grant (most "Open with" from mail and chat apps): keep the edits where the
        // user finds them, in the app's own folder of the Download directory.
        runCatching {
            val directory = File(AppUtils.ensureDocumentDirectory())
            val copy = ToolRunner.uniqueFile(directory, file.nameWithoutExtension, file.extension)
            file.copyTo(copy)
            AppUtils.notifyMediaScanner(context, copy.absolutePath)
            WriteBack.Copy(copy)
        }.getOrDefault(WriteBack.Failed)
    }

    /** Overwrites the document at [uri] with [file]; false when its app does not let us write. */
    private fun writeTo(context: Context, uri: Uri, file: File): Boolean {
        // "wt" truncates; a provider without it may take "rwt". Never "w": it can leave old bytes behind.
        for (mode in arrayOf("wt", "rwt")) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri, mode)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    true
                } ?: false
            }.getOrDefault(false)
            if (written) return true
        }
        return false
    }

    /** One folder per uri, named after it. */
    private fun key(uri: Uri): String =
        MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    /** Copies (and the flat files of the earlier layout) not opened for [KEEP_DAYS] days. */
    private fun removeOld(root: File, keep: File) {
        val limit = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        root.listFiles()?.forEach { entry ->
            if (entry != keep && entry.lastModified() < limit) entry.deleteRecursively()
        }
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
