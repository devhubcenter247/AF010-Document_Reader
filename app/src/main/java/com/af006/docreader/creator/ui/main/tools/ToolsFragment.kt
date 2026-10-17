package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentToolsBinding
import com.af010.creator.docreader.databinding.ItemToolSectionBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolGroup
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone

class ToolsFragment : BaseAppFragment<FragmentToolsBinding>(FragmentToolsBinding::inflate) {

    /** null = "All". */
    private var group: ToolGroup? = null
    private var query = ""

    private val groupAdapter by lazy { FilterChipAdapter<ToolGroup?>() }

    override fun FragmentToolsBinding.initView() {
        btnPro.visibleOrGone(remoteConfig.paywallEnabled)
        tvSubtitle.text = getString(R.string.tools_subtitle, Tool.entries.size)

        rcvGroups.adapter = groupAdapter
        groupAdapter.submitList(
            listOf(FilterChip<ToolGroup?>(null, R.string.filter_all, isSelected = group == null)) +
                    ToolGroup.entries.map { FilterChip<ToolGroup?>(it, it.title, isSelected = it == group) }
        )
        renderSections()
    }

    override fun FragmentToolsBinding.onClick() {
        btnPro.clickSingle { PaywallActivity.start(requireActivity()) }
        cardScan.clickSingle { requireActivity().openTool(Tool.Scan) }
        cardSign.clickSingle { requireActivity().openTool(Tool.Sign) }
        cardCompress.clickSingle { requireActivity().openTool(Tool.Compress) }

        groupAdapter.onChipClick {
            group = it
            groupAdapter.select(it)
            renderSections()
        }
        edtSearch.afterTextChanged {
            query = it.trim()
            renderSections()
        }
    }

    private fun FragmentToolsBinding.renderSections() {
        layoutSections.removeAllViews()
        val inflater = LayoutInflater.from(layoutSections.context)
        ToolGroup.entries
            .filter { group == null || it == group }
            .forEach { toolGroup ->
                val tools = Tool.entries.filter {
                    it.group == toolGroup && getString(it.title).contains(query, ignoreCase = true)
                }
                if (tools.isEmpty()) return@forEach

                val section = ItemToolSectionBinding.inflate(inflater, layoutSections, true)
                section.viewColor.setFillColor(ContextCompat.getColor(section.root.context, toolGroup.color))
                section.tvTitle.setText(toolGroup.title)
                section.tvCount.text =
                    resources.getQuantityString(R.plurals.tool_count, tools.size, tools.size)
                section.rcvTools.layoutManager = GridLayoutManager(section.root.context, 2).apply {
                    // An odd tool out at the end takes the full row.
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int) =
                            if (tools.size % 2 == 1 && position == tools.lastIndex) 2 else 1
                    }
                }
                section.rcvTools.adapter = ToolAdapter()
                    .onToolClick { requireActivity().openTool(it) }
                    .submitList(tools)
            }
    }
}
