package com.af010.creator.docreader.ui.main.files.sheet

import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetCreateBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

/** The "+" sheet: scan, images to PDF, or a new blank Word / Excel / PowerPoint file. */
class CreateBottomSheet(
    activity: FragmentActivity,
    private val onTool: (Tool) -> Unit,
    private val onNewDocument: (DocumentType) -> Unit
) : BaseBottomSheet<SheetCreateBinding>(activity, SheetCreateBinding::inflate) {

    override fun SheetCreateBinding.onBind() {
        header.tvTitle.setText(R.string.create_new)
        header.btnClose.click { dismissSafe() }

        cardScan.clickSingle { pick { onTool(Tool.Scan) } }
        cardImages.clickSingle { pick { onTool(Tool.ImagesToPdf) } }
        cardWord.clickSingle { pick { onNewDocument(DocumentType.Doc) } }
        cardExcel.clickSingle { pick { onNewDocument(DocumentType.Excel) } }
        cardPpt.clickSingle { pick { onNewDocument(DocumentType.Ppt) } }
    }

    private fun pick(action: () -> Unit) {
        dismissSafe()
        action()
    }
}
