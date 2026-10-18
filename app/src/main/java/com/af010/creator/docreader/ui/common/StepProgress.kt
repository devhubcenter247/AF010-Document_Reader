package com.af010.creator.docreader.ui.common

import android.view.View
import android.widget.LinearLayout
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutStepProgressBinding

/**
 * Shows step [current] (1-based) of [total]: a pill for this step, blue dots for the steps
 * behind it and grey ones ahead. [detail] follows the count ("Step 2 of 3 · Order").
 */
fun LayoutStepProgressBinding.show(current: Int, total: Int, detail: CharSequence? = null) {
    val res = root.resources
    val dot = res.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
    val pill = res.getDimensionPixelSize(com.intuit.sdp.R.dimen._17sdp)
    val gap = res.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
    layoutDots.removeAllViews()
    for (step in 1..total) {
        layoutDots.addView(View(root.context).apply {
            setBackgroundResource(if (step <= current) R.drawable.bg_step_done else R.drawable.bg_step_todo)
            layoutParams = LinearLayout.LayoutParams(if (step == current) pill else dot, dot).apply {
                if (step > 1) marginStart = gap
            }
        })
    }
    val count = res.getString(R.string.fo_step_of, current, total)
    tvStep.text = if (detail.isNullOrEmpty()) count else "$count · $detail"
}
