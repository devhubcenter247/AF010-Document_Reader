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

    /**
     * Up to [limit] places in [doc] where [query] appears, each with a little text around it and
     * where it is: the page of a PDF, the line of a text file.
     */
    suspend fun hits(doc: RecentDocument, query: String, limit: Int = 2): List<ContentHit> {
        val text = textOf(doc) ?: return emptyList()
        val breakChar = if (doc.type == DocumentType.Pdf) PdfToolbox.PAGE_BREAK else '\n'
        val found = ArrayList<ContentHit>()
        var from = 0
        while (found.size < limit) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            val start = (at - CONTEXT).coerceAtLeast(0)
            val end = (at + query.length + CONTEXT).coerceAtMost(text.length)
            found += ContentHit(
                document = doc,
                place = text.subSequence(0, at).count { it == breakChar } + 1,
                inPdf = doc.type == DocumentType.Pdf,
                before = text.substring(start, at).flattened().trimStart(),
                match = text.substring(at, at + query.length),
                after = text.substring(at + query.length, end).flattened().trimEnd()
            )
            from = at + query.length
        }
        return found
    }

    private fun String.flattened() = replace(Regex("\\s+"), " ")

    private suspend fun textOf(doc: RecentDocument): String? = withContext(Dispatchers.IO) {
        val file = File(doc.path)
        when (doc.type) {
            DocumentType.Txt ->
                if (file.length() <= MAX_TEXT_BYTES) runCatching { file.readText() }.getOrNull() else null

            DocumentType.Pdf -> {
                val key = "v2_${doc.path.hashCode().toUInt().toString(16)}_${doc.lastModified}_${doc.size}.txt"
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
        /** Characters of text kept on each side of a match. */
        const val CONTEXT = 42
        const val MAX_PAGES = 50
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}

/** A match inside a document: [place] is its page (PDF, [inPdf]) or line (text file). */
data class ContentHit(
    val document: RecentDocument,
    val place: Int,
    val inPdf: Boolean,
    val before: String,
    val match: String,
    val after: String
)
