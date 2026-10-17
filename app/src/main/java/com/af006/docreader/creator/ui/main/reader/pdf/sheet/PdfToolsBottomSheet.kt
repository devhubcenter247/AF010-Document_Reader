package com.af010.creator.docreader.ui.main.reader.pdf.sheet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemSheetToolBinding
import com.af010.creator.docreader.databinding.SheetPdfToolsBinding
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click

/** What the reader's Tools sheet offers for the open PDF. */
enum class PdfSheetTool(@StringRes val title: Int, @DrawableRes val icon: Int, val editsInPlace: Boolean = false) {
    Annotate(R.string.tool_annotate, R.drawable.ic_app_tool_annotate, true),
    AddText(R.string.add_text, R.drawable.ic_app_tool_add_text, true),
    Sign(R.string.tool_sign, R.drawable.ic_app_tool_sign_pdf, true),
    FillForm(R.string.tool_fill_form, R.drawable.ic_app_tool_fill_form, true),
    Pages(R.string.tool_pages, R.drawable.ic_app_tool_organize_pages),
    Compress(R.string.tool_compress, R.drawable.ic_app_tool_compress_pdf),
    Password(R.string.tool_password, R.drawable.ic_app_tool_protect_pdf),
    Watermark(R.string.tool_watermark, R.drawable.ic_app_tool_watermark),
    Ocr(R.string.tool_ocr, R.drawable.ic_app_tool_ocr),
    Redact(R.string.tool_redact, R.drawable.ic_app_tool_redact),
    Translate(R.string.tool_translate, R.drawable.ic_app_tool_translate_pdf),
    ToImages(R.string.tool_to_images, R.drawable.ic_app_tool_pdf_to_images)
}

class PdfToolsBottomSheet(
    activity: FragmentActivity,
    private val onTool: (PdfSheetTool) -> Unit
) : BaseBottomSheet<SheetPdfToolsBinding>(activity, SheetPdfToolsBinding::inflate) {

    override fun SheetPdfToolsBinding.onBind() {
        header.tvTitle.setText(R.string.tools_for_this_pdf)
        header.btnClose.click { dismissSafe() }

        val (edit, file) = PdfSheetTool.entries.partition { it.editsInPlace }
        rcvEdit.adapter = SheetToolAdapter().onToolClick(::pick).submitList(edit)
        rcvFile.adapter = SheetToolAdapter().onToolClick(::pick).submitList(file)
    }

    private fun pick(tool: PdfSheetTool) {
        dismissSafe()
        onTool(tool)
    }
}

class SheetToolAdapter : BaseAdapter<PdfSheetTool, ItemSheetToolBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemSheetToolBinding.inflate(inflater, parent, false)

    private var onItemClick: ((PdfSheetTool) -> Unit) = {}
    fun onToolClick(listener: (PdfSheetTool) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemSheetToolBinding.bind(item: PdfSheetTool, position: Int) {
        ivIcon.setImageResource(item.icon)
        tvLabel.setText(item.title)
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
