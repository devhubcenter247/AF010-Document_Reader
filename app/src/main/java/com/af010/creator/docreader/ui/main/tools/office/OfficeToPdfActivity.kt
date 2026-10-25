package com.af010.creator.docreader.ui.main.tools.office

import androidx.core.content.ContextCompat
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityOfficeToPdfBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.OfficeWriter
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.pdf.ToolState
import com.af010.creator.docreader.ui.dialog.MessageDialog
import com.af010.creator.docreader.ui.dialog.OpenPasswordDialog
import com.af010.creator.docreader.ui.main.reader.office.OfficePdf
import com.af010.creator.docreader.ui.main.reader.office.office
import com.af010.creator.docreader.ui.main.reader.office.openMessage
import com.af010.creator.docreader.ui.main.reader.office.paintBehindPages
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.FileChanger
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.showSetupStep
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.editor.docsdk.DocumentException
import com.editor.docsdk.DocumentType
import com.editor.docsdk.DocumentView
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import com.wxiwei.office.reader.ReaderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import com.intuit.sdp.R as SdpR
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Office to PDF: shows the Word, Excel or PowerPoint file as it will be in the PDF and converts it
 * when the user taps the button. The pages are drawn from the document open here, so this screen
 * stays under Processing until the PDF is written, then closes.
 */
class OfficeToPdfActivity : BaseAppActivity<ActivityOfficeToPdfBinding>(ActivityOfficeToPdfBinding::inflate) {

    private val runner: ToolRunner by inject()
    private var source: File = File("")
    private var password: String? = null
    private var loaded = false

    /** Handed to [runner] on Convert; dropped here if the job never took it. */
    private var writer: OfficeWriter? = null
    private var readerJob: Job? = null
    private var doneJob: Job? = null

    private val documentView: DocumentView get() = binding.documentView

    private val changer = FileChanger(this, Tool.OfficeToPdf) {
        source = it
        password = null
        open()
    }

    override fun backPressed() = finish()

