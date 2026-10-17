package com.af010.creator.docreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.af010.creator.docreader.model.CompressLevel
import com.af010.creator.docreader.model.PageOrientation
import com.af010.creator.docreader.model.PageSetup
import com.af010.creator.docreader.model.PageSize
import com.af010.creator.docreader.model.RedactArea
import com.editor.docsdk.DocumentTools
import com.editor.docsdk.DocumentType
import com.reader.pdfviewer.pdfium.PdfDocument
import com.reader.pdfviewer.pdfium.PdfPageText
import com.reader.pdfviewer.pdfium.PdfiumCore
import com.reader.pdfviewer.tools.PdfSource
import com.reader.pdfviewer.tools.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

typealias ToolProgress = (done: Int, total: Int) -> Unit

/** One page of an organized document, see [PdfToolbox.organize]. */
data class PageEdit(val source: Int, val quarterTurns: Int = 0)

/** Text of one PDF page with the page size in points. */
class PageText(val text: String, val width: Float, val height: Float)

/**
 * File operations behind the tools. Merge, split, compress, protect and OCR go through the
 * document SDK's [DocumentTools]; watermark, redact and page text work on pdfium directly because
 * the SDK has no call for them. Every operation writes a new file and leaves its input untouched.
 */
class PdfToolbox(context: Context) {

    private val appContext = context.applicationContext
    private val tools = DocumentTools(appContext)
    private val pdfium by lazy { PdfiumCore(appContext) }
    private val pdfTools by lazy { PdfTools(appContext) }

    suspend fun pageCount(file: File, password: String? = null): Int = tools.pageCount(file, password)

    /** Page [page] (zero based) drawn [width] pixels wide. */
    suspend fun renderPage(file: File, page: Int, width: Int, password: String? = null): Bitmap =
        tools.renderPage(file, page, width, password)

    suspend fun merge(files: List<File>, output: File, onProgress: ToolProgress) {
        tools.mergePdfs(files, output) { done, total -> onProgress(done, total) }
    }

    suspend fun split(file: File, pages: IntArray, output: File) {
        tools.splitPdf(file, pages, output)
    }

    suspend fun compress(file: File, output: File, level: CompressLevel, onProgress: ToolProgress) {
        val compression = when (level) {
            CompressLevel.Low -> DocumentTools.Compression.LOW
            CompressLevel.Medium -> DocumentTools.Compression.MEDIUM
            CompressLevel.High -> DocumentTools.Compression.HIGH
        }
        tools.compressPdf(file, output, compression, null) { done, total -> onProgress(done, total) }
    }

    suspend fun protect(file: File, output: File, password: String) {
        tools.protectPdf(file, output, password, true, true)
    }

    /** Adds an invisible text layer to scanned pages so they can be searched and copied. */
    suspend fun recognizeText(file: File, output: File, onProgress: ToolProgress) {
        tools.recognizeText(file, output, null) { done, total -> onProgress(done, total) }
    }

    /**
     * OCR of [page] (zero based) only, into [output]: a whole long document takes far too long for
     * a long press in the reader. [DocumentTools] has no page filter, so this goes to the SDK's
     * [PdfTools]. Returns the number of pages that got text, 0 when none was found.
     */
    suspend fun recognizePage(file: File, page: Int, output: File, password: String? = null): Int =
        pdfTools.addTextLayer(PdfSource(file, password), output, { _, _ -> }, only = listOf(page))

    suspend fun createDocument(type: DocumentType, output: File): File =
        tools.createDocument(type, output)

    /** [text] across the middle of every page, sized to span most of the page width. */
    suspend fun watermark(
        file: File,
        output: File,
        text: String,
        color: Int,
        angle: Float,
        onProgress: ToolProgress
    ) = withContext(dispatcher) {
        val doc = open(file)
        try {
            val count = pdfium.getPageCount(doc)
            val font = systemFont()
            for (index in 0 until count) {
                currentCoroutineContext().ensureActive()
                val size = pdfium.getPagePointSize(doc, index) ?: continue
                val turns = pdfium.getPageRotation(doc, index)
                pdfium.openPage(doc, index)
                // The page centre as shown (origin top left), mapped into page space.
                val center = try {
                    pdfium.deviceToPageCoords(
                        doc, index, 0, 0, size.width * 10, size.height * 10, 0,
                        size.width * 5, size.height * 5
                    )
                } finally {
                    pdfium.closePage(doc, index)
                } ?: continue
                // A page turned clockwise shows its content turned: turn the text the other way.
                val added = pdfium.addPageText(
                    doc, index, text, watermarkTextSize(text, size.width.toFloat()), color,
                    center.x, center.y, angle + turns * 90f, 1, font
                )
                if (added == null) throw IOException("Cannot add text to page ${index + 1}")
                onProgress(index + 1, count)
            }
            save(doc, output)
        } finally {
            pdfium.closeDocument(doc)
        }
    }

