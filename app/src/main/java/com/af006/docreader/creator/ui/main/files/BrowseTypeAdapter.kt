package com.af010.creator.docreader.ui.main.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemBrowseTypeBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.utils.extensions.iconRes
import com.ui.baselib.base.DiffAdapter

data class BrowseType(
    val type: DocumentType,
    @StringRes val title: Int,
    val count: Int = 0
)

class BrowseTypeAdapter : DiffAdapter<BrowseType, ItemBrowseTypeBinding>(
    areItemsTheSame = { old, new -> old.type == new.type }
) {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemBrowseTypeBinding.inflate(inflater, parent, false)

    private var onItemClick: ((BrowseType) -> Unit) = {}
    fun onTypeClick(listener: (BrowseType) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemBrowseTypeBinding.bind(item: BrowseType, position: Int) {
        ivType.setImageResource(item.type.iconRes)
        tvTitle.setText(item.title)
        tvCount.text = root.resources.getQuantityString(R.plurals.file_count, item.count, item.count)
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
