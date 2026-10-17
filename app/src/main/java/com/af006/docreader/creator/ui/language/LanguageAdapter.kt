package com.af010.creator.docreader.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemViewLanguageBinding
import com.ui.baselib.base.BaseAdapter

class LanguageAdapter : BaseAdapter<LanguageUiModel, ItemViewLanguageBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemViewLanguageBinding.inflate(inflater, parent, false)

    override fun ItemViewLanguageBinding.bind(
        item: LanguageUiModel,
        position: Int
    ) {
        itemLanguage.setImageResource(item.item.flagId)
        languageName.text = item.title
        locateName.text = item.subtitle
        root.isSelected = item.isSelected
        root.setOnClickListener {
            if (item.isSelected) return@setOnClickListener
            select(item.item.code)
        }
    }

    val selectedItem: LanguageUiModel?
        get() = currentItems.firstOrNull { it.isSelected }

    fun select(code: String) {
        currentItems.forEachIndexed { index, model ->
            val selected = model.item.code == code
            if (model.isSelected != selected) updateItem(index, model.copy(isSelected = selected))
        }
    }

}