    /**
     * Blacks out [areas] for good: each page that has one becomes a picture with the boxes
     * painted over, so nothing of what was under them is left in the file. Other pages are
     * copied as they are.
     */
    suspend fun redact(
        file: File,
        output: File,
        areas: List<RedactArea>,
        onProgress: ToolProgress
    ) = withContext(dispatcher) {
        val byPage = areas.groupBy { it.page }
        val src = open(file)
        val dest = pdfium.newEmptyDocument()
        try {
            val count = pdfium.getPageCount(src)
            val black = Paint().apply { color = Color.BLACK }
            for (index in 0 until count) {
                currentCoroutineContext().ensureActive()
                val boxes = byPage[index].orEmpty()
                if (boxes.isEmpty()) {
                    if (!pdfium.importPages(dest, src, intArrayOf(index))) {
                        throw IOException("Cannot copy page ${index + 1}")
                    }
                } else {
                    val size = pdfium.getPagePointSize(src, index)
                        ?: throw IOException("Cannot read page ${index + 1}")
                    val scale = REDACT_DPI / 72f
                    val bitmap = createBitmap(
                        max(1, (size.width * scale).toInt()),
                        max(1, (size.height * scale).toInt())
                    )
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        if (!pdfium.renderPageBitmapOnce(src, bitmap, index, true)) {
                            throw IOException("Cannot draw page ${index + 1}")
                        }
                        val canvas = Canvas(bitmap)
                        for (box in boxes) {
                            canvas.drawRect(
                                box.left * bitmap.width, box.top * bitmap.height,
                                box.right * bitmap.width, box.bottom * bitmap.height, black
                            )
                        }
                        val added = pdfium.addJpegPage(
                            dest, bitmap.toJpeg(), size.width.toFloat(), size.height.toFloat()
                        )
                        if (!added) throw IOException("Cannot add page ${index + 1}")
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, count)
            }
            save(dest, output)
        } finally {
            pdfium.closeDocument(dest)
            pdfium.closeDocument(src)
        }
    }

