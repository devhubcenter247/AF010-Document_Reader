package com.af010.creator.docreader.pdf

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class TranslateError { NoText, UnknownLanguage, ModelDownload, Failed }

class TranslateException(val error: TranslateError, cause: Throwable? = null) : IOException(error.name, cause)

sealed interface TranslateStep {
    data class Reading(val done: Int, val total: Int) : TranslateStep
    data object Downloading : TranslateStep
    data class Translating(val done: Int, val total: Int) : TranslateStep
}

/** One translated page: paragraphs before and after, and the page size in points. */
class TranslatedPage(
    val width: Float,
    val height: Float,
    val original: List<String>,
    val translated: List<String>
)

/** The outcome of translating some pages of [sourcePath], which has [totalPages] pages. */
class TranslatedDocument(
    val sourcePath: String,
    val totalPages: Int,
    val pages: List<TranslatedPage>
) {
    fun save(file: File) {
        val json = JSONObject()
            .put(KEY_SOURCE, sourcePath)
            .put(KEY_TOTAL, totalPages)
            .put(KEY_PAGES, JSONArray(pages.map { page ->
                JSONObject()
                    .put(KEY_WIDTH, page.width.toDouble())
                    .put(KEY_HEIGHT, page.height.toDouble())
                    .put(KEY_ORIGINAL, JSONArray(page.original))
                    .put(KEY_TRANSLATED, JSONArray(page.translated))
            }))
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_TOTAL = "total"
        private const val KEY_PAGES = "pages"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_ORIGINAL = "original"
        private const val KEY_TRANSLATED = "translated"

        fun load(file: File): TranslatedDocument {
            val json = JSONObject(file.readText())
            val pages = json.getJSONArray(KEY_PAGES)
            return TranslatedDocument(
                json.getString(KEY_SOURCE),
                json.getInt(KEY_TOTAL),
                (0 until pages.length()).map { index ->
                    val page = pages.getJSONObject(index)
                    TranslatedPage(
                        page.getDouble(KEY_WIDTH).toFloat(),
                        page.getDouble(KEY_HEIGHT).toFloat(),
                        page.getJSONArray(KEY_ORIGINAL).strings(),
                        page.getJSONArray(KEY_TRANSLATED).strings()
                    )
                }
            )
        }

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    }
}

/** Translates the text of PDF pages on the device with ML Kit, and writes it out as a new PDF. */
class PdfTranslator(private val toolbox: PdfToolbox) {

    /** Language tags ML Kit can translate, with their names in the current locale, sorted by name. */
    val languages: List<Pair<String, String>> by lazy {
        val locale = Locale.getDefault()
        TranslateLanguage.getAllLanguages()
            .map { tag -> tag to languageName(tag) }
            .sortedBy { it.second.lowercase(locale) }
    }