    override fun initialize() {
        source = File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first())
    }

    override fun ActivityOfficeToPdfBinding.setData() {
        topBar.show(Tool.OfficeToPdf)
        steps.showSetupStep(R.string.fo_step_check_pages)
        file.btnChange.isVisible = true
        action.show(getString(R.string.convert_to_pdf), Tool.OfficeToPdf.glyph)
        action.tvNote.setText(R.string.fo_office_pdf_note)
        action.tvNote.isVisible = true
        documentView.listener = documentListener
        open()
    }

    override fun ActivityOfficeToPdfBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        file.btnChange.clickSingle { changer.launch() }
        action.btnAction.clickSingle { convert() }
    }

    private fun open() = with(binding) {
        loaded = false
        readerJob?.cancel()
        file.show(source, source.sizeText(this@OfficeToPdfActivity))
        pagerPages.adapter = null
        tvPagePill.isInvisible = true
        tvPreviewCount.text = ""
        tvEmpty.gone()
        progress.visible()
        render()
        documentView.open(source, password)
    }

    /** Convert once the pages are on screen: what the user checked is what the PDF gets. */
    private fun render() {
        val ready = loaded && (binding.pagerPages.adapter?.itemCount ?: 0) > 0
        binding.action.btnAction.isEnabled = ready
        binding.action.btnAction.alpha = if (ready) 1f else DISABLED_ALPHA
    }

    private val documentListener = object : DocumentView.Listener {
        override fun onLoaded(pageCount: Int) {
            if (isFinishing || isDestroyed) return
            loaded = true
            binding.file.tvMeta.text = getString(
                R.string.document_meta, countText(pageCount), source.sizeText(this@OfficeToPdfActivity)
            )
            showPages()
        }

        override fun onError(error: DocumentException) {
            if (isFinishing || isDestroyed) return
            binding.progress.gone()
            when (error.reason) {
                DocumentException.Reason.PASSWORD_REQUIRED,
                DocumentException.Reason.PASSWORD_INCORRECT -> askPassword()
                else -> MessageDialog(this@OfficeToPdfActivity, R.string.cant_open_file, error.reason.openMessage, R.string.fo_okay, alert = true).apply {
                    setOnDismissListener { if (!isFinishing) finish() }
                    show()
                }
            }
        }
    }

    /** "12 pages", "8 slides" or "3 sheets": what the file is made of. */
    private fun countText(count: Int): String = when (documentView.documentType) {
        DocumentType.POWERPOINT -> resources.getQuantityString(R.plurals.fo_slide_count, count, count)
        DocumentType.EXCEL -> resources.getQuantityString(R.plurals.fo_sheet_count, count, count)
        else -> pageCountText(count)
    }

    /** Once the document is laid out, its PDF pages side by side, drawn as they come into view. */
    private fun showPages() {
        readerJob?.cancel()
        val office = documentView.office ?: return
        readerJob = lifecycleScope.launch {
            office.state.first { it.status == ReaderState.Status.Ready }
            office.paintBehindPages(ContextCompat.getColor(this@OfficeToPdfActivity, R.color.color_surface_track))
            val preview = OfficePdf.preview(office)
            binding.progress.gone()
            if (preview == null || preview.count == 0) {
                binding.tvEmpty.visible()
                render()
                return@launch
            }
            binding.pagerPages.awaitLayout()
            setUpPager(preview)
            render()
        }
    }

    private suspend fun View.awaitLayout() {
        if (isLaidOut && width > 0) return
        suspendCancellableCoroutine { done -> doOnLayout { done.resume(Unit) } }
    }

    private fun setUpPager(preview: OfficePdf.Preview) = with(binding) {
        // The pages next to the one in the middle peek in from the sides; a single page takes the width.
        val side = resources.getDimensionPixelSize(if (preview.count > 1) SdpR.dimen._34sdp else SdpR.dimen._9sdp)
        (pagerPages.getChildAt(0) as RecyclerView).apply {
            setPadding(side, 0, side, 0)
            clipToPadding = false
        }
        pagerPages.offscreenPageLimit = 1
        pagerPages.setPageTransformer { page, position ->
            val away = abs(position).coerceAtMost(1f)
            page.scaleX = 1f - NEIGHBOUR_SHRINK * away
            page.scaleY = 1f - NEIGHBOUR_SHRINK * away
            page.alpha = 1f - NEIGHBOUR_FADE * away
        }
        pagerPages.adapter = PdfPageAdapter(preview, lifecycleScope, width = pagerPages.width - 2 * side)
        val count = preview.count
        // The count when the file opened can be short: Word pages and slides keep coming in after.
        if (documentView.documentType != DocumentType.EXCEL) {
            file.tvMeta.text = getString(R.string.document_meta, countText(count), source.sizeText(this@OfficeToPdfActivity))
        }
        tvPreviewCount.text = if (count > 1) getString(R.string.fo_swipe_to_see_all, pageCountText(count)) else pageCountText(count)
        tvPagePill.isInvisible = count < 2
        showPagePill(0, count)
        pagerPages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = showPagePill(position, count)
        })
    }

    private fun showPagePill(index: Int, count: Int) {
        binding.tvPagePill.text = getString(R.string.page_of, index + 1, count)
    }

    private fun askPassword() {
        var submitted = false
        OpenPasswordDialog(this, source, wrong = password != null) { entered ->
            submitted = true
            password = entered
            open()
        }.apply {
            setOnDismissListener { if (!submitted && !isFinishing) finish() }
            show()
        }
    }

    private fun convert() {
        val office = documentView.office
        if (!loaded || office == null || !OfficePdf.canWrite(office)) return toast(R.string.convert_pdf_not_ready)
        val path = source.path
        writer = OfficeWriter(path) { output, progress -> OfficePdf.write(office, output, progress) }
        runner.officeWriter = writer
        // Not tied to the lifecycle: this screen is stopped under Processing while the job runs.
        doneJob?.cancel()
        doneJob = lifecycleScope.launch {
            runner.state.first { it is ToolState.Done && it.result.tool == Tool.OfficeToPdf }
            finish()
        }
        ProcessingActivity.start(this, ToolRequest.OfficeToPdf(path, source.nameWithoutExtension))
    }

    override fun onDestroy() {
        if (runner.officeWriter === writer) runner.officeWriter = null
        readerJob?.cancel()
        documentView.listener = null
        documentView.close()
        super.onDestroy()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.5f
        const val NEIGHBOUR_SHRINK = 0.08f
        const val NEIGHBOUR_FADE = 0.4f
    }
}
