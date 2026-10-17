package com.af010.creator.docreader.ui.main.reader.office.sheet

import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetSaveDocumentBinding
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.visibleOrGone

enum class SaveAction { Save, SaveCopy, ExportPdf, Share }

/** Where the edits go: the file itself, a copy, a PDF, or another app. [available] are the rows shown. */
class SaveDocumentBottomSheet(
    private val activity: FragmentActivity,
    private val fileName: String,
    private val available: Set<SaveAction>,
    private val onAction: (SaveAction) -> Unit
) : BaseBottomSheet<SheetSaveDocumentBinding>(activity, SheetSaveDocumentBinding::inflate) {

    override fun SheetSaveDocumentBinding.onBind() {
        header.tvTitle.setText(R.string.save_document)
        header.btnClose.click { dismissSafe() }
        tvSaveDesc.text = activity.getString(R.string.save_to_original_desc, fileName)

        mapOf(
            rowSave to SaveAction.Save,
            rowSaveCopy to SaveAction.SaveCopy,
            rowExportPdf to SaveAction.ExportPdf,
            rowShare to SaveAction.Share
        ).forEach { (row, action) ->
            row.visibleOrGone(action in available)
            row.click {
                dismissSafe()
                onAction(action)
            }
        }
    }
}
