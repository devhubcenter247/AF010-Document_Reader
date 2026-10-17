package com.af010.creator.docreader.ui.main.tools.compress

import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityCompressBinding
import com.af010.creator.docreader.model.CompressLevel
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
import org.koin.android.ext.android.inject
import java.io.File

class CompressActivity : BaseAppActivity<ActivityCompressBinding>(ActivityCompressBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }
    private var level = CompressLevel.Medium

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityCompressBinding.setData() {
        header.show(Tool.Compress, R.string.compress_pdf, R.string.compress_subtitle)
        file.show(source, source.sizeText(this@CompressActivity))
        launchMain {
            val pages = runCatching { toolbox.pageCount(source) }.getOrNull() ?: return@launchMain
            file.tvMeta.text = getString(
                R.string.document_meta, pageCountText(pages),
                source.sizeText(this@CompressActivity)
            )
        }
        options().forEach { (option, optionLevel) ->
            option.show(optionLevel.title, getString(optionLevel.desc))
        }
        action.btnAction.setText(R.string.compress_pdf)
        render()
    }

    override fun ActivityCompressBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        options().forEach { (option, optionLevel) ->
            option.root.click {
                level = optionLevel
                render()
            }
        }
        action.btnAction.clickSingle {
            ProcessingActivity.start(
                this@CompressActivity,
                ToolRequest.Compress(source.path, level)
            )
            finish()
        }
    }

    private fun ActivityCompressBinding.options() = listOf(
        optionLow to CompressLevel.Low,
        optionMedium to CompressLevel.Medium,
        optionHigh to CompressLevel.High
    )

    private fun ActivityCompressBinding.render() {
        options().forEach { (option, optionLevel) -> option.setChecked(optionLevel == level) }
    }
}
