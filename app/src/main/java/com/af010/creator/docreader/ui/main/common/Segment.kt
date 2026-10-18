package com.af010.creator.docreader.ui.main.common

import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.children
import com.af010.creator.docreader.R
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

/**
 * The same control built in code inside a bg_segment_track row, for lists longer than
 * layout_segment holds (opacity steps).
 */
fun LinearLayout.fillSegment(labels: List<CharSequence>, onSelected: (index: Int) -> Unit) {
    removeAllViews()
    val height = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._31sdp)
    labels.forEachIndexed { index, label ->
        addView(TextView(context, null, 0, R.style.SegmentItem).apply {
            text = label
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, height, 1f)
            setOnClickListener { onSelected(index) }
        })
    }
}

fun LinearLayout.selectSegment(index: Int) {
    children.forEachIndexed { i, view -> view.isSelected = i == index }
}
