package com.af010.creator.docreader.ui.welcome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.databinding.ItemUseCaseBinding
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.ui.baselib.base.BaseAdapter

/** The use-case cards: tapping one picks or unpicks it; any number can be picked. */
class UseCaseAdapter(private val onChange: (picked: Int) -> Unit) : BaseAdapter<UseCaseItem, ItemUseCaseBinding>() {

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int) =
        ItemUseCaseBinding.inflate(inflater, parent, false)

    override fun ItemUseCaseBinding.bind(item: UseCaseItem, position: Int) {
        tvTitle.setText(item.titleRes)
        tvDesc.setText(item.descRes)
        ivIcon.setImageResource(item.iconRes)
        ivIcon.setFillColor(ContextCompat.getColor(root.context, item.color))
        root.isSelected = item.isSelected
        ivCheck.imageAlpha = if (item.isSelected) 255 else 0
        // The four cards never move, so the bound position stays right.
        root.setOnClickListener {
            updateItem(position, currentItems[position].let { it.copy(isSelected = !it.isSelected) })
            onChange(picked.size)
        }
    }

    val picked: List<UseCaseItem>
        get() = currentItems.filter { it.isSelected }
}
