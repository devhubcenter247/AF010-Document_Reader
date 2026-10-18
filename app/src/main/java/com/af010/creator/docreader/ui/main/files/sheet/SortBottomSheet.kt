package com.af010.creator.docreader.ui.main.files.sheet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutChoiceRowBinding
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

/** Picks what to sort by and which way round; the order's labels follow the field. */
class SortBottomSheet(
    activity: FragmentActivity,
    current: SortOption,
    private val onApply: (SortOption) -> Unit
) : BaseBottomSheet<SheetSortBinding>(activity, SheetSortBinding::inflate) {

    /** A sort field; [first] is the left order and [firstOrder] the direction it stands for. */
    private enum class Field(
        val by: SortByData,
        @param:StringRes val title: Int,
        @param:DrawableRes val icon: Int,
        @param:StringRes val first: Int,
        @param:StringRes val second: Int,
        val firstOrder: SortOrder
    ) {
        Date(SortByData.SortByDate, R.string.sort_date_modified, R.drawable.ic_fo_clock, R.string.sort_newest_first, R.string.sort_oldest_first, SortOrder.Decrease),
        Name(SortByData.SortByName, R.string.sort_name, R.drawable.ic_fo_sort_name, R.string.sort_a_to_z, R.string.sort_z_to_a, SortOrder.Increase),
        Size(SortByData.SortBySize, R.string.sort_size, R.drawable.ic_fo_weight, R.string.sort_largest_first, R.string.sort_smallest_first, SortOrder.Decrease),
        Type(SortByData.SortByType, R.string.sort_file_type, R.drawable.ic_fo_type, R.string.fo_pdf_first, R.string.fo_images_first, SortOrder.Increase)
    }

    private var field = Field.entries.firstOrNull { it.by == current.by } ?: Field.Date
    private var order = current.order

    private val SheetSortBinding.rows get() = listOf(rowDate, rowName, rowSize, rowType)

    override fun SheetSortBinding.onBind() {
        header.tvTitle.setText(R.string.fo_sort_title)
        header.btnClose.click { dismissSafe() }
        rows.zip(Field.entries).forEach { (row, rowField) -> row.describe(rowField) }
        segmentOrder.setUp(field.first, field.second) { index ->
            order = if (index == 0) field.firstOrder else field.firstOrder.flipped()
            show()
        }
        btnApply.clickSingle {
            dismissSafe()
            onApply(SortOption(field.by, order))
        }
        show()
    }

    private fun LayoutChoiceRowBinding.describe(rowField: Field) {
        ivIcon.setImageResource(rowField.icon)
        tvLabel.setText(rowField.title)
        root.click {
            if (field == rowField) return@click
            field = rowField
            order = rowField.firstOrder
            binding.show()
        }
    }

    private fun SheetSortBinding.show() {
        rows.zip(Field.entries).forEach { (row, rowField) ->
            row.root.isSelected = rowField == field
            row.ivTick.isInvisible = rowField != field
        }
        segmentOrder.setLabels(field.first, field.second)
        segmentOrder.select(if (order == field.firstOrder) 0 else 1)
    }

    private fun SortOrder.flipped() = if (this == SortOrder.Increase) SortOrder.Decrease else SortOrder.Increase
}
