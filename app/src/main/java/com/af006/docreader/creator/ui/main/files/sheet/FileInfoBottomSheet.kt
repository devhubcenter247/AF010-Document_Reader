package com.af010.creator.docreader.ui.main.files.sheet

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutInfoRowBinding
import com.af010.creator.docreader.databinding.SheetFileInfoBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.sizeText
import com.af010.creator.docreader.utils.extensions.titleRes
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.gone
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Name, type, size, pages (PDF), folder and date of one document. */
class FileInfoBottomSheet(
    private val activity: FragmentActivity,
    private val doc: RecentDocument
) : BaseBottomSheet<SheetFileInfoBinding>(activity, SheetFileInfoBinding::inflate) {

    override fun SheetFileInfoBinding.onBind() {
        val context = root.context
        val file = File(doc.path)
        header.tvTitle.setText(R.string.file_info)
        header.btnClose.click { dismissSafe() }

        rowName.show(R.string.sort_name, doc.name)
        val extension = file.extension.uppercase()
        rowType.show(
            R.string.sort_file_type,
            listOf(context.getString(doc.type.titleRes), extension).distinct().filter { it.isNotEmpty() }
                .joinToString(" · ")
        )
        rowSize.show(R.string.sort_size, doc.sizeText(context))
        rowLocation.show(R.string.file_info_location, file.parent.orEmpty())
        rowModified.show(
            R.string.sort_date_modified,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doc.lastModified))
        )

        rowPages.root.gone()
        if (doc.type == DocumentType.Pdf) {
            // A locked or damaged PDF has no count to show: the row stays hidden.
            activity.lifecycleScope.launch {
                val pages = runCatching { activity.get<PdfToolbox>().pageCount(file) }.getOrNull() ?: return@launch
                if (!isShowing) return@launch
                rowPages.show(R.string.file_info_pages, pages.toString())
                rowPages.root.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun LayoutInfoRowBinding.show(label: Int, value: String) {
        tvLabel.setText(label)
        tvValue.text = value
    }
}
