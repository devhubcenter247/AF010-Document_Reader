package com.af010.creator.docreader.ui.main.search

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemRecentSearchBinding
import com.ui.baselib.base.BaseAdapter

class RecentSearchAdapter : BaseAdapter<String, ItemRecentSearchBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemRecentSearchBinding.inflate(inflater, parent, false)

    private var onItemClick: ((String) -> Unit) = {}
    private var onItemRemove: ((String) -> Unit) = {}

    fun onQueryClick(listener: (String) -> Unit) = apply { onItemClick = listener }
    fun onQueryRemove(listener: (String) -> Unit) = apply { onItemRemove = listener }

    override fun ItemRecentSearchBinding.bind(item: String, position: Int) {
        tvQuery.text = item
        root.setOnClickListener { onItemClick.invoke(item) }
        btnRemove.setOnClickListener { onItemRemove.invoke(item) }
    }
}
