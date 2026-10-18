package com.af010.creator.docreader.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.R

/** The sections of the Toolbox; [chip] is the shorter name on its filter chip. */
enum class ToolGroup(@param:StringRes val title: Int, @param:StringRes val chip: Int) {
    Convert(R.string.tool_group_convert, R.string.tool_group_convert),
    EditSign(R.string.tool_group_edit_sign, R.string.tool_group_edit_sign),
    Organize(R.string.tool_group_organize, R.string.tool_group_organize),
    Security(R.string.fo_group_protect, R.string.fo_group_protect_short)
}

/**
 * Every tool, drawn as a [glyph] in white on a [tint] tile. [inToolbox] is false for actions
 * reached from elsewhere only (Add text, from the Files tab's edit card).
 */
enum class Tool(
    val group: ToolGroup,
    @param:StringRes val title: Int,
    @param:StringRes val desc: Int,
    @param:DrawableRes val glyph: Int,
    @param:ColorRes val tint: Int,
    val isPremium: Boolean = false,
    val inToolbox: Boolean = true
) {
    Scan(ToolGroup.Convert, R.string.scan, R.string.paper_to_pdf, R.drawable.ic_fo_scan, R.color.color_file_pdf),
    ImagesToPdf(ToolGroup.Convert, R.string.photos_to_pdf, R.string.fo_tool_photos_desc, R.drawable.ic_fo_photo, R.color.color_file_image),
    OfficeToPdf(ToolGroup.Convert, R.string.tool_office_to_pdf, R.string.tool_office_to_pdf_desc, R.drawable.ic_fo_office, R.color.primary),
    PdfToImages(ToolGroup.Convert, R.string.tool_pdf_to_images, R.string.tool_pdf_to_images_desc, R.drawable.ic_fo_pdf_images, R.color.color_file_powerpoint),
    Sign(ToolGroup.EditSign, R.string.tool_sign, R.string.tool_sign_desc, R.drawable.ic_fo_sign, R.color.color_file_image),
    FillForm(ToolGroup.EditSign, R.string.tool_fill_form, R.string.tool_fill_form_desc, R.drawable.ic_fo_form, R.color.primary),
    Annotate(ToolGroup.EditSign, R.string.tool_annotate, R.string.fo_tool_annotate_desc, R.drawable.ic_fo_annotate, R.color.color_tool_amber),
    Watermark(ToolGroup.EditSign, R.string.tool_watermark, R.string.tool_watermark_desc, R.drawable.ic_fo_watermark, R.color.color_file_pdf),
    AddText(ToolGroup.EditSign, R.string.add_text, R.string.tool_annotate_desc, R.drawable.ic_fo_pen, R.color.primary, inToolbox = false),
    Merge(ToolGroup.Organize, R.string.tool_merge, R.string.tool_merge_desc, R.drawable.ic_fo_merge, R.color.color_file_pdf),
    Split(ToolGroup.Organize, R.string.tool_split, R.string.tool_split_desc, R.drawable.ic_fo_split, R.color.color_file_powerpoint),
    Organize(ToolGroup.Organize, R.string.tool_organize, R.string.tool_organize_desc, R.drawable.ic_fo_organize, R.color.primary),
    Compress(ToolGroup.Organize, R.string.tool_compress, R.string.tool_compress_desc, R.drawable.ic_fo_compress, R.color.color_file_excel),
    Protect(ToolGroup.Security, R.string.tool_protect, R.string.tool_protect_desc, R.drawable.ic_fo_lock, R.color.color_file_excel, isPremium = true),
    Redact(ToolGroup.Security, R.string.tool_redact, R.string.tool_redact_desc, R.drawable.ic_fo_redact, R.color.color_tool_indigo, isPremium = true),
    Ocr(ToolGroup.Security, R.string.tool_ocr, R.string.tool_ocr_desc, R.drawable.ic_fo_ocr, R.color.color_tool_amber, isPremium = true),
    Translate(ToolGroup.Security, R.string.tool_translate, R.string.tool_translate_desc, R.drawable.ic_fo_translate, R.color.color_file_image),
    Zip(ToolGroup.Security, R.string.zip, R.string.tool_zip_desc, R.drawable.ic_fo_zip, R.color.color_file_zip)
}
