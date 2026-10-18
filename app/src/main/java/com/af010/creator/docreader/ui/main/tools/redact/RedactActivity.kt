package com.af010.creator.docreader.ui.main.tools.redact

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityRedactBinding
import com.af010.creator.docreader.model.RedactArea
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.dialog.TextInputDialog
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrInvisible
import java.io.File
import org.koin.android.ext.android.inject

/** Mark areas to black out, page by page, by dragging boxes or by finding text. */
class RedactActivity : BaseAppActivity<ActivityRedactBinding>(ActivityRedactBinding::inflate) {
    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var pageCount = 0
    private var pageIndex = 0
    private var pageBitmap: Bitmap? = null

    /** In the order they were added, so Undo removes the latest. */
    private var areas: List<RedactArea> = emptyList()

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityRedactBinding.setData() {
        topBar.show(Tool.Redact, R.string.tool_redact, " ")
        topBar.btnSecond.setImageResource(R.drawable.ic_fo_search)
        topBar.btnSecond.contentDescription = getString(R.string.redact_find_text)
        topBar.btnSecond.isVisible = true
        topBar.btnFirst.setImageResource(R.drawable.ic_fo_undo)
        topBar.btnFirst.contentDescription = getString(R.string.undo)
        topBar.btnFirst.isVisible = true
        action.show(getString(R.string.fo_hide_for_good), R.drawable.ic_fo_lines)

        launchMain {
            pageCount = runCatching { toolbox.pageCount(source) }.getOrDefault(0)
            if (pageCount <= 0) {
                toast(R.string.error_file_open_generic)
                finish()
                return@launchMain
            }
            showPage(0)
        }
        render()
    }

    override fun ActivityRedactBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        topBar.btnFirst.click {
            areas = areas.dropLast(1)
            render()
        }
        btnPrevious.click { showPage(pageIndex - 1) }
        btnNext.click { showPage(pageIndex + 1) }
        pageView.onBoxDrawn = { box ->
            areas = areas + RedactArea(pageIndex, box.left, box.top, box.right, box.bottom)
            render()
        }
        topBar.btnSecond.clickSingle {
            TextInputDialog(
                this@RedactActivity, R.string.redact_find_text, R.string.redact_find_hint, R.string.redact_find
            ) { query -> redactText(query) }.show()
        }
        action.btnAction.clickSingle {
            if (areas.isEmpty()) return@clickSingle
            ProcessingActivity.start(this@RedactActivity, ToolRequest.Redact(source.path, areas))
            finish()
        }
    }

    private fun showPage(index: Int) {
        if (index !in 0 until pageCount) return
        pageIndex = index
        pageBitmap = null
        render()
        launchMain {
            val bitmap = runCatching { toolbox.renderPage(source, index, PAGE_WIDTH) }.getOrNull()
            // Another page may have been asked for while this one was drawn.
            if (pageIndex == index) {
                pageBitmap = bitmap
                render()
            }
        }
    }

    /** Marks every place [query] appears in the document. */
    private fun redactText(query: String) {
        launchMain {
            val found = (0 until pageCount).flatMap { page ->
                runCatching { toolbox.findText(source, page, query) }.getOrDefault(emptyList())
                    .map { RedactArea(page, it.left, it.top, it.right, it.bottom) }
            }
            if (found.isEmpty()) {
                toast(R.string.no_result_found)
            } else {
                areas = areas + found
                toast(resources.getQuantityString(R.plurals.redact_found, found.size, found.size))
                render()
            }
        }
    }

    private fun render() = with(binding) {
        topBar.tvSubtitle.text = getString(
            R.string.fo_redact_subtitle, areas.size,
            getString(R.string.fo_page_x_of_y, pageIndex + 1, pageCount.coerceAtLeast(1))
        )
        pageView.setPage(
            pageBitmap,
            areas.filter { it.page == pageIndex }.map { RectF(it.left, it.top, it.right, it.bottom) }
        )
        btnPrevious.visibleOrInvisible(pageIndex > 0)
        btnNext.visibleOrInvisible(pageIndex < pageCount - 1)
        topBar.btnFirst.isEnabled = areas.isNotEmpty()
        topBar.btnFirst.alpha = if (areas.isEmpty()) 0.4f else 1f
        // The hint goes once the first box is drawn.
        tvHint.isVisible = areas.isEmpty()
        action.btnAction.isEnabled = areas.isNotEmpty()
    }

    private companion object {
        const val PAGE_WIDTH = 1000
    }
}
