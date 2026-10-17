package com.af010.creator.docreader.ui.main.reader.office

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemSlideSorterBinding
import com.af010.creator.docreader.databinding.ItemSlideThumbBinding
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.utils.dpToPx

/**
 * Slides as pictures: the strip under the slide being edited, or with [grid] the two-column
 * sorter. Items are slide numbers from 0; [load] draws one.
 */
class SlideAdapter(
    private val grid: Boolean,
    private val load: (slide: Int, onReady: (Bitmap) -> Unit) -> Unit
) : BaseAdapter<Int, ViewBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = if (grid) ItemSlideSorterBinding.inflate(inflater, parent, false)
    else ItemSlideThumbBinding.inflate(inflater, parent, false)

    private var onItemClick: ((Int) -> Unit) = {}
    fun onSlideClick(listener: (slide: Int) -> Unit) = apply {
        onItemClick = listener
    }

    var selected: Int = -1
        set(value) {
            if (field == value) return
            val old = field
            field = value
            notifyItemChanged(old)
            notifyItemChanged(value)
        }

    override fun ViewBinding.bind(item: Int, position: Int) {
        val (card, image, number) = when (this) {
            is ItemSlideSorterBinding -> Triple(card, ivSlide, tvNumber)
            is ItemSlideThumbBinding -> Triple(card, ivSlide, tvNumber)
            else -> return
        }
        val context = root.context
        val isSelected = item == selected
        number.text = (item + 1).toString()
        card.setStroke(context.dpToPx(if (isSelected) 2 else 1), ContextCompat.getColor(
            context, if (isSelected) R.color.primary else R.color.color_stroke
        ))
        card.invalidate()

        image.tag = item
        image.setImageDrawable(null)
        load(item) { bitmap ->
            // The row may show another slide by the time the picture is ready.
            if (image.tag == item) image.setImageBitmap(bitmap)
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
