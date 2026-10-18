package com.af010.creator.docreader.ui.main.files

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemDocumentBinding
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.ui.common.showTypeTile
import com.af010.creator.docreader.utils.extensions.dateText
import com.af010.creator.docreader.utils.extensions.folderName
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.sizeText
import com.ui.baselib.base.DiffAdapter

/** What closes a document row: the star and options button, an "Open" pill, or nothing. */
enum class RowEnd { Options, Open, Nothing }

/** Document rows as floating cards: the type tile, name and details, then [end]. */
class DocumentAdapter(private val end: RowEnd = RowEnd.Options) : DiffAdapter<RecentUi, ItemDocumentBinding>(
    areItemsTheSame = { old, new -> old.document.mediaId == new.document.mediaId },
    areContentsTheSame = { old, new -> old.sameContent(new) }
) {
    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int) =
        ItemDocumentBinding.inflate(inflater, parent, false)

    private var onItemClick: (RecentUi) -> Unit = {}
    private var onItemLongClick: (RecentUi) -> Unit = {}
    private var onItemMoreClick: (RecentUi) -> Unit = {}

    fun onDocumentClick(listener: (RecentUi) -> Unit) = apply { onItemClick = listener }
    fun onDocumentLongClick(listener: (RecentUi) -> Unit) = apply { onItemLongClick = listener }
    fun onMoreClick(listener: (RecentUi) -> Unit) = apply { onItemMoreClick = listener }

    /** Multi-select: a tick box leads each row and picked rows are ringed. */
    var selectionMode = false

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /** Search: the matching part of the name is marked, and the folder replaces the date. */
    var query = ""
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemDocumentBinding.bind(item: RecentUi, position: Int) {
        val context = root.context
        val doc = item.document
        tvType.showTypeTile(doc)
        tvName.text = marked(doc.name, ContextCompat.getColor(context, R.color.color_pro))
        tvMeta.text = context.getString(
            R.string.document_meta,
            doc.sizeText(context),
            if (query.isEmpty()) doc.dateText(context) else doc.folderName
        )

        root.isActivated = selectionMode && doc.mediaId in selectedIds
        ivCheck.isVisible = selectionMode
        ivCheck.imageAlpha = if (root.isActivated) 255 else 0
        ivFavorite.isVisible = item.isFavorite && !selectionMode && end == RowEnd.Options
        btnOpen.isVisible = end == RowEnd.Open && !selectionMode
        btnMore.isVisible = end == RowEnd.Options && !selectionMode

        root.setOnClickListener { onItemClick(item) }
        btnOpen.setOnClickListener { onItemClick(item) }
        root.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
        btnMore.setOnClickListener { onItemMoreClick(item) }
    }

    private fun marked(name: String, color: Int): CharSequence {
        val start = if (query.isEmpty()) -1 else name.indexOf(query, ignoreCase = true)
        if (start < 0) return name
        return SpannableString(name).apply {
            setSpan(BackgroundColorSpan(color), start, start + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
