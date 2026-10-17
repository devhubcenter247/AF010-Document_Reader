package com.af010.creator.docreader.ui.main.tools

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutOptionBinding
import com.af010.creator.docreader.databinding.LayoutToolFileBinding
import com.af010.creator.docreader.databinding.LayoutToolHeaderBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.iconRes
import com.ui.baselib.utils.dpToPx
import java.io.File

/** Intent extra carrying the absolute paths of the files a tool screen works on. */
const val EXTRA_PATHS = "EXTRA_PATHS"

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

val File.isImage: Boolean
    get() = extension.lowercase() in IMAGE_EXTENSIONS

val File.typeIcon: Int
    get() = AppUtils.documentTypeOf(this)?.iconRes ?: when {
        isImage -> R.drawable.ic_file_image
        extension.equals("zip", ignoreCase = true) -> R.drawable.ic_file_zip
        else -> R.drawable.ic_file_txt
    }

fun File.sizeText(context: Context): String = Formatter.formatShortFileSize(context, length())

fun Context.pageCountText(count: Int): String =
    resources.getQuantityString(R.plurals.translate_page_count, count, count)

fun LayoutToolHeaderBinding.show(tool: Tool, @StringRes title: Int, @StringRes subtitle: Int) {
    ivIcon.setImageResource(tool.icon)
    tvTitle.setText(title)
    tvSubtitle.setText(subtitle)
}

fun LayoutToolFileBinding.show(file: File, meta: String) {
    ivType.setImageResource(file.typeIcon)
    tvName.text = file.name
    tvMeta.text = meta
}

fun LayoutOptionBinding.show(@StringRes title: Int, desc: String) {
    tvTitle.setText(title)
    tvDesc.text = desc
}

fun LayoutOptionBinding.setChecked(checked: Boolean) {
    val context = root.context
    ivRadio.setImageResource(if (checked) R.drawable.ic_app_radio_on else R.drawable.ic_app_radio_off)
    root.helper.bgColor = ContextCompat.getColor(
        context,
        if (checked) R.color.color_bg_selected else R.color.color_surface_card
    )
    root.helper.stColor = ContextCompat.getColor(
        context,
        if (checked) R.color.primary else R.color.color_stroke
    )
    root.helper.stWidth = context.dpToPx(if (checked) 2 else 1).toFloat()
    root.invalidate()
}
