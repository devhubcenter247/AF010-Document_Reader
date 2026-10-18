package com.af010.creator.docreader.utils.extensions

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.model.SortByData
import com.af010.creator.docreader.model.SortOption
import com.af010.creator.docreader.model.SortOrder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DocumentType.iconRes: Int
    @DrawableRes get() = when (this) {
        DocumentType.Pdf -> R.drawable.ic_file_pdf
        DocumentType.Doc -> R.drawable.ic_file_word
        DocumentType.Excel -> R.drawable.ic_file_excel
        DocumentType.Ppt -> R.drawable.ic_file_powerpoint
        DocumentType.Txt -> R.drawable.ic_file_txt
        DocumentType.Image -> R.drawable.ic_file_image
    }

val DocumentType.titleRes: Int
    @StringRes get() = when (this) {
        DocumentType.Pdf -> R.string.tab_pdf
        DocumentType.Doc -> R.string.tab_word
        DocumentType.Excel -> R.string.tab_excel
        DocumentType.Ppt -> R.string.powerpoint
        DocumentType.Txt -> R.string.tab_txt
        DocumentType.Image -> R.string.images
    }

val DocumentType.colorRes: Int
    @ColorRes get() = when (this) {
        DocumentType.Pdf -> R.color.color_file_pdf
        DocumentType.Doc -> R.color.color_file_word
        DocumentType.Excel -> R.color.color_file_excel
        DocumentType.Ppt -> R.color.color_file_powerpoint
        DocumentType.Txt -> R.color.color_file_txt
        DocumentType.Image -> R.color.color_file_image
    }

/** The pale tint of [colorRes], for soft badges. */
val DocumentType.softColorRes: Int
    @ColorRes get() = when (this) {
        DocumentType.Pdf -> R.color.color_file_pdf_bg
        DocumentType.Doc -> R.color.color_file_word_bg
        DocumentType.Excel -> R.color.color_file_excel_bg
        DocumentType.Ppt -> R.color.color_file_powerpoint_bg
        DocumentType.Txt -> R.color.color_file_txt_bg
        DocumentType.Image -> R.color.color_file_image_bg
    }

/** The file's extension as shown on its tile: "PDF", "DOCX", at most four letters. */
val RecentDocument.extensionLabel: String
    get() = path.substringAfterLast('.', "").uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }

val RecentDocument.name: String get() = path.substringAfterLast('/')

val RecentDocument.folderName: String get() = File(path).parentFile?.name.orEmpty()

fun RecentDocument.sizeText(context: Context): String =
    Formatter.formatShortFileSize(context, size)

/** "Just now", "5 min ago", "Today", "Yesterday", then "Sep 27". */
fun RecentDocument.dateText(context: Context, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - lastModified) / DateUtils.MINUTE_IN_MILLIS
    return when {
        minutes < 1 -> context.getString(R.string.just_now)
        minutes < 60 -> context.getString(R.string.minutes_ago, minutes.toInt())
        DateUtils.isToday(lastModified) -> context.getString(R.string.today)
        DateUtils.isToday(lastModified + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.yesterday)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastModified))
    }
}

fun List<RecentDocument>.sortedBy(option: SortOption): List<RecentDocument> {
    val comparator: Comparator<RecentDocument> = when (option.by) {
        SortByData.SortByName -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortByData.SortBySize -> compareBy { it.size }
        SortByData.SortByType -> compareBy<RecentDocument> { it.type.ordinal }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortByData.SortByDate, SortByData.None -> compareBy { it.lastModified }
    }
    return sortedWith(if (option.order == SortOrder.Decrease) comparator.reversed() else comparator)
}
