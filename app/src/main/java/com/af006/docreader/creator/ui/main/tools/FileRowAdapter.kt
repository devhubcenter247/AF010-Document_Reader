package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemToolFileRowBinding
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.visibleOrGone
import java.io.File

/** A file picked for a tool with the text under its name ("1.1 MB · 12 pages"). */
data class FileRow(val file: File, val meta: String)

/** Removable (and, with [draggable], reorderable) list of files in the Merge and ZIP screens. */
class FileRowAdapter(private val draggable: Boolean) : BaseAdapter<FileRow, ItemToolFileRowBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemToolFileRowBinding.inflate(inflater, parent, false)

    private var onItemRemove: ((FileRow) -> Unit) = {}
    fun onRemoveClick(listener: (FileRow) -> Unit) = apply {
        onItemRemove = listener
    }

    override fun ItemToolFileRowBinding.bind(item: FileRow, position: Int) {
        ivDrag.visibleOrGone(draggable)
        ivType.setImageResource(item.file.typeIcon)
        tvName.text = item.file.name
        tvMeta.text = item.meta
        btnRemove.setOnClickListener { onItemRemove.invoke(item) }
    }
}
