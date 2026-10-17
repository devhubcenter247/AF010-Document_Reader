package com.af010.creator.docreader.ui.main.reader.pdf.organize

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemOrganizePageBinding
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.visibleOrGone
import com.ui.baselib.utils.dpToPx

/**
 * A page of the document being organized: page [source] of the file (negative: a new blank
 * page), turned [quarterTurns] clockwise. [id] tells copies of the same page apart.
 */
data class OrganizePage(val id: Long, val source: Int, val quarterTurns: Int = 0)

class OrganizePageAdapter(
    private val load: (source: Int, onReady: (Bitmap) -> Unit) -> Unit
) : BaseAdapter<OrganizePage, ItemOrganizePageBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemOrganizePageBinding.inflate(inflater, parent, false)

    private var onItemClick: ((OrganizePage) -> Unit) = {}
    fun onPageClick(listener: (OrganizePage) -> Unit) = apply {
        onItemClick = listener
    }

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemOrganizePageBinding.bind(item: OrganizePage, position: Int) {
        val context = root.context
        val selected = item.id in selectedIds
        tvNumber.text = (position + 1).toString()
        ivCheck.visibleOrGone(selected)
        card.helper.stColor = ContextCompat.getColor(
            context, if (selected) R.color.primary else R.color.color_stroke
        )
        card.helper.stWidth = context.dpToPx(if (selected) 2 else 1).toFloat()
        card.invalidate()

        // A page on its side has to fit the upright card.
        val sideways = item.quarterTurns % 2 != 0
        ivPage.rotation = item.quarterTurns % 4 * 90f
        ivPage.scaleX = if (sideways) SIDEWAYS_SCALE else 1f
        ivPage.scaleY = if (sideways) SIDEWAYS_SCALE else 1f
        ivPage.tag = item.id
        ivPage.setImageDrawable(null)
        if (item.source >= 0) load(item.source) { bitmap ->
            // The row may show another page by the time the picture is ready.
            if (ivPage.tag == item.id) ivPage.setImageBitmap(bitmap)
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }

    private companion object {
        const val SIDEWAYS_SCALE = 0.72f
    }
}
