package com.af010.creator.docreader.ui.main.tools.split

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemPageThumbBinding
import com.af010.creator.docreader.databinding.ItemSplitRangeBinding
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.utils.dpToPx

/** One-based, inclusive page range typed by the user; a bound is null while its field is empty. */
data class PageRange(val from: Int?, val to: Int?) {
    fun isValid(pageCount: Int): Boolean =
        from != null && to != null && from >= 1 && to >= from && to <= pageCount
}

class PageRangeAdapter : BaseAdapter<PageRange, ItemSplitRangeBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ItemSplitRangeBinding = ItemSplitRangeBinding.inflate(inflater, parent, false).apply {
        // Typing updates the item in place: rebinding the row would move the cursor.
        edtFrom.doAfterTextChanged { edit(root.tag as? Int) { range -> range.copy(from = it.toNumber()) } }
        edtTo.doAfterTextChanged { edit(root.tag as? Int) { range -> range.copy(to = it.toNumber()) } }
    }

    private var onRangesChanged: (() -> Unit) = {}
    private var onItemRemove: ((Int) -> Unit) = {}

    fun onChanged(listener: () -> Unit) = apply { onRangesChanged = listener }
    fun onRemoveClick(listener: (position: Int) -> Unit) = apply { onItemRemove = listener }

    override fun ItemSplitRangeBinding.bind(item: PageRange, position: Int) {
        root.tag = null
        edtFrom.setText(item.from?.toString().orEmpty())
        edtTo.setText(item.to?.toString().orEmpty())
        root.tag = position
        btnRemove.setOnClickListener { onItemRemove.invoke(position) }
    }

    private fun edit(position: Int?, change: (PageRange) -> PageRange) {
        val list = getMutableList()
        val current = list.getOrNull(position ?: return) ?: return
        list[position] = change(current)
        onRangesChanged()
    }

    private fun CharSequence?.toNumber(): Int? = this?.toString()?.toIntOrNull()
}

/** Page thumbnails to tap; [load] hands back the picture of a page once it is drawn. */
class PageThumbAdapter(
    private val load: (page: Int, onReady: (Bitmap) -> Unit) -> Unit
) : BaseAdapter<Int, ItemPageThumbBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemPageThumbBinding.inflate(inflater, parent, false)

    private var onItemClick: ((Int) -> Unit) = {}
    fun onPageClick(listener: (page: Int) -> Unit) = apply {
        onItemClick = listener
    }

    var selectedPages: Set<Int> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemPageThumbBinding.bind(item: Int, position: Int) {
        val context = root.context
        val selected = item in selectedPages
        tvNumber.text = (item + 1).toString()
        tvNumber.setFillColor(ContextCompat.getColor(
            context, if (selected) R.color.primary else R.color.color_ink
        ))
        tvNumber.invalidate()
        card.setStroke(context.dpToPx(if (selected) 2 else 1), ContextCompat.getColor(
            context, if (selected) R.color.primary else R.color.color_stroke
        ))
        card.invalidate()

        ivPage.tag = item
        ivPage.setImageDrawable(null)
        load(item) { bitmap ->
            // The row may show another page by the time the picture is ready.
            if (ivPage.tag == item) ivPage.setImageBitmap(bitmap)
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
