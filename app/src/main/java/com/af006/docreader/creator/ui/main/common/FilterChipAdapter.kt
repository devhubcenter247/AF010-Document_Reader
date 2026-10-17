package com.af010.creator.docreader.ui.main.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemFilterChipBinding
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
        tvChip.setCompoundDrawablesRelativeWithIntrinsicBounds(item.icon, 0, 0, 0)
        tvChip.setTextColor(
            ContextCompat.getColor(
                context,
                if (item.isSelected) R.color.text_on_selected else R.color.text_primary
            )
        )
        tvChip.helper.bgColor = ContextCompat.getColor(
            context,
            if (item.isSelected) R.color.color_bg_selected else R.color.color_surface_card
        )
        tvChip.helper.stColor = ContextCompat.getColor(
            context,
            if (item.isSelected) R.color.primary else R.color.color_stroke
        )
        tvChip.invalidate()
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
