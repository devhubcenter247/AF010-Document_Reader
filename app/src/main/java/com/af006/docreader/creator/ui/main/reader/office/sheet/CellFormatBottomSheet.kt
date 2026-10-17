package com.af010.creator.docreader.ui.main.reader.office.sheet

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetCellFormatBinding
import com.af010.creator.docreader.ui.main.reader.office.runSafely
import com.af010.creator.docreader.ui.main.reader.pdf.addColorDots
import com.af010.creator.docreader.ui.main.reader.pdf.selectColorDot
import com.editor.docsdk.DocumentEditor
import com.editor.docsdk.EditAction
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

/**
 * Fill, alignment and wrapping of the selected cells ([range], like "B2:D4"). Nothing changes
 * until Apply; what was not touched is left as it is.
 *
 * The design also has number formats here; SDK 1.0.0 throws on its NUMBER_FORMAT command (a
 * label with a bare % goes through String.format), so they are left out until the SDK is fixed.
 */
class CellFormatBottomSheet(
    private val activity: FragmentActivity,
    private val editor: DocumentEditor,
    private val range: String
) : BaseBottomSheet<SheetCellFormatBinding>(activity, SheetCellFormatBinding::inflate) {

    private var fill: Int? = null
    private var align: EditAction? = null
    private var wrap = editor.isActive(EditAction.WRAP_TEXT)
    private val wrapAtStart = wrap

    override fun SheetCellFormatBinding.onBind() {
        header.tvTitle.setText(R.string.cell_format)
        header.btnClose.click { dismissSafe() }

        layoutFillColors.addColorDots(FILLS, com.intuit.sdp.R.dimen._28sdp) { color ->
            fill = color
            render()
        }
        aligns().forEach { (view, action) ->
            view.setImageResource(action.icon)
            view.click {
                align = action
                render()
            }
        }
        btnWrap.click {
            wrap = !wrap
            render()
        }
        btnApply.text = activity.getString(R.string.apply_to_cells, range)
        btnApply.clickSingle {
            dismissSafe()
            apply()
        }
        render()
    }

    private fun apply() {
        // White stands for no fill.
        fill?.let { editor.runSafely(EditAction.FILL_COLOR, if (it == FILLS.first()) null else String.format("%06X", it and 0xFFFFFF)) }
        align?.let { editor.runSafely(it) }
        if (wrap != wrapAtStart) editor.runSafely(EditAction.WRAP_TEXT)
    }

    private fun SheetCellFormatBinding.aligns() = mapOf(
        btnAlignLeft to EditAction.ALIGN_LEFT,
        btnAlignCenter to EditAction.ALIGN_CENTER,
        btnAlignRight to EditAction.ALIGN_RIGHT
    )

    private fun SheetCellFormatBinding.render() {
        layoutFillColors.selectColorDot(fill ?: NO_COLOR)
        aligns().forEach { (view, action) ->
            val selected = action == align
            view.helper.bgColor = ContextCompat.getColor(
                activity, if (selected) R.color.color_bg_selected else R.color.color_surface_variant
            )
            ImageViewCompat.setImageTintList(
                view,
                ColorStateList.valueOf(
                    ContextCompat.getColor(activity, if (selected) R.color.text_on_selected else R.color.text_primary)
                )
            )
            view.invalidate()
        }
        btnWrap.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (wrap) R.drawable.ic_app_checkbox_on else R.drawable.ic_app_checkbox_off, 0, 0, 0
        )
    }

    private companion object {
        const val NO_COLOR = 0
        val FILLS = listOf(
            0xFFFFFFFF.toInt(), 0xFFE6F6EE.toInt(), 0xFFE8F0FE.toInt(), 0xFFFEF5E1.toInt(),
            0xFFFDECEB.toInt(), 0xFFEFEBFF.toInt(), 0xFF20243A.toInt()
        )
    }
}
