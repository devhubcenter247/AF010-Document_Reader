package com.af010.creator.docreader.ui.main.files.sheet

import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetSortBinding
import com.af010.creator.docreader.model.SortByData
import com.af010.creator.docreader.model.SortOption
import com.af010.creator.docreader.model.SortOrder
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setLabels
import com.af010.creator.docreader.ui.main.common.setUp
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

class SortBottomSheet(
    activity: FragmentActivity,
    current: SortOption,
    private val onApply: (SortOption) -> Unit
) : BaseBottomSheet<SheetSortBinding>(activity, SheetSortBinding::inflate) {

    /** A sort field with the labels of its two directions; [firstOrder] is the left one. */
    private enum class Choice(
        val by: SortByData,
        @StringRes val title: Int,
        @StringRes val first: Int,
        @StringRes val second: Int,
        val firstOrder: SortOrder
    ) {
        Date(
            SortByData.SortByDate, R.string.sort_date_modified,
            R.string.sort_newest_first, R.string.sort_oldest_first, SortOrder.Decrease
        ),
        Name(
            SortByData.SortByName, R.string.sort_name,
            R.string.sort_a_to_z, R.string.sort_z_to_a, SortOrder.Increase
        ),
        Size(
            SortByData.SortBySize, R.string.sort_size,
            R.string.sort_largest_first, R.string.sort_smallest_first, SortOrder.Decrease
        ),
        Type(
            SortByData.SortByType, R.string.sort_file_type,
            R.string.sort_a_to_z, R.string.sort_z_to_a, SortOrder.Increase
        )
    }

    private var choice = Choice.entries.firstOrNull { it.by == current.by } ?: Choice.Date
    private var order = current.order

    override fun SheetSortBinding.onBind() {
        header.tvTitle.setText(R.string.sort_by)
        header.btnClose.click { dismissSafe() }

        val rows = listOf(tvSortDate, tvSortName, tvSortSize, tvSortType)
        rows.forEachIndexed { index, row ->
            val rowChoice = Choice.entries[index]
            row.setText(rowChoice.title)
            row.click {
                if (choice != rowChoice) {
                    choice = rowChoice
                    order = rowChoice.firstOrder
                    render()
                }
            }
        }
        segmentOrder.setUp(choice.first, choice.second) { index ->
            order = if (index == 0) choice.firstOrder else choice.firstOrder.opposite()
            render()
        }
        btnApply.clickSingle {
            dismissSafe()
            onApply(SortOption(choice.by, order))
        }
        render()
    }

    private fun SheetSortBinding.render() {
        listOf(tvSortDate, tvSortName, tvSortSize, tvSortType).forEachIndexed { index, row ->
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (Choice.entries[index] == choice) R.drawable.ic_app_radio_on else R.drawable.ic_app_radio_off,
                0, 0, 0
            )
        }
        segmentOrder.setLabels(choice.first, choice.second)
        segmentOrder.select(if (order == choice.firstOrder) 0 else 1)
    }

    private fun SortOrder.opposite() =
        if (this == SortOrder.Increase) SortOrder.Decrease else SortOrder.Increase
}
