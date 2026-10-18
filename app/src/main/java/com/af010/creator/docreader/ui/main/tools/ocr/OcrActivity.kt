package com.af010.creator.docreader.ui.main.tools.ocr

import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityOcrBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.FileChanger
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.setChecked
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.showSetupStep
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject
import java.io.File

/** Shows what OCR does to a scanned PDF before it runs: the result is a new, searchable copy. */
class OcrActivity : BaseAppActivity<ActivityOcrBinding>(ActivityOcrBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private var source: File = File("")

    private val changer = FileChanger(this, Tool.Ocr) {
        source = it
        binding.showSource()
    }

    override fun backPressed() = finish()

    override fun initialize() {
        source = File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first())
    }

    override fun ActivityOcrBinding.setData() {
        topBar.show(Tool.Ocr, R.string.fo_ocr_title)
        steps.showSetupStep(R.string.fo_step_find_words)
        file.btnChange.isVisible = true
        // The only output there is: shown as a choice so it is clear what comes out.
        optionSearchable.show(R.string.fo_ocr_option, getString(R.string.fo_ocr_option_desc))
        optionSearchable.setChecked(true)
        action.show(getString(R.string.fo_find_the_text), R.drawable.ic_fo_scan)
        showSource()
    }

    override fun ActivityOcrBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        file.btnChange.clickSingle { changer.launch() }
        action.btnAction.clickSingle {
            ProcessingActivity.start(this@OcrActivity, ToolRequest.Ocr(source.path))
            finish()
        }
    }

    private fun ActivityOcrBinding.showSource() {
        file.show(source, source.sizeText(this@OcrActivity))
        val shown = source
        launchMain {
            val pages = runCatching { toolbox.pageCount(shown) }.getOrNull() ?: return@launchMain
            if (shown != source) return@launchMain
            file.tvMeta.text = getString(R.string.document_meta, pageCountText(pages), source.sizeText(this@OcrActivity))
        }
    }
}
