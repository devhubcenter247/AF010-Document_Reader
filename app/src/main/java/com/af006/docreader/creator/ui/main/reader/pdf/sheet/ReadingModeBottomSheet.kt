package com.af010.creator.docreader.ui.main.reader.pdf.sheet

import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetReadingModeBinding
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfReadingPrefs
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.utils.dpToPx

enum class ReadingAction { TextView, ReadAloud, AutoScroll }

/** Page colours, the text view, read aloud, auto-scroll and the scroll direction of the reader. */
class ReadingModeBottomSheet(
    private val activity: FragmentActivity,
    private var theme: Int,
    private var horizontal: Boolean,
    private val onTheme: (Int) -> Unit,
    private val onLayout: (horizontal: Boolean) -> Unit,
    private val onAction: (ReadingAction) -> Unit
) : BaseBottomSheet<SheetReadingModeBinding>(activity, SheetReadingModeBinding::inflate) {

    override fun SheetReadingModeBinding.onBind() {
        header.tvTitle.setText(R.string.reading_mode)
        header.btnClose.click { dismissSafe() }

        mapOf(
            cardNormal to PdfReadingPrefs.THEME_NORMAL,
            cardNight to PdfReadingPrefs.THEME_NIGHT,
            cardSepia to PdfReadingPrefs.THEME_SEPIA
        ).forEach { (card, cardTheme) ->
            card.click {
                if (theme == cardTheme) return@click
                theme = cardTheme
                render()
                dismissSafe()
                onTheme(cardTheme)
            }
        }
        mapOf(
            rowTextView to ReadingAction.TextView,
            rowReadAloud to ReadingAction.ReadAloud,
            rowAutoScroll to ReadingAction.AutoScroll
        ).forEach { (row, action) ->
            row.click {
                dismissSafe()
                onAction(action)
            }
        }
        rowLayout.click {
            horizontal = !horizontal
            dismissSafe()
            onLayout(horizontal)
        }
        render()
    }

    private fun SheetReadingModeBinding.render() {
        listOf(cardNormal, cardNight, cardSepia).forEachIndexed { index, card ->
            val selected = index == theme
            card.setStroke(activity.dpToPx(if (selected) 2 else 1), ContextCompat.getColor(
                activity, if (selected) R.color.primary else R.color.color_stroke
            ))
            card.invalidate()
        }
        tvLayout.setText(if (horizontal) R.string.layout_horizontal else R.string.layout_vertical)
    }
}
