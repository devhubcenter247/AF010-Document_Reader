package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemToolFileRowBinding
import com.af010.creator.docreader.ui.common.showTypeTile
import com.ui.baselib.base.BaseAdapter
import java.io.File

/** A file picked for a tool with the text under its name ("1.1 MB · 12 pages"). */
data class FileRow(val file: File, val meta: String)

/**
 * The files of the Merge and ZIP screens. [ordered] rows show their place with a move-up button
 * and a drag handle (Merge); the others show the file type and a remove button (ZIP).
 */
class FileRowAdapter(private val ordered: Boolean) : BaseAdapter<FileRow, ItemToolFileRowBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemToolFileRowBinding.inflate(inflater, parent, false)

    private var onItemRemove: ((FileRow) -> Unit) = {}
    private var onItemUp: ((Int) -> Unit) = {}

    fun onRemoveClick(listener: (FileRow) -> Unit) = apply { onItemRemove = listener }
    fun onMoveUpClick(listener: (position: Int) -> Unit) = apply { onItemUp = listener }

    override fun ItemToolFileRowBinding.bind(item: FileRow, position: Int) {
        tvNumber.isVisible = ordered
        btnUp.isVisible = ordered
        ivDrag.isVisible = ordered
        tvType.isVisible = !ordered
        btnRemove.isVisible = !ordered

        tvNumber.text = (position + 1).toString()
        tvType.showTypeTile(item.file)
        tvName.text = item.file.name
        tvMeta.text = item.meta
        btnUp.isEnabled = position > 0
        btnUp.alpha = if (position > 0) 1f else 0.35f
        btnUp.setOnClickListener { onItemUp(position) }
        btnRemove.contentDescription = root.context.getString(R.string.fo_remove_file, item.file.name)
        btnRemove.setOnClickListener { onItemRemove(item) }
    }
}
