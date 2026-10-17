package com.af010.creator.docreader.ui.main.files

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemDocumentBinding
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.utils.extensions.dateText
import com.af010.creator.docreader.utils.extensions.folderName
import com.af010.creator.docreader.utils.extensions.iconRes
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.af010.creator.docreader.utils.extensions.sizeText
import com.ui.baselib.base.DiffAdapter
import com.ui.baselib.extensions.visibleOrGone

class DocumentAdapter : DiffAdapter<RecentUi, ItemDocumentBinding>(
    areItemsTheSame = { old, new -> old.document.mediaId == new.document.mediaId },
    areContentsTheSame = { old, new -> old.sameContent(new) }
) {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemDocumentBinding.inflate(inflater, parent, false)

    private var onItemClick: ((RecentUi) -> Unit) = {}
    private var onItemLongClick: ((RecentUi) -> Unit) = {}
    private var onItemMoreClick: ((RecentUi) -> Unit) = {}

    fun onDocumentClick(listener: (RecentUi) -> Unit) = apply { onItemClick = listener }
    fun onDocumentLongClick(listener: (RecentUi) -> Unit) = apply { onItemLongClick = listener }
    fun onMoreClick(listener: (RecentUi) -> Unit) = apply { onItemMoreClick = listener }

    /** Multi-select: rows show a checkbox instead of the options button. */
    var selectionMode = false

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /** Search: the part of the name matching this text is highlighted, and the folder replaces the date. */
    var query = ""
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemDocumentBinding.bind(item: RecentUi, position: Int) {
        val context = root.context
        val doc = item.document
        ivType.setImageResource(doc.type.iconRes)
        tvName.text = highlighted(doc.name, ContextCompat.getColor(context, R.color.color_search_highlight))
        tvName.setCompoundDrawablesSized(
            com.intuit.sdp.R.dimen._10sdp,
            end = if (item.isFavorite && !selectionMode) R.drawable.ic_app_star_filled else 0
        )
        tvMeta.text = context.getString(
            R.string.document_meta,
            if (query.isEmpty()) doc.dateText(context) else doc.folderName,
            doc.sizeText(context)
        )

        ivCheck.visibleOrGone(selectionMode)
        ivCheck.setImageResource(
            if (doc.mediaId in selectedIds) R.drawable.ic_app_checkbox_on else R.drawable.ic_app_checkbox_off
        )
        btnMore.visibleOrGone(!selectionMode)

        root.setOnClickListener { onItemClick.invoke(item) }
        root.setOnLongClickListener {
            onItemLongClick.invoke(item)
            true
        }
        btnMore.setOnClickListener { onItemMoreClick.invoke(item) }
    }

    private fun highlighted(name: String, color: Int): CharSequence {
        val start = if (query.isEmpty()) -1 else name.indexOf(query, ignoreCase = true)
        if (start < 0) return name
        return SpannableString(name).apply {
            setSpan(
                BackgroundColorSpan(color), start, start + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
