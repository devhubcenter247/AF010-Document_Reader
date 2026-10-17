package com.af010.creator.docreader.pdf

import android.content.Context
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The text inside documents, for the search screen. A PDF is read once per version of the file
 * and its text kept in the cache; plain text files are read directly. Office files are not
 * covered: the document SDK only reads their text through a view on screen.
 */
class ContentIndex(context: Context, private val toolbox: PdfToolbox) {
    private val directory = File(context.cacheDir, "content_index")

    suspend fun contains(doc: RecentDocument, query: String): Boolean =
        textOf(doc)?.contains(query, ignoreCase = true) == true

    private suspend fun textOf(doc: RecentDocument): String? = withContext(Dispatchers.IO) {
        val file = File(doc.path)
        when (doc.type) {
            DocumentType.Txt ->
                if (file.length() <= MAX_TEXT_BYTES) runCatching { file.readText() }.getOrNull() else null

            DocumentType.Pdf -> {
                val key = "${doc.path.hashCode().toUInt().toString(16)}_${doc.lastModified}_${doc.size}.txt"
                val cached = File(directory, key)
                if (cached.exists()) return@withContext runCatching { cached.readText() }.getOrNull()
                // A locked or damaged PDF is remembered as empty, so it is not opened again each search.
                val text = try {
                    toolbox.documentText(file, MAX_PAGES)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    ""
                }
                runCatching {
                    directory.mkdirs()
                    cached.writeText(text)
                }
                text
            }

            else -> null
        }
    }

    private companion object {
        const val MAX_PAGES = 50
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}
