package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.databinding.ItemToolBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.showToolTile
import com.ui.baselib.base.BaseAdapter

/** Toolbox cards; the tiles lean a little, each its own way, like cards on a table. */
class ToolAdapter(private val onTool: (Tool) -> Unit) : BaseAdapter<Tool, ItemToolBinding>() {

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int) =
        ItemToolBinding.inflate(inflater, parent, false)

    override fun ItemToolBinding.bind(item: Tool, position: Int) {
        ivIcon.showToolTile(item)
        ivIcon.rotation = TILTS[position % TILTS.size]
        tvTitle.setText(item.title)
        val soon = item == Tool.OfficeToPdf
        tvDesc.isVisible = !soon
        tvSoon.isVisible = soon
        if (!soon) tvDesc.setText(item.desc)
        ivPremium.isVisible = item.isPremium && remoteConfig.paywallEnabled
        card.setOnClickListener { onTool(item) }
    }

    private companion object {
        val TILTS = floatArrayOf(-4f, 3f, -2f, 4f)
    }
}
