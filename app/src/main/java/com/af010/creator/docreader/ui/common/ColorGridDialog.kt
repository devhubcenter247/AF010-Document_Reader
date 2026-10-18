package com.af010.creator.docreader.ui.common

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogColorGridBinding
import com.af010.creator.docreader.utils.extensions.setCircle
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.base.BaseDialog

/**
 * The full palette behind the "+" of a row of colour dots: ten hues in five shades and a grey
 * row. [selected] gets a ring; [onPick] gets the colour tapped.
 */
class ColorGridDialog(
    context: Context,
    private val selected: Int?,
    private val onPick: (Int) -> Unit
) : BaseDialog<DialogColorGridBinding>(context = context, DialogColorGridBinding::inflate) {

    override fun DialogColorGridBinding.initView() {
        val gap = context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
        val ring = context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
        PALETTE.forEach { color ->
            gridColors.addView(View(context).apply {
                // Equal columns: each swatch takes a tenth of the row and stays square.
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    setMargins(gap, gap, gap, gap)
                }
                setCircle(color)
                if (color == Color.WHITE || color == selected) {
                    setStroke(ring, ContextCompat.getColor(context, if (color == selected) R.color.primary else R.color.color_checkbox_stroke))
                }
                setOnClickListener {
                    dismiss()
                    onPick(color)
                }
            })
        }
        gridColors.post {
            for (index in 0 until gridColors.childCount) {
                val swatch = gridColors.getChildAt(index)
                swatch.layoutParams = swatch.layoutParams.apply { height = swatch.width }
            }
            gridColors.requestLayout()
        }
    }

    companion object {
        private val HUES = floatArrayOf(0f, 25f, 48f, 130f, 168f, 195f, 218f, 250f, 280f, 325f)
        private val LIGHTNESS = floatArrayOf(0.86f, 0.70f, 0.55f, 0.42f, 0.28f)

        /** Row by row: each lightness across the hues, then white to black. */
        val PALETTE: List<Int> = LIGHTNESS.flatMap { light ->
            HUES.map { hue -> ColorUtils.HSLToColor(floatArrayOf(hue, 0.78f, light)) }
        } + (0 until 10).map { step ->
            val value = 1f - step / 9f
            ColorUtils.HSLToColor(floatArrayOf(0f, 0f, value))
        }
    }
}
