package com.af010.creator.docreader.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.R

enum class ToolGroup(@StringRes val title: Int, @ColorRes val color: Int) {
    Convert(R.string.tool_group_convert, R.color.color_ff784f),
    EditSign(R.string.tool_group_edit_sign, R.color.color_9254e8),
    Organize(R.string.tool_group_organize, R.color.color_7457f5),
    Security(R.string.tool_group_security, R.color.color_35ac71)
}

/**
 * [icon] is the full tile (pastel background included) used in the Tools grid;
 * [glyph] + [color] + [bgColor] build the small chips on the Files tab.
 */
enum class Tool(
    val group: ToolGroup,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val glyph: Int,
    @ColorRes val color: Int,
    @ColorRes val bgColor: Int,
    val isPremium: Boolean = false
) {
    Scan(
        ToolGroup.Convert, R.string.scan, R.string.paper_to_pdf,
        R.drawable.ic_app_tool_scan, R.drawable.ic_app_scan, R.color.color_ff784f, R.color.color_fff0e9
    ),
    ImagesToPdf(
        ToolGroup.Convert, R.string.images_to_pdf, R.string.tool_images_to_pdf_desc,
        R.drawable.ic_app_tool_images_to_pdf, R.drawable.ic_app_image, R.color.color_ef4da8, R.color.color_fdebf5
    ),
    OfficeToPdf(
        ToolGroup.Convert, R.string.tool_office_to_pdf, R.string.tool_office_to_pdf_desc,
        R.drawable.ic_app_tool_office_to_pdf, R.drawable.ic_app_convert, R.color.color_3984ff, R.color.color_eaf2ff
    ),
    PdfToImages(
        ToolGroup.Convert, R.string.tool_pdf_to_images, R.string.tool_pdf_to_images_desc,
        R.drawable.ic_app_tool_pdf_to_images, R.drawable.ic_app_photos, R.color.color_ad55ed, R.color.color_f5eaff
    ),
    Sign(
        ToolGroup.EditSign, R.string.tool_sign, R.string.tool_sign_desc,
        R.drawable.ic_app_tool_sign_pdf, R.drawable.ic_app_sign, R.color.color_9254e8, R.color.color_f2eafe
    ),
    FillForm(
        ToolGroup.EditSign, R.string.tool_fill_form, R.string.tool_fill_form_desc,
        R.drawable.ic_app_tool_fill_form, R.drawable.ic_app_form, R.color.color_268bcb, R.color.color_e7f4fc
    ),
    Annotate(
        ToolGroup.EditSign, R.string.tool_annotate, R.string.tool_annotate_desc,
        R.drawable.ic_app_tool_annotate, R.drawable.ic_app_highlight, R.color.color_e4a11b, R.color.color_fff6da
    ),
    Watermark(
        ToolGroup.EditSign, R.string.tool_watermark, R.string.tool_watermark_desc,
        R.drawable.ic_app_tool_watermark, R.drawable.ic_app_stamp, R.color.color_5279e8, R.color.color_edf1ff
    ),
    Merge(
        ToolGroup.Organize, R.string.tool_merge, R.string.tool_merge_desc,
        R.drawable.ic_app_tool_merge_pdf, R.drawable.ic_app_merge, R.color.color_7457f5, R.color.color_f0ebff
    ),
    Split(
        ToolGroup.Organize, R.string.tool_split, R.string.tool_split_desc,
        R.drawable.ic_app_tool_split_pdf, R.drawable.ic_app_split, R.color.color_f59a23, R.color.color_fff3df
    ),
    Organize(
        ToolGroup.Organize, R.string.tool_organize, R.string.tool_organize_desc,
        R.drawable.ic_app_tool_organize_pages, R.drawable.ic_app_pages, R.color.color_596bef, R.color.color_eceeff
    ),
    Compress(
        ToolGroup.Organize, R.string.tool_compress, R.string.tool_compress_desc,
        R.drawable.ic_app_tool_compress_pdf, R.drawable.ic_app_compress, R.color.color_14b8a6, R.color.color_e0f8f3
    ),
    Protect(
        ToolGroup.Security, R.string.tool_protect, R.string.tool_protect_desc,
        R.drawable.ic_app_tool_protect_pdf, R.drawable.ic_app_lock, R.color.color_35ac71, R.color.color_e7f7ed,
        isPremium = true
    ),
    Redact(
        ToolGroup.Security, R.string.tool_redact, R.string.tool_redact_desc,
        R.drawable.ic_app_tool_redact, R.drawable.ic_app_redact, R.color.color_4b5168, R.color.color_eeeff4,
        isPremium = true
    ),
    Ocr(
        ToolGroup.Security, R.string.tool_ocr, R.string.tool_ocr_desc,
        R.drawable.ic_app_tool_ocr, R.drawable.ic_app_ocr, R.color.color_08a6c4, R.color.color_e1f7fb,
        isPremium = true
    ),
    Translate(
        ToolGroup.Security, R.string.tool_translate, R.string.tool_translate_desc,
        R.drawable.ic_app_tool_translate_pdf, R.drawable.ic_app_translate, R.color.color_ec5877, R.color.color_fdecf0
    ),
    Zip(
        ToolGroup.Security, R.string.zip, R.string.tool_zip_desc,
        R.drawable.ic_app_tool_create_zip, R.drawable.ic_app_zip, R.color.color_ecaf23, R.color.color_fff7dd
    )
}
