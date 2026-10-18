package com.af010.creator.docreader.ui.main.files.sheet

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutBlankTileBinding
import com.af010.creator.docreader.databinding.SheetCreateBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.clickSingle

/** The "+" sheet: scan, photos to PDF, or a blank Word / Excel / PowerPoint file. */
class CreateBottomSheet(
    private val host: FragmentActivity,
    private val onTool: (Tool) -> Unit,
    private val onNewDocument: (DocumentType) -> Unit
) : BaseBottomSheet<SheetCreateBinding>(host, SheetCreateBinding::inflate) {

    override fun SheetCreateBinding.onBind() {
        cardWord.blank("DOCX", R.string.tab_word, R.drawable.bg_blank_word, R.color.color_file_word) { onNewDocument(DocumentType.Doc) }
        cardExcel.blank("XLSX", R.string.tab_excel, R.drawable.bg_blank_excel, R.color.color_file_excel) { onNewDocument(DocumentType.Excel) }
        cardPpt.blank("PPTX", R.string.powerpoint, R.drawable.bg_blank_powerpoint, R.color.color_file_powerpoint) { onNewDocument(DocumentType.Ppt) }
        cardScan.clickSingle { then { onTool(Tool.Scan) } }
        cardImages.clickSingle { then { onTool(Tool.ImagesToPdf) } }
        btnNotNow.clickSingle { dismissSafe() }
    }

    private fun LayoutBlankTileBinding.blank(
        badge: String, @StringRes name: Int, @DrawableRes ground: Int, @ColorRes ink: Int, action: () -> Unit
    ) {
        root.setBackgroundResource(ground)
        tvBadge.text = badge
        tvBadge.setTextColor(host.getColor(ink))
        tvName.setText(name)
        root.clickSingle { then(action) }
    }

    private fun then(action: () -> Unit) {
        dismissSafe()
        action()
    }
}
