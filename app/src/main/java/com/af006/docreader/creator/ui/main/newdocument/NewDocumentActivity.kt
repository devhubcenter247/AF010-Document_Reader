package com.af010.creator.docreader.ui.main.newdocument

import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityNewDocumentBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.iconRes
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.af010.creator.docreader.utils.extensions.setStroke
import com.editor.docsdk.DocumentType as SdkDocumentType
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.utils.dpToPx
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.android.ext.android.inject

/** Name and location of a new blank Word / Excel / PowerPoint file; [EXTRA_TYPE] preselects the type. */
class NewDocumentActivity :
    BaseAppActivity<ActivityNewDocumentBinding>(ActivityNewDocumentBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val toolbox: PdfToolbox by inject()
    private val repository: DocumentRepository by inject()

    private var type = DocumentType.Doc
    private var saveDirectory: String? = null

    private val DocumentType.extension: String
        get() = when (this) {
            DocumentType.Excel -> "xlsx"
            DocumentType.Ppt -> "pptx"
            else -> "docx"
        }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        val name = intent.getStringExtra(EXTRA_TYPE)
        type = DocumentType.entries.firstOrNull { it.name == name } ?: DocumentType.Doc
    }

    override fun ActivityNewDocumentBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.setText(R.string.new_document)
        toolbar.btnBack.setImageResource(R.drawable.ic_app_close)

        val date = SimpleDateFormat("MMM-dd", Locale.ENGLISH).format(Date())
        edtName.setText(getString(R.string.untitled).plus("-").plus(date))
        edtName.setSelection(edtName.text.length)

        launchIO {
            val directory = AppUtils.ensureDocumentDirectory()
            withMain {
                saveDirectory = directory
                tvSaveTo.text = directory.split('/').takeLast(2).joinToString(" / ")
            }
        }
        render()
    }

    override fun ActivityNewDocumentBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        mapOf(
            cardWord to DocumentType.Doc,
            cardExcel to DocumentType.Excel,
            cardPpt to DocumentType.Ppt
        ).forEach { (card, cardType) ->
            card.click {
                type = cardType
                render()
            }
        }
        btnCreate.clickSingle { create() }
    }

    private fun ActivityNewDocumentBinding.render() {
        val context = root.context
        mapOf(
            cardWord to DocumentType.Doc,
            cardExcel to DocumentType.Excel,
            cardPpt to DocumentType.Ppt
        ).forEach { (card, cardType) ->
            val selected = cardType == type
            card.setFillColor(ContextCompat.getColor(
                context,
                if (selected) R.color.color_bg_selected else R.color.color_surface_card
            ))
            card.setStroke(context.dpToPx(if (selected) 2 else 1), ContextCompat.getColor(
                context,
                if (selected) R.color.primary else R.color.color_stroke
            ))
            card.invalidate()
        }
        tvExtension.text = ".".plus(type.extension)
        ivPreview.setImageResource(type.iconRes)
        tvPreviewTitle.setText(
            when (type) {
                DocumentType.Excel -> R.string.blank_spreadsheet
                DocumentType.Ppt -> R.string.blank_presentation
                else -> R.string.blank_document
            }
        )
    }

    private fun ActivityNewDocumentBinding.create() {
        val name = edtName.text.toString().trim()
        val directory = saveDirectory ?: return
        when {
            name.isEmpty() -> toast(R.string.file_rename_empty)
            name.contains('/') -> toast(R.string.file_rename_invalid)
            File(directory, "$name.${type.extension}").exists() -> toast(R.string.file_name_exists)
            else -> createDocument(File(directory, "$name.${type.extension}"))
        }
    }

    private fun createDocument(output: File) {
        val sdkType = when (type) {
            DocumentType.Excel -> SdkDocumentType.EXCEL
            DocumentType.Ppt -> SdkDocumentType.POWERPOINT
            else -> SdkDocumentType.WORD
        }
        launchMain {
            runCatching { toolbox.createDocument(sdkType, output) }
                .onSuccess { file ->
                    AppUtils.notifyMediaScanner(this@NewDocumentActivity, file.path)
                    repository.refresh()
                    OfficeActivity.start(this@NewDocumentActivity, file.path, edit = true)
                    finish()
                }
                .onFailure { toast(R.string.error_file_storage) }
        }
    }

    companion object {
        const val EXTRA_TYPE = "EXTRA_TYPE"
    }
}