    /** Page [page] of [file] as a JPG picture on a white sheet. */
    suspend fun pageToImage(file: File, page: Int, output: File) {
        val rendered = renderPage(file, page, IMAGE_WIDTH)
        withContext(dispatcher) {
            val sheet = Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply {
                drawColor(Color.WHITE)
                drawBitmap(rendered, 0f, 0f, null)
            }
            output.outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, it) }
            sheet.recycle()
            rendered.recycle()
        }
    }

    /** Page [page] of [file] with the box of every word and line, for reading aloud and the text view. */
    suspend fun pageLayout(file: File, page: Int, password: String? = null): PdfPageText? =
        withContext(dispatcher) {
            val doc = open(file, password)
            try {
                pdfium.getPageTextLayout(doc, page)
            } finally {
                pdfium.closeDocument(doc)
            }
        }

    /**
     * A new document made of [pages], in that order: each is a page of [file] ([PageEdit.source],
     * zero based) or a blank A4 page (negative source), turned [PageEdit.quarterTurns] clockwise.
     */
    suspend fun organize(file: File, password: String?, pages: List<PageEdit>, output: File) =
        withContext(dispatcher) {
            val src = open(file, password)
            val dest = pdfium.newEmptyDocument()
            try {
                for (edit in pages) {
                    currentCoroutineContext().ensureActive()
                    val added = if (edit.source < 0) pdfium.insertBlankPage(dest, -1, A4_WIDTH, A4_HEIGHT)
                    else pdfium.importPages(dest, src, intArrayOf(edit.source))
                    if (!added) throw IOException("Cannot add page ${edit.source + 1}")
                    if (edit.quarterTurns % 4 != 0) {
                        val index = pdfium.getPageCount(dest) - 1
                        pdfium.setPageRotation(dest, index, pdfium.getPageRotation(dest, index) + edit.quarterTurns)
                    }
                }
                save(dest, output)
            } finally {
                pdfium.closeDocument(dest)
                pdfium.closeDocument(src)
            }
        }

    /** The text layer of page [page], or null when the page cannot be read. */
    suspend fun pageText(file: File, page: Int): PageText? = withContext(dispatcher) {
        val doc = open(file)
        try {
            pdfium.getPageTextLayout(doc, page)?.let { PageText(it.text, it.pageWidth, it.pageHeight) }
        } finally {
            pdfium.closeDocument(doc)
        }
    }

    /** The text layers of the first [maxPages] pages, one after the other. */
    suspend fun documentText(file: File, maxPages: Int): String = withContext(dispatcher) {
        val doc = open(file)
        try {
            buildString {
                for (page in 0 until minOf(pdfium.getPageCount(doc), maxPages)) {
                    currentCoroutineContext().ensureActive()
                    pdfium.getPageTextLayout(doc, page)?.let { append(it.text).append('\n') }
                }
            }
        } finally {
            pdfium.closeDocument(doc)
        }
    }

    /**
     * Where [query] appears on page [page]: one box per line of each match, as fractions of the
     * page as shown.
     */
    suspend fun findText(file: File, page: Int, query: String): List<RectF> = withContext(dispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        val doc = open(file)
        try {
            val layout = pdfium.getPageTextLayout(doc, page) ?: return@withContext emptyList()
            val boxes = ArrayList<RectF>()
            var start = layout.text.indexOf(query, ignoreCase = true)
            while (start >= 0) {
                var line: RectF? = null
                for (index in start until minOf(start + query.length, layout.charCount)) {
                    val box = layout.charBox(index)?.let { layout.toBitmapRect(it, UNIT, UNIT) }
                    if (box == null || box.isEmpty) continue
                    val current = line
                    // A character that starts well below the current box begins a new line.
                    if (current != null && box.top < current.bottom) {
                        current.union(box)
                    } else {
                        current?.let(boxes::add)
                        line = RectF(box)
                    }
                }
                line?.let(boxes::add)
                start = layout.text.indexOf(query, start + query.length, ignoreCase = true)
            }
            boxes.onEach { it.set(it.left / UNIT, it.top / UNIT, it.right / UNIT, it.bottom / UNIT) }
        } finally {
            pdfium.closeDocument(doc)
        }
    }

    /** One page per image, laid out by [setup]. */
    suspend fun imagesToPdf(
        images: List<File>,
        output: File,
        setup: PageSetup,
        onProgress: ToolProgress
    ) = withContext(dispatcher) {
        val doc = pdfium.newEmptyDocument()
        try {
            images.forEachIndexed { index, image ->
                currentCoroutineContext().ensureActive()
                val bitmap = decodeImage(image) ?: throw IOException("Cannot read ${image.name}")
                try {
                    addImagePage(doc, bitmap, setup)
                } finally {
                    bitmap.recycle()
                }
                onProgress(index + 1, images.size)
            }
            save(doc, output)
        } finally {
            pdfium.closeDocument(doc)
        }
    }

    suspend fun zip(files: List<File>, output: File, onProgress: ToolProgress) =
        withContext(Dispatchers.IO) {
            val temp = File(output.parentFile, "${output.name}.tmp")
            try {
                ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                    val usedNames = HashSet<String>()
                    files.forEachIndexed { index, file ->
                        currentCoroutineContext().ensureActive()
                        zip.putNextEntry(ZipEntry(uniqueEntryName(file, usedNames)))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        onProgress(index + 1, files.size)
                    }
                }
                if (!temp.renameTo(output)) throw IOException("Cannot write ${output.name}")
            } finally {
                temp.delete()
            }
        }

    private fun addImagePage(doc: PdfDocument, bitmap: Bitmap, setup: PageSetup) {
        val landscape = when (setup.orientation) {
            PageOrientation.Portrait -> false
            PageOrientation.Landscape -> true
            PageOrientation.Auto -> bitmap.width > bitmap.height
        }
        val margin = setup.margin.points
        var pageWidth = setup.size.width
        var pageHeight = setup.size.height
        if (setup.size == PageSize.Fit) {
            pageHeight = (pageWidth - 2 * margin) * bitmap.height / bitmap.width + 2 * margin
        } else if (landscape) {
            pageWidth = setup.size.height
            pageHeight = setup.size.width
        }

        val jpeg = if (setup.size == PageSize.Fit && margin == 0f) {
            bitmap.toJpeg()
        } else {
            // Draw the page as one picture: white paper with the image fitted inside the margins.
            val scale = PAGE_DPI / 72f
            val page = createBitmap((pageWidth * scale).roundToInt(), (pageHeight * scale).roundToInt())
            try {
                page.eraseColor(Color.WHITE)
                val area = RectF(
                    margin * scale, margin * scale, page.width - margin * scale, page.height - margin * scale
                )
                val fit = minOf(area.width() / bitmap.width, area.height() / bitmap.height)
                val width = bitmap.width * fit
                val height = bitmap.height * fit
                val left = area.centerX() - width / 2
                val top = area.centerY() - height / 2
                Canvas(page).drawBitmap(
                    bitmap, null, RectF(left, top, left + width, top + height),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                page.toJpeg()
            } finally {
                page.recycle()
            }
        }
        if (!pdfium.addJpegPage(doc, jpeg, pageWidth, pageHeight)) throw IOException("Cannot add page")
    }

    /** Decoded upright (EXIF applied) and at most [MAX_IMAGE_SIDE] pixels on its longer side. */
    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val degrees = when (
            runCatching { ExifInterface(file.path) }.getOrNull()
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().use { out ->
        if (!compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Cannot encode page")
        out.toByteArray()
    }

    private fun uniqueEntryName(file: File, used: MutableSet<String>): String {
        var name = file.name
        var copy = 1
        while (!used.add(name)) {
            val suffix = if (file.extension.isEmpty()) "" else ".${file.extension}"
            name = "${file.nameWithoutExtension} (${copy++})$suffix"
        }
        return name
    }

    private fun open(file: File, password: String? = null): PdfDocument {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            pdfium.newDocument(fd, password)
        } catch (e: Throwable) {
            fd.close()
            throw e
        }
    }

    private suspend fun save(doc: PdfDocument, output: File) {
        currentCoroutineContext().ensureActive()
        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, "${output.name}.tmp")
        try {
            if (!pdfium.saveAsCopy(doc, temp.path)) throw IOException("Cannot write ${output.name}")
            if (!temp.renameTo(output)) throw IOException("Cannot move ${temp.name} to ${output.name}")
        } finally {
            temp.delete()
        }
    }

    /** A font with Latin, Vietnamese and more, or null (pdfium then falls back to Helvetica). */
    private fun systemFont(): String? = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    ).firstOrNull { File(it).canRead() }

    companion object {
        // A4 at 200 dpi
        private const val IMAGE_WIDTH = 1654
        private const val IMAGE_QUALITY = 90
        private const val A4_WIDTH = 595f
        private const val A4_HEIGHT = 842f
        private const val REDACT_DPI = 200f
        private const val PAGE_DPI = 150f
        private const val MAX_IMAGE_SIDE = 2480
        private const val JPEG_QUALITY = 88
        private const val UNIT = 10_000

        /** Share of the page width a watermark spans, and the largest size relative to the width. */
        private const val WATERMARK_SPAN = 0.7f
        private const val WATERMARK_MAX_SIZE = 0.25f

        /** Pdfium calls run one at a time; each works on its own document. */
        private val dispatcher = Dispatchers.IO.limitedParallelism(1)

        /** Text size (in the unit of [pageWidth]) that makes [text] span most of the page. */
        fun watermarkTextSize(text: String, pageWidth: Float): Float {
            val widthAtHundred = Paint().apply { textSize = 100f }.measureText(text).coerceAtLeast(1f)
            return (pageWidth * WATERMARK_SPAN / widthAtHundred * 100f)
                .coerceAtMost(pageWidth * WATERMARK_MAX_SIZE)
        }
    }
}
