package com.af010.creator.docreader.ui.main.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.databinding.ItemFilterChipBinding
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.ui.baselib.base.BaseAdapter

/** [label] replaces [title] for chips named by data (a folder); [icon] is shown before the text. */
data class FilterChip<T>(
    val value: T,
    @StringRes val title: Int,
    @DrawableRes val icon: Int = 0,
    val isSelected: Boolean = false,
    val label: String? = null
)

/** Horizontal row of single-choice chips. */
class FilterChipAdapter<T> : BaseAdapter<FilterChip<T>, ItemFilterChipBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemFilterChipBinding.inflate(inflater, parent, false)

    private var onItemClick: ((T) -> Unit) = {}
    fun onChipClick(listener: (T) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemFilterChipBinding.bind(item: FilterChip<T>, position: Int) {
        val context = root.context
        tvChip.text = item.label ?: context.getString(item.title)
        tvChip.setCompoundDrawablesSized(com.intuit.sdp.R.dimen._12sdp, start = item.icon)
        tvChip.isSelected = item.isSelected
        root.setOnClickListener { onItemClick.invoke(item.value) }
    }

    /** Rebinds only the chips whose state changes. */
    fun select(value: T) {
        currentItems.forEachIndexed { index, chip ->
            val selected = chip.value == value
            if (chip.isSelected != selected) updateItem(index, chip.copy(isSelected = selected))
        }
    }
}
