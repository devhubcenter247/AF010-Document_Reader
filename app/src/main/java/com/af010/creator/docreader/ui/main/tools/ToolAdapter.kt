package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.databinding.ItemToolBinding
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseAdapter

/** Toolbox cards: the tool's picture, name and line, and a Pro tag on Premium tools. */
class ToolAdapter(private val onTool: (Tool) -> Unit) : BaseAdapter<Tool, ItemToolBinding>() {

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int) =
        ItemToolBinding.inflate(inflater, parent, false)

    override fun ItemToolBinding.bind(item: Tool, position: Int) {
        ivIcon.setImageResource(item.art)
        tvTitle.setText(item.title)
        tvDesc.setText(item.desc)
        tvPremium.isVisible = item.isPremium && remoteConfig.paywallEnabled
        card.setOnClickListener { onTool(item) }
    }
}
