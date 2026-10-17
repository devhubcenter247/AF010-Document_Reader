package com.af010.creator.docreader.ui.main.tools.compress

import android.text.format.Formatter
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityCompressBinding
import com.af010.creator.docreader.model.CompressLevel
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
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject
import java.io.File

/** Picks how hard to squeeze the PDF, with a rough size it will end up at. */
class CompressActivity : BaseAppActivity<ActivityCompressBinding>(ActivityCompressBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private var source: File = File("")
    private var level = CompressLevel.Medium

    private val changer = FileChanger(this, Tool.Compress) {
        source = it
        binding.showSource()
    }

    override fun backPressed() = finish()

    override fun initialize() {
        source = File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first())
    }

    override fun ActivityCompressBinding.setData() {
        topBar.show(Tool.Compress, R.string.compress_pdf)
        steps.showSetupStep(R.string.fo_step_how_small)
        file.btnChange.isVisible = true
        action.show(getString(R.string.fo_make_it_smaller), R.drawable.ic_fo_compress)
        options().forEach { (option, optionLevel) ->
            option.show(optionLevel.title, getString(optionLevel.desc), "−${100 - optionLevel.keeps}%")
        }
        showSource()
    }

    override fun ActivityCompressBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        file.btnChange.clickSingle { changer.launch() }
        options().forEach { (option, optionLevel) ->
            option.root.click {
                level = optionLevel
                render()
            }
        }
        action.btnAction.clickSingle {
            ProcessingActivity.start(this@CompressActivity, ToolRequest.Compress(source.path, level))
            finish()
        }
    }

    private fun ActivityCompressBinding.showSource() {
        file.show(source, source.sizeText(this@CompressActivity))
        tvBefore.text = source.sizeText(this@CompressActivity)
        render()
        val shown = source
        launchMain {
            val pages = runCatching { toolbox.pageCount(shown) }.getOrNull() ?: return@launchMain
            if (shown != source) return@launchMain
            file.tvMeta.text = getString(
                R.string.document_meta, pageCountText(pages), source.sizeText(this@CompressActivity)
            )
        }
    }

    private fun ActivityCompressBinding.options() = listOf(
        optionLow to CompressLevel.Low,
        optionMedium to CompressLevel.Medium,
        optionHigh to CompressLevel.High
    )

    private fun ActivityCompressBinding.render() {
        options().forEach { (option, optionLevel) -> option.setChecked(optionLevel == level) }
        tvAfter.text = Formatter.formatShortFileSize(this@CompressActivity, source.length() * level.keeps / 100)
        viewBarFill.updateLayoutParams<LinearLayout.LayoutParams> { weight = level.keeps.toFloat() }
    }
}
