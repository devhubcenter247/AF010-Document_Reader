package com.af010.creator.docreader.ui.main.reader.office

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.af010.creator.docreader.databinding.ItemSlideSorterBinding
import com.af010.creator.docreader.databinding.ItemSlideThumbBinding
import com.af010.creator.docreader.ui.common.showPageThumb
import com.intuit.sdp.R as SdpR
import com.ui.baselib.base.BaseAdapter

/**
 * Slides as pictures: the strip under the slide being edited, or with [grid] the two-column
 * sorter. Items are slide numbers from 0; [load] draws one. In the strip an [ADD] item at the end
 * is the "+" tile. The picked slide is ringed in [accent], the file type's colour.
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

    private var onAdd: (() -> Unit) = {}
    fun onAddClick(listener: () -> Unit) = apply {
        onAdd = listener
    }

    @ColorInt
    var accent: Int = 0

    @ColorInt
    var accentSoft: Int = 0

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
        val adding = item == ADD
        (this as? ItemSlideThumbBinding)?.ivAdd?.let { add ->
            add.isVisible = adding
            add.setColorFilter(accent)
        }
        image.isVisible = !adding
        // Kept in the layout so the "+" tile is as tall as the slides.
        number.isInvisible = adding
        if (adding) {
            card.foreground = null
            card.background = shape(SdpR.dimen._8sdp) {
                setColor(accentSoft)
                val dash = px(SdpR.dimen._4sdp).toFloat()
                setStroke(px(SdpR.dimen._1sdp), accent, dash, dash / 2)
            }
            root.isSelected = false
            root.setOnClickListener { onAdd.invoke() }
            return
        }

        val picked = item == selected
        number.text = (item + 1).toString()
        root.isSelected = picked
        showPageThumb(card, image, number, picked, accent)

        image.tag = item
        image.setImageDrawable(null)
        load(item) { bitmap ->
            // The row may show another slide by the time the picture is ready.
            if (image.tag == item) image.setImageBitmap(bitmap)
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }

    private fun ViewBinding.px(@DimenRes id: Int) = root.resources.getDimensionPixelSize(id)

    private fun ViewBinding.shape(@DimenRes radius: Int, block: GradientDrawable.() -> Unit) = GradientDrawable().apply {
        cornerRadius = px(radius).toFloat()
        block()
    }

    companion object {
        /** The strip's last item: the "+" tile that adds a slide. */
        const val ADD = -1
    }
}
