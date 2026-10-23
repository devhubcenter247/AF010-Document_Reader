package com.af010.creator.docreader.ui.main.reader.pdf.organize

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemOrganizePageBinding
import com.af010.creator.docreader.ui.common.showPageThumb
import com.af010.creator.docreader.ui.dialog.hostAccent
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.visibleOrGone

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
        val selected = item.id in selectedIds
        tvNumber.text = (position + 1).toString()
        ivCheck.visibleOrGone(selected)
        root.isSelected = selected
        showPageThumb(card, ivPage, tvNumber, selected)
        ivCheck.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(root.context, root.context.hostAccent() ?: R.color.primary)
        )

        ivPage.tag = item.id
        ivPage.setImageDrawable(null)
        if (item.source >= 0) load(item.source) { bitmap ->
            // The row may show another page by the time the picture is ready.
            if (ivPage.tag == item.id) ivPage.setImageBitmap(bitmap.turned(item.quarterTurns))
        }
        root.setOnClickListener { onItemClick.invoke(item) }
    }

    /** The picture turned, not the view: the sheet and its ring stay upright and fitCenter fits a page on its side. */
    private fun Bitmap.turned(quarterTurns: Int): Bitmap {
        val degrees = quarterTurns.mod(4) * 90f
        if (degrees == 0f) return this
        return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
    }
}
