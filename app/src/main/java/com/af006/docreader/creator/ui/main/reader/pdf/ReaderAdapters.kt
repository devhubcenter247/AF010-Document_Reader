package com.af010.creator.docreader.ui.main.reader.pdf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemAnnotateToolBinding
import com.af010.creator.docreader.databinding.ItemTocRowBinding
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.visibleOrGone

/** The tools of the Annotate bar. Shapes opens a choice of four. */
enum class AnnotateTool(@StringRes val title: Int, @DrawableRes val icon: Int) {
    Select(R.string.annot_select, R.drawable.ic_app_hand),
    Pen(R.string.annot_pen, R.drawable.ic_app_pen),
    Highlight(R.string.annot_highlight, R.drawable.ic_app_highlight),
    Text(R.string.annot_text, R.drawable.ic_app_text),
    Note(R.string.annot_note, R.drawable.ic_app_note),
    Shapes(R.string.annot_shapes, R.drawable.ic_app_shape),
    Image(R.string.annot_image, R.drawable.ic_app_image),
    Eraser(R.string.annot_eraser, R.drawable.ic_app_eraser)
}

class AnnotateToolAdapter : BaseAdapter<AnnotateTool, ItemAnnotateToolBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemAnnotateToolBinding.inflate(inflater, parent, false)

    private var onItemClick: ((AnnotateTool) -> Unit) = {}
    fun onToolClick(listener: (AnnotateTool) -> Unit) = apply {
        onItemClick = listener
    }

    var selected: AnnotateTool = AnnotateTool.Highlight
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemAnnotateToolBinding.bind(item: AnnotateTool, position: Int) {
        ivIcon.setImageResource(item.icon)
        tvLabel.setText(item.title)
        ivIcon.isSelected = item == selected
        tvLabel.isSelected = item == selected
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}

/** A row of the contents panel: an outline entry ([depth] levels in) or a bookmark ([removable]). */
data class TocRow(
    val title: String,
    val page: Int,
    val depth: Int = 0,
    val removable: Boolean = false
)

class TocAdapter : BaseAdapter<TocRow, ItemTocRowBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemTocRowBinding.inflate(inflater, parent, false)

    private var onItemClick: ((TocRow) -> Unit) = {}
    fun onRowClick(listener: (TocRow) -> Unit) = apply {
        onItemClick = listener
    }

    private var onRemove: ((TocRow) -> Unit) = {}
    fun onRemoveClick(listener: (TocRow) -> Unit) = apply {
        onRemove = listener
    }

    /** The row the reader is in gets the selected background. */
    var currentRow: Int = -1

    override fun ItemTocRowBinding.bind(item: TocRow, position: Int) {
        val context = root.context
        val top = item.depth == 0
        val indent = context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
        tvTitle.text = item.title
        tvTitle.typeface = ResourcesCompat.getFont(context, if (top) R.font.nunito_900 else R.font.nunito_700)
        tvTitle.setTextColor(
            ContextCompat.getColor(context, if (top) R.color.text_primary else R.color.text_secondary)
        )
        tvPage.text = (item.page + 1).toString()
        btnRemove.visibleOrGone(item.removable)
        row.updatePadding(left = indent * (item.depth.coerceAtMost(4) + 1))
        row.setBackgroundColor(
            ContextCompat.getColor(
                context,
                if (position == currentRow) R.color.color_bg_selected else android.R.color.transparent
            )
        )
        row.setOnClickListener { onItemClick.invoke(item) }
        btnRemove.setOnClickListener { onRemove.invoke(item) }
    }
}
