package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.ui.baselib.extensions.clickSingle

/** The Toolbox: every tool by section, narrowed by a section chip or by name (the search field). */
class ToolsFragment : BaseAppFragment<FragmentToolsBinding>(FragmentToolsBinding::inflate) {

    /** null = every section. */
    private var section: ToolGroup? = null
    private var query = ""

    private val sectionChips = FilterChipAdapter<ToolGroup?>()
    private val toolbox = Tool.entries.filter { it.inToolbox }

    override fun FragmentToolsBinding.initView() {
        btnPro.isVisible = remoteConfig.paywallEnabled
        tvSubtitle.text = resources.getQuantityString(R.plurals.fo_toolbox_count, toolbox.size, toolbox.size)
        rcvGroups.adapter = sectionChips
        sectionChips.submitList(
            listOf(FilterChip<ToolGroup?>(null, R.string.fo_all, isSelected = section == null)) +
                ToolGroup.entries.map { FilterChip<ToolGroup?>(it, it.chip, isSelected = it == section) }
        )
        showSections()
    }

    override fun FragmentToolsBinding.onClick() {
        btnPro.clickSingle { PaywallActivity.start(requireActivity()) }
        sectionChips.onChipClick {
            section = it
            sectionChips.select(it)
            showSections()
        }
        edtSearch.doAfterTextChanged {
            query = it?.toString()?.trim().orEmpty()
            showSections()
        }
    }

    private fun FragmentToolsBinding.showSections() {
        layoutSections.removeAllViews()
        val inflater = LayoutInflater.from(layoutSections.context)
        var shown = 0
        for (group in ToolGroup.entries) {
            if (section != null && group != section) continue
            val tools = toolbox.filter { it.group == group && getString(it.title).contains(query, ignoreCase = true) }
            if (tools.isEmpty()) continue
            shown += tools.size
            ItemToolSectionBinding.inflate(inflater, layoutSections, true).apply {
                tvTitle.setText(group.title)
                tvCount.text = resources.getQuantityString(R.plurals.fo_section_tool_count, tools.size, tools.size)
                vDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, group.tint))
                rcvTools.adapter = ToolAdapter { requireActivity().openTool(it) }.apply { submitList(tools) }
            }
        }
        tvNoTools.isVisible = shown == 0
    }
}
