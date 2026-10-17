package com.af010.creator.docreader.ui.main.reader.pdf

import android.graphics.Bitmap
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutPdfContentsBinding
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfBookmark
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfReadingPrefs
import com.af010.creator.docreader.ui.main.tools.split.PageThumbAdapter
import com.reader.pdfviewer.PDFView
import com.reader.pdfviewer.pdfium.PdfDocument
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.visibleOrGone

/** The outline of the open PDF, the reader's own bookmarks and the pages as thumbnails. */
internal class PdfContentsPanel(
    private val binding: LayoutPdfContentsBinding,
    private val pdfView: PDFView,
    private val prefs: PdfReadingPrefs,
    private val path: String,
    loadThumbnail: (page: Int, onReady: (Bitmap) -> Unit) -> Unit,
    private val onJump: (page: Int) -> Unit
) {
    private enum class Tab { Contents, Bookmarks, Pages }

    private val context get() = binding.root.context
    private val tocAdapter = TocAdapter()
    private val pageAdapter = PageThumbAdapter(loadThumbnail)
    private var tab = Tab.Contents

    init {
        binding.segment.setUp(R.string.fo_outline, R.string.fo_bookmarks, R.string.tool_pages) { index ->
            tab = Tab.entries[index]
            render()
        }
        tocAdapter
            .onRowClick { row -> onJump(row.page) }
            .onRemoveClick { row ->
                prefs.setBookmarks(path, prefs.bookmarks(path).filter { it.page != row.page })
                render()
            }
        pageAdapter.onPageClick { page -> onJump(page) }
        binding.btnAddBookmark.click {
            val page = pdfView.currentPage
            val marks = prefs.bookmarks(path)
            if (marks.none { it.page == page }) {
                val mark = PdfBookmark(page, context.getString(R.string.page_number, page + 1))
                prefs.setBookmarks(path, (marks + mark).sortedBy { it.page })
            }
            render()
        }
    }

    /** Thumbnails were drawn from the file before it changed. */
    fun refreshPages() {
        pageAdapter.notifyItemRangeChanged(0, pageAdapter.itemCount)
    }

    fun render() = with(binding) {
        val marks = prefs.bookmarks(path)
        val page = pdfView.currentPage
        segment.select(tab.ordinal)
        btnAddBookmark.visibleOrGone(tab == Tab.Bookmarks)
        btnAddBookmark.text = context.getString(R.string.bookmark_add, page + 1)

        when (tab) {
            Tab.Contents -> {
                val rows = outline()
                tocAdapter.currentRow = rows.indexOfLast { it.page <= page }
                showRows(rows, R.string.contents_none)
            }
            Tab.Bookmarks -> {
                val rows = marks.map { TocRow(it.label, it.page, removable = true) }
                tocAdapter.currentRow = rows.indexOfFirst { it.page == page }
                showRows(rows, R.string.bookmarks_none)
            }
            Tab.Pages -> {
                if (rcvContents.adapter !== pageAdapter) {
                    rcvContents.layoutManager = GridLayoutManager(context, PAGE_COLUMNS)
                    rcvContents.adapter = pageAdapter
                    pageAdapter.submitList((0 until pdfView.pageCount).toList())
                    rcvContents.scrollToPosition(page)
                }
                pageAdapter.selectedPages = setOf(page)
                tvContentsEmpty.visibleOrGone(false)
            }
        }
    }

    private fun showRows(rows: List<TocRow>, emptyMessage: Int) = with(binding) {
        if (rcvContents.adapter !== tocAdapter) {
            rcvContents.layoutManager = LinearLayoutManager(context)
            rcvContents.adapter = tocAdapter
        }
        tocAdapter.submitList(rows)
        tvContentsEmpty.setText(emptyMessage)
        tvContentsEmpty.visibleOrGone(rows.isEmpty())
    }

    private fun outline(): List<TocRow> {
        val rows = ArrayList<TocRow>()
        fun walk(list: List<PdfDocument.Bookmark?>, depth: Int) {
            for (item in list) {
                item ?: continue
                rows += TocRow(item.title.orEmpty(), item.pageIdx.toInt().coerceAtLeast(0), depth)
                if (item.hasChildren()) walk(item.children, depth + 1)
            }
        }
        runCatching { walk(pdfView.tableOfContents.orEmpty(), 0) }
        return rows
    }

    private companion object {
        const val PAGE_COLUMNS = 3
    }
}
