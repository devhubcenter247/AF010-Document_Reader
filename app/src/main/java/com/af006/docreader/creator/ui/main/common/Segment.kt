package com.af010.creator.docreader.ui.main.common

import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutSegmentBinding
import com.af010.creator.docreader.utils.extensions.setFillColor
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
    val context = root.context
    listOf(tvFirst, tvSecond, tvThird).forEachIndexed { i, view ->
        val selected = i == index
        view.setFillColor(ContextCompat.getColor(
            context,
            if (selected) R.color.color_surface_card else android.R.color.transparent
        ))
        view.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.text_primary else R.color.text_secondary
            )
        )
        view.invalidate()
    }
}
