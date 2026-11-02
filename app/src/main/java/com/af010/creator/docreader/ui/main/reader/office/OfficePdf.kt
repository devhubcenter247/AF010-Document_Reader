package com.af010.creator.docreader.ui.main.reader.office

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.editor.docsdk.internal.ag
import com.editor.docsdk.internal.b6
import com.editor.docsdk.internal.bd
import com.editor.docsdk.internal.f2
import com.editor.docsdk.internal.l4
import com.editor.docsdk.internal.m9
import com.editor.docsdk.internal.qb
import com.editor.docsdk.internal.u1
import com.editor.docsdk.internal.uc
import com.editor.docsdk.internal.xa
import com.editor.docsdk.internal.xf
import com.wxiwei.office.editor.ui.writeDeckPdf
import com.wxiwei.office.reader.OfficeDocumentView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * An open Word, PowerPoint or Excel document written as a PDF drawn as vectors, so its text stays
 * text: every Word page at its own size, every slide, and the used part of every sheet cut into
 * A4 landscape pages. The SDK draws these but has no API to write them; this calls its view
 * classes directly (ported from Pdf-Document-Reader's OfficePdfExport).
 */
object OfficePdf {

    /** Whether [office] shows a document this can write. */
    fun canWrite(office: OfficeDocumentView?): Boolean = when (office?.content) {
        is xf, is xa, is l4 -> true
        else -> false
    }

    /**
     * Writes [office] to [output]; returns the pages written (0 when nothing was laid out yet).
     * [progress] may be called from the SDK's drawing thread.
     */
    suspend fun write(office: OfficeDocumentView, output: File, progress: (done: Int, total: Int) -> Unit): Int =
        when (val view = office.content) {
            is xf -> onDrawingThread(office, output) { out ->
                val count = view.getPageCount()
                writeWord(view, out) { progress(it, count) }
            }
            is xa -> {
                val count = office.state.value.pageCount
                onDrawingThread(office, output) { out -> writeDeckPdf(view, count, out) { progress(it, count) } }
            }
            is l4 -> {
                // Sheets are read when first shown and drawn on the main thread.
                var written = 0
                val bytes = withContext(Dispatchers.Main) {
                    loadAllSheets(view)
                    val pages = sheetPages(view)
                    ByteArrayOutputStream().also { out -> written = writeSheets(view, pages, out) { progress(it, pages.size) } }
                }
                if (written > 0) withContext(Dispatchers.IO) { output.writeBytes(bytes.toByteArray()) }
                written
            }
            else -> 0
        }

    /**
     * The pages the PDF will have, for a preview: Word pages and slides as the SDK draws them, sheet
     * pages exactly as [write] cuts them. Null when [office] shows nothing this can write.
     */
    suspend fun preview(office: OfficeDocumentView): Preview? = when (val view = office.content) {
        // Drawn like the PDF's pages, not as the SDK's thumbnails (those have a frame and margin marks).
        is xf -> Preview(steadyCount(office) { view.getPageCount() }, landscape = false) { index, width ->
            onDrawingThread(office) { drawWordPicture(view, index + 1, width) }
        }
        is xa -> office.thumbnails?.let { thumbnails ->
            val count = steadyCount(office) { office.state.value.pageCount }
            Preview(count, landscape = true) { index, width -> thumbnails.render(index + 1, width) }
        }
        is l4 -> withContext(Dispatchers.Main) {
            loadAllSheets(view)
            val pages = sheetPages(view)
            Preview(pages.size, landscape = true) { index, width ->
                withContext(Dispatchers.Main) { drawSheetPicture(view, pages[index], width) }
            }
        }
        else -> null
    }

    /** [count] pages, each drawn [draw] (index from 0, width in px); [landscape] is their usual shape. */
    class Preview(val count: Int, val landscape: Boolean, val draw: suspend (index: Int, width: Int) -> Bitmap?)

    /** Runs [draw] on the document's drawing thread, or on the main thread when it has none. */
    private suspend fun <T> onDrawingThread(office: OfficeDocumentView, draw: () -> T): T =
        office.thumbnails?.onDrawingThread(draw) ?: withContext(Dispatchers.Main) { draw() }

    /** Pages and slides are drawn on the document's drawing thread, or on the main thread when it has none. */
    private suspend fun onDrawingThread(office: OfficeDocumentView, output: File, write: (OutputStream) -> Int): Int =
        withContext(Dispatchers.IO) {
            output.outputStream().use { out -> onDrawingThread(office) { write(out) } }
        }

    // ---- Word ----

    /**
     * The SDK keeps adding pages after it reports the document ready (Word lays them out once the
     * view draws, slides come in one by one): waits until [count] has stopped growing for a moment.
     */
    private suspend fun steadyCount(office: OfficeDocumentView, count: () -> Int): Int = withContext(Dispatchers.Main) {
        withTimeoutOrNull(LAYOUT_WAIT_MS.toLong()) { office.state.first { !it.isLoading } }
        var count = count()
        var steady = 0
        var waited = 0
        while (waited < LAYOUT_WAIT_MS && (count == 0 || steady < LAYOUT_STEADY_MS)) {
            delay(LAYOUT_POLL_MS)
            waited += LAYOUT_POLL_MS.toInt()
            val now = count()
            steady = if (now == count) steady + LAYOUT_POLL_MS.toInt() else 0
            count = now
        }
        count
    }

    private fun writeWord(word: xf, out: OutputStream, progress: (Int) -> Unit): Int {
        val count = word.getPageCount()
        val doc = PdfDocument()
        var written = 0
        try {
            for (n in 1..count) {
                val bounds = word.pageBounds(n) ?: continue
                if (bounds.width() <= 0 || bounds.height() <= 0) continue
                val info = PdfDocument.PageInfo.Builder(Math.round(bounds.width() * PX_TO_PT), Math.round(bounds.height() * PX_TO_PT), n).create()
                val page = doc.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.scale(PX_TO_PT, PX_TO_PT)
                if (word.withoutPageFrame(n) { word.drawPage(n, page.canvas) }) written++
                doc.finishPage(page)
                progress(n)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return written
    }

    /** Word page [page] (from 1) as a picture [width] px wide, drawn as in the PDF; null when not laid out. */
    private fun drawWordPicture(word: xf, page: Int, width: Int): Bitmap? {
        val bounds = word.pageBounds(page) ?: return null
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val scale = width.toFloat() / bounds.width()
        val bitmap = Bitmap.createBitmap(width, Math.round(bounds.height() * scale), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        word.withoutPageFrame(page) { word.drawPage(page, canvas) }
        return bitmap
    }

    /**
     * Runs [draw] with the black frame round the page and the grey margin corners hidden: the
     * SDK draws them for the screen, with the page's own paint. A document with a page border of
     * its own keeps them, as that border is drawn with the same paint.
     */
    private inline fun <T> xf.withoutPageFrame(page: Int, draw: () -> T): T {
        val paint = pageView(page)?.takeIf { it.pageBorderIndex < 0 }?.framePaint
        val kept = paint?.colorFilter
        paint?.colorFilter = HIDDEN
        try {
            return draw()
        } finally {
            paint?.colorFilter = kept
        }
    }

    private val HIDDEN = PorterDuffColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC)

    // ---- Excel ----

    /**
     * One page of a sheet: from column edge [left] / row edge [top] to [right] / [bottom] (sheet
     * pixels), drawn [scale] times its size.
     */
    private class SheetPage(val sheet: uc, val left: Float, val top: Float, val right: Float, val bottom: Float, val scale: Float = 1f)

    /** Shows each sheet not read yet until it is read, then goes back to the sheet that was shown. */
    private suspend fun loadAllSheets(excel: l4) {
        val book = excel.book ?: return
        val shown = excel.sheetView?.currentSheet?.let { book.indexOf(it) } ?: 0
        var switched = false
        for (i in 0 until book.sheetCount) {
            val sheet = book.sheet(i) ?: continue
            if (sheet.state == SHEET_READ) continue
            excel.showSheet(i)
            switched = true
            var waited = 0
            while (sheet.state != SHEET_READ && waited < SHEET_WAIT_MS) {
                delay(100)
                waited += 100
            }
        }
        if (switched) excel.showSheet(shown)
    }

    /**
     * The used part of every read sheet on A4 landscape pages: shrunk onto one page when that keeps
     * it at least [MIN_FIT] of its size, else cut at column and row edges (a column or row wider
     * than a page is cut where it must), left to right, then top to bottom.
     */
    private fun sheetPages(excel: l4): List<SheetPage> {
        val book = excel.book ?: return emptyList()
        val maxW = (A4_LONG - 2 * MARGIN) / PX_TO_PT
        val maxH = (A4_SHORT - 2 * MARGIN) / PX_TO_PT
        fun breaks(edges: FloatArray, max: Float): List<Float> {
            val result = arrayListOf(0f)
            var start = 0f
            var last = 0f
            for (e in edges) {
                if (e - start > max) {
                    if (last > start) { result += last; start = last }
                    while (e - start > max) { start += max; result += start }
                }
                last = e
            }
            if (edges.last() > start) result += edges.last()
            return result
        }
        val pages = ArrayList<SheetPage>()
        for (i in 0 until book.sheetCount) {
            val sheet = book.sheet(i) ?: continue
            if (sheet.state != SHEET_READ) continue
            val (xs, ys) = printEdges(sheet)
            if (xs.isEmpty() || ys.isEmpty()) continue
            val fit = minOf(1f, maxW / xs.last(), maxH / ys.last())
            if (fit >= MIN_FIT) {
                pages += SheetPage(sheet, 0f, 0f, xs.last(), ys.last(), fit)
                continue
            }
            val bx = breaks(xs, maxW)
            val by = breaks(ys, maxH)
            for (c in 0 until bx.size - 1) for (r in 0 until by.size - 1) pages += SheetPage(sheet, bx[c], by[r], bx[c + 1], by[r + 1])
        }
        return pages
    }

    /**
     * The right edges of the used columns and the bottom edges of the used rows of [sheet] (sheet
     * pixels at zoom 1), up to the last cell that shows something or the last shape. The SDK's
     * own SheetView.printEdges is not in the published library, so this is a copy of it.
     */
    private fun printEdges(sheet: uc): Pair<FloatArray, FloatArray> {
        var lastCol = -1
        var lastRow = -1
        for (r in sheet.firstRow..sheet.lastRow) {
            val row = sheet.row(r) ?: continue
            for (cell in row.cells) {
                if (cell !is u1 || !cell.showsSomething) continue
                lastCol = maxOf(lastCol, cell.column)
                lastRow = r
            }
        }
        var shapesRight = 0f
        var shapesBottom = 0f
        for (shape in sheet.shapes.orEmpty()) {
            val b = shape?.getBounds() ?: continue
            shapesRight = maxOf(shapesRight, (b.b + b.d).toFloat())
            shapesBottom = maxOf(shapesBottom, (b.c + b.e).toFloat())
        }
        val xs = ArrayList<Float>()
        var x = 0f
        var c = 0
        while ((c <= lastCol || x < shapesRight) && c < MAX_COLUMNS) {
            if (!sheet.isColumnHidden(c)) x += sheet.columnWidth(c)
            xs += x
            c++
        }
        val ys = ArrayList<Float>()
        var y = 0f
        var r = 0
        while ((r <= lastRow || y < shapesBottom) && r < MAX_ROWS) {
            val row = sheet.row(r)
            y += when {
                row == null -> sheet.defaultRowHeight.toFloat()
                row.isZeroHeight -> 0f
                else -> row.height
            }
            ys += y
            r++
        }
        if (xs.isEmpty() || ys.isEmpty()) return FloatArray(0) to FloatArray(0)
        return xs.toFloatArray() to ys.toFloatArray()
    }

    /**
     * [pages] as A4 landscape PDF pages, the cells as vectors without row and column headers; the
     * pages written. Main thread: it lets the screen update and can be cancelled between pages.
     */
    private suspend fun writeSheets(excel: l4, pages: List<SheetPage>, out: OutputStream, progress: (Int) -> Unit): Int {
        val view = excel.sheetView ?: return 0
        if (pages.isEmpty()) return 0
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_LONG.toInt(), A4_SHORT.toInt(), i + 1).create())
                drawSheetPage(view, p, page.canvas)
                doc.finishPage(page)
                progress(i + 1)
                yield()
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return pages.size
    }

    /** One sheet page on an A4 landscape [canvas] measured in PDF points. */
    private fun drawSheetPage(view: bd, p: SheetPage, canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.translate(MARGIN, MARGIN)
        canvas.scale(PX_TO_PT * p.scale, PX_TO_PT * p.scale)
        canvas.clipRect(0f, 0f, p.right - p.left, p.bottom - p.top)
        view.drawRegion(p.sheet, Math.round(p.left), Math.round(p.top), canvas)
    }

    /** One sheet page as a picture [width] px wide. Main thread. */
    private fun drawSheetPicture(excel: l4, p: SheetPage, width: Int): Bitmap? {
        val view = excel.sheetView ?: return null
        val bitmap = Bitmap.createBitmap(width, Math.round(width * A4_SHORT / A4_LONG), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / A4_LONG, width / A4_LONG)
        drawSheetPage(view, p, canvas)
        return bitmap
    }

    /** Layout pixels (96 per inch) to PDF points (72 per inch). */
    private const val PX_TO_PT = 72f / 96f
    private const val A4_LONG = 842f
    private const val A4_SHORT = 595f
    private const val MARGIN = 28f
    private const val SHEET_WAIT_MS = 15_000
    /** A sheet a little larger than a page is shrunk onto it rather than cut. */
    private const val MIN_FIT = 0.6f
    private const val LAYOUT_WAIT_MS = 15_000
    private const val LAYOUT_STEADY_MS = 600
    private const val LAYOUT_POLL_MS = 100L
    /** Sheet.State_Accomplished: the sheet is read. */
    private const val SHEET_READ: Short = 2
    /** Cell.CELL_TYPE_BLANK. */
    private const val CELL_BLANK: Short = 3
    private const val MAX_COLUMNS = 16384
    private const val MAX_ROWS = 1048576

    // ---- SDK members by their R8 names in docsdk 1.0.0 (mappings/docsdk-1.0.0.txt in the SDK
    // project). The first thing to break when the SDK version changes: check each one again. ----

    /** MainControl.getView(): Word (xf), Presentation (xa) or ExcelView (l4). */
    private val OfficeDocumentView.content get() = control?.k()

    // com.wxiwei.office.wp.control.Word
    private fun xf.pageBounds(page: Int): Rect? = c(page)
    private fun xf.drawPage(page: Int, canvas: Canvas): Boolean = a(page, canvas)
    /** Word.pageRoot.getPageView(page - 1): com.wxiwei.office.wp.view.PageView. */
    private fun xf.pageView(page: Int): m9? = r?.h(page - 1)
    private val m9.pageBorderIndex: Int get() = r
    private val m9.framePaint: Paint? get() = s

    // com.wxiwei.office.ss.control.ExcelView, Spreadsheet, SheetView
    private val l4.book: ag? get() = getSpreadsheet()?.getWorkbook()
    private val l4.sheetView: bd? get() = getSpreadsheet()?.getSheetView()
    private fun l4.showSheet(index: Int) = b(index)
    private val bd.currentSheet: uc? get() = d()
    private fun bd.drawRegion(sheet: uc, left: Int, top: Int, canvas: Canvas) = a(sheet, left, top, 1f, canvas, false)

    // com.wxiwei.office.ss.model.baseModel.Workbook, Sheet, Row, Cell; style.CellStyle
    private val ag.sheetCount: Int get() = f()
    private fun ag.sheet(index: Int): uc? = g(index)
    private fun ag.indexOf(sheet: uc): Int = a(sheet)
    private val uc.state: Short get() = z()
    private val uc.firstRow: Int get() = j()
    private val uc.lastRow: Int get() = l()
    private fun uc.row(index: Int): qb? = e(index)
    private val uc.shapes: Array<b6?>? get() = w()
    private fun uc.isColumnHidden(column: Int): Boolean = i(column)
    private fun uc.columnWidth(column: Int): Float = b(column)
    private val uc.defaultRowHeight: Int get() = i()
    private val qb.cells: Collection<*> get() = a()
    private val qb.isZeroHeight: Boolean get() = m()
    private val qb.height: Float get() = i()
    private val u1.column: Int get() = g()
    private val u1.showsSomething: Boolean
        get() {
            if (s() && f() != CELL_BLANK) return true
            val style: f2 = d() ?: return false
            return style.r().toInt() != 0 || style.j().toInt() != 0 || style.l().toInt() != 0 ||
                style.n().toInt() != 0 || style.h().toInt() != 0
        }
}
