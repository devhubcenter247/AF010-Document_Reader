package com.af010.creator.docreader.ui.main.tools.translate

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityTranslateResultBinding
import com.af010.creator.docreader.databinding.ItemPageNumberBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolResult
import com.af010.creator.docreader.pdf.PdfTranslator
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.pdf.TranslatedDocument
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.main.tools.result.SuccessActivity
import com.af010.creator.docreader.utils.AppUtils
import com.d010.pdf.reader.base.shareFile
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject
import java.io.File

/** Reads a finished translation as text, next to the original, and saves or shares it as a PDF. */
class TranslateResultActivity :
    BaseAppActivity<ActivityTranslateResultBinding>(ActivityTranslateResultBinding::inflate) {

    private val translator: PdfTranslator by inject()
    private val repository: DocumentRepository by inject()

    private var document: TranslatedDocument? = null
    private val numberAdapter by lazy { PageNumberAdapter() }
    private var page = 0
    private var showOriginal = false

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
        topBar.tvTitle.setText(R.string.fo_your_translation)
        val target = document.to.takeIf { it.isNotEmpty() }?.let(translator::languageName)
        segmentMode.setUp(R.string.translation, R.string.scanner_original) { index ->
            showOriginal = index == 1
            showPage()
        }
        segmentMode.tvFirst.text = target ?: getString(R.string.translation)
        rcvPages.adapter = numberAdapter
        numberAdapter.submitList(document.pages.indices.toList())
        numberAdapter.onNumberClick {
            page = it
            showPage()
        }

        val remaining = document.totalPages - document.pages.size
        btnMore.isVisible = remaining > 0
        btnMore.text = resources.getQuantityString(R.plurals.fo_more_pages, remaining, remaining)
        showPage()
    }

    override fun ActivityTranslateResultBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        // TODO: translate the remaining pages once the purchase exists; for now this shows the offer.
        btnMore.clickSingle { PaywallActivity.start(this@TranslateResultActivity, Tool.Translate) }
        btnShare.clickSingle {
            writePdf(File(cacheDir, "translate/$outputName.pdf")) { file ->
                if (!shareFile(file.path)) toast(R.string.file_share_failed)
            }
        }
        btnSave.clickSingle {
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

    /** Shows the text of [page], translated or original, and where it sits in the document. */
    private fun showPage() = with(binding) {
        val document = document ?: return@with
        val current = document.pages.getOrNull(page) ?: return@with
        segmentMode.select(if (showOriginal) 1 else 0)
        val lines = if (showOriginal) current.original else current.translated
        // A page drawn as a picture has no text to show.
        tvText.text = if (lines.isEmpty()) getString(R.string.fo_page_no_text) else lines.joinToString("\n\n")
        tvText.setTextColor(getColor(if (lines.isEmpty()) R.color.text_secondary else R.color.text_primary))
        scrollPage.scrollTo(0, 0)
        numberAdapter.selected = page
        val where = getString(R.string.fo_page_x_of_y, page + 1, document.pages.size)
        topBar.tvSubtitle.isVisible = true
        topBar.tvSubtitle.text = if (document.from.isNotEmpty() && document.to.isNotEmpty()) {
            getString(
                R.string.fo_translation_route,
                translator.languageName(document.from), translator.languageName(document.to), where
            )
        } else where
    }

    private fun writePdf(output: File, onWritten: (File) -> Unit) {
        val document = document ?: return
        launchMain {
            runCatching { translator.write(document, output) }
                .onSuccess { onWritten(output) }
                .onFailure { toast(R.string.translate_save_failed) }
        }
    }

    /** The translated pages by number; [selected] is the one on screen. */
    private class PageNumberAdapter : BaseAdapter<Int, ItemPageNumberBinding>() {
        var selected = 0
            set(value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }

        private var onItemClick: (Int) -> Unit = {}
        fun onNumberClick(listener: (Int) -> Unit) = apply { onItemClick = listener }

        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemPageNumberBinding.inflate(inflater, parent, false)

        override fun ItemPageNumberBinding.bind(item: Int, position: Int) {
            tvNumber.text = (item + 1).toString()
            tvNumber.isSelected = item == selected
            tvNumber.contentDescription = root.context.getString(R.string.page_number, item + 1)
            tvNumber.setOnClickListener { onItemClick(item) }
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
