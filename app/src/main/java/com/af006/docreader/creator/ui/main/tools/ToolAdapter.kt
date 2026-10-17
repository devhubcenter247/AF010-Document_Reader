package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemToolBinding
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseAdapter
import com.af010.creator.docreader.app.remote.remoteConfig
import com.ui.baselib.extensions.visibleOrGone

class ToolAdapter : BaseAdapter<Tool, ItemToolBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemToolBinding.inflate(inflater, parent, false)

    private var onItemClick: ((Tool) -> Unit) = {}
    fun onToolClick(listener: (Tool) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemToolBinding.bind(item: Tool, position: Int) {
        ivIcon.setImageResource(item.icon)
        tvTitle.setText(item.title)
        tvDesc.setText(item.desc)
        ivPremium.visibleOrGone(item.isPremium && remoteConfig.paywallEnabled)
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
