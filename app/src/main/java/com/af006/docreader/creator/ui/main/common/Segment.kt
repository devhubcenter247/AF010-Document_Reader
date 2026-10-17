package com.af010.creator.docreader.ui.main.common

import androidx.annotation.StringRes
import com.af010.creator.docreader.databinding.LayoutSegmentBinding
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.visibleOrGone

/** Segmented control with two or three options (layout_segment.xml). */
fun LayoutSegmentBinding.setUp(
    @StringRes first: Int,
    @StringRes second: Int,
    @StringRes third: Int = 0,
    onSelected: (index: Int) -> Unit
) {
    setLabels(first, second, third)
    tvFirst.click { onSelected(0) }
    tvSecond.click { onSelected(1) }
    tvThird.click { onSelected(2) }
}

fun LayoutSegmentBinding.setLabels(@StringRes first: Int, @StringRes second: Int, @StringRes third: Int = 0) {
    tvFirst.setText(first)
    tvSecond.setText(second)
    tvThird.visibleOrGone(third != 0)
    if (third != 0) tvThird.setText(third)
}

fun LayoutSegmentBinding.select(index: Int) {
    listOf(tvFirst, tvSecond, tvThird).forEachIndexed { i, view -> view.isSelected = i == index }
}
