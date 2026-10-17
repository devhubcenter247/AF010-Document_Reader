package com.af010.creator.docreader.ui.main.tools.split

import android.graphics.Bitmap
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySplitBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.setChecked
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import org.koin.android.ext.android.inject
import java.io.File

class SplitActivity : BaseAppActivity<ActivitySplitBinding>(ActivitySplitBinding::inflate) {

    private enum class Mode { Ranges, EachPage, Pick }

    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var mode = Mode.Ranges
    private var pageCount = 0
    private var pickedPages: Set<Int> = emptySet()
    private val thumbnails = HashMap<Int, Bitmap>()

    private val rangeAdapter by lazy { PageRangeAdapter() }
    private val pageAdapter by lazy { PageThumbAdapter(::loadThumbnail) }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivitySplitBinding.setData() {
        header.show(Tool.Split, R.string.split_pdf, R.string.split_subtitle)
        file.show(source, source.sizeText(this@SplitActivity))
        rcvRanges.adapter = rangeAdapter
        rcvPages.adapter = pageAdapter
        render()

        launchMain {
            pageCount = runCatching { toolbox.pageCount(source) }.getOrDefault(0)
            if (pageCount <= 0) {
                toast(R.string.split_open_failed)
                finish()
                return@launchMain
            }
            file.tvMeta.text = getString(
                R.string.document_meta, pageCountText(pageCount), source.sizeText(this@SplitActivity)
            )
            rangeAdapter.submitList(listOf(PageRange(1, pageCount)))
            pageAdapter.submitList((0 until pageCount).toList())
            render()
        }
    }

    override fun ActivitySplitBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        mapOf(optionRanges to Mode.Ranges, optionEachPage to Mode.EachPage, optionPick to Mode.Pick)
            .forEach { (option, optionMode) ->
                option.root.click {
                    mode = optionMode
                    render()
                }
            }

        rangeAdapter
            .onChanged { render() }
            .onRemoveClick { position ->
                if (rangeAdapter.itemCount > 1) {
                    rangeAdapter.removeItem(position)
                    render()
                }
            }
        btnAddRange.click {
            // Start the new range right after the last one that is filled in.
            val next = rangeAdapter.currentItems.mapNotNull { it.to }.maxOrNull()
                ?.plus(1)?.coerceAtMost(pageCount) ?: 1
            rangeAdapter.addItem(PageRange(next, pageCount))
            render()
        }
        pageAdapter.onPageClick { page ->
            pickedPages = if (page in pickedPages) pickedPages - page else pickedPages + page
            pageAdapter.selectedPages = pickedPages
            render()
        }

        action.btnAction.clickSingle {
            val parts = parts() ?: return@clickSingle
            hideKeyboard()
            ProcessingActivity.start(this@SplitActivity, ToolRequest.Split(source.path, parts))
            finish()
        }
    }

    /** The zero-based pages of each file to create, or null while the choice is not complete. */
    private fun parts(): List<List<Int>>? = when (mode) {
        Mode.Ranges -> rangeAdapter.currentItems
            .takeIf { ranges -> ranges.isNotEmpty() && ranges.all { it.isValid(pageCount) } }
            ?.map { range -> (range.from!! - 1..range.to!! - 1).toList() }
        Mode.EachPage -> (0 until pageCount).map { listOf(it) }.takeIf { it.isNotEmpty() }
        Mode.Pick -> pickedPages.sorted().takeIf { it.isNotEmpty() }?.let { listOf(it) }
    }

    private fun ActivitySplitBinding.render() {
        optionRanges.show(R.string.split_by_range, getString(R.string.split_by_range_desc))
        optionEachPage.show(
            R.string.split_each_page,
            resources.getQuantityString(R.plurals.split_creates_files, pageCount, pageCount)
        )
        optionPick.show(R.string.split_pick_pages, getString(R.string.split_pick_pages_desc))
        optionRanges.setChecked(mode == Mode.Ranges)
        optionEachPage.setChecked(mode == Mode.EachPage)
        optionPick.setChecked(mode == Mode.Pick)
        layoutRanges.visibleOrGone(mode == Mode.Ranges)
        rcvPages.visibleOrGone(mode == Mode.Pick)

        val parts = parts()
        val count = parts?.size ?: 0
        action.btnAction.text = resources.getQuantityString(R.plurals.split_action, count, count)
        action.btnAction.isEnabled = parts != null
        action.btnAction.alpha = if (parts != null) 1f else 0.5f
    }

    private fun loadThumbnail(page: Int, onReady: (Bitmap) -> Unit) {
        thumbnails[page]?.let { return onReady(it) }
        launchMain {
            val bitmap = runCatching { toolbox.renderPage(source, page, THUMBNAIL_WIDTH) }.getOrNull()
                ?: return@launchMain
            thumbnails[page] = bitmap
            onReady(bitmap)
        }
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 240
    }
}
