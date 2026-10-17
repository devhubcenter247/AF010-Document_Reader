package com.af010.creator.docreader.ui.main.documents

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemFolderBinding
import com.ui.baselib.base.DiffAdapter

/** Items are folder name to file count. */
class FolderAdapter : DiffAdapter<Pair<String, Int>, ItemFolderBinding>(
    areItemsTheSame = { old, new -> old.first == new.first }
) {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemFolderBinding.inflate(inflater, parent, false)

    private var onItemClick: ((String) -> Unit) = {}
    fun onFolderClick(listener: (String) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemFolderBinding.bind(item: Pair<String, Int>, position: Int) {
        tvName.text = item.first
        tvCount.text = item.second.toString()
        root.setOnClickListener { onItemClick.invoke(item.first) }
    }
}
