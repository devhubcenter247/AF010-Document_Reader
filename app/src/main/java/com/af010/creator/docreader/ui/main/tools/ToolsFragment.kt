package com.af010.creator.docreader.ui.main.tools

import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentToolsBinding
import com.af010.creator.docreader.databinding.ItemToolSectionBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolGroup
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.ui.baselib.extensions.clickSingle

/** The Toolbox: every tool by section, narrowed by a section chip or by name. */
class ToolsFragment : BaseAppFragment<FragmentToolsBinding>(FragmentToolsBinding::inflate) {

    /** null = every section. */
    private var section: ToolGroup? = null
    private var query = ""

    private val sectionChips = FilterChipAdapter<ToolGroup?>()
    private val toolbox = Tool.entries.filter { it.inToolbox }

    override fun FragmentToolsBinding.initView() {
        tvSubtitle.text = resources.getQuantityString(R.plurals.fo_toolbox_count, toolbox.size, toolbox.size)
        rcvGroups.adapter = sectionChips
        sectionChips.submitList(
            listOf(FilterChip<ToolGroup?>(null, R.string.fo_all, isSelected = section == null)) +
                ToolGroup.entries.map { FilterChip<ToolGroup?>(it, it.chip, isSelected = it == section) }
        )
        showSections()
    }

    override fun FragmentToolsBinding.onClick() {
        btnSearch.clickSingle { toggleSearch() }
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

    private fun FragmentToolsBinding.toggleSearch() {
        val open = !edtSearch.isVisible
        edtSearch.isVisible = open
        val keyboard = requireContext().getSystemService(InputMethodManager::class.java)
        if (open) {
            edtSearch.requestFocus()
            keyboard.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT)
        } else {
            edtSearch.text = null
            keyboard.hideSoftInputFromWindow(edtSearch.windowToken, 0)
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
                rcvTools.adapter = ToolAdapter { requireActivity().openTool(it) }.apply { submitList(tools) }
            }
        }
        tvNoTools.isVisible = shown == 0
    }
}