    fun languageName(tag: String): String {
        val locale = Locale.getDefault()
        return Locale.forLanguageTag(tag).getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** The language of the first pages of [file], or null when it has no text or cannot be told. */
    suspend fun detectLanguage(file: File, pageCount: Int): String? {
        val sample = StringBuilder()
        for (page in 0 until minOf(pageCount, DETECT_PAGES)) {
            sample.append(toolbox.pageText(file, page)?.text.orEmpty()).append(' ')
            if (sample.length >= LANGUAGE_SAMPLE_LENGTH) break
        }
        if (sample.isBlank()) return null
        return runCatching { identify(sample.toString()) }.getOrNull()
    }

    suspend fun translate(
        file: File,
        pages: List<Int>,
        totalPages: Int,
        sourceLanguage: String?,
        targetLanguage: String,
        onStep: (TranslateStep) -> Unit
    ): TranslatedDocument {
        val read = pages.mapIndexed { index, page ->
            onStep(TranslateStep.Reading(index, pages.size))
            val text = toolbox.pageText(file, page)
            Triple(text?.width ?: A4_WIDTH, text?.height ?: A4_HEIGHT, paragraphsOf(text?.text.orEmpty()))
        }
        val all = read.flatMap { it.third }
        if (all.isEmpty()) throw TranslateException(TranslateError.NoText)

        val from = sourceLanguage ?: identify(all.joinToString(" "))
        if (from == targetLanguage) {
            return TranslatedDocument(file.path, totalPages, read.map { TranslatedPage(it.first, it.second, it.third, it.third) })
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        try {
            onStep(TranslateStep.Downloading)
            try {
                translator.downloadModelIfNeeded().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TranslateException(TranslateError.ModelDownload, e)
            }
            var done = 0
            val translated = read.map { (width, height, paragraphs) ->
                TranslatedPage(width, height, paragraphs, paragraphs.map { paragraph ->
                    currentCoroutineContext().ensureActive()
                    onStep(TranslateStep.Translating(done++, all.size))
                    try {
                        translator.translate(paragraph).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw TranslateException(TranslateError.Failed, e)
                    }
                })
            }
            return TranslatedDocument(file.path, totalPages, translated)
        } finally {
            translator.close()
        }
    }

    /** Writes the translated paragraphs as a text-only PDF, one or more pages per source page. */
    suspend fun write(document: TranslatedDocument, output: File) = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        try {
            var number = 1
            for (page in document.pages) {
                currentCoroutineContext().ensureActive()
                val width = page.width.roundToInt().coerceAtLeast(1)
                val height = page.height.roundToInt().coerceAtLeast(1)

                val scale = page.width / A4_WIDTH
                val margin = MARGIN * scale
                val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                    textSize = TEXT_SIZE * scale
                    color = Color.BLACK
                }
                val contentWidth = (width - 2 * margin).roundToInt().coerceAtLeast(1)
                val contentHeight = height - 2 * margin

                val text = page.translated.joinToString("\n\n").ifEmpty { " " }
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, LINE_SPACING)
                    .build()

                var line = 0
                do {
                    val top = layout.getLineTop(line)
                    var end = line
                    while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= contentHeight) end++
                    val bottom = layout.getLineBottom(end)
                    val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, number++).create())
                    pdfPage.canvas.apply {
                        translate(margin, margin)
                        clipRect(0f, 0f, contentWidth.toFloat(), (bottom - top).toFloat())
                        translate(0f, -top.toFloat())
                        layout.draw(this)
                    }
                    pdf.finishPage(pdfPage)
                    line = end + 1
                } while (line < layout.lineCount)
            }
            output.parentFile?.mkdirs()
            val temp = File(output.parentFile, "${output.name}.tmp")
            try {
                temp.outputStream().use(pdf::writeTo)
                if (!temp.renameTo(output)) throw IOException("Cannot move ${temp.name} to ${output.name}")
            } finally {
                temp.delete()
            }
        } finally {
            pdf.close()
        }
    }

    private suspend fun identify(text: String): String {
        val client = LanguageIdentification.getClient()
        val tag = try {
            client.identifyLanguage(text.take(LANGUAGE_SAMPLE_LENGTH)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TranslateException(TranslateError.Failed, e)
        } finally {
            client.close()
        }
        return TranslateLanguage.fromLanguageTag(tag.substringBefore('-'))
            ?: throw TranslateException(TranslateError.UnknownLanguage)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    companion object {
        private const val A4_WIDTH = 595f
        private const val A4_HEIGHT = 842f
        private const val MARGIN = 48f
        private const val TEXT_SIZE = 11f
        private const val LINE_SPACING = 1.2f
        private const val LANGUAGE_SAMPLE_LENGTH = 1000
        private const val DETECT_PAGES = 3
        private const val PARAGRAPH_END = ".!?:;。！？"
        private const val SHORT_LINE_RATIO = 0.8f

        /** Joins the lines of a page into paragraphs: a blank line or a short line ending a sentence closes one. */
        internal fun paragraphsOf(text: String): List<String> {
            val lines = text.split("\r\n", "\n", "\r").map { it.trim() }
            val longest = lines.maxOfOrNull { it.length } ?: 0
            val paragraphs = ArrayList<String>()
            val current = StringBuilder()
            fun flush() {
                if (current.isNotBlank()) paragraphs += current.toString()
                current.clear()
            }
            for (line in lines) {
                if (line.isEmpty()) {
                    flush()
                    continue
                }
                if (current.isNotEmpty()) {
                    if (current.last() == '-') current.setLength(current.length - 1) else current.append(' ')
                }
                current.append(line)
                if (line.last() in PARAGRAPH_END && line.length < longest * SHORT_LINE_RATIO) flush()
            }
            flush()
            return paragraphs
        }
    }
}
