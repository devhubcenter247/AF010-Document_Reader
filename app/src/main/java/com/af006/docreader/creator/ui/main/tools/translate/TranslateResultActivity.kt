package com.af010.creator.docreader.ui.main.tools.translate

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityTranslateResultBinding
import com.af010.creator.docreader.databinding.ItemTranslatedPageBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolResult
import com.af010.creator.docreader.pdf.PdfTranslator
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.pdf.TranslatedDocument
import com.af010.creator.docreader.pdf.TranslatedPage
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.main.tools.result.SuccessActivity
import com.af010.creator.docreader.utils.AppUtils
import com.d010.pdf.reader.base.shareFile
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import org.koin.android.ext.android.inject
import java.io.File

/** Reads a finished translation as text, next to the original, and saves or shares it as a PDF. */
class TranslateResultActivity :
    BaseAppActivity<ActivityTranslateResultBinding>(ActivityTranslateResultBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private enum class Mode { Original, Translation, SideBySide }

    private val translator: PdfTranslator by inject()
    private val repository: DocumentRepository by inject()

    private var document: TranslatedDocument? = null
    private val pageAdapter by lazy { TranslatedPageAdapter() }

    /** Base name of the saved PDF. */
    private val outputName: String
        get() = File(document?.sourcePath.orEmpty()).nameWithoutExtension + "-translated"

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        document = runCatching {
            TranslatedDocument.load(File(intent.getStringExtra(EXTRA_FILE).orEmpty()))
        }.getOrNull()
    }

    override fun ActivityTranslateResultBinding.setData() {
        val document = document ?: run {
            toast(R.string.translate_failed)
            return finish()
        }
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.text = File(document.sourcePath).name
        toolbar.tvSubtitle.visible()
        toolbar.tvSubtitle.text =
            getString(R.string.translated_pages, document.pages.size, document.totalPages)
        toolbar.btnExtra.setImageResource(R.drawable.ic_app_share)
        toolbar.btnExtra.visible()
        toolbar.btnSearch.setImageResource(R.drawable.ic_app_save)
        toolbar.btnSearch.contentDescription = getString(R.string.file_action_save)
        toolbar.btnSearch.visible()

        rcvPages.adapter = pageAdapter
        pageAdapter.submitList(document.pages)
        segmentMode.setUp(R.string.scanner_original, R.string.translation, R.string.side_by_side) { index ->
            showMode(Mode.entries[index])
        }
        showMode(Mode.Translation)

        val remaining = document.totalPages - document.pages.size
        cardUnlock.visibleOrGone(remaining > 0)
        tvUnlockTitle.text =
            resources.getQuantityString(R.plurals.translate_unlock_title, remaining, remaining)
    }

    override fun ActivityTranslateResultBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        // TODO: translate the remaining pages once the purchase exists; for now this shows the offer.
        btnUnlock.clickSingle { PaywallActivity.start(this@TranslateResultActivity, Tool.Translate) }
        toolbar.btnExtra.clickSingle {
            writePdf(File(cacheDir, "translate/$outputName.pdf")) { file ->
                if (!shareFile(file.path)) toast(R.string.file_share_failed)
            }
        }
        toolbar.btnSearch.clickSingle {
            launchMain {
                val directory = File(AppUtils.ensureDocumentDirectory())
                writePdf(ToolRunner.uniqueFile(directory, outputName, "pdf")) { file ->
                    AppUtils.notifyMediaScanner(this@TranslateResultActivity, file.path)
                    repository.refresh()
                    SuccessActivity.start(
                        this@TranslateResultActivity, ToolResult(Tool.Translate, listOf(file.path))
                    )
                    finish()
                }
            }
        }
    }

    private fun ActivityTranslateResultBinding.showMode(mode: Mode) {
        segmentMode.select(mode.ordinal)
        pageAdapter.showOriginal = mode != Mode.Translation
        pageAdapter.showTranslated = mode != Mode.Original
        pageAdapter.notifyItemRangeChanged(0, pageAdapter.itemCount)
    }

    private fun writePdf(output: File, onWritten: (File) -> Unit) {
        val document = document ?: return
        launchMain {
            runCatching { translator.write(document, output) }
                .onSuccess { onWritten(output) }
                .onFailure { toast(R.string.translate_save_failed) }
        }
    }

    private class TranslatedPageAdapter : BaseAdapter<TranslatedPage, ItemTranslatedPageBinding>() {
        var showOriginal = false
        var showTranslated = true

        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemTranslatedPageBinding.inflate(inflater, parent, false)

        override fun ItemTranslatedPageBinding.bind(item: TranslatedPage, position: Int) {
            tvPage.text = root.context.getString(R.string.page_number, position + 1)
            tvOriginal.text = item.original.joinToString("\n\n")
            tvTranslated.text = item.translated.joinToString("\n\n")
            tvOriginal.visibleOrGone(showOriginal)
            tvTranslated.visibleOrGone(showTranslated)
            spaceBetween.visibleOrGone(showOriginal && showTranslated)
        }
    }

    companion object {
        private const val EXTRA_FILE = "EXTRA_FILE"

        /** [file] is the translation saved by the tool runner. */
        fun start(context: Context, file: String) {
            context.startActivity(
                Intent(context, TranslateResultActivity::class.java).putExtra(EXTRA_FILE, file)
            )
        }
    }
}
