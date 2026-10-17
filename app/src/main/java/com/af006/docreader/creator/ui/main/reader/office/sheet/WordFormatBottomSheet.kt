package com.af010.creator.docreader.ui.main.reader.office.sheet

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetWordFormatBinding
import com.af010.creator.docreader.ui.main.reader.office.runSafely
import com.af010.creator.docreader.ui.main.reader.pdf.addColorDots
import com.af010.creator.docreader.ui.main.reader.pdf.selectColorDot
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.editor.docsdk.DocumentEditor
import com.editor.docsdk.EditAction
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.visibleOrGone

/**
 * Character and paragraph formatting of the selection in a Word document. Every tap is applied
 * right away through [editor]; [chipText] is what the font and size buttons show.
 */
class WordFormatBottomSheet(
    private val activity: FragmentActivity,
    private val editor: DocumentEditor,
    private val chipText: (EditAction) -> String?
) : BaseBottomSheet<SheetWordFormatBinding>(activity, SheetWordFormatBinding::inflate) {

    override fun SheetWordFormatBinding.onBind() {
        header.tvTitle.setText(R.string.ribbon_format)
        header.btnClose.click { dismissSafe() }

        btnFont.click { editor.runSafely(EditAction.FONT) }
        btnSize.click { editor.runSafely(EditAction.FONT_SIZE) }
        btnSpacing.click { editor.runSafely(EditAction.LINE_SPACING) }
        toggles().forEach { (view, action) ->
            view.visibleOrGone(editor.isAvailable(action))
            (view as? ImageView)?.takeIf { it.drawable == null }?.setImageResource(action.icon)
            view.click {
                editor.runSafely(action)
                render()
            }
        }
        layoutTextColors.addColorDots(TEXT_COLORS, com.intuit.sdp.R.dimen._28sdp) { color -> editor.runSafely(EditAction.TEXT_COLOR, hex(color)) }
        layoutHighlights.addColorDots(HIGHLIGHTS, com.intuit.sdp.R.dimen._28sdp) { color ->
            // White stands for no highlight.
            editor.runSafely(EditAction.HIGHLIGHT, if (color == HIGHLIGHTS.first()) null else hex(color))
        }
        // No colour is marked as the current one: the SDK does not tell it.
        layoutTextColors.selectColorDot(NO_COLOR)
        layoutHighlights.selectColorDot(NO_COLOR)
        render()
    }

    /** The state of the buttons follows the selection: call when it may have changed. */
    fun refresh() {
        if (isShowing) binding.render()
    }

    private fun SheetWordFormatBinding.toggles(): Map<View, EditAction> = mapOf(
        btnBold to EditAction.BOLD,
        btnItalic to EditAction.ITALIC,
        btnUnderline to EditAction.UNDERLINE,
        btnStrike to EditAction.STRIKETHROUGH,
        btnSuperscript to EditAction.SUPERSCRIPT,
        btnSubscript to EditAction.SUBSCRIPT,
        btnAlignLeft to EditAction.ALIGN_LEFT,
        btnAlignCenter to EditAction.ALIGN_CENTER,
        btnAlignRight to EditAction.ALIGN_RIGHT,
        btnAlignJustify to EditAction.ALIGN_JUSTIFY,
        btnBullets to EditAction.BULLETS,
        btnNumbering to EditAction.NUMBERING
    )

    private fun SheetWordFormatBinding.render() {
        btnFont.text = chipText(EditAction.FONT) ?: activity.getString(R.string.ribbon_font)
        btnSize.text = chipText(EditAction.FONT_SIZE) ?: activity.getString(R.string.ribbon_size)
        toggles().forEach { (view, action) ->
            val active = editor.isActive(action)
            val background = ContextCompat.getColor(
                activity, if (active) R.color.color_bg_selected else R.color.color_surface_variant
            )
            val tint = ContextCompat.getColor(activity, if (active) R.color.text_on_selected else R.color.text_primary)
            view.setFillColor(background)
            when (view) {
                is ImageView -> ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(tint))
                is TextView -> view.setTextColor(tint)
            }
        }
    }

    private fun hex(color: Int) = String.format("%06X", color and 0xFFFFFF)

    private companion object {
        const val NO_COLOR = 0
        val TEXT_COLORS = listOf(
            0xFF20243A.toInt(), 0xFFD93A2F.toInt(), 0xFFEA580C.toInt(), 0xFF16A34A.toInt(),
            0xFF2563EB.toInt(), 0xFF4850F4.toInt(), 0xFFDB2777.toInt()
        )
        val HIGHLIGHTS = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFFA7F3D0.toInt(), 0xFFBFDBFE.toInt(),
            0xFFFBCFE8.toInt(), 0xFFFED7AA.toInt()
        )
    }
}
