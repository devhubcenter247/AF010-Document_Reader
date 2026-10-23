package com.af010.creator.docreader.ui.main.tools.split

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemPageThumbBinding
import com.af010.creator.docreader.ui.common.showPageThumb
import com.ui.baselib.base.BaseAdapter

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
        tvNumber.text = (item + 1).toString()
        root.isSelected = item in selectedPages
        showPageThumb(card, ivPage, tvNumber, root.isSelected)

        ivPage.tag = item
        ivPage.setImageDrawable(null)
        load(item) { bitmap ->
            // The row may show another page by the time the picture is ready.
            if (ivPage.tag == item) ivPage.setImageBitmap(bitmap)
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
