package com.af010.creator.docreader.ui.main.reader.pdf

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.af010.creator.docreader.databinding.ItemSearchMatchBinding
import com.reader.pdfviewer.model.SearchResult
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
enum class AnnotateTool(@StringRes val title: Int, @DrawableRes val icon: Int, @StringRes val hint: Int) {
    Select(R.string.annot_select, R.drawable.ic_fo_hand, R.string.fo_hint_select),
    Pen(R.string.annot_pen, R.drawable.ic_fo_pen_line, R.string.fo_hint_pen),
    Highlight(R.string.annot_highlight, R.drawable.ic_fo_highlight, R.string.fo_hint_highlight),
    Text(R.string.annot_text, R.drawable.ic_fo_type, R.string.fo_hint_text),
    Note(R.string.annot_note, R.drawable.ic_fo_note, R.string.fo_hint_note),
    Shapes(R.string.annot_shapes, R.drawable.ic_fo_shape, R.string.fo_hint_shapes),
    Image(R.string.annot_image, R.drawable.ic_fo_photo, R.string.fo_hint_image),
    Eraser(R.string.annot_eraser, R.drawable.ic_fo_eraser, R.string.fo_hint_eraser)
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
        ivIcon.contentDescription = root.context.getString(item.title)
        ivIcon.isSelected = item == selected
        ivIcon.setOnClickListener { onItemClick.invoke(item) }
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
        tvTitle.typeface = ResourcesCompat.getFont(context, if (top) R.font.nunito_800 else R.font.nunito_600)
        tvTitle.setTextColor(
            ContextCompat.getColor(context, if (top) R.color.text_primary else R.color.text_secondary)
        )
        tvPage.text = (item.page + 1).toString()
        btnRemove.visibleOrGone(item.removable)
        ivStar.visibleOrGone(item.removable)
        val start = if (item.removable) indent / 2 else indent * (item.depth.coerceAtMost(4) + 1)
        row.updatePadding(left = start)
        row.isSelected = position == currentRow
        if (row.isSelected) tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_on_selected))
        row.setOnClickListener { onItemClick.invoke(item) }
        btnRemove.setOnClickListener { onRemove.invoke(item) }
    }
}

/** The matches of a search in the open PDF; [selected] is the one on screen. */
class SearchMatchAdapter : BaseAdapter<SearchResult, ItemSearchMatchBinding>() {
    private var query = ""
    private var selected = 0
    private var onItemClick: (Int) -> Unit = {}

    fun onMatchClick(listener: (index: Int) -> Unit) = apply { onItemClick = listener }

    fun show(results: List<SearchResult>, query: String, selected: Int) {
        this.query = query
        this.selected = selected
        submitList(results)
    }

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemSearchMatchBinding.inflate(inflater, parent, false)

    override fun ItemSearchMatchBinding.bind(item: SearchResult, position: Int) {
        val context = root.context
        tvPage.text = context.getString(R.string.page_number, item.page + 1)
        tvSnippet.text = snippet(item.content, context.getColor(R.color.text_primary))
        root.isSelected = position == selected
        root.setOnClickListener { onItemClick(position) }
    }

    /** The words around the first match, the match itself in bold. */
    private fun snippet(content: String, strong: Int): CharSequence {
        val text = content.replace(Regex("\\s+"), " ").trim()
        val at = text.indexOf(query, ignoreCase = true)
        if (at < 0 || query.isEmpty()) return text
        val start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
        val prefix = if (start > 0) "…" else ""
        val shown = prefix + text.substring(start)
        val from = at - start + prefix.length
        return SpannableString(shown).apply {
            setSpan(StyleSpan(Typeface.BOLD), from, from + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(strong), from, from + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private companion object {
        const val SNIPPET_BEFORE = 18
    }
}
