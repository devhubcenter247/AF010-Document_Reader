package com.af010.creator.docreader.ui.main.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.databinding.ItemToolChipBinding
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseAdapter

class ToolChipAdapter : BaseAdapter<Tool, ItemToolChipBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemToolChipBinding.inflate(inflater, parent, false)

    private var onItemClick: ((Tool) -> Unit) = {}
    fun onToolClick(listener: (Tool) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemToolChipBinding.bind(item: Tool, position: Int) {
        val context = root.context
        root.helper.bgColor = ContextCompat.getColor(context, item.bgColor)
        root.invalidate()
        ivIcon.setImageResource(item.glyph)
        ivIcon.helper.bgColor = ContextCompat.getColor(context, item.color)
        ivIcon.invalidate()
        tvTitle.setText(item.title)
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
