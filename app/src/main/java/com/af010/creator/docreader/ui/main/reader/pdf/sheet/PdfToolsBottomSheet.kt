package com.af010.creator.docreader.ui.main.reader.pdf.sheet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.databinding.ItemSheetToolBinding
import com.af010.creator.docreader.databinding.SheetPdfToolsBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.showToolTile
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfReadingPrefs
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click

/** What the reader's More sheet starts besides a theme or a tool. */
enum class ReaderAction { TextView, ReadAloud, AutoScroll, Share, Rename, Info, Organize }

/**
 * The reader's More sheet: the page colours and the scroll direction (applied as soon as they are
 * picked), reading aids, the tools that make a new file from this one, and the file actions.
 */
class PdfToolsBottomSheet(
    activity: FragmentActivity,
    private var theme: Int,
    private var horizontal: Boolean,
    private val onTheme: (Int) -> Unit,
    private val onLayout: (horizontal: Boolean) -> Unit,
    private val onAction: (ReaderAction) -> Unit,
    private val onTool: (Tool) -> Unit
) : BaseBottomSheet<SheetPdfToolsBinding>(activity, SheetPdfToolsBinding::inflate) {

    override fun SheetPdfToolsBinding.onBind() {
        header.tvTitle.setText(R.string.fo_reading_comfort)
        header.btnClose.click { dismissSafe() }

        mapOf(
            cardNormal to PdfReadingPrefs.THEME_NORMAL,
            cardSepia to PdfReadingPrefs.THEME_SEPIA,
            cardNight to PdfReadingPrefs.THEME_NIGHT
        ).forEach { (card, cardTheme) ->
            card.click {
                if (theme == cardTheme) return@click
                theme = cardTheme
                // Night recreates the reader: the sheet goes with it.
                dismissSafe()
                onTheme(cardTheme)
            }
        }
        segmentLayout.setUp(R.string.fo_scroll_down, R.string.fo_swipe_pages) { index ->
            if (horizontal == (index == 1)) return@setUp
            horizontal = index == 1
            render()
            onLayout(horizontal)
        }

        mapOf(
            btnTextView to ReaderAction.TextView,
            btnReadAloud to ReaderAction.ReadAloud,
            btnAutoScroll to ReaderAction.AutoScroll,
            chipShare to ReaderAction.Share,
            chipRename to ReaderAction.Rename,
            chipInfo to ReaderAction.Info,
            chipOrganize to ReaderAction.Organize
        ).forEach { (view, action) ->
            view.click {
                dismissSafe()
                onAction(action)
            }
        }

        rcvTools.adapter = SheetToolAdapter().onToolClick { tool ->
            dismissSafe()
            onTool(tool)
        }.submitList(TOOLS)
        render()
    }

    private fun SheetPdfToolsBinding.render() {
        cardNormal.isSelected = theme == PdfReadingPrefs.THEME_NORMAL
        cardSepia.isSelected = theme == PdfReadingPrefs.THEME_SEPIA
        cardNight.isSelected = theme == PdfReadingPrefs.THEME_NIGHT
        segmentLayout.select(if (horizontal) 1 else 0)
    }

    private companion object {
        val TOOLS = listOf(
            Tool.Compress, Tool.Split, Tool.Watermark, Tool.PdfToImages,
            Tool.Translate, Tool.Ocr, Tool.Protect, Tool.Redact
        )
    }
}

class SheetToolAdapter : BaseAdapter<Tool, ItemSheetToolBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemSheetToolBinding.inflate(inflater, parent, false)

    private var onItemClick: ((Tool) -> Unit) = {}
    fun onToolClick(listener: (Tool) -> Unit) = apply {
        onItemClick = listener
    }

    override fun ItemSheetToolBinding.bind(item: Tool, position: Int) {
        ivIcon.showToolTile(item)
        // The sheet's tiles are narrow: "To images" says it in fewer letters.
        tvLabel.setText(if (item == Tool.PdfToImages) R.string.fo_to_images else item.title)
        tvLabel.isSelected = true
        ivPro.isVisible = item.isPremium && remoteConfig.paywallEnabled
        card.setOnClickListener { onItemClick.invoke(item) }
    }
}
