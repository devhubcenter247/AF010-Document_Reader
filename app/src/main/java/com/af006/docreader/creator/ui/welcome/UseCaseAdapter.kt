package com.af010.creator.docreader.ui.welcome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemUseCaseBinding
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.utils.dpToPx

class UseCaseAdapter : BaseAdapter<UseCaseItem, ItemUseCaseBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ItemUseCaseBinding = ItemUseCaseBinding.inflate(inflater, parent, false)

    private var onItemClick: ((UseCaseItem) -> Unit) = {}

    fun onUseCaseSelected(listener: (UseCaseItem) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemUseCaseBinding.bind(item: UseCaseItem, position: Int) {
        val context = root.context
        tvTitle.setText(item.titleRes)
        tvDesc.setText(item.descRes)
        ivIcon.setImageResource(item.iconRes)
        ivIcon.setFillColor(item.iconBgColor)
        ivIcon.invalidate()

        val selectedBg = ContextCompat.getColor(context, R.color.color_bg_selected)
        val unselectedBg = ContextCompat.getColor(context, R.color.color_surface_card)
        val selectedStroke = ContextCompat.getColor(context, R.color.primary)
        val unselectedStroke = ContextCompat.getColor(context, R.color.color_stroke)

        if (item.isSelected) {
            root.setFillColor(selectedBg)
            root.setStroke(context.dpToPx(2), selectedStroke)
            ivCheck.visibility = View.VISIBLE
        } else {
            root.setFillColor(unselectedBg)
            root.setStroke(context.dpToPx(1), unselectedStroke)
            ivCheck.visibility = View.GONE
        }
        root.invalidate()

        root.setOnClickListener {
            toggleSelection(position)
            onItemClick.invoke(currentItems[position])
        }
    }

    fun toggleSelection(position: Int) {
        if (position in currentItems.indices) {
            val item = currentItems[position]
            updateItem(position, item.copy(isSelected = !item.isSelected))
        }
    }

    val selectedItems: List<UseCaseItem>
        get() = currentItems.filter { it.isSelected }
}
