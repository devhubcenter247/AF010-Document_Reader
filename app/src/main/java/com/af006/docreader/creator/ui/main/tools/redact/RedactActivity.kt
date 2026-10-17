package com.af010.creator.docreader.ui.main.tools.redact

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityRedactBinding
import com.af010.creator.docreader.model.RedactArea
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.dialog.TextInputDialog
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrInvisible
import java.io.File
import org.koin.android.ext.android.inject

/** Mark areas to black out, page by page, by dragging boxes or by finding text. */
class RedactActivity : BaseAppActivity<ActivityRedactBinding>(ActivityRedactBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

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
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.btnBack.setImageResource(R.drawable.ic_app_close)
        toolbar.tvTitle.setText(R.string.tool_redact)
        toolbar.tvSubtitle.visible()
        toolbar.btnExtra.setImageResource(R.drawable.ic_app_undo)
        toolbar.btnExtra.visible()
        toolbar.btnAction.setText(R.string.apply)
        toolbar.btnAction.setFillColor(getColor(R.color.color_ink))
        toolbar.btnAction.visible()

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
        toolbar.btnBack.clickSingle { finish() }
        toolbar.btnExtra.click {
            areas = areas.dropLast(1)
            render()
        }
        btnPrevious.click { showPage(pageIndex - 1) }
        btnNext.click { showPage(pageIndex + 1) }
        pageView.onBoxDrawn = { box ->
            areas = areas + RedactArea(pageIndex, box.left, box.top, box.right, box.bottom)
            render()
        }
        btnFindText.clickSingle {
            TextInputDialog(
                this@RedactActivity, R.string.redact_find_text, R.string.redact_find_hint, R.string.redact_find
            ) { query -> redactText(query) }.show()
        }
        toolbar.btnAction.clickSingle {
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
        toolbar.tvSubtitle.text = getString(R.string.page_of, pageIndex + 1, pageCount.coerceAtLeast(1))
        pageView.setPage(
            pageBitmap,
            areas.filter { it.page == pageIndex }.map { RectF(it.left, it.top, it.right, it.bottom) }
        )
        btnPrevious.visibleOrInvisible(pageIndex > 0)
        btnNext.visibleOrInvisible(pageIndex < pageCount - 1)
        toolbar.btnExtra.alpha = if (areas.isEmpty()) 0.4f else 1f
        toolbar.btnAction.alpha = if (areas.isEmpty()) 0.5f else 1f
    }

    private companion object {
        const val PAGE_WIDTH = 1000
    }
}
